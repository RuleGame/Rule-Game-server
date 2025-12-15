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

import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.tools.MwSeries.MoveInfo;
import edu.wisc.game.sql.ReplayedEpisode.RandomPlayer;

/** Ranking rule sets by the ease of learning by human players. As
    requested by PK, 2022-12-22.

    <p>This is also the base class from which BuildCurves derives, so
    it has some stuff (mostly related to parsing command line args)
    need only for the latter.
 */
public class MwByHuman extends AnalyzeTranscripts {

    protected final PrecMode precMode;

    /** The set of rule names which, if they appear in the preceding-rules
	list, are ignored. (Introduced in ver. 6.014, after PK's request (2023-06-01): "One issue that I may not have mentioned is whether to "ignore a particular predecessor." I decided to do that for the pk/noRule rule (after checking that it did not seem to affect performance by the F* measure).  In other words, when it was a predecessor I represented it by no character at all."
    */
    protected static Set<String> ignorePrec = new HashSet<>();
    protected static int ignorePrecCnt=0;
    static void incrementIgnorePrecCnt() {
	ignorePrecCnt++;
    }
    
    /** Info about each episode gets added here */
    public Vector<MwSeries> savedMws = new Vector<>();

    protected final int targetStreak;
    protected final double targetR;
    /** It is double rather than int so that Infinity could be represented */
    protected final double  defaultMStar;
    protected final MakeMwSeries makeMwSeries;

    /** @param  _targetStreak this is how many consecutive error-free moves the player must make (e.g. 10) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _targetR the product of R values of a series of consecutive moves should be at least this high) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _randomPlayerModel This is only needed in BuildCurves, to control the creation of random players 
     */
    public MwByHuman(PrecMode _precMode, int _targetStreak, double _targetR, double _defaultMStar,
		     RandomPlayer _randomPlayerModel,
		     Fmter _fm) {
	super( null, null, _randomPlayerModel);	
	quiet = true;
	precMode = _precMode;
	targetStreak = _targetStreak;
	targetR = _targetR;
	defaultMStar = _defaultMStar;
	fm = _fm;
	makeMwSeries = new MakeMwSeries(target,  _precMode,  _targetStreak,  _targetR, _defaultMStar);
    }


    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("For usage, see tools/analyze-transcripts-mwh.html\n\n");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    /** If non-null, restrict to experiences that have this rule set
	as the "main" one (with various preceding sets) */
    protected static String target=null;

    /** This structure contains various parameters that come
	from parsing the command line. This is moved into a separate
	class so that different utilities can use this class to parse
	their command line arguments.
    */
    static class RunParams {
	boolean doRandom=false;
	RandomPlayer randomPlayerModel;
	
	ArgType argType = ArgType.PLAN;
	boolean fromFile = false;
	
	Vector<String> plans = new Vector<>();
	Vector<String> pids = new Vector<>();
	Vector<String> nicknames = new Vector<>();
	Vector<Long> uids = new Vector<>();

	int targetStreak = 0;
	double targetR = 0;
	double defaultMStar=300;
	PrecMode precMode = PrecMode.Naive;
	CurveMode curveMode = CurveMode.AAIC;
	CurveArgMode curveArgMode = CurveArgMode.C;
	MedianMode medianMode = MedianMode.Real;
	boolean useMDagger = false;
	File csvOutDir = null;
	
	// At most one of them can be non-null
	String exportTo = null;
	Vector<String> importFrom = new Vector<>();

	/**  BuildCurves: do we draw pair plots? */
	boolean doPairs = false;
	/**  BuildCurves: do we annotate the longest flat section? */
	boolean doAnn = false;
	
	String config = null;
	
	RunParams(String[] argv) throws IOException, IllegalInputException {
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
	    } else if  (a.equals("-debug")) {
		debug=true;
	    } else if (a.equals("-mDagger")) {
		useMDagger = true;
	    } else if (j+1< argv.length && a.equals("-random")) {
		doRandom = Boolean.parseBoolean( argv[++j] );
	    } else if (j+1< argv.length && a.equals("-pairs")) {
		doPairs = Boolean.parseBoolean( argv[++j] );
	    } else if (j+1< argv.length && a.equals("-annotate")) {
		doAnn = Boolean.parseBoolean( argv[++j] );
	    } else if (j+1< argv.length && a.equals("-p0")) {
		randomPlayerModel = ReplayedEpisode.RandomPlayer.valueOf1(argv[++j]);
	    } else if (j+1< argv.length && a.equals("-config")) {
		config = argv[++j];
	    } else if (j+1< argv.length && a.equals("-export")) {
		exportTo = argv[++j];
	    } else if (j+1< argv.length && a.equals("-import")) {
		importFrom.add(argv[++j]);
	    } else if (j+1< argv.length && a.equals("-targetStreak")) {
		targetStreak = Integer.parseInt( argv[++j] );
	    } else if (j+1< argv.length && a.equals("-targetR")) {
		targetR = Double.parseDouble( argv[++j] );
	    } else if (j+1< argv.length && a.equals("-defaultMStar")) {
		defaultMStar = Double.parseDouble( argv[++j] );
	    } else if (j+1< argv.length && a.equals("-precMode")) {
		precMode = MwByHuman.PrecMode.valueOf( argv[++j]);
	    } else if (j+1< argv.length && a.equals("-curveMode")) {
		String s = argv[++j];
		if (s.equals("all")) {
		    curveMode = CurveMode.ALL;
		    curveArgMode = CurveArgMode.ALL;
		} else if (s.equals("none")) {
		    curveMode = CurveMode.NONE;
		    curveArgMode = CurveArgMode.NONE;
		} else {
		    String [] ss = s.split(":");
		    if (ss.length!=2) usage("Expected '-curveMode yChoice:xChoice', or '-curveMode all'");
		    curveMode = CurveMode.valueOf( ss[0]);
		    curveArgMode = CurveArgMode.valueOf( ss[1]);
		}
	    } else if (j+1< argv.length && a.equals("-median")) {
		medianMode = MedianMode.valueOf( argv[++j]);
	    } else if (j+1< argv.length && a.equals("-csvOut")) {
		csvOutDir = new File(argv[++j]);
	    } else if (j+1< argv.length && a.equals("-target")) {
		target = argv[++j];
	    } else if (j+1< argv.length && a.equals("-ignorePrec")) {
		ignorePrec.addAll( Util.array2set(argv[++j].split(":")));
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
	//System.out.println("ARGV: targetStreak=" + targetStreak+", targetR="+targetR );
	if (targetStreak<=0 && targetR <=0) {
	    targetStreak = 10;
	}

	//if (curveMode==null) {
	//    curveMode = CurveMode.AAIB;
	//    curveArgMode = CurveArgMode.C;
	//}
	
	//System.out.println("Adjusted: targetStreak=" + targetStreak+", targetR="+targetR );	
	if (config!=null) {
	    // Instead of the master conf file in /opt/w2020, use the customized one
	    MainConfig.setPath(config);
	}

	if (exportTo!=null && importFrom.size()>0) {
	    usage("You cannot combine the options -export and -import. At most one of them can be used in any single run");
	}

	if (importFrom.size()>0 && plans.size()>0) {
	    usage("If you use the -import option, you should not specify experiment plans!");
	}



	}

	MwByHuman mkProcessor() {
	    Fmter plainFm = new Fmter();
	    return new MwByHuman(precMode, targetStreak, targetR, defaultMStar,
				 randomPlayerModel, plainFm);
	}
	
    }

    
    public static void main(String[] argv) throws Exception {
	RunParams p = new RunParams(argv);
	MwByHuman processor = p.mkProcessor();
	try {
	    if (p.importFrom.size()==0) {
	    // Extract the data from the transcript, and put them into savedMws
		processor.processStage1(p.plans, p.pids, p.nicknames, p.uids);
		
		if (p.exportTo!=null) {
		    File gsum=new File(p.exportTo);
		    exportMws(gsum, processor.savedMws);
		}
	    } else {
		processor.savedMws.clear();		
		for(String from: p.importFrom) {
		    File g = new File(from);
		    MwSeries.readFromFile(g, processor.savedMws);
		    //System.out.println("DEBUG: Has read " + processor.savedMws.size() + " data lines");
		}		
	    }
	    // M-W test on the data from savedMws
	    processor.processStage2( p.importFrom.size()>0, p.useMDagger, p.csvOutDir);	    
	} finally {
	    String text = processor.getReport();
	    System.out.println(text);
	}
    }


    /** The printable report. Various parts of processStage1 and Stage2
	add lines to it */
    protected StringBuffer result = new StringBuffer();
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

    protected Fmter fm=new Fmter();
    public void setFm(Fmter _fm) { fm = _fm; }


    /** The Stage 1 processing involves scanning the transcripts
	for the players associated with the relevant experiment
	plan, and computing the required statistics for
	all (player,ruleSet) pairs involved. The data are then 
	added to savedMws (via a callback to saveAnyData()).
     */
    public void processStage1(Vector<String> plans,
			      Vector<String> pids,
			      Vector<String> nicknames,
			      Vector<Long> uids ) throws Exception {
	
      	EntityManager em = Main.getNewEM();

	try {

	EpisodesByPlayer ph = listEpisodesByPlayer( em, plans, pids, nicknames, uids);
	
	PrintWriter wsum =null;


	for(String playerId: ph.keySet()) {
	    Vector<EpisodeHandle> v = ph.get(playerId);
	    try {
		// This puts the data for (player,rule) pairs into savedMws
		// (via a callback to saveAnyData()).
		analyzePlayerRecord(playerId, v);

	    } catch(Exception ex) {
		String msg = "ERROR: Cannot process data for player=" +playerId+" due to missing data. The problem is as follows:";
		result.append(fm.para(msg));
		result.append(fm.para(ex.toString()));
		System.err.println(msg);
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }
	}

	// Add mDagger to all entries in savedMws
	MwSeries.computeMDagger(savedMws);
	       
	if (ignorePrecCnt>0) {
	    result.append(fm.para("Ignored a preceding rule in " + ignorePrecCnt + " entries"));
	}
	    

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
    protected static void exportMws(File gsum, Vector<MwSeries> saved) throws IOException {
	PrintWriter wsum = new PrintWriter(new FileWriter(gsum, false));
	String h = MwSeries.header;
	boolean hasMi = false;
	for(MwSeries ser: saved) {
	    if (ser.moveInfo!=null) hasMi=true;
	}
	if (hasMi) h += ",moveInfo";				
	
	wsum.println(h);
	
	for(MwSeries ser: saved) {
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
    public void processStage2(boolean fromFile, boolean useMDagger, File csvOutDir )    {
	if (error) return;

	File[] csvOut = MannWhitneyComparison.expandCsvOutDir(csvOutDir);
	
	MannWhitneyComparison.Mode mode = MannWhitneyComparison.Mode.CMP_RULES_HUMAN;
	MannWhitneyComparison mwc = new MannWhitneyComparison(mode);

	MwSeries[] a = savedMws.toArray(new MwSeries[0]);
	Comparandum[][] allComp = Comparandum.mkHumanComparanda(a,  precMode, useMDagger);	
	//System.out.println("DEBUG: Human comparanda: "+allComp[0].length+" learned, "+allComp[1].length+" unlearned, ");
	
	if (fromFile) {
	    result.append( fm.para("Processing data from an imported CSV file"));
	} else {
	    Vector<String> v = new Vector<>();
	    if (targetStreak>0) v.add("to make "+fm.tt(""+targetStreak)+" consecutive moves with no errors");
	    if (targetR>0) v.add("to make a series of successful consecutive moves with the product of R values at or above "+fm.tt(""+targetR));
	    
	    result.append( fm.para("In the tables below, 'learning' means demonstrating the ability " + Util.joinNonBlank(" or ", v)));

	    result.append( fm.para("mStar is the number of move/pick attempts the player makes until he 'learns' by the above definition. Those who have not learned, or take more than "+fm.tt(""+defaultMStar)+" move/pick attempts to learn, are assigned mStar="+defaultMStar));
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
	// Like Every, but treats successful and failed series differently
	EveryCond,
	//When analyzing a set, ignore preceding rule sets
	Ignore
    }

    public enum CurveMode {
	ALL, NONE,
	// Raw error count, Sum_m(e_m) (formerly E)
	W,
	// Error count normalized error prob, Sum_m(e_m)/Sum_m(1-p0(m))
	AAI,
	// AAI * m
	AAIB,
	AAIC, // simplification of AAID (like AAIE, but with no freezing)
	AAID, // Paul's Nov 19 proposal
	AAIE, // based on Paul's Nov 19 proposal, but with regularized sumZP
	AAIG, // W*C/sum(mu)
	AAIH, // W*C/sum(mu)
	OMEGA
       
    }

    public enum CurveArgMode {
	ALL, NONE,
	// count of move attempts
	M,
	// count of removed pieces (formerly Q)
	C
    }

    public enum MedianMode {
	// For each x, find the median of the set of all actually measured y_j(x)
	Real,
	// For each x, find the median of the set of all actually measured or extrapolated y_j(x)
	Extra
    }
    

    /** Saves the data (the summary of a series) for a single (player, ruleSet) pair. The saved data is put into an MwSeries object, which is then appened to savedMws.

	In some cases, a series can be skipped (not saved). This is the case
	if only the data for a specific target is requested (target!=null),
	or if we only want the data for "Naive" players.

	<p>This method is called from AnalyzeTranscripts.analyzePlayerRecord(),
	overriding the eponymous method in that class.
	
	@param section A vector of arrays, each array representing the recorded
	moves for one episode. In its entirety, <tt>section</tt> describes all episodes
	in the series (i.e., for one rule set). Before returning, this method clears this array.
	@param includedEpisodes All non-empty episodes played by this player in this rule set. This array must be aligned with section[]. Before returning, this method clears this array.
    */
  
    protected void saveAnyData(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
			       Vector<EpisodeHandle> includedEpisodes)
	throws  IOException, IllegalInputException,  RuleParseException {

	if (includedEpisodes.size()==0) return; // empty series (all "give-ups")

	EpisodeHandle eh = includedEpisodes.firstElement();
	Vector<Board> boardHistory = null;
	P0andR p0andR =  computeP0andR(section, eh.para, eh.ruleSetName, boardHistory);

	if (eh.neededPartnerPlayerId != null) {
	    System.out.println("For " +eh.playerId + ", also make partner entry for " + eh.neededPartnerPlayerId);
	    for(int j=0; j<2; j++) {
		MwSeries ser = makeMwSeries.mkMwSeries(section, includedEpisodes, p0andR, j, false);
		if (ser!=null) savedMws.add(ser);
	    }
	} else {
	    System.out.println("For " +eh.playerId + ", no partner needed");
	    MwSeries ser = makeMwSeries.mkMwSeries(section, includedEpisodes, p0andR, -1, false);
	    if (ser!=null) savedMws.add(ser);
	}
	
	section.clear();
	includedEpisodes.clear();
    }
}

