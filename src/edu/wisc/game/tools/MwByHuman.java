package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.text.*;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.math.*;
import edu.wisc.game.formatter.*;

import edu.wisc.game.sql.Episode.CODE;

/** Ranking rule sets by the ease of learning by human players. As
 * requested by PK, 2022-12-22.
 */
public class MwByHuman extends AnalyzeTranscripts {


    public MwByHuman( int _targetStreak, double _defaultMStar,Fmter _fm    ) {
	super( null, null);	
	quiet = true;
	targetStreak = _targetStreak;
	defaultMStar = _defaultMStar;
	fm = _fm; 
    }


  static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("For usage, see tools/analyze-transcripts-mwh.html\n\n");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }
    
  public static void main(String[] argv) throws Exception {

	ArgType argType = ArgType.PLAN;
	boolean fromFile = false;
	
	Vector<String> plans = new Vector<>();
	Vector<String> pids = new Vector<>();
	Vector<String> nicknames = new Vector<>();
	Vector<Long> uids = new Vector<>();

	int targetStreak = 10;
	double defaultMStar=300;
	PrecMode precMode = PrecMode.Naive;
	boolean useMDagger = false;
	File csvOutDir = null;
	
	// At most one of them can be non-null
	String exportTo = null, importFrom = null;

	
	
	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];

	    if  (a.equals("-nickname")) {
		argType = ArgType.UNICK;
	    } else if  (a.equals("-plan")) {
		argType = ArgType.PLAN;
	    } else if  (a.equals("-uid")) {
		argType = ArgType.UID;
	    } else if  (a.equals("-pid")) {
		argType = ArgType.PID;
	    } else if  (a.equals("-file")) {
		fromFile=true;
	    } else if (a.equals("-mDagger")) {
		useMDagger = true;
	    } else if (j+1< argv.length && a.equals("-export")) {
		exportTo = argv[++j];
	    } else if (j+1< argv.length && a.equals("-import")) {
		importFrom = argv[++j];
	    } else if (j+1< argv.length && a.equals("-targetStreak")) {
		targetStreak = Integer.parseInt( argv[++j] );
	    } else if (j+1< argv.length && a.equals("-defaultMStar")) {
		defaultMStar = Double.parseDouble( argv[++j] );
	    } else if (j+1< argv.length && a.equals("-precMode")) {
		precMode = Enum.valueOf(MwByHuman.PrecMode.class, argv[++j]);
	    } else if (j+1< argv.length && a.equals("-csvOut")) {
		csvOutDir = new File(argv[++j]);
	    } else if (a.startsWith("-")) {
		usage("Unknown option: " + a);
	    } else {

		String[] v=  fromFile? readList(new File(a)):  new String[]{a};
				  
		for(String b: v) {
		    if (argType==ArgType.PLAN) 	plans.add(b);
		    else if (argType==ArgType.UNICK)  nicknames.add(b);
		    else if (argType==ArgType.UID)  uids.add(Long.parseLong(b));
		    else if (argType==ArgType.PID)  pids.add(b);
		    else usage("Unsupported argType: " + argType);
		}
	    }

	}

	if (exportTo!=null && importFrom!=null) {
	    usage("You cannot combine the options -export and -import. At most one of them can be used in any single run");
	}

	if (importFrom!=null && plans.size()>0) {
	    usage("If you use the -import option, you should not specify experiment plans!");
	}

	Fmter plainFm = new Fmter();
	MwByHuman processor = new MwByHuman( targetStreak, defaultMStar, plainFm);

	try {
	if (importFrom==null) {
	    // Extract the data from the transcript, and put them into savedMws
	    processor.processStage1(plans, pids, nicknames, uids);

	    if (exportTo!=null) {
		File gsum=new File(exportTo);
		processor.exportSavedMws(gsum);
	    }
	} else {
	    File g = new File(importFrom);
	    MwSeries.readFromFile(g, processor.savedMws);
	    //System.out.println("Has read " + processor.savedMws.size() + " data lines");

	}
	
	
	// M-W test on the data from savedMws
	processor.processStage2(precMode, importFrom!=null, useMDagger, csvOutDir);

	} finally {
	    String text = processor.getReport();
	    System.out.println(text);
	}

  }


    /** The printable report. Various parts of processStage1 and Stage2
	add lines to it */
    private StringBuffer result = new StringBuffer();
    public String getReport() { return result.toString(); }


    boolean error=false;
    String errmsg=null;
   /** The JSP page should always print this message. Most often
        it is just an empty string, anyway; but it may be used
        for debugging and status messages. */
    public String infomsg = "";
  
    public boolean getError() { return error; }
    public void setError(boolean _error) { error = _error; }
    
    public String getErrmsg() { return errmsg; }
    public void setErrmsg(String _errmsg) { errmsg = _errmsg; }

    /** Sets the error flag and the error message */
    protected void giveError(String msg) {
	setError(true);
	setErrmsg(msg);
    }

    protected void giveError(Exception _ex) {
	setError(true);
	setErrmsg(ex.getMessage());
	ex = _ex;
    }


    
    /** Propagate error from another class */
    //protected void giveError(ResultsBase other) {
    //	giveError(other.errmsg);
    //if (other.ex!=null) ex=other.ex;
    //}
    
    Exception ex=null;
    public Exception getEx() { return ex; }
    public String exceptionTrace() {
        StringWriter sw = new StringWriter();
        try {
            if (ex==null) return "No exception was caught";
            ex.printStackTrace(new PrintWriter(sw));
            sw.close();
        } catch (IOException _ex){}
        return sw.toString();
    }

    private Fmter fm=new Fmter();
    public void setFm(Fmter _fm) { fm = _fm; }


    /** The Stage 1 processing involves scanning the transcripts
	for the players associated with the relevant experiment
	plan, and computing the required statistics for
	all (player,ruleSet) pairs involved.
     */
    public void processStage1(Vector<String> plans,
				 Vector<String> pids,
				 Vector<String> nicknames,
				 Vector<Long> uids ) throws Exception {

      	EntityManager em = Main.getNewEM();

	try {
	plans = expandPlans(em, plans);


	PlayerList plist = new 	PlayerList(em,  pids,  nicknames,   uids);
	EpisodesByPlayer ph =new EpisodesByPlayer();

	// for each experiment plan...
	for(String exp: plans) {		
	    //System.out.println("Experiment plan=" +exp);

	    try {
	    
	    // ... List all trial lists 
	    TrialListMap trialListMap=new TrialListMap(exp);
	    //System.out.println("Experiment plan=" +exp+" has " + trialListMap.size() +" trial lists: "+ Util.joinNonBlank(", ", trialListMap.keySet()));
	    
	    Vector<EpisodeHandle> handles= new Vector<>();

	    // ... and all players enrolled in the plan
	    Query q = em.createQuery("select m from PlayerInfo m where m.experimentPlan=:e");
	    q.setParameter("e", exp);
	    List<PlayerInfo> res = (List<PlayerInfo>)q.getResultList();
	    for(PlayerInfo p:res) {
		ph.doOnePlayer(p,  trialListMap, handles);
	    }
	    
	    //System.out.println("For experiment plan=" +exp+", found " + handles.size()+" good episodes");//: "+Util.joinNonBlank(" ", handles));
	    } catch(Exception ex) {
		String msg = "ERROR: Skipping plan=" +exp+" due to an exception:" + ex;
		result.append(fm.para(msg));
		System.err.println(msg);
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }

	}	

	PrintWriter wsum =null;


	for(String playerId: ph.keySet()) {
	    Vector<EpisodeHandle> v = ph.get(playerId);
	    try {
		// This puts the data for (player,rule) pairs into savedMws
		analyzePlayerRecord(playerId, v);

	    } catch(Exception ex) {
		String msg = "ERROR: Cannot process data for player=" +playerId+" due to missing data. The problem is as follows:";
		result.append(fm.para(msg));
		System.err.println(msg);
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }
	}

	// Add mDagger to all entries in savedMws
	MwSeries.computeMDagger(savedMws);
	       
	
	} catch(Exception _ex) {
	    giveError(_ex.getMessage());
	    ex = _ex;
	    throw ex;
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}	

    }

    /** Exports the data generated in Stage1
	@param gsum File to write
     */
    public void exportSavedMws(File gsum) throws IOException {
	PrintWriter wsum = new PrintWriter(new FileWriter(gsum, false));
	wsum.println( MwSeries.header);
	
	for(MwSeries ser: savedMws) {
	    wsum.println(ser.toCsv());
	}

	wsum.close();
    }

    /** Now, the MW Test, using this.savedMws computed in
	stage1. Generates a report that's attached to this.results.
	@param precMode Controls how the series are assigned to "distinct experiences".
	@param fromFile Indicates that the m* data have come from an extrernal
	file, and are not internally computed.
     */
    public void processStage2(MwByHuman.PrecMode precMode, boolean fromFile, boolean useMDagger, File csvOutDir )    {
	if (error) return;

	File[] csvOut = null;
	if (csvOutDir!=null) {
	    csvOutDir.mkdirs();
	    csvOut = new File[] { new File(csvOutDir, "raw-wm.csv"),
		new File(csvOutDir, "ratio-wm.csv"),
		new File(csvOutDir, "ranking.csv")};
	}
		
	
	MannWhitneyComparison.Mode mode = MannWhitneyComparison.Mode.CMP_RULES_HUMAN;
	MannWhitneyComparison mwc = new MannWhitneyComparison(mode);
	
	Comparandum[][] allComp = Comparandum.mkHumanComparanda(savedMws.toArray(new MwSeries[0]),  precMode, useMDagger);	

	if (fromFile) {
	    result.append( fm.para("Processing data from an imported CSV file"));
	} else {
	    result.append( fm.para("In the tables below, 'learning' means demonstrating the ability to make "+fm.tt(""+targetStreak)+" consecutive moves with no errorrs"));

	    result.append( fm.para("mStar is the number of errors the player make until he 'learns' by the above definition. Those who have not learned, or take more than "+fm.tt(""+defaultMStar)+" errors to learn, are assigned mStar="+defaultMStar));
	    result.append( fm.para("M-W matrix is computed based on " + (useMDagger? "mDagger" : "mStar")));
	}
	    
	result.append( mwc.doCompare("humans", null, allComp, fm, csvOut));
    }

    /** Used to control how series are assigned to comparanda */
    public enum PrecMode {       
	//For each rule sets, only include "naive" players (those who played this rule set as their first rule set
	Naive,
	//Consider each (rule set + preceding set) combination as a separate experience to be ranked
	Every,
	//When analyzing a set, ignore preceding rule sets
	Ignore
    }
    
    /**  The data for a series (group of episodes played by a player under the same rule set) needed to contribute a number to an M-W Comparandum. For each episode, we need these data:
	 <pre>
playerId
episodeId
ruleSetName
predecessors
achieved10
m*
</pre>
     */
    public static class MwSeries {

	public final String ruleSetName;	
	/** Which other rules preceded this rule in the trial list? */
	final Vector<String> precedingRules;
	final String exp;
	final String trialListId;
	final int seriesNo;
	//final int orderInSeries;
	//final String episodeId;
	final String playerId;
	//final boolean useImages;
	//final ParaSet para;

	/** The 'key' (what comparandum, if any, this series belongs to) depends on the mode */
	public String getKey(PrecMode mode) {
	    switch (mode) {
	    case Ignore: return ruleSetName;	    
	    case Every:
		String s = String.join(":", precedingRules);
		if (s.length()>0) s += ":";
		return s + ruleSetName;
	    case Naive: return (precedingRules.size()==0)? ruleSetName: null;
	    default: throw new IllegalArgumentException("" + mode);
	    }
	}

	
	boolean learned=false;
	public boolean getLearned() { return learned; }
	/** The number of errors until the first "winning streak" has been
	    achieved, or in the entire series (if no winning streak) */
	int errcnt=0;
	/** The number of errors until the first  "winning streak" has been
	    achieved, or the large default number otherwise */
	double mStar=0;
	/** Total failed attempt (including those after the "achievement of learning") */
	int totalErrors=0;
	public int getTotalErrors() { return totalErrors; }
	/** Total move and pick attempts (successful and unsuccessful) */
	int totalMoves=0;
	public int getTotalMoves() { return totalMoves; }
	
	public double getMStar() { return mStar; }
	/** An integer approimation to MStar */
	public int getMStarInt() {
	    return (mStar >= Integer.MAX_VALUE)? Integer.MAX_VALUE:
		(int)Math.round(mStar);
	}
	public int getMDaggerInt() {
	    return (mDagger >= Integer.MAX_VALUE)? Integer.MAX_VALUE:
		(int)Math.round(mDagger);
	}

	/** As per PK's messages, 2023-01 */
	double mDagger=0;
	public double getMDagger() { return mDagger; }
	
	MwSeries(EpisodeHandle o) {
	    ruleSetName = o.ruleSetName;
	    precedingRules = o.precedingRules;
	    exp = o.exp;
	    trialListId = o.trialListId;
	    seriesNo = o.seriesNo;
	    playerId = o.playerId;
	}

	static final String header="#ruleSetName,precedingRules,"+
	    "exp,trialListId,seriesNo,playerId,learned,total_moves,total_errors,mStar,mDagger";

	String toCsv() {
	    String[] v = { ruleSetName,
			   String.join(";", precedingRules),
			   exp,
			   trialListId,
			   ""+seriesNo,
			   playerId,
			   ""+learned,
			   ""+getTotalMoves(),
			   ""+getTotalErrors(),
			   ""+mStar,
			   ""+mDagger};
	    return ImportCSV.escape(v);
	}

	/** Reads a CSV file with MwSeries entries.
	    @param into Put the data into this vector
	 */
	static void readFromFile(File f, Vector<MwSeries> into) throws IOException, IllegalInputException {
	    into.clear();
	    if (!f.exists()) throw new IOException("File does not exist: " + f);
	    if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	    CsvData csv = new CsvData(f, false, false, null);

	    if (csv.entries.length<2) throw new IOException("No data found in file: " + f);
	    CsvData.BasicLineEntry header =  (CsvData.BasicLineEntry)csv.header;
	    System.out.println("Header=" + header);
	    //int nCol = header.nCol();
	    
	    for(int j=0; j<csv.entries.length; j++) {
		CsvData.BasicLineEntry line = (CsvData.BasicLineEntry)csv.entries[j];
		//System.out.println("DEBUG: TL(f=" + f+"), adding para set " + j);
		into.add(new MwSeries( header, line));
	    }
	}
	
	/**  This is basically the inverse of toCsv() */
	MwSeries(CsvData.BasicLineEntry header, CsvData.BasicLineEntry line) throws  IllegalInputException {

	    // header="#ruleSetName,precedingRules,"+
	    //"exp,trialListId,seriesNo,playerId,learned,total_moves,total_errors,mStar";
	    
	    ruleSetName = line.getColByName(header, "ruleSetName", null);
	    if (ruleSetName==null) throw new  IllegalInputException("No ruleSetName");
	    String q =  line.getColByName(header, "precedingRules", "");
	    precedingRules = new Vector<>( Arrays.stream( q.split("[;:]")).filter(e->!e.isEmpty()).collect(Collectors.toList()));

	    //precedingRules = Util.array2vector( q.split("[;:]"));

	    exp = line.getColByName(header, "exp", "");
	    trialListId =  line.getColByName(header, "trialListId", "");
	    seriesNo =  Integer.parseInt(line.getColByName(header, "seriesNo", "0"));
	    playerId =  line.getColByName(header, "playerId", "");
	    learned =  Boolean.parseBoolean(line.getColByName(header, "learned", "false"));
	    totalMoves = Integer.parseInt(line.getColByName(header, "total_moves", "0"));
	    totalErrors = Integer.parseInt(line.getColByName(header, "total_errors", "0"));
	    mStar = Double.parseDouble(line.getColByName(header, "mStar", "-1"));
	    if (mStar<0)  throw new  IllegalInputException("No mStar value");
	    // mDagger is optional in input files. (But it had better
	    // be there if we want to use it, of course!)	    
	    mDagger = Double.parseDouble(line.getColByName(header, "mStar", "NaN"));


	}

	/** Computes and sets mDagger in every field, as mDagger(P,E)
	    = mStar(P,E) - Avg( mStar(P,*)), where averaging is
	    carried over all "successful" experiences of P (i.e. over
	    all rules sets that P played and learned).

	    <p>The value of mDagger will be NaN in all (P,E) pairs where
	    P learned the rules neither in E nor in any other experience.
	 */
	static void computeMDagger(Vector<MwSeries> v) {
	    // maps playerId to {sum, count}
	    HashMap<String,double[]> h = new HashMap<>();
	    for(MwSeries ser: v) {
		double[] z = h.get(ser.playerId);
		if (z==null) h.put(ser.playerId, z=new double[2]);
		if (ser.learned) {
		    z[0] += ser.mStar;
		    z[1] += 1;
		}
	    }
	    for(MwSeries ser: v) {
		double[] z = h.get(ser.playerId);
		ser.mDagger = ser.mStar - z[0]/z[1];
	    }
	}
	
	
    }

    /** Info about each episode gets added here */
    private Vector<MwSeries> savedMws = new Vector<>();
    
    private final int targetStreak;
    /** It is double rather than int so that Infinity could be represented */
    private final double  defaultMStar;

    /** Saves the data for a single (player, ruleSet) pair
	@param section A vector of arrays, each array representing the recorded
	moves for one episode.
	@param includedEpisodes All non-empty episodes played by this player in this rule set. This array is aligned with section[]
    */
  
    protected void saveAnyData(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
			     Vector<EpisodeHandle> includedEpisodes)
	throws  IOException, IllegalInputException,  RuleParseException {


	int je =0;

	MwSeries ser = null;
	int streak=0;       
	
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: section) {
	    EpisodeHandle eh = includedEpisodes.get(je ++);

	    if (ser==null || !ser.ruleSetName.equals(eh.ruleSetName)) {
		savedMws.add(ser = new MwSeries(eh));
		streak=0;
		ser.errcnt = 0;
		ser.mStar = defaultMStar;

	    }

	    // skip the rest of transcript for the rule set (i.e. this
	    // series) if the player has already demonstrated his
	    // mastery of this rule set
	    int j=0;
	    for(; j<subsection.length && !ser.learned; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		if (!eh.episodeId.equals(e.eid)) throw new IllegalArgumentException("Array mismatch");
		
		if (e.code==CODE.ACCEPT) {
		    if (e.pick instanceof Episode.Move) streak++;
		} else {
		    streak = 0;
		    ser.errcnt ++;
		    ser.totalErrors++;
		}

		if (streak>=targetStreak) {
		    ser.learned=true;
		    ser.mStar = Math.min( ser.errcnt, ser.mStar);
		}
		
	    }

	    // Any post-learning-success errors
	    for(; j<subsection.length; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		if (!eh.episodeId.equals(e.eid)) throw new IllegalArgumentException("Array mismatch");
		
		if (e.code!=CODE.ACCEPT) {
		    ser.totalErrors ++;
		}
	    }
	    
	    ser.totalMoves += subsection.length;
	}

	section.clear();
	includedEpisodes.clear();

    }
    
}
