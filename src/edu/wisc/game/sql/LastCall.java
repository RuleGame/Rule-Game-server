package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import edu.wisc.game.util.*;
import edu.wisc.game.sql.EpisodeInfo.ExtendedDisplay;

/** An auxiliary class for EpisodeInfo. It serves to detect repeated identical
    /move or /pick calls (which may result e.g. from an accidental double-clicking)
    and to return the same structure that was returned previously.

    <p>A LastCall instance should be stored in easch EpisodeInfo
    object, When the server receives a /move or /pick call for that
    episode, it can use one of the LastCheck.xxxCheck methods of this
    class to compare the arguments of this web API call with those of
    the last recorded call. If there is a full match, and the LastCall
    instance stores the ExtendedDisplay value that was returned during
    the last recorded call, then the xxxCheck method will return that
    last ExtendedDisplay value, so that the server can immediately
    return that to the client. Otherwise null is returned, indicating
    that this is genuinely a new move to be processed.

    <p>When the /move or /pick calls completes its processing, it 
    can save the ExtendedDisplay value in its LastCall instance,
    for use on the next call.
*/
class LastCall {

    /** Stores the arguments to a /move or /pick call,
	and, once available, the ExtendedDisplay value
	returned by that call.
     */
    static private class Datum {
	ExtendedDisplay lastDisplay = null;
	String moverPlayerId=null;
	int x=-1, y=-1, bx=-1, by=-1, pieceId=-1, bucketId=-1;
	int attemptCnt=-1;

	Datum(String moverPlayerId, int y, int x, int attemptCnt) {
	    this.moverPlayerId = moverPlayerId;
	    this.y = y;
	    this.x = x;
	    this.attemptCnt = attemptCnt;
	}
    	
	Datum(String moverPlayerId, int pieceId, int attemptCnt) {
	    this.moverPlayerId = moverPlayerId;
	    this.pieceId = pieceId;
	    this.attemptCnt = attemptCnt;
	}


	public boolean equalArgs(Object o) {
	    if (!(o instanceof Datum)) return false;
	    Datum d = (Datum)o;
	    return x==d.x && y==d.y && bx==d.bx  && by==d.by && pieceId==d.pieceId && bucketId==d.bucketId &&
		attemptCnt == d.attemptCnt &&
		Util.same(moverPlayerId, d.moverPlayerId);
	}
    }

    private Datum lastDatum = null;

    void saveDisplay(ExtendedDisplay q) {
	if (lastDatum==null) {
	    Logging.error("Trying to save display without lastDatum!");
	    return;
	}
	lastDatum.lastDisplay = q;
    }

    private ExtendedDisplay check(Datum d) {
	if (lastDatum!=null && d.equalArgs(lastDatum)) {
	    Logging.info("Detected a duplicate call for attemptCnt=" + d.attemptCnt);
	    return lastDatum.lastDisplay;
	}
	lastDatum = d;
	return null;	
    }
    
    public ExtendedDisplay doMoveCheck(String moverPlayerId, int y, int x, int by, int bx, int attemptCnt) {
	Datum d = new Datum( moverPlayerId, y, x,  attemptCnt);
	d.by = by;
	d.bx = bx;
	return check(d);	
    }	
	
    public ExtendedDisplay doMove2Check(String moverPlayerId, int pieceId, int bucketId, int attemptCnt) {
	Datum d = new Datum( moverPlayerId, pieceId,  attemptCnt);
	d.bucketId = bucketId;
	return check(d);	
    }


    public ExtendedDisplay doPickCheck(String moverPlayerId, int y, int x, int attemptCnt) {
	Datum d = new Datum( moverPlayerId, y, x,  attemptCnt);
	return check(d);	
    }	
	
    public ExtendedDisplay doPick2Check(String moverPlayerId, int pieceId, int attemptCnt) {
	Datum d = new Datum( moverPlayerId, pieceId,  attemptCnt);
	return check(d);	
    }
}

