package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
//import javax.servlet.http.HttpServletResponse;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;

@XmlRootElement(name = "ParaSet") 


public class ParaSet extends HashMap<String, Object> {

    /*
   private int x;
 
       public int getX() { return x; }
  @XmlElement 
    public void setX(int _x) { x = _x; }


    private boolean error;
    private String errmsg;

    public boolean getError() { return error; }
    @XmlElement
    public void setError(boolean _error) { error = _error; }

    public String getErrmsg() { return errmsg; }
    @XmlElement
    public void setErrmsg(String _errmsg) { errmsg = _errmsg; }
    */

    ParaSet(String name) {
	put("error", false);
	put("errmsg", "No error");
	put("name", name);
	//put("true-flag", new Boolean(true));
	//	put("seven-field", new Integer(7));
	try {

	    if (name==null) throw new IOException("File name not specified");
	    File base = new File("/opt/tomcat/game-data");
	    base = new File(base, "param");
	    String ext = ".csv";
	    if (!name.endsWith(ext)) name += ext;
	    File f= new File(base, name);
	    if (!f.exists()) throw new IOException("File does not exist: " + f);
	    if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	    CsvData csv = new CsvData(f, true, false, null);
	    for(CsvData.LineEntry e: csv.entries) {
		String key = e.getKey();
		String val = ((CsvData.BasicLineEntry)e).getCol(1);
		if (val==null) continue;
		val = val.trim();
		String s= val.toLowerCase();
		s = s.trim();
		if (s.equals("true") || s.equals("false")) {
		    put(key, Boolean.valueOf(s));
		} else if (s.matches("[0-9]+")) {
		    put(key, Integer.valueOf(s));
		} else if (s.matches("[0-9]*\\.[0-9]+")) {
		    put(key, Double.valueOf(s));
		} else {
		    put(key, val);
		}
		    
	    }


	} catch(Exception ex) {
	    put("error", true);
	    put("errmsg", ex.getMessage());
	}

    }
    
}
			     
