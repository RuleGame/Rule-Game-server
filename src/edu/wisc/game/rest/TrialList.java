package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Board;

public class TrialList extends Vector<ParaSet> {
    
    boolean error;
    String errmsg;
    String path;
    
    public boolean getError() { return error; }
    @XmlElement
    public void setError(boolean _error) { error = _error; }
    
    public String getErrmsg() { return errmsg; }   
    @XmlElement
    public void setErrmsg(String _errmsg) { errmsg = _errmsg; }

    public String getPath() { return path; }
    @XmlElement
    public void setPath(String _path) { path = _path; }

    /** To which experiment plan does this player ID belong? */
    public static String extractExperimentPlanFromPlayerId(String playerId) {
	if (playerId==null || playerId.equals("") || playerId.startsWith("-")) throw new IllegalArgumentException("Illegal playerId: " + playerId);
	String[] seg = playerId.split("-");
	return (seg.length>1) ? seg[0]:  "default";
    }

    static File dirForExperiment(String exp) {
	if (exp==null) throw new IllegalArgumentException("Experiment plan not specified");
	File base = Files.trialListMainDir();
	return new File(base, exp);	
    }

    public static Vector<String> listTrialLists(String exp) throws IOException {
	File base = dirForExperiment(exp);
	//try {
	if (!base.isDirectory()) throw new IOException("No experiment plan directory exists: " + base);
	if (!base.canRead()) throw new IOException("Cannot read experiment plan directory: " + base);
	//	Vector<File> v = new Vector<>();
	Vector<String> names = new Vector<>();
	for(String s: base.list()) {
	    File f = new File(base, s);		
	    if (!f.isFile()) continue;
	    if (!s.endsWith(suff)) continue;
	    if (s.equals(defectFileName)) continue;
	    String key=s.substring(0, s.length()-suff.length());
	    names.add(key);
	}
	return names;
    }


    /** The "defect file", which the experiment manager can use to tell the
	system that a certain number of "completers" in some trial lists
	should not be taken into account during load balancing.
     */
    static final String defectFileName = "defect.csv";
    public static HashMap<String,Integer> readDefects(String exp) {
	HashMap<String,Integer> h = new HashMap<>();
	File f = new File(dirForExperiment(exp), defectFileName);
	if (!f.exists()) return h;
	try {
	    CsvData csv = new CsvData(f, true, false, null);	
	    for(CsvData.LineEntry _e: csv.entries) {
		CsvData.BasicLineEntry e= (CsvData.BasicLineEntry)_e;
		String key = e.getKey();
		Integer val = new Integer(e.getCol(1));
		if (val!=null) h.put(key, val);
	    }		
	} catch(Exception ex) {
	    System.err.println("Failed to process defect file '"+f+"'. Exception: " + ex);
	}
	return h;	
    }


    /** The error object  */
    TrialList(boolean _error, String _errmsg) {
	setError(_error);
	setErrmsg(_errmsg);
    }

    static final String suff = ".csv";

    /** Reads a trial list from the  file that corresponds to a given
       experiment trial and the specified trial list id within that
       experiment. */
    public static File trialListFile(String exp, String trialListId) {
	 return new File(dirForExperiment(exp), trialListId + suff);
    }

    /** Reads a trial list from the  file that corresponds to a given
	experiment trial and the specified trial list id within that
	experiment. */
    public TrialList(String exp, String trialListId) {
    	this( trialListFile(exp, trialListId));
    }

    
    /** Reads a trial list from the specified file. */
    TrialList(File f) {
	this(false, "No error");
	setPath(f.getPath());
	try {

	    if (!f.exists()) throw new IOException("File does not exist: " + f);
	    if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	    CsvData csv = new CsvData(f, true, false, null);
	    if (csv.entries.length<2) throw new IOException("No data found in file: " + f);
	    CsvData.BasicLineEntry header =  (CsvData.BasicLineEntry)csv.entries[0];
	    //int nCol = header.nCol();

	    for(int j=1; j<csv.entries.length; j++) {
		CsvData.BasicLineEntry line = (CsvData.BasicLineEntry)csv.entries[j];
		add(new ParaSet( header, line));
	    }


	} catch(Exception ex) {
	    setError(true);
	    setErrmsg( ex.getMessage());
	}

    }
    
    

}
