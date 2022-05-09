package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
//import edu.wisc.game.reflect.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.engine.RuleSet.BucketSelector;

import edu.wisc.game.parser.Expression.PropMap;

/** Auxiliary data structures for the Episode class, keeping track of
    "memory variables".
*/
class EpisodeMemory {

    /** The last bucket to receieve a piece */
    private Integer pp =null;
    

    /** Which bucket was the last one to receive a piece of a given color? */
    private HashMap<Piece.Color, Integer> pcMap = new HashMap<>();
    /** Which bucket was the last  one to receive a piece of a given shape? */
    private HashMap<Piece.Shape, Integer> psMap = new HashMap<>();

     /** Which bucket was the last  one to receive a piece with a given value of each property? (get(propName).get(propValue)==bucket) */
    private HashMap<String, HashMap<String, Integer>> pMap = new HashMap<>();

    private String showPMap() {
	Vector<String> v = new Vector<>();       
	for(String p: pMap.keySet()) {
	    HashMap<String, Integer> h = pMap.get(p);
	    Vector<String> w = new Vector<>();
	    for(String key: h.keySet()) {
		w.add("("+key+":"+h.get(key)+")");
	    }
	    v.add(p + " -> " + String.join(" ", w));
	}
	return String.join("\n", v);
    }
    
    /** For each (propName, propValue), what was the most recently
	removed game piece with that propName:propValue pair?
    */
    private HashMap<String, HashMap<String, PropMap>> qMap = new HashMap<>();

    /** Record the fact that a game piece with a particular 
	propertyName:propertyValue pair has just been removed.
	@param key Property name
	@param value Propety value 
    */
    private void savePair(String key, String value, PropMap piece, int bucketNo) {
	HashMap<String, Integer> h=pMap.get(key);
	if (h==null) pMap.put(key,h=new HashMap<>());
	h.put(value, bucketNo);

	HashMap<String, PropMap> hq = qMap.get(key);
	if (hq==null) qMap.put(key,hq=new HashMap<>());
	hq.put(value, piece);
	
    }

    private PropMap lastPiece;

    /** Remember where this piece was moved, for future used
	in bucket vars etc
    */
    void enterMove(Piece piece, int bucketNo) {
	pp = bucketNo;
	
	if (piece.xgetColor()!=null) pcMap.put(piece.xgetColor(), bucketNo);
	if (piece.xgetShape()!=null) psMap.put(piece.xgetShape(), bucketNo);

	PropMap thisPiece = piece.toPropMap();
	thisPiece.put("bucket", bucketNo);
	lastPiece = thisPiece;
	
	for(String key: thisPiece.keySet()) {
	    savePair(key, thisPiece.get(key).toString(), thisPiece, bucketNo);
	}
	System.out.println("DEBUG: pMap=\n" + showPMap());
	
    }
	
    /** Contains the values of various variables that may be used in 
	finding the destination buckets for a given piece. This is
	what has been used in GS1 thru GS4.
    */ 
    class BucketVarMap extends Expression.VarMap {
	
	/** Puts together the values of the variables that may be used in 
	    finding the destination buckets for a particular game piece. */
	BucketVarMap(Piece p) {
	    if (p.xgetColor()!=null) {
		Integer z = pcMap.get(p.xgetColor());
		if (z!=null) pu(BucketSelector.pc.toString(), z);
	    }
	    if (p.xgetShape()!=null) {
		Integer z = psMap.get(p.xgetShape());
		if (z!=null) pu(BucketSelector.ps.toString(), z);
	    }
	    if (pp!=null) pu(BucketSelector.p.toString(), pp);
	    ImageObject io = p.getImageObject();
	    if (io!=null) {
		for(String key: io.keySet()) {
		    String val = io.get(key);
		    if (pMap.get(key)!=null) {			
			Integer z = pMap.get(key).get(val);
			if (z!=null) pu("p."+key, z);
		    }
		}
	    }
	    Pos pos = p.pos();
	    put(BucketSelector.Nearby.toString(), pos.nearestBucket());
	    put(BucketSelector.Remotest.toString(), pos.remotestBucket());
	    //System.out.println("DEBUG: For piece="+p+ ", BucketVarMap=" + this);
	}
	
    }

    /** Variables needed for GS5 expressions.
	p, ps, pc -- legacy bucket numbers
	p.propName -- GS3 property-based bucket numbers
	this -- the game piece which the player tries to move now
	last -- the most recently successfully moved piece
	q.propName -- the most recently successfully moved piece whose property with the specified name shared its value with the current piece. (E.g. the most recent same-color or same-shape piece)
	q.bucket -- the piece most recently moved into the same bucket as being tried now
	
     */
    class BucketVarMap2 extends Expression.VarMap2 {
	BucketVarMap2(Piece p, int bucketNo)  {
	    if (pp!=null) pu(BucketSelector.p.toString(), pp);

	    PropMap thisPiece = p.toPropMap();
	    thisPiece.put("bucket", bucketNo);

	    for(String key: thisPiece.keySet()) {
		// must use String as the 2nd key (prop value)
		String val = thisPiece.get(key).toString();
		if (pMap.get(key)!=null) {			
		    Integer z = pMap.get(key).get(val);
		    if (z!=null) pu("p."+key, z);
		}
		if (qMap.get(key)!=null) {			
		    PropMap z = qMap.get(key).get(val);
		    if (z!=null) pu("q."+key, z);
		}
	    }

	    addValue("this", thisPiece);
	    if (lastPiece!=null) addValue("last", lastPiece);
	    
	    Pos pos = p.pos();
	    put(BucketSelector.Nearby.toString(),anySetToOset( pos.nearestBucket()));
	    put(BucketSelector.Remotest.toString(),anySetToOset( pos.remotestBucket()));

	    System.out.println("DEBUG: For piece="+p+ " moved to "+bucketNo+", VarMap2=" + this);

	}
    }

    static <T> HashSet<Object> anySetToOset(Set<T> h) {
	HashSet<Object> r = new HashSet<>();
	r.addAll(h);
	return r;
    }


}
