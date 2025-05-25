package edu.wisc.game.pseudo;

import java.io.*;
import java.util.*;
//import javax.persistence.*;

//import org.apache.openjpa.persistence.jdbc.*;

//import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.sql.EpisodeInfo.ExtendedDisplay;
import edu.wisc.game.rest.ParaSet;
//import edu.wisc.game.rest.TrialList;
import edu.wisc.game.rest.Files;
import edu.wisc.game.rest.PlayerResponse;
//import edu.wisc.game.engine.RuleSet;
//import edu.wisc.game.engine.AllRuleSets;
import edu.wisc.game.saved.*;

import edu.wisc.game.websocket.WatchPlayer;

public class Pseudo {

    /** The bot player (who is not necessarily Player 0, the one who stores the episode!) */
    PlayerInfo p;
    /** Episode in which the move is to be made */
    EpisodeInfo epi;
    int expectedAttemptCnt;
    //    ExtendedDisplay d;
    
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
