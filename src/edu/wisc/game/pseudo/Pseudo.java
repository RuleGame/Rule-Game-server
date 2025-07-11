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

public class Pseudo {

    /** The bot player (who is not necessarily Player 0, the one who stores the episode!) */
    PlayerInfo p;
    /** Episode in which the move is to be made */
    EpisodeInfo epi;
    int expectedAttemptCnt;
    
    /** Creates a bot player. Modeled, to some extent on PlayerResponse.
	@param p The live partner with whom this bot will eventually play
     */
    public static PlayerInfo mkBot(PlayerInfo p) {

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

    public Pseudo(   PlayerInfo _p,     EpisodeInfo _epi, int _expectedAttemptCnt) {
	p = _p;
	epi = _epi;
	expectedAttemptCnt = _expectedAttemptCnt;
    }

    public String toString() {
	return "PseudoTask(" + p.getPlayerId()+", " + epi.getEpisodeId() +", " + expectedAttemptCnt+")";
    }
    
    static private Queue<Pseudo> taskQueue = new ArrayDeque<>();
    
    static public void addTask(PlayerInfo botPlayer, EpisodeInfo epi, int expectedAttemptCnt) {
	Pseudo task = new Pseudo(botPlayer, epi, expectedAttemptCnt);
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
    
    /** Pseudo-randomly proposes a move, without actually executing it */
    public Move proposeMove() throws IOException {
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
	
	double	halftime = p.pseudoHalftime;
	// the sum for all episodes in the series
	int t = owner.seriesAttemptCntExcludingSuccessfulPicks(); 
	// The probability of offering a bad move (if bad moves are
	// possible at all): an exponential-decay function with the
	// initial value 0.75 (or as specified in the para set) and
	// the specified half-life time
	double Q =
	    (halftime <= 0)? 0:
	    p.pseudoInitErrorRate * 
	    (halftime == Double.POSITIVE_INFINITY ? 1:
	     Math.exp( -t/halftime*log2));
	
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
	
	double	halftime = p.pseudoHalftime;

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
	    (halftime <= 0)? 0:
	    (halftime == Double.POSITIVE_INFINITY) ? 1:
	    Math.exp( -t/halftime*log2);

	boolean allowed = false;
	int[] bu = piece.getBuckets();
	for(int i: bu) {
	    allowed = (i==k);
	    if (allowed) break;
	}
	if (!allowed) {
	    boolean mustRedo;
	    if (halftime <= 0) mustRedo=true;
	    else if (halftime == Double.POSITIVE_INFINITY)  mustRedo=false;
	    else {
		double redoProb = 1 - ex;
		Logging.info("Pseudo-AI: after " +t+ "/"+halftime + "m, redoProb=" + redoProb);
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
