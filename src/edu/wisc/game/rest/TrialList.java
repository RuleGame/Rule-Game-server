package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import jakarta.json.*;


import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Board;
import edu.wisc.game.rest.ParaSet.Incentive;

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

    /** The path to the file from which the trial list has been read */
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
	"trialListId" field in the PlayerInfo table.
	
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
		Integer val = Integer.parseInt(e.getCol(1));
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
    static public class ExperimentPlanHandle {
	/** The main directory which contains the plans' trial list file
	    (which can be modified by a modifier), or null (in the "R:" mode). */
	public final File mainDir;
	/** Normally null; rule set name in "R:" mode */
	final public String mainRuleSetName;
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

	/** @param An experiment name plan (static or dynamic of any type) */
	public ExperimentPlanHandle(String exp)  throws IOException{
	    String q[] = exp.split(":");
	    if (q.length==1) { // traditional static plan
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

    /** Reads all lines of the trial list file, and creates a
	ParaSet objects for each line
	@param f The trial list file to read
	@param v If not null, the new ParaSet objects will be added to this vector.
	@return The vector containing new ParaSet objects (and maybe some old ones, if v is not null).
	This will be the same vector object as v, if v was not null, or a newly created one otherwise.
    */
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
	@param exp The name of an experiment plan (static or dynamic)
	@param The trial list id (typically, file name of the trial
	list file, relative to the plan's directory, and 
	fwithout extension). This should be null for R:-type dynamic plans.
    */
    public TrialList(String exp, String trialListId) throws IOException,IllegalInputException  {
	this(false, "No error");
	try {
	    ExperimentPlanHandle eph = new ExperimentPlanHandle(exp);	    

	    if (eph.mainDir!=null) {
		File mainFile = new File(eph.mainDir,  trialListId + suff);
		setPath(mainFile.getPath());	    
		readTrialListFile(this, mainFile);
	    } else if (eph.mainRuleSetName!=null) {  // R:-type
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

	    checkContinue();
	    
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
	    System.err.println(ex);
	    ex.printStackTrace(System.err);

	    setError(true);
	    setErrmsg( ex.getMessage());
	}
   }


    /** Throws an exception is something related to super-series is configured
	incorrectly in this TrialList.  */
    private void checkContinue() throws IllegalInputException {
	boolean lastCont = false;
	for(int j=0; j<size(); j++) {
	    ParaSet para = get(j);
	    boolean cont = para.getCont();
	    Incentive ince = para.getIncentive();
	    if (cont) {
		if (j==size()-1) {
		    throw new  IllegalInputException("The last parameter set of this trial list has continue==true, which is prohibited. (This flag indicates that the current series is 'continued' by the next one)");
		}
		if (ince!=null) {
		    throw new  IllegalInputException("ParaSet["+j+"] specifies incentive scheme " + ince +". This is prohibied, because no incentives are allowed in super-series (other than in the last line of the super-series)");
		}
	    } else if (lastCont) {
		if (ince==Incentive.DOUBLING || ince==Incentive.LIKELIHOOD) {
		    throw new  IllegalInputException("ParaSet["+j+"], which is tthe last line of a super-series specifies incentive scheme " + ince +". This is not allowed (feature not supported).");
		}		
	    }
	    lastCont = cont;
	}	    
    }

    


    
}
