package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Piece;

@XmlRootElement(name = "ColorMap") 

/**  Maps a color name (string) to a Vector<Integer> that represents the R G B components.
<pre>
RED,255,0,0
GREEN,0,255,0
etc
</pre>
*/
    
public class ColorMap extends HashMap<String, Object> {
    /** Reads in the color map file */
    public ColorMap() {
	put("error", false);
	put("errmsg", "No error");
	try {
	    File base = new File(Files.inputDir, "colors");
	    File f= new File(base, "colors.csv");
	    if (!f.exists()) throw new IOException("File does not exist: " + f);
	    if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	    CsvData csv = new CsvData(f, true, false, null);
	    for(CsvData.LineEntry _e: csv.entries) {
		CsvData.BasicLineEntry e = (CsvData.BasicLineEntry)_e;
		String key = e.getKey().toUpperCase();
		if (e.nCol()!=4) throw new IOException("Invalid entry in file " + f+": expected 4 columns, found " + e.nCol()+": " + e);
		Vector<Integer> v = new Vector<>();
		for(int j=1; j<=3; j++) {
		    Integer q = new Integer(e.getCol(j));
		    if (q<0 || q>255) throw new IOException("Invalid value (" + q + ") in column " + j + " in file " + f +", Line: " + e);
		    v.add(q);
		}
		put(key, v);
	    }
	} catch(Exception ex) {
	    put("error", true);
	    put("errmsg", ex.getMessage());
	}

    }

    public String getHex(Piece.Color color, boolean brighten) {
	return getHex(color.toString(), brighten);
    }
    
    /** @param name "RED"
	@param brighten If true, replace with a lighter color, so that it can
	be more suitable for use as a background.
	@return "FF00000"
     */
    public String getHex(String name, boolean brighten) {
	Vector<Integer> q = (Vector<Integer>)get(name.toUpperCase());
	if (q==null) return null;
	if (brighten) {
	    Vector<Integer> z = new Vector<>();
	    for(int x: q) z.add( (x+255)/2);
	    q=z;
	}
	return vectorToHex(q);
    }

    private static String vectorToHex(Vector<Integer> q) {
	if (q==null || q.size()!=3) return null;
	ByteArrayOutputStream baos = new	ByteArrayOutputStream();
	PrintStream out = new PrintStream(baos);
	for(int x: q) {
	    out.printf("%02X", x);
	}
	out.flush();
	return baos.toString();
    }

    public boolean hasColor(Piece.Color color) {
	Object o = get(color.toString());
	return o!=null && o instanceof Vector;
    }
    
}
