package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.json.*;

import jakarta.xml.bind.annotation.XmlElement; 


import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
//import edu.wisc.game.formatter.*;

/** Retrieving various pages needed by the client to customize the player's pregame and postgame experience. Discussed with Kevin, Paul and Gary (2022-11-10).

<p>Maintaining compatibility with several instruction booklets that used to be hard-coded in the client, and controlled by the config parameter "init":
<pre>
        ...(init === 3
          ? INSTRUCTIONS_R =  [R1, R2, R3, R4, R5, R6, R7, R8] = instructions_relational
          : init === 11
          ? INSTRUCTIONS_11 = [I11_1 ... ] = doubling_scheme
          : init === 2
          ? INSTRUCTIONS_2  = [I2_1 ... ] = stalemate_instructions

          : INSTRUCTIONS_1 =  [I1_1 ...] = instructions_NOBINS
</pre>  

 */

@Path("/PregameService") 
public class PregameService {


    @GET
    @Path("/getPage") 
    @Produces(MediaType.APPLICATION_JSON)
    public Page display(@QueryParam("playerId") String playerId,
			@QueryParam("name") String name)   {
	return new Page(playerId, name);
    }


    /** Some info  + error code */
    public static class PregameResponseBase  extends ResponseBase {
	/** The directory for the player's pregame experience bundle */
	File d;
	/** The directory for the instruction booklet */
	File imgDir;
	int bookletSize;
	File[] bookletPages;

        public int getBookletSize() { return bookletSize; }
        @XmlElement
        public void setBookletSize(int _bookletSize) { bookletSize = _bookletSize; }

        public String getPath() { return d==null? null: d.getPath(); }

	public File[] _getBookletPages() { return bookletPages;}
     
	/** Identifies and tests the pregame experience directory for the
	    player's experiment plan. The main parameter for that is
	    "pregame", but for legacy (pre ver. 6.*) plans we also look
	    at "init".

	    @param pid playerId. The server will look up the  pregame experience directory for that player's experiment plan.
	    @param name The pregame experience file to retrieve from the 
	    PGE directory, e.g. "consent.html"
	 */
	 public PregameResponseBase(String pid) {
	    try {
		PlayerInfo x = PlayerResponse.findPlayerInfo(null, pid);
		if (x==null) {
		    hasError("Player not found: " + pid);
		    return;
		}
		ParaSet para = x.getFirstPara();
		if (para==null) {
		    hasError("Don't know the players parameter set");
		    return;
		}
		String pregame = (String)para.get("pregame");
		if (pregame==null || pregame.trim().equals("")) {
		    int init = para.getInt("init", true, -1);
		    if (init < 0) {
			pregame = "default";
		    } else if (init == 3) {
			// INSTRUCTIONS_R =  [R1, R2, R3, R4, R5, R6, R7, R8] = instructions_relational
			pregame = "relational";
		    } else if (init == 11) {
			// INSTRUCTIONS_11 = [I11_1 ... ] = doubling_scheme
			pregame = "doubling";
		    } else if (init == 2) {
			// INSTRUCTIONS_2 = [I2_1 ... ] = stalemate_instructions
			pregame = "stalemate";
		    } else {
			pregame = "default";
		    }
		}
		d = Files.pregameDir(pregame);
		if (!d.exists() || !d.isDirectory()) {
		    hasError("No such server directory: " + d);
		    return;
		}
		File imgDir = new File(d, "instructions");
		if (!imgDir.exists() || !imgDir.isDirectory()) {
		    hasError("No such server directory: " + imgDir);
		    return;
		}
		bookletPages = listBookletPages(imgDir);
		bookletSize = 	bookletPages.length;
		if (bookletSize==0) {
		    hasError("No suitably named images found in booklet directory '" + imgDir + "'");
		    return;		    
		}
		
	    } catch(Exception ex) {
		setError(true);
		String msg = ex.getMessage();
		if (msg==null) msg = "Unknown internal error ("+ex+"); see stack trace in the server logs";
		setErrmsg(msg);
		System.err.print(ex);
		ex.printStackTrace(System.err);
	    } finally {
		//Logging.info("Pregame.Page(pid="+ pid+", name="+name+"): returning:\n" +			 JsonReflect.reflectToJSONObject(this, true));
	    }	    
	}
    }

    
    /** Auxiliary class for sorting PNG files "numerically. One could have
	done it in a rather more compact way in Perl */
    static private class FileEntry implements Comparable {
	private static Pattern pat =  Pattern.compile("[0-9]+");
	final File f;
	final boolean bad;
	final String name, prefix;
	final int n;
	FileEntry(File _f) {
	    f = _f;
	    name = f.getName();
	    Matcher m = pat.matcher(name);
	    if (m.find()) {
		bad = false;
		prefix = name.substring(0, m.start());
		String num  = m.group().replaceAll("^0+", "");
		n = Integer.parseInt(num);
	    } else {
		bad = true;
		prefix = "";
		n = -1;
	    } 
	     
	}

	public int compareTo(Object _o) {
	    if (!(_o instanceof FileEntry)) throw new IllegalArgumentException();
	    FileEntry o = (FileEntry)_o;	    

	    int d = n - o.n;
	    if (d==0) d = prefix.compareTo(o.prefix);
	    if (d==0) d = name.compareTo(o.name);
	    return d;
	}
    }

    /** Checks if the file name is of the PNG or JPG type
	@return "image/jpeg" or "image/png", or null
     */
    static String getMimeTime(String name) {
	String s= name.toLowerCase();
	return s.endsWith(".png") ? "image/png" :
	    (s.endsWith(".jpg") || s.endsWith(".jpeg")) ? "image/jpeg":
	    null;
    }

    /** Lists booklet pages (image files) in the specified directory,
	in the proper (numeric) order. Each file name must contain a
	number in it. */
    private static File[] listBookletPages(File dir) {
	Vector<FileEntry> v= new Vector<>();
	for(File f: dir.listFiles()) {
	    if (!f.isFile()) continue;
	    String name = f.getName();
	    if (getMimeTime(name)==null) continue;
	    FileEntry e = new FileEntry(f);
	    if (e.bad) continue;
	    v.add(e);
	}
	FileEntry[] a = v.toArray(new FileEntry[0]);
	Arrays.sort(a);
	File[] q = new File[a.length];
	for(int j=0; j<a.length; j++) q[j] = a[j].f;
	return q;		
    }
    
    /** An HTML page + error code */
    public static class Page  extends PregameResponseBase {

	/** The HTML etc text that the client needs to process */
	String value=null;
        public String getValue() { return value; }
        @XmlElement
        public void setValue(String _value) { value = _value; }


	/** @param pid playerId. The server will look up the  pregame experience directory for that player's experiment plan.
	    @param name The pregame experience file to retrieve from the 
	    PGE directory, e.g. "consent.html"
	 */
	Page(String pid, String name) {
	    super(pid);
	    if (error) return;
	    try {
		File f = new File(d, name);
		if (!f.exists() || !f.canRead()) {
		    hasError("Cannot read server file: " + f);
		    return;
		}
		value = Util.readTextFile(f);
	    } catch(Exception ex) {
		setError(true);
		String msg = ex.getMessage();
		if (msg==null) msg = "Unknown internal error ("+ex+"); see stack trace in the server logs";
		setErrmsg(msg);
		System.err.print(ex);
		ex.printStackTrace(System.err);
	    } finally {
		//Logging.info("Pregame.Page(pid="+ pid+", name="+name+"): returning:\n" +			 JsonReflect.reflectToJSONObject(this, true));
	    }

	    
	}
    }

    
    @GET
    @Path("/getBookletSize") 
    @Produces(MediaType.APPLICATION_JSON)
    public BookletSize display(@QueryParam("playerId") String playerId)   {
	return new BookletSize(playerId);
    }

    /** An HTML page + error code */
    public static class BookletSize  extends PregameResponseBase {
	/** The HTML etc text that the client needs to process */
	String value=null;
	/** @param pid playerId. The server will look up the  pregame experience directory for that player's experiment plan.
	 */
	BookletSize(String pid) {
	    super(pid);

	}
    }

}

