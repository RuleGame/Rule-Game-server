package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.json.*;

import javax.persistence.*;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.math.*;
import edu.wisc.game.sql.MlcLog.LogFormat;

import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

/** Uploading and processing results files by MLC
    participants. Discussed in email in June-July 2022.  Extended in
    Jan 2023 to support compact format.

    <p>This service includes calls for uploading data, and for
    various comparison pages.
 */

@Path("/MlcUploadService") 
public class MlcUploadService {
    private static HTMLFmter  fm = new HTMLFmter();

    /** As REST is stateless, this table is used for authorization, instead of sessions. It is worked by MlcLoginServlet (via LoginServlet) */
    private static HashMap<String,String> userKeyTable = new HashMap<String,String>();

    private static Random random = new Random();

    /** Used by the MlcLoginServlet etc */
    public static synchronized String giveKey(String nickname) {
	if (nickname==null) return null;
	
	String s = userKeyTable.get(nickname);
	if (s==null) {
	    byte bytes[] = new byte[10];	    
	    random.nextBytes( bytes);
	    StringBuffer x = new StringBuffer();
	    for(byte b: bytes) {
		x.append((char)('A' + (b & 0x0F)));
		x.append((char)('A' + (b >> 4)));
	    }
	    userKeyTable.put(nickname, s = x.toString());
	}
	return s;
    }

    private boolean matched(String  nickname, String key) {
	if (nickname==null) return false;
	String s = userKeyTable.get(nickname);
	return s!=null && s.equals(key);
    }

    /** Ensures that the header line appears in the standard
	format, with the leading '#'
    */
    private static String prepareHeaderLine(String s) {
	s=s.trim();
	if (!s.startsWith("#")) s = "#" + s;
	return s;
    }

    
    /**  Allows an MLC participant to upload a file with the results
	 of his ML algorithm's performance. For each section of the
	 input file (a sequence of lines, each of which represents an
	 episode), creates a single MlcEntry object containing the
	 aggregate statistics for the runs represented by that section.
	 

	 <p>
	 Long format:
<pre>
nickname,rule_name,trial_id,board_id,number_of_pieces,number_of_moves,move_acc,if_clear
RandomTest,alternateShape2Bucket_color2Bucket,0,0,9,29,0.3103448275862069,1
RandomTest,alternateShape2Bucket_color2Bucket,0,1,9,20,0.45,1
...
</pre>
Compact format: see captive.html#results
<pre>
#.nickname,rule_name,trial_id
#number_of_moves,number_of_errors,if_clear
.RandomTest,alternateShape2Bucket_color2Bucket,0
47,38,1
36,27,1
</pre>
    */
    @Path("/uploadFile")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public String uploadFile(@FormDataParam("nickname") String nickname,
			     @FormDataParam("key") String key,
			     @FormDataParam("file") FormDataBodyPart parts				    ) {
	
	String title="", body="";

	try {

	    if (nickname==null || key==null) {
		throw new IllegalInputException("No nickname/key parameter in the form. (n="+nickname+", k="+key+")");		
	    }

	    if (!matched(nickname, key)) {
		throw new IllegalInputException("Authentication failed. Go back and try to log out and login again");
	    }

	    if (parts==null) {
		throw new IllegalInputException("No data uploaded");		
	    }

	    title ="File uploading for MLC participant " + fm.tt(nickname);

	    File d = Files.mlcUploadDir(nickname, false);

	    Date now = new Date();
	    
	    for(BodyPart part : parts.getParent().getBodyParts()){
		InputStream file = part.getEntityAs(InputStream.class);
		ContentDisposition fileDisposition = part.getContentDisposition();
		String fileName=fileDisposition.getFileName();
		String type=fileDisposition.getType();

		if (fileName==null) continue; // some other param than a fle

		body += fm.para("Writing "+ fm.tt(fileName) + " ...");

		File g = new File(d, fileName);
		/*
		OutputStream out = new FileOutputStream(g);
		int b;
		int cnt=0;
		while ((b=file.read())>=0) {
		    out.write(b);
		    cnt ++;
		}
		out.close();
		*/

		PrintWriter w = new PrintWriter(new FileWriter(g));
		InputStreamReader r0 = new InputStreamReader(file);
		LineNumberReader r = new LineNumberReader(r0);
		String line = null;
		long lineCnt = 0, charCnt=0;
		Vector<MlcEntry> results = new Vector<>();
		MlcEntry e = null;

		LogFormat format = null;
		int episodeNo=0;
		
		while((line = r.readLine())!=null) {
		    if (lineCnt==0) {
			String s = prepareHeaderLine(line);
			
			String [] hLong =  LogFormat.Long.header(),
			    hCompact =  LogFormat.Compact.header();

			if (s.equals(hLong[0])) {
			    format = LogFormat.Long;
			} else if (s.equals(hCompact[0])) {
			    w.println(line);
			    line = r.readLine();
			    if (line==null) {
				body += fm.para("Error: unexpected end of file after the first header line");
				break;
			    }


			    s = prepareHeaderLine(line);
			    if (s.equals(hCompact[1])) {
				format = LogFormat.Compact;
			    } else {
				body += fm.para("Error: unexpected 2nd line of header. Expected: " + fm.pre(hCompact[1]) + "Found: " + fm.pre(line));
				break;
			    }

			} else  {
			    body += fm.para("Error: unexpected header. Expected: " + fm.pre(hLong[0]) + ", or "+fm.pre(hCompact[0])+". Found: " + fm.pre(line));
			    break;
			} 
		    } else { // normal (non-header) line

			String q[] = line.trim().split(",");
			StringBuffer errmsg = new StringBuffer();
			Boolean z=null;

			if (format == LogFormat.Long) {
			    int M = 8;
			    if (q.length!=M) {
				body += fm.para("Error: unexpected number of columns in line "+(lineCnt+1)+" Expected: " + M+ " columns, found " + q.length + ". Line: " + fm.pre(line));
				break;
			    }	
			    
			    String _nickname = q[0];
			    if (!_nickname.equals(nickname)) {
				body += fm.para("Error: unexpected nickname in line "+(lineCnt+1)+" Expected: " + fm.tt(nickname)+ ", found " + fm.tt(_nickname) + ". Line: " + fm.pre(line));
				break;
			    }
			    
			    String rule_name = q[1];
			    int runNo = Integer.parseInt(q[2]);
			    
			    if (e!=null && e.matches(nickname, rule_name, runNo)) {
				// keep going
			    } else {
				e = new MlcEntry(nickname, rule_name, runNo, now);
				results.add(e);
			    }
		 			  
			
			    episodeNo = Integer.parseInt(q[3]);			
			    int number_of_pieces =Integer.parseInt(q[4]);
			    int number_of_moves=Integer.parseInt(q[5]);
			    double move_acc = Double.parseDouble(q[6]);
			    boolean if_clear = q[7].equals("1");
			
			    z = e.addEpisode( episodeNo,
					      number_of_pieces,
					      number_of_moves,
					      move_acc,
					      if_clear,
					      errmsg);
			} else if (format == LogFormat.Compact) {
			    if (q[0].startsWith(".")) {
				//.RandomTest,alternateShape2Bucket_color2Bucket,0
				
				episodeNo = 0;

				String _nickname = q[0].substring(1);
				if (!_nickname.equals(nickname)) {
				    body += fm.para("Error: unexpected nickname in line "+(lineCnt+1)+" Expected: " + fm.tt(nickname)+ ", found " + fm.tt(_nickname) + ". Line: " + fm.pre(line));
				    break;
				}
			    
				String rule_name = q[1];
				int runNo = Integer.parseInt(q[2]);
			    
				if (e!=null && e.matches(nickname, rule_name, runNo)) {
				   // keep going
				} else {
				    e = new MlcEntry(nickname, rule_name, runNo, now);
				    results.add(e);
				}
			    } else {
				if (e==null) throw new IllegalArgumentException("episodeNo etc not set");
				int number_of_moves=Integer.parseInt(q[0]);
				int number_of_errors =Integer.parseInt(q[1]);
				boolean if_clear = q[2].equals("1");
			    
				z = e.addEpisode2( episodeNo++,
						   number_of_moves,
						   number_of_errors,
						   if_clear,
						   errmsg);
			    }

			} else throw new IllegalArgumentException("Illegal format: " + format);
			if (z!=null && !z) {
			    body += fm.para("Error in line "+(lineCnt+1)+". " + errmsg + ". Line: " + fm.pre(line))
				+ fm.para("Message: " + errmsg)
				+ fm.para("Format=" + format);
			    break;
			}
								   
		    }
		    w.println(line);
		    lineCnt++;
		    charCnt += line.length();
		}
		file.close();
		w.close();
		
		body += fm.para("Copied " + lineCnt + " lines, wrote file "+ g+ " with the length of " +  g.length() + " bytes");	

		body += fm.para("Processed data for " + results.size() + " runs");
			  				 
		body += summaryTable(results);


		body += fm.hr();
		body += fm.para(fm.a("../../mlc/", "Back to the MLC participant's dashboard"));


		saveToDatabase(results, nickname);

		//if (myRules.size()>0) {    body += fm.h2("Compare ");	     }
		
		
	    }
	} catch(Exception ex) {
	    System.err.println(ex);
	    ex.printStackTrace(System.err);
	    title ="Error, nickname = " + nickname;
	    if (ex instanceof IllegalInputException) {
		body = ex.getMessage();
	    } else {
		body = ex.toString();
	    }
	}
	return fm.html(title, body);		
    }

    /** Removes from the database any data already stored for this
	algo nickname, and saves the new data instead.
	@param results the new data (summarizing the content of an uploaded file)
     */
    void saveToDatabase(Vector<MlcEntry> results, String nickname) {	
	EntityManager em=null;
	try {
	    em = Main.getNewEM();
	    em.getTransaction().begin();

	    Query q = em.createQuery("select m from MlcEntry m where m.nickname=:n");
	    q.setParameter("n", nickname);
	    List<MlcEntry> res = (List<MlcEntry>)q.getResultList();
	    for(MlcEntry e: res) {
		em.remove(e);
	    }
	    for(MlcEntry e: results) {
		em.persist(e);	
	    }

	    
	    em.getTransaction().commit();

	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}

    }

    /** Produces a summary table for a player's data on a given 
	rule set, or on several rule sets */
    private static String summaryTable(Collection<MlcEntry> entries) {

	Vector<String> rows = new Vector<>();
	rows.add( fm.tr( fm.th("Rule Set") +
			 fm.th("Run No.") +
			 fm.th("Episodes") +
			 fm.th("Total moves") +
			 fm.th("Total errors") +
			 fm.th("Learned?") +
			 fm.th("Episodes till learned") +
			 fm.th("Moves till learned")));
	String lastRule = "";
	//Vector<String> myRules = new Vector<>();
	//	HashSet<String> myRulesHash = new HashSet<>();
	
	for(MlcEntry e: entries) {
	    String rule = e.getRuleSetName();

	    String s = 	(rule.equals(lastRule))? fm.td(""): fm.td(rule);
	    lastRule = rule;
	    s +=
		fm.td( ""+e.getRunNo())+
		fm.td( ""+e.getTotalEpisodes()) +		      
		fm.td("" +e.getTotalMoves()) +		      
		fm.td("" +e.getTotalErrors()) +		      
		fm.th(e.getLearned()? "Yes" : "No");
	    if (e.getLearned()) {
		s += fm.td("" + e.getEpisodesUntilLearned() ) +
		    fm.td("" +e.getMovesUntilLearned());
	    }
	    rows.add(fm.tr(s));
	    
	    //  if (!myRulesHash.contains(rule)) {
	    //	myRulesHash.add(rule);
	    //	myRules.add(rule);			
	    //}
	    
	}			      
			  				 
	return fm.table( "border=\"1\"", rows);
    }
	

   
    /** Prints the summary of a specified player's performance on 
	a particular rule set */
    @GET
    @Path("/summary")
    @Produces(MediaType.TEXT_HTML)
    public String summary(@QueryParam("nickname") String nickname,
			  @QueryParam("rule") String rule) {
	String title="", body="", errmsg = null;
	EntityManager em=null;

	try {

	    
	    if (nickname==null || nickname.trim().equals("")) {
		throw new IllegalInputException("No nickname parameter in the form.");		
	    } else if (rule==null || rule.trim().equals(""))  {
		throw new IllegalInputException("No rule parameter in the form.");		
	    }

	    em = Main.getNewEM();

	    Query q = em.createQuery("select m from MlcEntry m where m.nickname=:n and m.ruleSetName=:r");
	    q.setParameter("n", nickname);
	    q.setParameter("r", rule);
	    List<MlcEntry> res = (List<MlcEntry>)q.getResultList();

	    title = "Submitted results for algorithm " + nickname +
		" on rule set " + rule;
	    
	    body += fm.h2("Summary of " + res.size() + " runs by "+fm.tt(nickname)+" on rule set " +
			  fm.tt(rule));
	    body += summaryTable(res);

	    
	} catch(Exception ex) {
	    title = "Error";
	    body = fm.para(ex.toString());
	    ex.printStackTrace(System.err);
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}

	return fm.html(title, body);		
 
    }


 /** Prints the summary of a specified player's performance on 
	a particular rule set, in comparison with other players.
	
	@param nickname The nickname of the player being compared to
	others.  It does not actually affect the content of the table
	(as it will show all players who have learned the rule set),
	but we will display the table row for this player in bold.

	<p>Sample URL:
	<code>
	http://sapir.psych.wisc.edu:7150/w2020/game-data/MlcUploadService/compare?nickname=RandomTest&rule=position_A
	</code>
 */
    @GET
    @Path("/compare")
    @Produces(MediaType.TEXT_HTML)
    public String compare(@QueryParam("nickname") String nickname,
			  @QueryParam("rule") String rule
			  ) {

	MannWhitneyComparison mwc = new MannWhitneyComparison(MannWhitneyComparison.Mode.CMP_ALGOS);
	Comparandum[][] allComp = mwc.mkMlcComparanda(nickname,  rule);
	return mwc.doCompare(nickname, rule, allComp, fm, null);
    }

    /** The REST call for comparing rule set with respect to an algo
     */
    @GET
    @Path("/compareRules")
    @Produces(MediaType.TEXT_HTML)
    public String compareRules(@QueryParam("nickname") String nickname,
			       @QueryParam("rule") String rule
			       ) {

	MannWhitneyComparison mwc = new MannWhitneyComparison(MannWhitneyComparison.Mode.CMP_RULES);
	Comparandum[][] allComp = mwc.mkMlcComparanda(nickname,  rule);

	return mwc.doCompare(nickname, rule, allComp, fm, null);
    }

}
