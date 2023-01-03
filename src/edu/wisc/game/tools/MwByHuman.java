package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
//import edu.wisc.game.reflect.*;
import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.math.*;
import edu.wisc.game.formatter.*;

import edu.wisc.game.sql.Episode.CODE;

/** Ranking rule sets by the ease of learning by human players. As
 * requested by PK, 2022-12-22.
 */
public class MwByHuman extends AnalyzeTranscripts {

    private MwByHuman(String _playerId, File _base, PrintWriter _wsum) {
	super( _playerId, _base, _wsum);
	quiet = true;
    }

    
  public static void main(String[] argv) throws Exception {
      	EntityManager em = Main.getNewEM();

	ArgType argType = ArgType.PLAN;
	boolean fromFile = false;
	
	Vector<String> plans = new Vector<>();
	Vector<String> pids = new Vector<>();
	Vector<String> nicknames = new Vector<>();
	Vector<Long> uids = new Vector<>();

	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];

	    if  (a.equals("-plan")) {
		argType = ArgType.PLAN;
	    } else if (a.startsWith("-")) {
		throw new IllegalArgumentException("Unknown option: " + a);
	    } else if  (a.equals("-file")) {
		fromFile=true;
	    } else {

		String[] v=  fromFile? readList(new File(a)):  new String[]{a};
				  
		for(String b: v) {
		    if (argType==ArgType.PLAN) 	plans.add(b);
		    //else if (argType==ArgType.UNICK)  nicknames.add(b);
		    //else if (argType==ArgType.UID)  uids.add(Long.parseLong(b));
		    //else if (argType==ArgType.PID)  pids.add(b);
		    else 	throw new IllegalArgumentException("Unsupported argType: " + argType);
		}
	    }

	}

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
		String msg = "ERROR: Skipping plan=" +exp+" due to an exception:";
		System.out.println(msg);
		System.err.println(msg);
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }

	}	

	PrintWriter wsum =null;
	File gsum=new File("wm-human.csv");
	wsum = new PrintWriter(new FileWriter(gsum, false));
	wsum.println( MwSeries.header);

	Vector<MwSeries> allMws = new Vector<>();
	
	for(String playerId: ph.keySet()) {
	    Vector<EpisodeHandle> v = ph.get(playerId);
	    try {
		MwByHuman atr = new MwByHuman(playerId, null, null); //base, wsum);
		atr.analyzePlayerRecord(v);

		allMws.addAll( atr.savedMws);
		for(MwSeries ser: atr.savedMws) {
		    wsum.println(ser.toCsv());
		}

		
	    } catch(Exception ex) {
		System.err.println("ERROR: Cannot process data for player=" +playerId+" due to missing data. The problem is as follows:");
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }
	}

	if (wsum!=null) wsum.close();

	//-- now, the MW Test
	Fmter plainFm = new Fmter();
	MannWhitneyComparison.Mode mode = MannWhitneyComparison.Mode.CMP_RULES_HUMAN;
	MannWhitneyComparison mwc = new MannWhitneyComparison(mode);
	
	Comparandum[][] allComp = Comparandum.mkMlcComparanda(allMws.toArray(new MwSeries[0]));
	

	String text =  mwc.doCompare("humans", null, allComp, plainFm);
	System.out.println(text);

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

	
	boolean learned=false;
	public boolean getLearned() { return learned; }
	/** The number of errors until the first "winning streak" has been
	    achieved, or in the entire series (if no winning streak) */
	int errcnt=0;
	/** The number of errors until the first  "winning streak" has been
	    achieved, or the large default number otherwise */
	int mStar=0;
	/** Total failed attempt (including those after the "achievement of learning") */
	int totalErrors=0;
	public int getTotalErrors() { return totalErrors; }
	/** Total move and pick attempts (successful and unsuccessful) */
	int totalMoves=0;
	public int getTotalMoves() { return totalMoves; }
	
	public int getMStar() { return mStar; }
	MwSeries(EpisodeHandle o) {
	    ruleSetName = o.ruleSetName;
	    precedingRules = o.precedingRules;
	    exp = o.exp;
	    trialListId = o.trialListId;
	    seriesNo = o.seriesNo;
	    playerId = o.playerId;
	}

	static final String header="#ruleSetName,precedingRules,"+
	    "exp,trialListId,seriesNo,playerId,learned,errcnt,mStar";

	String toCsv() {
	    String[] v = { ruleSetName,
			   String.join(";", precedingRules),
			   exp,
			   trialListId,
			   ""+seriesNo,
			   playerId,
			   ""+learned,
			   ""+errcnt,
			   ""+mStar};
	    return ImportCSV.escape(v);
	}
	
    }

    /** Info about each episode gets added here */
    private Vector<MwSeries> savedMws = new Vector<>();
    
    public static final int targetStreak = 10;

    /** Saves the data for a single (player, ruleSet) pair
	@param section A vector of arrays, each array representing the recorded
	moves for one episode.
	@param includedEpisodes All non-empty episodes played by this player in this rule set. This array is aligned with section[]
    */
  
    protected void saveAnyData(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
			     Vector<EpisodeHandle> includedEpisodes)
	throws  IOException, IllegalInputException,  RuleParseException {

	final int defaultMStar = 300;

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
		    ser.mStar = Math.min( ser.errcnt, defaultMStar);
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