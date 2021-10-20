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
    private String path;
    
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

    
    /** Lists the trialList IDs, i.e. the names of the trial list
	files (with the extension removed), asscociated with a
	particular experiment plan. For an R: plan, returns the name
	of the rule set, because that's what will be recorded as the
	"trialListId" field in the PlauyerInfo table.
	
	@param exp The name of the experiment plan (static or dynamic)
    */
     public static Vector<String> listTrialLists(String exp) throws IOException {
	 ExperimentPlanHandle eph = new	ExperimentPlanHandle(exp);

	 if (eph.mainDir==null) { // "R:" type
	     return Util.array2vector(new String[]{ eph.mainRuleSetName});
	 } else {      
	     return listTrialLists(eph.mainDir);
	 }
    }


    /** Lists the names of the trial list files (with the  extension
	removed) contained in a specified directory.
	@param base the directory (corresponding to one experiment plan)
	which contains trial list files
    */
    public static Vector<String> listTrialLists(File base) throws IOException {
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
	try {

	    ExperimentPlanHandle eph = new	ExperimentPlanHandle(exp);
	    if (eph.mainDir==null) throw new IllegalArgumentException("Experiment plan " + exp + " is an 'R:' dynamic plan, and has no defect file!");
	    
	    File f = new File(eph.mainDir, defectFileName);
	    if (!f.exists()) return h;
	    CsvData csv = new CsvData(f, true, false, null);	
	    for(CsvData.LineEntry _e: csv.entries) {
		CsvData.BasicLineEntry e= (CsvData.BasicLineEntry)_e;
		String key = e.getKey();
		Integer val = new Integer(e.getCol(1));
		if (val!=null) h.put(key, val);
	    }		
	} catch(Exception ex) {
	    System.err.println("Failed to process defect file for plan '"+exp+"'. Exception: " + ex);
	}
	return h;	
    }


    /** The error object  */
    TrialList(boolean _error, String _errmsg) {
	setError(_error);
	setErrmsg(_errmsg);
    }

    /** Trial list file names must end with this suffix */
    static final String suff = ".csv";

    /*
    private static File dirForExperiment(String mainExp) {
	if (mainExp==null) throw new IllegalArgumentException("Experiment plan not specified");
	File base = Files.trialListMainDir();
	return new File(base, mainExp);	
    }
    */
    
    /** Identifies an experiment plan as a static or dynamic (P: or R: type)
	one */
    static class ExperimentPlanHandle {
	/** The main directory which contains the plans' trial list file
	    (which can be modified by a modifier), or null (in the "R:" mode). */
	final File mainDir;
	/** Normally null; rule set name in "R:" mode */
	final String mainRuleSetName;
	/** Normally null; a File in R: or P: mode */
	final File modifierFile;

	//static File dirForExperiment(String exp) {
	//	    return (mainFile==null)? null: mainFile.getParentFile();
	//}

	/** @param expMain The proper directory name */
	private static File findDir(String expMain) {
	    if (expMain==null) throw new IllegalArgumentException("Experiment plan not specified");
	    File base = Files.trialListMainDir();
	    return new File(base, expMain);
	}

	ExperimentPlanHandle(String exp)  throws IOException{
	    String q[] = exp.split(":");
	    if (q.length==1) {
		mainDir = findDir(exp);
		mainRuleSetName=null;
		modifierFile=null;
	    } else if (q.length==3 && q[0].equals("P")) {
		// P:mode = static experiment plan name  + modifier
		mainDir = findDir(q[1]);
		mainRuleSetName=null;
		modifierFile=Files.modifierFile(q[2]);
	    } else if (q.length==3 && q[0].equals("R")) {
		// P:mode = rules set name  + modifier
		mainDir = null;
		mainRuleSetName=q[1];
		modifierFile=Files.modifierFile(q[2]);
	    } else {
		throw new IllegalArgumentException("Not a proper way to describe an experiment plan: " + exp);
	    }
	}
    }

    private static Vector<ParaSet> readTrialListFile(Vector<ParaSet> v, File f) throws IOException, IllegalInputException {
	if (v==null) v=new Vector<ParaSet>();
	if (!f.exists()) throw new IOException("File does not exist: " + f);
	if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	CsvData csv = new CsvData(f, true, false, null);
	if (csv.entries.length<2) throw new IOException("No data found in file: " + f);
	CsvData.BasicLineEntry header =  (CsvData.BasicLineEntry)csv.entries[0];
	//int nCol = header.nCol();
	
	for(int j=1; j<csv.entries.length; j++) {
	    CsvData.BasicLineEntry line = (CsvData.BasicLineEntry)csv.entries[j];
	    //System.out.println("DEBUG: TL(f=" + f+"), adding para set " + j);
	    v.add(new ParaSet( header, line));
	}
	return v;
    }
    
    /** Reads a trial list from the  file that corresponds to a given
	experiment plan  and the specified trial list id within that
	experiment. 
	@parame exp An experiment plan (static or dynamic)
    */
    public TrialList(String exp, String trialListId) throws IOException,IllegalInputException  {
	this(false, "No error");
	try {
	    ExperimentPlanHandle eph = new ExperimentPlanHandle(exp);	    

	    if (eph.mainDir!=null) {
		File mainFile = new File(eph.mainDir,  trialListId + suff);
		setPath(mainFile.getPath());	    
		readTrialListFile(this, mainFile);
	    } else if (eph.mainRuleSetName!=null) {
		setPath(null);	    
		add( ParaSet.ruleNameToParaSet(eph.mainRuleSetName));
	    }
		
	    if (eph.modifierFile!=null) {
		Vector<ParaSet> mv = readTrialListFile(null, eph.modifierFile);
		if (mv.size()!=1) throw new IllegalInputException("Invalid modifier file " +  eph.modifierFile +". Expected to find 1 para set in it, found " + mv.size());
		ParaSet mf = mv.get(0);
		for(ParaSet para: this) {
		    para.modifyBy(mf);		    
		}
	    }	    
	    
	} catch(IOException ex) {
	    setError(true);
	    setErrmsg( ex.getMessage());
	    throw ex;
	}

    }

    /** The original constructor: read the trial list from a single file!
     */
    public TrialList(File mainFile) throws IOException {	
	this(false, "No error");
	try {
	    setPath(mainFile.getPath());	    
	    readTrialListFile(this, mainFile);
 	} catch(Exception ex) {
	    setError(true);
	    setErrmsg( ex.getMessage());
	}
   }
    
}
