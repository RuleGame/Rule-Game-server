package edu.wisc.game.pseudo;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.sql.EpisodeInfo.ExtendedDisplay;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.rest.Files;
import edu.wisc.game.rest.PlayerResponse;
import edu.wisc.game.saved.*;

import edu.wisc.game.websocket.WatchPlayer;

/** Support for a Pseudo-learning bot playing as a partner with, or
    against, a human player in a 2PG, or as a bot assistant for a
    human player (in a 1PG or 2PG). An instance of Pseudo is created
    by EpisodeInfo (in human-vs-bot games), or by BotAssist, for each
    move that the bot needs to make or to suggest.
*/
public class Pseudo {

    public static class Params extends BotParams {
	/** This is used in pseudo-AI bots, to indicate how fast it pretends to learn */
	public double halftime = 8.0;
	public double initErrorRate = 0.75;
	/** Reads the pseudo learning params, for bot assist or bot
	    player, from the para set.
	    @param j 0 or 1; used to modify param names, so that we
	    can read params for Player 1 if j==1.
	*/
	public Params(ParaSet para, int j) {
	    String suff = (j==0)? "": "" + j;
	    halftime = para.getDouble("pseudo_halftime" + suff, true, 8.0);
	    initErrorRate =para.getDouble("pseudo_init_error_rate" + suff, true, 0.75);
	}

	public String toString() {
	    return "(Pseudo bot: halfLife=" + halftime +", initErrorRate="+initErrorRate+")";
	}

    }

    /** This bot's parameters */
    final Params params;
    
    
    /** In a game "human against bot", this is the bot player; in a bot assist
	game, this is the player whom the bot assists. Either way,
	this is not necessarily Player 0, the one who stores the episode! */
    PlayerInfo p;
    /** Episode in which the move is to be made */
    EpisodeInfo epi;
    int expectedAttemptCnt;
    
    /** Creates a PlayerInfo object that represents a bot player, rather than
	a human player.  This is only used in human-vs-bot 2PG, not in bot
	assist games. This method is modeled, to some extent on PlayerResponse.
	@param p The live partner against/with whom this bot will eventually play.
     */
    public static PlayerInfo mkBotPlayer(PlayerInfo p) {

	String pid = p.getPlayerId() + "-bot";
	PlayerResponse pr = new PlayerResponse(pid, p.getExperimentPlan(), -1, true);
	if (pr.getError()) {
	    throw new IllegalArgumentException("Error when creating a bot player: " + pr.getErrmsg());
	}
	PlayerInfo x = pr.getPlayerInfo();
	x.setAmBot(true);
	x.saveMe();
	return x;
    }

    /** Creates a task (for making a move), either for a bot that
	plays against a human (in a HvB 2PG), or for a bot that assists
	a human player.
	
	@param _params The bot parameters
	
	@param _p Either a bot player (playing against a human in a HvB 2PG),
	or an assistant bot. This is used to access information about the
	game that has been played so far, rather than for the parameters.

	@param _expectedAttemptCnt This is just used for bookkeeping purposes,
	not for the math.
     */
    public Pseudo(Params _params, PlayerInfo _p,    EpisodeInfo _epi, int _expectedAttemptCnt) {
	params = _params;
	p = _p;
	epi = _epi;
	expectedAttemptCnt = _expectedAttemptCnt;
    }

    public String toString() {
	return "PseudoTask(" + p.getPlayerId()+", " + epi.getEpisodeId() +", " + expectedAttemptCnt+")";
    }
    
    static private Queue<Pseudo> taskQueue = new ArrayDeque<>();

    /** Creates a queued a Pseudo object, which represents a task of making
	a bot move in a human-vs-bot 2PG.
	@param botPlayer A PlayerInfo representing a bot that plays against a human
     */
    static public void addTask(PlayerInfo botPlayer, EpisodeInfo epi, int expectedAttemptCnt) {
	BotParams params = botPlayer.botPartnerParams;
	if (params == null || !(params instanceof Params)) throw new IllegalArgumentException("Wrong bot parameter set (need Pseudo): "+ params);
	Pseudo task = new Pseudo((Params)params, botPlayer, epi, expectedAttemptCnt);
	Logging.info("Adding " + task);
	taskQueue.add(task);
    }

    static final double log2 = Math.log(2.0);

    /** This value, set by each proposeMove() call, contains the probability
	of that call returning a good move.
     */
    public double confidence = 0;

    /** Randomly picks one game piece from a non-empty vector of them */
    //    private static Piece pickPiece(Vector<Piece> v) {
    //	piece = v.get(  Episode.random.nextInt( v.size()));
    //}

    /** Creates an array listing the buckets that are not in bu[] */
    int[] otherBuckets(int[] bu) {
	int [] x = new int[ Episode.NBU - bu.length];
	int k = 0;
	for(int j=0; j<Episode.NBU; j++) {
	    boolean hasJ = false;
	    for(int a: bu) {
		hasJ = (j==a);
		if (hasJ) break;
	    }
	    if (!hasJ) x[k++] = j;
	}
	return x;
    }


    /** Used to reduce the chance of successive calls producing the same
	bad suggestion, which will make the player wonder how dumb the bot is */
    private Move lastProposedBadMove = null;
    
    /** Pseudo-randomly proposes a move, without actually executing it.
	This is used both in HvB and in bot assist.
     */
    public Move proposeMove() {//throws IOException {
	if (expectedAttemptCnt < epi.getAttemptCnt()) {
	    Logging.info("Pseudo: skipping apparently duplicate request " + this);
	    return null;
	}

	if (epi.getFinishCode()!=Episode.FINISH_CODE.NO) {
	    Logging.info("Pseudo: episode already completed, fc=" + epi.getFinishCode());
	    return null;
	}
	
	// Player 0, who owns the episodes
	PlayerInfo owner = p.getPlayerForRole(Pairing.State.ZERO);
		
	// the sum for all episodes in the series
	int t = owner.seriesAttemptCntExcludingSuccessfulPicks(); 
	// The probability of offering a bad move (if bad moves are
	// possible at all): an exponential-decay function with the
	// initial value 0.75 (or as specified in the para set) and
	// the specified half-life time
	double Q =
	    (params.halftime <= 0)? 0:
	    params.initErrorRate * 
	    (params.halftime == Double.POSITIVE_INFINITY ? 1:
	     Math.exp( -t/params.halftime*log2));
	
	Board b =  epi.getCurrentBoard(true);
	boolean show = epi.weShowAllMovables();

	// All pieces on the board that don't have a cross on them
	Vector<Piece> pieces = new Vector<>();
	// can be moved to at least 1 bucket
	Vector<Piece> movablePieces = new Vector<>();
	// either can be moved to some, but not all, buckets;
	// cannot be moved anywhere, but are not visibly marked with a cross,
	// because the "fixed" mode is not on   
	Vector<Piece> worsePieces = new Vector<>();

	int allCnt=0, goodCnt=0;
	for(Piece piece: b.getValue()) {
	    if (piece.getDropped()!=null) continue;
	    int n = piece.getBuckets().length;
	    goodCnt += n;
	    
	    if (n>0) {
		movablePieces.add(piece);
		if (n<Episode.NBU) worsePieces.add(piece);
	    } else if (!show) {
		worsePieces.add(piece);
	    }
	    
	    if (!show || n>0) {
		pieces.add(piece);
		allCnt += Episode.NBU;
	    }
	}

	// Do we want to return a good move?
	boolean doGood = (worsePieces.size()==0) ||
	    (Episode.random.nextDouble() >= Q);

	Move answer = null;
	
	if (doGood) { // propose a good move
	    Piece piece = Episode.random.pickFrom(movablePieces);
	    int[] bu = piece.getBuckets();
	    int k = Episode.random.pickFrom(bu);
	    answer = new Move(piece, k);
	} else { // propose a bad move
	    int retry = 0;
	    do {
		Piece piece = Episode.random.pickFrom(worsePieces);
		int[] bu = piece.getBuckets();
		int k = (bu.length==0) ? 
		    Episode.random.nextInt( Episode.NBU):
		    Episode.random.pickFrom(otherBuckets(bu));		
		answer = new Move(piece, k);
	    } while( answer.sameMove(lastProposedBadMove) && retry++ < 3);
	    lastProposedBadMove = answer;
	}


	/** "Confidence" estimates for the message to the player */
	confidence = 1 - Q;
	
	return answer;
    }

    
    public Move old_proposeMove() throws IOException {
	if (expectedAttemptCnt < epi.getAttemptCnt()) {
	    Logging.info("Pseudo: skipping apparently duplicate request " + this);
	    return null;
	}

	if (epi.getFinishCode()!=Episode.FINISH_CODE.NO) {
	    Logging.info("Pseudo: episode already completed, fc=" + epi.getFinishCode());
	    return null;
	}
	
	// Player 0, who owns the episodes
	PlayerInfo owner = p.getPlayerForRole(Pairing.State.ZERO);
	
        //Board b = d.getBoard();
	Board b =  epi.getCurrentBoard(true);
	boolean show = epi.weShowAllMovables();

	Vector<Piece> pieces = new Vector<>();
	Vector<Piece> movablePieces = new Vector<>();
	int allCnt=0, goodCnt=0;
	for(Piece piece: b.getValue()) {
	    if (piece.getDropped()!=null) continue;
	    int n = piece.getBuckets().length;
	    goodCnt += n;
	    
	    if (n>0) movablePieces.add(piece);
	    if (!show || n>0) {
		pieces.add(piece);
		allCnt += Episode.NBU;
	    }
	}

	
	if (movablePieces.size()==0) Logging.error("For episode " + epi.getEpisodeId() + ", there are no movable pieces");	
	
	Piece piece = pieces.get(Episode.random.nextInt( pieces.size()));
	int k =  Episode.random.nextInt( Episode.NBU);
	int t = owner.seriesAttemptCnt(); // the sum for all episodes in the series

	// An exponential-decay with the initial value 1 and the specified
	// half-life time
	double ex =
	    (params.halftime <= 0)? 0:
	    (params.halftime == Double.POSITIVE_INFINITY) ? 1:
	    Math.exp( -t/params.halftime*log2);

	boolean allowed = false;
	int[] bu = piece.getBuckets();
	for(int i: bu) {
	    allowed = (i==k);
	    if (allowed) break;
	}
	if (!allowed) {
	    boolean mustRedo;
	    if (params.halftime <= 0) mustRedo=true;
	    else if (params.halftime == Double.POSITIVE_INFINITY)  mustRedo=false;
	    else {
		double redoProb = 1 - ex;
		Logging.info("Pseudo-AI: after " +t+ "/"+params.halftime + "m, redoProb=" + redoProb);
		mustRedo = (Episode.random.nextDouble() < redoProb);
	    }
	    if (mustRedo) { // replace bad move with a guaranteed good one
		piece = movablePieces.get(  Episode.random.nextInt( movablePieces.size()));
		bu = piece.getBuckets();	     
		k = bu[ Episode.random.nextInt( bu.length) ];
	    }			 
	}
	Move answer = new Move(piece, k);
		
	/** "Confidence" estimates for the message to the player */
	double bad = (double)(allCnt-goodCnt)/(double)allCnt;
	confidence = 1 - bad * ex; 
	
	return answer;
    }

    void doTask() throws IOException {
	Episode.Move move = proposeMove();
	
	ExtendedDisplay q = epi.doMove2(p.getPlayerId(), move.getPieceId(), move.getBucketNo(), expectedAttemptCnt);
	
    }

    /** This should be run periodically from the maintenance thread */
    public static int checkTasks() {
	Pseudo task=null;
	int cnt=0;
	while( (task=taskQueue.poll())!=null) {
	    try {
		Logging.info("Popped " + task);
		task.doTask();		
		cnt++;
	    } catch(Exception ex) {
		Logging.error("Pseudo: error in task: " + ex);
		ex.printStackTrace(System.err);
	    }
	}
	return cnt;
    }
    
}
