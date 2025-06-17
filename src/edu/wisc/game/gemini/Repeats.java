package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;

/** Used to keep track of repeated moves, and to admonish the bot not to do that */
class Repeats {
    //    int failedRepeatsCnt = 0;
    //    int failedRepeatsCurrentStreak = 0;
    //    int failedRepeatsLongestStreak = 0;

    /** This is parallel to Episode.transcript, and identifies 
	redundant moves */
    Vector<Boolean> redundant = new Vector<>();
    
    static class MoveBase {
	final int pieceId, bucketNo;
	MoveBase(Move move) {
	    pieceId = move.getPieceId();
	    bucketNo = move.bucketNo;
	}
	public boolean equals(Object o) {
	    if (!(o instanceof MoveBase)) return false;
	    MoveBase b = (MoveBase)o;
	    return pieceId == b.pieceId && bucketNo == bucketNo;
	}
	public int hashCode() {
	    return pieceId * 4 + bucketNo;
	}
    }

    /** How many redundant move attempts were made despite a DENY response */
    int repeatAfterDenyCnt=0;
    /** How many redundant move attempts were made despite an IMMOVABLE response */
    int repeatAfterImmovableCnt=0;

    /** The total number of redundant move attemps in this episode */
    int totalRepeats() {
	return repeatAfterDenyCnt + repeatAfterImmovableCnt;
    }
    
    
    HashMap<MoveBase, Integer> recent = new HashMap<>();

    void reset() {
	recent.clear();
    }
    
    boolean add(Move move, int code) {

	boolean r = false;
	if (code == CODE.ACCEPT) {
	    recent.clear();
	} else {
	    MoveBase b = new MoveBase(move);
	    Integer n = recent.get(b);
	    if (n==null) {
		recent.put(b, 1);
	    } else {
		recent.put(b, n+1);
		
		if (code == CODE.DENY) repeatAfterDenyCnt++;
		if (code == CODE.IMMOVABLE) repeatAfterImmovableCnt++;
		r = true;
	    }
	}
	redundant.add(r);
	return r;

    }

    
}
