package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
//import javax.servlet.http.HttpServletResponse;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Board;

@XmlRootElement(name = "TrialList") 


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


    /** Which directory contains trial lists that can be assigned for this player?
	// FIXME: In the future, different directories could be used for
	// different experiment plans
     */
    static File dirForTrialLists(String playerId) {
	File base = new File("/opt/tomcat/game-data");	
	base = new File(base, "trial-lists");
	String subdir = playerId.startsWith("qt-")? "qt" : "default";
	return new File(base, subdir);
    }


    /** Chooses one trial list at random among the lists in the trial
	list directory for the experiment with which a given player is
	associated. While a uniform random distribution is used, there is no
	precise (non-stochastic) load balancing.
    */
    static TrialList chooseRandomTrialList(String playerId) {
	File base =  dirForTrialLists(playerId);
	try {
	    if (!base.isDirectory()) throw new IOException("No such directory: " + base);
	    if (!base.canRead()) throw new IOException("Cannot read directory: " + base);
	    Vector<File> v = new Vector<>();
	    for(String s: base.list()) {
		File f = new File(base, s);
		if (!f.isFile()) continue;
		if (!s.endsWith(suff)) continue;
		v.add(f);
	    }
	    if (v.size()==0)  throw new IOException("Found no CSV files in directory: " + base);
	    // FIXME: need to check if this player already played and was assigned a trial list, and return the same one. Or maybe send a prohibition signal...
	    int k = Board.random.nextInt( v.size());	    
	    return new TrialList(v.get(k));
	} catch(Exception ex) {
	    return new TrialList(true, ex.getMessage());
	}

	
    }
    
    /** The error object  */
    TrialList(boolean _error, String _errmsg) {
	setError(_error);
	setErrmsg(_errmsg);
    }

    static final String suff = ".csv";

    /** Locates a trial list based on the player ID and the already chosen 
	trialList ID */
    public TrialList(String playerId, String trialListId) {
	this( new File(  dirForTrialLists(playerId), trialListId + suff));
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
