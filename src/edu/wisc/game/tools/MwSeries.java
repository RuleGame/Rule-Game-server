package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;

import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.tools.MwByHuman.PrecMode;


/**  An auxiliary class for MwByHuman, an MWSeries object contains the data for one series (group of episodes played by one player under the same rule set) needed to contribute a number to an M-W Comparandum. For each episode, we need these data:
	 <pre>
playerId
episodeId
ruleSetName
predecessors
achieved10
m*
</pre>
     */
public  class MwSeries {

    public final String ruleSetName;	
    /** Which other rules preceded this rule in the trial list? */
    final Vector<String> precedingRules;
    final String exp;
    final String trialListId;
    final int seriesNo;
    /** In 1PG or adve 2PG the id of the actual player who made the moves. In coop 2PG, the id of Player 0. */
    final String playerId;
    
    /** When a value is put here, it it used instead of the
	normally-computed key. This is used in the Pooling app,
	when working with pooled samples.
    */
    private String forcedKey = null;
    public void setForcedKey(String key) { forcedKey = key; }
	
    /** The 'key' (what comparandum, if any, this series belongs to) depends on the mode */
    public String getKey(PrecMode mode) {
	if (forcedKey!=null) return forcedKey;
	
	switch (mode) {
	case Ignore: return ruleSetName;	    
	case Every:
	case EveryCond:
	    String s = String.join(":", precedingRules);
	    if (s.length()>0) s += ":";
	    return s + ruleSetName;
	case Naive: return (precedingRules.size()==0)? ruleSetName: null;
	default: throw new IllegalArgumentException("" + mode);
	}
    }

    /** Converts a key produced with any PrecMode to the key that
	would be produced with PrecMode.Ignore */
    public static String keyToIgnoreKey(String key) {
	return key.replaceAll(".*:", "");
    }
    
    /** Used for EveryCond; only lists the preceding,
	and does not include the target */
    public String getLightKey() {
	if (forcedKey!=null) return forcedKey;
	String s = String.join(":", precedingRules);
	return s;
    }
	
    boolean learned=false;
    public boolean getLearned() { return learned; }
    /** The number of errors until the first "winning streak" has been
	achieved, or in the entire series (if no winning streak) */
    int errcnt=0;
    /** The number of move attempts until the beginning of the first
	"winning streak" has been achieved, or the large default
	number otherwise. */
    double mStar=0;
    /** Total failed attempt (including those after the "achievement of learning") */
    int totalErrors=0;
    public int getTotalErrors() { return totalErrors; }
    /** Total move and pick attempts (successful and unsuccessful).
	Since ver 8.036: excludes successful picks (as useless for our analyses)
     */
    int totalMoves=0;
    public int getTotalMoves() { return totalMoves; }
    
    public double getMStar() { return mStar; }
    /** An integer approximation to MStar */
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

    /** @param chosenMover If it's -1, the entire transcript is
	analyzed. (That's the case for 1PG and coop 2PG). In adve 2PG,
	it is 0 or 1, and indicates which partner's record one extracts
	from the transcript.
    */
    MwSeries(EpisodeHandle o, Set<String> ignorePrec, int chosenMover) {
	ruleSetName = o.ruleSetName;
	precedingRules = new Vector<String>();
	for(String r: o.precedingRules) {
	    if (ignorePrec!=null && ignorePrec.contains(r)) {
		MwByHuman.incrementIgnorePrecCnt();
		continue;
	    }
	    precedingRules.add(r);
	}
	exp = o.exp;
	trialListId = o.trialListId;
	seriesNo = o.seriesNo;
	if (chosenMover == 1) {
	    if (o.neededPartnerPlayerId==null) throw new IllegalArgumentException("Partner player ID is not know for pid="+o.playerId);
	    playerId = o.neededPartnerPlayerId;
	} else {
	    playerId = o.playerId;
	}
    }

    static final String header="#ruleSetName,precedingRules,"+
	"exp,trialListId,seriesNo,playerId,learned,total_moves,total_errors,mStar,mDagger";

    String toCsv() {
	    String[] v = { ruleSetName,
			   String.join(":", precedingRules),
			   exp,
			   trialListId,
			   ""+seriesNo,
			   playerId,
			   ""+learned,
			   ""+getTotalMoves(),
			   ""+getTotalErrors(),
			   ""+mStar,
			   ""+mDagger};
	    String s = ImportCSV.escape(v);
	    if (moveInfo!=null) {
		s += "," + Util.joinNonBlank(";", moveInfo);
	    }
	    return s;
    }

    /** Reads a CSV file with MwSeries entries.
	@param into Adds the data into this vector.
    */
    public static void readFromFile(File f, Vector<MwSeries> into) throws IOException, IllegalInputException {
	if (!f.exists()) throw new IOException("File does not exist: " + f);
	if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	CsvData csv = new CsvData(f, false, false, null);
	
	if (csv.entries.length<2) throw new IOException("No data found in file: " + f);
	CsvData.BasicLineEntry header =  (CsvData.BasicLineEntry)csv.header;
	//System.out.println("Header=" + header);
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
	mDagger = Double.parseDouble(line.getColByName(header, "mDagger", "NaN"));

	String mi = line.getColByName(header, "moveInfo", null);
	if (mi!=null) {
	    String[] v = mi.split(";");
	    moveInfo = new MoveInfo[v.length];
	    for(int j=0; j<v.length; j++) moveInfo[j] = new MoveInfo(v[j]);
	}
	
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

    /** Modifies the precedingRules array, prepending "true." or
	"false." to each element depending on whether successful
	learning took place in the corresponding series. This is
	used in PrecMode.EveryCond mode.

	@param whatILearned Info about this player's learning 
	success on the rules he's done previously.
    */
    void adjustPreceding(HashMap<String,Boolean> whatILearned) {
	for(int j=0; j< precedingRules.size(); j++) {
	    String r = precedingRules.get(j);
	    if (r.startsWith("true.") ||r.startsWith("false.")) continue;
	    Boolean learned = whatILearned.get(r);
	    if (learned==null) throw new IllegalArgumentException("Cannot find preceding series for p="+playerId+", perhaps because the trial list files have been erased, or the player skipped the series for rule="+r);
	    r = "" + learned + "." + r;
	    
	    //if (ignorePrec.contains(r)) continue;
	    precedingRules.set(j, r);
	}   
    }

    /** Clears the precedingRules array. This is used in MWH when precMode=ignore
     */
    void stripPreceding() {
	precedingRules.setSize(0);
    }


    /** Various things that may be used to draw curves */
    static class MoveInfo {
	boolean success;
	double p0;
	MoveInfo(boolean _success,	double _p0) {
	    success = _success;
	    p0 = _p0;
	}
	/** @param s "success:p0", e.g. "1:0.33" */
	MoveInfo(String s) {
	    String [] v = s.split(":");
	    success = (v[0].equals("1"));
	    p0 = Double.parseDouble(v[1]);
	}
	public String toString() {
	    return  "" + (success? 1:0) +  ":" + p0;
	}
    }
    /** Besides the aggregate information that MwSeries contains, this
	also has per-move data used to draw curves. This is null in the
	MWH tool, but non-null in the BuildCurve tool
    */
    MoveInfo[] moveInfo = null;

    
}
