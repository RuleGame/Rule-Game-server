package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import jakarta.json.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.Board.Pos;

import edu.wisc.game.sql.Episode.CODE;

import jakarta.xml.bind.annotation.XmlElement; 


/** An auxiliary data structure, used as an element of EpisodeInfo.ExtendedDisplay, used to sent to the GUI client, in a concise form, the info about the knowledge of the board acquired through the recent players' actions (those since the last board change, i.e the last successful move). This is used to make it easier for the client to display the feedback about the recent actions.  */

public class RecentKnowledge extends HashMap<Integer, RecentKnowledge.Datum> {

    /** Which game piece do we know something about, and what do we know
	about it? */

    // {"bucketNo":0,"pos":26,"pieceId":3,"code":4,"rValue":2.0,"mover":0},
    static public class Datum {
	/** Which game piece */
	final int pos;	
	public int getPos() { return pos; }
	/** Set to true if we know it can be moved (due to a successful pick) */
	boolean knownMovable=false;
	public boolean getKnownMovable() {
	    return knownMovable;
	}
	/** Set to true if we know it cannot be moved (due to a failed pick) */
	boolean knownImmovable=false;
	public boolean getKnownImmovable() {
	    return knownImmovable;
	}
	/** Buckets to which we know this piece cannot go (due to failed moves).
	 This is only set if knownMovable==true. */
	int [] deniedBuckets = null;
	public int [] getDeniedBuckets() {
	    return deniedBuckets;
	}
	Datum(int _pos) { pos = _pos; }
	void update( Episode.Pick pick ) {
	    if (pick instanceof Episode.Move) { // Move, and it had better be failed
		Episode.Move move = (Episode.Move) pick;
		if (move.code != CODE.DENY) throw new IllegalArgumentException("Successful moves are not 'recent knowledge'");
		int b = move.bucketNo;
		if (deniedBuckets==null) {
		    deniedBuckets=new int[] { b };
		} else { // add to the list
		    BitSet q = new BitSet(Episode.NBU);
		    for(int z: deniedBuckets) { q.set(z); }
		    q.set(b);
		    deniedBuckets=Util.listBits(q);		    
		}
		knownMovable = true;
	    } else if (pick.code == CODE.ACCEPT) { // successful pick
		knownMovable = true;
	    } else { // failed pick
		knownImmovable = true;
	    }
	}
    }


    /** @param transcript The list of all move/pick attempts (successful or not) done so far
	in this episode */
    RecentKnowledge(Vector<Episode.Pick> transcript) {
	int lastChangeAt = -1;
	for(int j=0; j<transcript.size(); j++) {
	    Episode.Pick pick = transcript.elementAt(j);
	    if ((pick instanceof Episode.Move) && (pick.code == CODE.ACCEPT)) lastChangeAt = j;
	}

	for(int j=lastChangeAt+1; j<transcript.size(); j++) {
	    Episode.Pick pick = transcript.elementAt(j);
	    int pos = pick.pos;
	    Datum datum = get(pos);
	    if (datum == null) put(pos, datum=new Datum(pos));
	    datum.update(pick);
	}

	
    }

}
