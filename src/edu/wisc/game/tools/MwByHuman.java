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

    private final MwByHuman.PrecMode precMode;

    /** The set of rule names which, if they appear in the preceding-rules
	list, are ignored. (Introduced in ver. 6.014, after PK's request (2023-06-01): "One issue that I may not have mentioned is whether to "ignore a particular predecessor." I decided to do that for the pk/noRule rule (after checking that it did not seem to affect performance by the F* measure).  In other words, when it was a predecessor I represented it by no character at all."
    */
    private static Set<String> ignorePrec = new HashSet<>();
    private static int ignorePrecCnt=0;
    static void incrementIgnorePrecCnt() {
	ignorePrecCnt++;
    }
    
    /** Info about each episode gets added here */
    public Vector<MwSeries> savedMws = new Vector<>();

    private final int targetStreak;
    private final double targetR;
    /** It is double rather than int so that Infinity could be represented */
    private final double  defaultMStar;

    /** @param  _targetStreak this is how many consecutive error-free moves the player must make (e.g. 10) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _targetR the product of R values of a series of consecutive moves should be at least this high) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
     */
    public MwByHuman(MwByHuman.PrecMode _precMode, int _targetStreak, double _targetR, double _defaultMStar,Fmter _fm    ) {
	super( null, null);	
	quiet = true;
	precMode = _precMode;
	targetStreak = _targetStreak;
	targetR = _targetR;
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

    /** If non-null, restrict to experiences that have this rule set
	as the "main" one (with various preceding sets) */
    private static String target=null;
    
  public static void main(String[] argv) throws Exception {

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
	boolean useMDagger = false;
	File csvOutDir = null;
	
	// At most one of them can be non-null
	String exportTo = null;
	Vector<String> importFrom = new Vector<>();

	String config = null;
	
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
		precMode = Enum.valueOf(MwByHuman.PrecMode.class, argv[++j]);
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

	//System.out.println("Adjusted: targetStreak=" + targetStreak+", targetR="+targetR );	
	if (config!=null) {
	    // Instead of the master conf file in /opt/w2020, use the customized one
	    MainConfig.setPath(config);

	    // Set the input directory as per the config file
	    //	    String inputDir = MainConfig.getString("FILES_SAVED", null);
	    //if (inputDir != null) {
	    //	if (!(new File(inputDir)).isDirectory()) usage("Not a directory: " + inputDir);
	    //Files.setSavedDir(inputDir);
	    //}
	}

	if (exportTo!=null && importFrom.size()>0) {
	    usage("You cannot combine the options -export and -import. At most one of them can be used in any single run");
	}

	if (importFrom.size()>0 && plans.size()>0) {
	    usage("If you use the -import option, you should not specify experiment plans!");
	}

	Fmter plainFm = new Fmter();
	MwByHuman processor = new MwByHuman(precMode, targetStreak, targetR, defaultMStar, plainFm);

	try {
	    if (importFrom.size()==0) {
	    // Extract the data from the transcript, and put them into savedMws
		processor.processStage1(plans, pids, nicknames, uids);
		
		if (exportTo!=null) {
		    File gsum=new File(exportTo);
		    processor.exportSavedMws(gsum);
		}
	    } else {
		processor.savedMws.clear();
		
		for(String from: importFrom) {
		    File g = new File(from);
		    MwSeries.readFromFile(g, processor.savedMws);
		    //System.out.println("DEBUG: Has read " + processor.savedMws.size() + " data lines");
		}		
	    }

	    // M-W test on the data from savedMws
	    processor.processStage2( importFrom.size()>0, useMDagger, csvOutDir);
	    
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

	EpisodesByPlayer ph = listEpisodesByPlayer( em, plans, pids, nicknames, uids);
	
	PrintWriter wsum =null;


	for(String playerId: ph.keySet()) {
	    Vector<EpisodeHandle> v = ph.get(playerId);
	    try {
		// This puts the data for (player,rule) pairs into savedMws
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
	// Like Every, but treats successful and failed series differently
	EveryCond,
	//When analyzing a set, ignore preceding rule sets
	Ignore
    }

    /** Who learned what. (playerId : (ruleSetName: learned)) */
    HashMap<String, HashMap<String, Boolean>> whoLearnedWhat = new HashMap<>();

    /** Saves the data (the summary of a series) for a single (player, ruleSet) pair. The saved data is put into an MwSeries object, which is then appened to savedMws.

	In some cases, a series can be skipped (not saved). This is the case
	if only the data for a specific target is requested (target!=null),
	or if we only want the data for "Naive" players.

	<p>This method is called from AnalyzeTranscripts.analyzePlayerRecord(),
	overriding the eponymous method in that class.
	
	@param section A vector of arrays, each array representing the recorded
	moves for one episode. In its entirety, <tt>section</tt> describes all episodes
	in the series. Before returning, this method clears this array.
	@param includedEpisodes All non-empty episodes played by this player in this rule set. This array must be aligned with section[]. Before returning, this method clears this array.
    */
  
    protected void saveAnyData(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
			       Vector<EpisodeHandle> includedEpisodes)
	throws  IOException, IllegalInputException,  RuleParseException {

	if (includedEpisodes.size()==0) return; // empty series (all "give-ups")

	EpisodeHandle eh = includedEpisodes.firstElement();
	Vector<Board> boardHistory = null;
	double rValues[] = computeP0andR(section, eh.para, eh.ruleSetName, boardHistory).rValues;

	if (eh.neededPartnerPlayerId != null) {
	    System.out.println("For " +eh.playerId + ", also make partner entry for " + eh.neededPartnerPlayerId);
	    MwSeries[] ser={
		fillMwSeries(section, includedEpisodes, rValues, 0),
		fillMwSeries(section, includedEpisodes, rValues, 1)};
	} else {
	    System.out.println("For " +eh.playerId + ", no partner needed");
	    MwSeries ser = fillMwSeries(section, includedEpisodes, rValues, -1);
	}

	section.clear();
	includedEpisodes.clear();
    }


    /** Creates an MwSeries object for a (player,rule set)
	interaction, and adds it to savedMws, if appropriate.


        @param chosenMover If it's -1, the entire transcript is
	analyzed. (That's the case for 1PG and coop 2PG). In adve 2PG,
	it is 0 or 1, and indicates which partner's record one extracts
	from the transcript.
     */
    private MwSeries fillMwSeries(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
				  Vector<EpisodeHandle> includedEpisodes,
				  double[] rValues, int chosenMover)
	throws  IOException, IllegalInputException,  RuleParseException {

	// this players successes and failures on the rules he's done
	EpisodeHandle eh = includedEpisodes.firstElement();
	MwSeries ser = new MwSeries(eh, ignorePrec, chosenMover);
	HashMap<String,Boolean> whatILearned = whoLearnedWhat.get(ser.playerId);
	if (whatILearned == null) whoLearnedWhat.put(ser.playerId, whatILearned = new HashMap<>());

	boolean shouldRecord = (target==null) || eh.ruleSetName.equals(target);
	shouldRecord = shouldRecord && !(precMode == PrecMode.Naive && ser.precedingRules.size()>0);
	
	int je =0;
	int streak=0;
	double lastR = 0;

	ser.errcnt = 0;
	ser.mStar = defaultMStar;
	if (precMode == PrecMode.EveryCond) {
	    ser.adjustPreceding( whatILearned);
	}
	// Do recording only after a successful adjustPreceding (if applicable)
	if (shouldRecord) 		savedMws.add(ser);

	if (debug) System.out.println("Scoring");

	int k=0;
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: section) {
	    eh = includedEpisodes.get(je ++);

	    if (!ser.ruleSetName.equals(eh.ruleSetName)) {
		throw new IllegalArgumentException("Rule set name changed in the middle of a series");
	    }

	    // We will skip the rest of transcript for the rule set (i.e. this
	    // series) if the player has already demonstrated his
	    // mastery of this rule set
	    int j=0;
	    for(; j<subsection.length && !ser.learned; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		if (!eh.episodeId.equals(e.eid)) throw new IllegalArgumentException("Array mismatch");

		double r = rValues[k++];

		boolean wrongPlayer= (chosenMover>0) && (e.mover!=chosenMover);
		if (wrongPlayer) continue;
		
		if (e.code==CODE.ACCEPT) {
		    if (e.pick instanceof Episode.Move) {
			streak++;
			if (lastR==0) lastR=1;
			lastR *= r;
			if (debug) System.out.println("["+j+"] R*" + r + "=" +lastR);
		    } else {
			if (debug) System.out.println("["+j+"] successful pick");
		    }
		} else {
		    streak = 0;
		    lastR = 0;
		    if (debug) System.out.println("["+j+"] R=" + lastR);
		    ser.errcnt ++;
		    ser.totalErrors++;
		}


		boolean learned =
		    (targetStreak>0 && streak>=targetStreak) ||
		    (targetR>0 && lastR>=targetR);
		
		if (learned) {
		    ser.learned=true;
		    ser.mStar = Math.min( ser.errcnt, ser.mStar);
		}
		whatILearned.put(eh.ruleSetName, learned);
	    }

	    // Also count any errors that were made after the learning success
	    for(; j<subsection.length; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		if (!eh.episodeId.equals(e.eid)) throw new IllegalArgumentException("Array mismatch");
		
		if (e.code!=CODE.ACCEPT) {
		    ser.totalErrors ++;
		}
	    }
	    
	    ser.totalMoves += subsection.length;
	}
	return ser;
    }
    
}
