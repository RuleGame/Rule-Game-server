package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
//import javax.servlet.http.HttpServletResponse;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;

@XmlRootElement(name = "ParaSet") 

/** <pre>
rule_id,max_boards,min_points,max_points,activate_bonus_at,min_objects,max_objects,min_shapes,max_shapes,min_colors,max_colors,f,m,n,b,clear_how_many,bonus_extra_pts,clearing_threshold,feedback_switches,stack_m
emory_depth,stack_memory_show_order,grid_memory_show_order
TD-01,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
TD-02,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
TD-03,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
TD-04,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
TD-05,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
</pre>
*/
    
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

    /** Initializes a ParaSet object from a line of trial list file */
    ParaSet(CsvData.BasicLineEntry header, CsvData.BasicLineEntry line) throws IOException {
	int nCol=header.nCol();
	if (nCol!=line.nCol()) throw new  IOException("Column count mismatch:\nHEADER=" + header + ";\nLINE=" + line);
	for(int k=0; k<nCol; k++) {
	    typedPut(header.getCol(k), line.getCol(k));		    	    
	}
    }

    /** Converts the value to an object of a (likely) proper type, and 
	puts it into this HashMap */
    private Object typedPut(String key, String val) {
	val = val.trim();
	String s= val.toLowerCase();
	return
	    (s.equals("true")||s.equals("false")) ? put(key,Boolean.valueOf(s)):
	    s.matches("[0-9]+") ? 	    put(key, Integer.valueOf(s)) :
	    s.matches("[0-9]*\\.[0-9]+") ?    put(key, Double.valueOf(s)) :
	    put(key, val);
    }

    /** Reads a ParaSet from a CSV file with key-val columns */
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
		typedPut(key, val);		    
	    }


	} catch(Exception ex) {
	    put("error", true);
	    put("errmsg", ex.getMessage());
	}

    }

    public int getInt(String key) {
	Integer o = (Integer)get(key);
	if (o==null) throw new IllegalArgumentException("Parameter set has no variable named "+key);
	return o.intValue();
    }

    public double getDouble(String key) {
	Object o = get(key);
	if (o==null) throw new IllegalArgumentException("Parameter set has no variable named "+key);
	if (o instanceof Integer) {
	    Integer q = (Integer)get(key);	    
	    return q.intValue();
	} else {
	    Double q = (Double)get(key);	    
	    return q.doubleValue();
	}
	
    }

    public int getMaxBoards() {
	return getInt("max_boards");
    }

    public String getRuleSetName() {
	return (String)get("rule_id");
    }

    public double getClearingThreshold() {
	Double x = getDouble("clearing_threshold");
	return x;
    }

	
}
			     
