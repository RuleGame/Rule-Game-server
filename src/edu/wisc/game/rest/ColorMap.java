package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;

@XmlRootElement(name = "ColorMap") 

/** <pre>
RED,255,0,0
GREEN,0,255,0
etc
</pre>
*/
    
public class ColorMap extends HashMap<String, Object> {

    ColorMap() {
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
		String key = e.getKey();
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
						       
}
