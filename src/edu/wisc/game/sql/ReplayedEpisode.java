package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;

//import javax.xml.bind.annotation.XmlElement; 

/** Built on top of an Episode object, a ReplayedEpisode is created during
    the analysis of transcripts, in order to recreate the episode's events 
    step by step. This is primarily needed so that we can compute the p0(D) 
    value    for each move of the episode.
*/
public class ReplayedEpisode extends Episode {


    //    public Game(RuleSet _rules, Board _initialBoard);

    
   /** Creates an Episode in order to replay an old recorded Game (with a known rule set and a known initial board)
	properties of the initial board). 
	@param _in The input stream for commands; it will be null in the web app
	@param _out Will be null in the web app.
    */
    public ReplayedEpisode(String _episodeId, Game game) {
	super(game, Episode.OutputMode.BRIEF, null, null, _episodeId);
	
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
    */
    public double computeP0( Pick nextMove) {
    
	int knownFailedPicks = failedPicks.cardinality();
	int knownFailedMoves = 0;	
	for(BitSet b : failedMoves)  {	    
	    if (b!=null) knownFailedMoves += b.cardinality();
	}

	return  (nextMove instanceof Move) ?
	    ruleLine.computeP0ForMoves(knownFailedMoves):
	    ruleLine.computeP0ForPicks(knownFailedPicks);
    }

	    
    public int accept(Pick pick) {
	int code = super.accept(pick);

	if (code==CODE.ACCEPT) {
	    // the board has changed; the old knowledge can be wiped out
	    failedPicks.clear();
	    failedMoves.clear();
	} else if (code==CODE.DENY) {
	    // The player's knowledge has increased
	    int pos = pick.getPos();
	    if (pick instanceof Move) {
		BitSet b = movesFor(pos);
		Move move = (Move)pick;
		b.set(move.getBucketNo());
		if (b.cardinality()==NBU) failedPicks.set(pos);
	    } else {
		failedPicks.set(pos);
		// A failed pick prohibits all NBU=4 moves for this piece, too!
		BitSet b = movesFor(pos);
		for(int j=0; j<NBU; j++) b.set(j);
	    }
	    
	}
	
	return code;
    }

    
}
