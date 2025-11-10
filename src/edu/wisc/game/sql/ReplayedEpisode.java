package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.tools.*;
import edu.wisc.game.tools.AnalyzeTranscripts.TrialListMap;
import edu.wisc.game.tools.AnalyzeTranscripts.P0andR;

/** Built on top of an Episode object, a ReplayedEpisode is created during
    the analysis of transcripts, in order to recreate the episode's events 
    step by step. This is primarily needed so that we can compute the p0(D) 
    value    for each move of the episode.
*/
public class ReplayedEpisode extends Episode {

    static boolean debug = true;
    
    /** The possible random player models.
     */
    public enum RandomPlayer {
	COMPLETELY_RANDOM, MCP1;

	public static RandomPlayer valueOf1(String mode) {
	    if (mode.equals("random")) {
		return COMPLETELY_RANDOM;
	    }
	    return valueOf( mode.toUpperCase());
	}
	
    };
    

    //    public Game(RuleSet _rules, Board _initialBoard);
    final ParaSet para;

    /** We tell the player where all movable pieces are, unless the 
	para set mandates "free" mode.

     */
    public boolean weShowAllMovables() {
	return !para.isFeedbackSwitchesFree();
    }

    /** The random player model used to compute p0 */
    final RandomPlayer randomPlayerModel;
    
   /** Creates an Episode in order to replay an old recorded Game
       (with a known rule set and a known initial board).
    */
    public ReplayedEpisode(String _episodeId, ParaSet _para, Game game,
			   RandomPlayer _randomPlayerModel   ) {
	super(game, Episode.OutputMode.BRIEF, null, null, _episodeId, false);
	randomPlayerModel  = _randomPlayerModel;
	if (randomPlayerModel  == null) throw new IllegalArgumentException("randomPlayerModel not specified when creating ReplayedEpisode");

	para = _para;
	if (game.initialBoard==null) {
	    throw new IllegalArgumentException("Cannot replay a game without knowing the initial board!");
	}
	
    }

    /** The player knowledge obtained by failed attempts on the current board.
	Indexed by j (piece number in the Episode's array)
     */
    private BitSet failedPicks = new BitSet();    
    private Vector<BitSet> failedMoves=new Vector<>();
    private BitSet movesForJ(int j) {	
 	if (failedMoves.size()<=j) failedMoves.setSize(j+1);
	BitSet b = failedMoves.get(j);
	if (b==null) failedMoves.set(j, b = new BitSet(NBU));
	return b;
   }

    /** Computes the probability of success for a random pick or
	random move made by the player modeled by
	this.randomPlayerModel (such as a frugal player (MCP1), or a
	completely random one). A call to this method should precede a
	call to accept().

	<p>This method uses this.failedPicks, which is set in this.accept()
	depending on this.randomPlayerModel. (For MCP1, it contains some 
	knowledge; for a random player, it's empty).
	

	<p>
	As of 2021-09-18, the approach is that "P0 for a pick" is only
	used for successful picks (i.e. when there is incontrovertible
	evidence that the player wanted to do a pick). For failed
	picks, we use the "P0 for a move", since it's believed that the
	player most likely intended to attempt a move, but the GUI 
	converts a move attempt on an immovable piece to a failed pick.

	@param nextMove The pick/move attempt the value of p0 before which
	(for which) we want to compute. This pick or move has been
	read from the transcript, and contains the success code, which we
	can use to interpret what the random player may have wanted here.
	The nextMove.code field is not set yet, because it will only be 
	set during an actual replay (the Episode.accept() call).

	@param successfulPick True if this was a "successful pick",
	based on the historical acceptance code for this attempt, as
	read from the transcript
    */
    public double computeP0( Pick nextMove, boolean successfulPick) {
    
	if (successfulPick) {
	    int knownFailedPicks = failedPicks.cardinality();
	    return  ruleLine.computeP0ForPicks(knownFailedPicks);
	} else {
	    int knownFailedMoves = 	countKnownFailedMoves();
	    return ruleLine.computeP0ForMoves(knownFailedMoves);
	}
    }

    int countKnownFailedMoves() {
	int knownFailedMoves = 0;	
	for(BitSet b : failedMoves)  {	    
	    if (b!=null) knownFailedMoves += b.cardinality();
	}
	return knownFailedMoves;	
    }

    /** In addition to the normal "accept" stuff, this method may
	eithe erases or augments the player's knowledge of the current
	board's properties. (Depending on whether the board has
	changed or not). This "knowledge accumulation" may or may not
	be done depending on the current random player model.
     */
    public int accept(Pick pick) {
	int code = super.accept(pick);

	if (randomPlayerModel==RandomPlayer.COMPLETELY_RANDOM) {
	    // No knowledge kept!
	} else if (randomPlayerModel==RandomPlayer.MCP1) {
	    // MCP player accumulates knowledge
	
	if (code==CODE.ACCEPT) {
	    if (pick instanceof Move) {	    
		// the board has changed; the old knowledge can be wiped out
		failedPicks.clear();
		failedMoves.clear();
	    } else {
		// FIXME: successful Pick also gives new knowledge about
		// the current display, but I don't have a model and
		// a structure to represent it
	    }
	} else {
	    // The player's knowledge has increased
	    int j = findJ(pick);
	    
	    if (pick instanceof Move) {  // failed move
		// In the show-movables mode, only movable pieces are taken into account.
		// Ideally, this conditional is not even needed, since in the show-movables
		// mode the GUI client should not even allow the client to attempt moving
		// an immovable piece. But we have the condition just in case the client
		// does not behaves quite right; and also for the HTML Play.
		if (!weShowAllMovables() ||  ruleLine.isJMoveable[j]) {
		    BitSet b = movesForJ(j);
		    Move move = (Move)pick;
		    b.set(move.getBucketNo());
		    if (b.cardinality()==NBU) failedPicks.set(j);
		}
	    } else {
		failedPicks.set(j);
		// A failed pick prohibits all NBU=4 moves for this piece, too!
		BitSet b = movesForJ(j);
		b.set(0, NBU);
	    }
	    
	}
	} else {
	    throw new IllegalArgumentException("Model not supported: " + randomPlayerModel);
	}
		   
	return code;
    }


    static boolean quiet = false;
    
    private static void usage() {
	usage(null);
    }
    private static void usage(String msg) {
	System.err.println("Usage: java ReplayedEpisode exp pid");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    /** A unit test, which can be run without relying on SQL, just
	with files in the transcript and board directories.

	<p>Usage:
	java ReplayedEpisode exp playerId
	

	<p>//FIXME: if the data set contains any adve 2PG, this thing
	won't know what the adversary's (Player 1's) playerId was,
	because it's not recorded in the CSV files. (It's only on the
	SQL database).

    */
    public static void main(String[] argv) throws IOException, IllegalInputException,  RuleParseException {

	//---

	String config = null;
	String inputDir = null;

	int j=0;
	for(; j<argv.length; j++) {
	    String a = argv[j];
	    if (j+1< argv.length && a.equals("-config")) {
		config = argv[++j];
	    } else if (j+1< argv.length && a.equals("-in")) {
		inputDir = argv[++j];
	    } else {
		break;
	    }
	}

	if (config!=null) {
	    // Instead of the master conf file in /opt/w2020, use the customized one
	    MainConfig.setPath(config);

	    // Set the input directory as per the config file, unless
	    // explicitly overridden by the "-in" option.
	    //	    if (inputDir == null) inputDir = MainConfig.getString("FILES_SAVED", null);
	}

	// The -input option may override -config
	if (inputDir != null) {
	    if (!(new File(inputDir)).isDirectory()) usage("Not a directory: " + inputDir);
	    //Files.setSavedDir(inputDir);
	    MainConfig.put("FILES_SAVED", inputDir);
	}


	//--

	System.out.println("j=" + j);
	String exp = argv[j++];
	String playerId = argv[j++];
	System.out.println("Analyzing data for exp=" + exp +", pid=" + playerId);

	
	TrialListMap trialListMap=new TrialListMap(exp);

	File inFile = Files.transcriptsFile(playerId, true);
	TranscriptManager.ReadTranscriptData transcript = new TranscriptManager.ReadTranscriptData(inFile);

	// split by episode 
	Vector<TranscriptManager.ReadTranscriptData.Entry[]> subsections = AnalyzeTranscripts.splitTranscriptIntoEpisodes(transcript);
	// remove any duplicates that may exist due to imperfections in the transcript saving mechanism
	AnalyzeTranscripts.removeDuplicates(subsections);
	
	
	// One subsection per episode
	if (!quiet) System.out.println("Player "+playerId+": split the transcript ("+transcript.size()+" moves) into "+subsections.size()+ " episode sections");
	    
	String lastRid=null;
	// all episodes' subsections for a given rule sets
	Vector<TranscriptManager.ReadTranscriptData.Entry[]> section=new Vector<>();
	//Vector<EpisodeHandle> includedEpisodes=new Vector<>();

	File detailedTranscriptsFile = Files.detailedTranscriptsFile(playerId, true);
	HashMap<String,EpisodeHandle> ehh = TranscriptManager.findRuleSetNames(exp, trialListMap,  detailedTranscriptsFile);


	File boardsFile =  Files.boardsFile(playerId, true);
	HashMap<String,Board> boards = BoardManager.readBoardFile(boardsFile, null);	

	P0andR result = new P0andR( Util.sumLen(subsections));
	int k=0;

	
	// For each episode...
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: subsections)  {
	    String eid = subsection[0].eid;

	    EpisodeHandle eh = ehh.get(eid);
	    if (eh==null) throw new IllegalArgumentException("No rule set name etc found for eid=" + eid);

	    String rid= eh.ruleSetName;

	    if (lastRid == null) lastRid = rid;
	    else if (!lastRid.equals(rid)) {
		//saveAnyData( section, includedEpisodes);
		lastRid=rid;
	    }
	    //includedEpisodes.add(eh);
	    section.add(subsection);


	    //---
	    RuleSet rules = AllRuleSets.obtain( rid);
	
	    
	    Board board = boards.get(eid);
	    Game game = new Game(rules, board);
	    ReplayedEpisode.RandomPlayer randomPlayerModel=	ReplayedEpisode.RandomPlayer.COMPLETELY_RANDOM;	

	    TrialList t = trialListMap.get( eh.trialListId);

	    
	    ReplayedEpisode rep = new ReplayedEpisode(eid, eh.para, game, randomPlayerModel);

	    //----

	    if (debug) System.out.println("All moves for eid=" + eid + " :");
	    for(j=0; j<subsection.length; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		if (debug) System.out.println(e.pick.toString());
	    }


	    Vector<Board> boardHistory = new Vector<>();

	    
	    for(j=0; j<subsection.length; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];

		if (debug) {
		    System.out.println( rep.displayJson());
		    System.out.println( rep.graphicDisplay(false));
		}
		
		
		if (boardHistory!=null) {		
		    Board b = rep.getCurrentBoard();
		    boardHistory.add(b);
		}

		double p =rep.computeP0(e.pick, false);	    
		result.p0[k] = p;
	    
		//-- replay the move/pick attempt 
		int code = rep.accept(e.pick);

		result.rValues[k] = e.pick.getRValue();

		if (debug) System.out.println("Replay move["+k+"]=" +e.pick.toString() +", p0=" + p+", recorded code=" + e.code +", r=" + result.rValues[k]);

		k++;

		
		if (!Episode.CODE.areSimilar(code,e.code)) {
		    String msg = "Unexpected code in episode "+eid+", replay code=" + code +", vs. the recorded code=" + e.code;
		    if (debug) {
			System.out.println(msg);
			System.out.println( rep.displayJson());
			System.out.println( rep.graphicDisplay(false));

		    }
		    throw new IllegalArgumentException(msg);
		}
	    }


	}

    }

    /** Lists all (piece, destinations) pairs that a random player may try.
     */
    private Vector<Pick> allMovesToTry() {
	Vector<Pick> w = new Vector<>();

	for(int j=0; j<getValues().size(); j++) {
	    boolean moveable = ruleLine.isJMoveable[j];
	    if (weShowAllMovables() && !moveable) continue;
	    if (failedPicks.get(j)) continue;
	    BitSet fm = (j<failedMoves.size()? failedMoves.get(j): null);
	    for(int b=0; b<NBU; b++) {
		if (fm!=null && fm.get(b)) continue;
		Piece p = getValues().get(j);
		Pick move = new Move(p, b);
		w.add(move);
	    }
	}
	return w;	
    }


    /** Generates a Move or Pick that a UI could send if the player
	were a random player described by this.randomPlayerMode.
	FIXME: using Episode.random, instead of a run-specific 
	random number generator.
    */
    public Pick generateRandomMove() {
	Vector<Pick> w = allMovesToTry();
	return w.get( random.nextInt(w.size()));
    }

    

    
    
}
