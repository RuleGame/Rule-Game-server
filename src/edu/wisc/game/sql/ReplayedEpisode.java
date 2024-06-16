package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;


/** Built on top of an Episode object, a ReplayedEpisode is created during
    the analysis of transcripts, in order to recreate the episode's events 
    step by step. This is primarily needed so that we can compute the p0(D) 
    value    for each move of the episode.
*/
public class ReplayedEpisode extends Episode {

    /** The possible random player models.
     */
    public enum RandomPlayer {
	COMPLETELY_RANDOM, MCP1 
    };
    

    //    public Game(RuleSet _rules, Board _initialBoard);
    final ParaSet para;

    /** We tell the player where all movable pieces are, unless the 
	para set mandates "free" mode.
     */
    boolean weShowAllMovables() {
	return !para.isFeedbackSwitchesFree();
    }

    /** The random player model used to compute p0 */
    final RandomPlayer randomPlayerModel;
    
   /** Creates an Episode in order to replay an old recorded Game
       (with a known rule set and a known initial board).
    */
public ReplayedEpisode(String _episodeId, ParaSet _para, Game game,
		       RandomPlayer _randomPlayerModel
		       ) {
	super(game, Episode.OutputMode.BRIEF, null, null, _episodeId);
	randomPlayerModel  = _randomPlayerModel;
	para = _para;
	if (game.initialBoard==null) {
	    throw new IllegalArgumentException("Cannot replay a game without knowing the initial board!");
	}
	
    }

    /** The player knowledge obtained by failed attempts on the current board */
    private BitSet failedPicks = new BitSet();    
    private Vector<BitSet> failedMoves=new Vector<>();
    private BitSet movesFor(int pos) {	
 	if (failedMoves.size()<=pos) failedMoves.setSize(pos+1);
	BitSet b = failedMoves.get(pos);
	if (b==null) failedMoves.set(pos, b = new BitSet(NBU));
	return b;
   }

    /** Computes the probability of success for a random pick or random
	move made by a frugal player. A call to this method should precede
	a call to accept().

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

	@param code The historical acceptance code for this attempt,
	as read from the transcript
    */
    public double computeP0( Pick nextMove, int code) {
    
	int knownFailedPicks = failedPicks.cardinality();
	int knownFailedMoves = 0;	
	for(BitSet b : failedMoves)  {	    
	    if (b!=null) knownFailedMoves += b.cardinality();
	}

	boolean successfulPick = !(nextMove instanceof Move) && code==CODE.ACCEPT;
	System.out.println("RE.computeP0(" +nextMove+"); code="+code+", successfulPick=" + successfulPick);

	
	return  successfulPick?
	    ruleLine.computeP0ForPicks(knownFailedPicks):
	    ruleLine.computeP0ForMoves(knownFailedMoves);

    }

    /** In addition to the normal "accept" stuff, either erases or
	augments the player's knowledge of the current board's
	properties. (Depending on whether the board has changed or not).
     */
    public int accept(Pick pick) {
	int code = super.accept(pick);

	if (randomPlayerModel==RandomPlayer.COMPLETELY_RANDOM) {
	    // No knowledge kept!
	} else if (randomPlayerModel==RandomPlayer.MCP1) {

	
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
	    int pos = pick.getPos();
	    if (pick instanceof Move) {
		// In the show-movables mode, only movable pieces are taken into account.
		// Ideally, this conditional is not even needed, since in the show-movables
		// mode the GUI client should not even allow the client to attempt moving
		// an immovable piece. But we have the condition just in case the client
		// does not behaves quite right; and also for the HTML Play.
		if (!weShowAllMovables() ||  ruleLine.isMoveable[pos]) {
		    BitSet b = movesFor(pos);
		    Move move = (Move)pick;
		    b.set(move.getBucketNo());
		    if (b.cardinality()==NBU) failedPicks.set(pos);
		}
	    } else {
		failedPicks.set(pos);
		// A failed pick prohibits all NBU=4 moves for this piece, too!
		BitSet b = movesFor(pos);
		for(int j=0; j<NBU; j++) b.set(j);
	    }
	    
	}
	} else {
	    throw new IllegalArgumentException("Model not supported: " + randomPlayerModel);
	}
		   
	return code;
    }

    
}
