package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;


public class Order extends Vector<Vector<Integer>> {

    static enum PositionSelector {
	L1,L2,L3,L4,
	T,B,R,L,
	NearestObject, Farthest;
	static PositionSelector[] keys ={
	    L1,L2,L3,L4,
	    B,L,
	    NearestObject, 
	};

    }

    /** The orders which don't need to be explicitly defined in the 
	rule file */
    static HashMap<String, Order> predefinedOrders = new  HashMap<>();

    private Order() {};

    public String toString() {
	Vector<String> q=new Vector<>();
	for(Vector<Integer> v: this) {
	    if (v.size()==1) q.add("" + v.get(0));
	    else q.add("["+Util.joinNonBlank(",", v) +"]");
	}
	return "["+String.join(",", q) +"]";
    }
    
    /** Creates a new order that's the reverse of this order */
    Order reverse() {
	Order q = new Order();
	for(int j=size()-1; j>=0; j--) q.add(get(j));
	return q;
    }

    

    // FIXME: this expects that the buckets are in the corners.
    private static int distToCorner(int pos) {
	Pos p = (new Pos(pos)).flip2corner();
	//Pos p = new Pos(pos);
	return p.norm2sq( new Pos(0,0));
    }
	
    /** Used to sort by ascending distance from the closest corner
	(i.e. nearest first)
     */
    private static class DistanceComparator implements Comparator<Integer> {
	public int  compare​(Integer o1, Integer o2) {
  	    return distToCorner(o1) - distToCorner(o2);
	} 
    }

    /** Used in constructors to make sure that all values are in the
	proper range, and there are no duplicates. */
    private void validate()  throws RuleParseException{
	HashSet<Integer> h=new HashSet<>();
	final int M = Board.N*Board.N;
	for(Vector<Integer> v: this) {
	    for(int pos: v) {
		if (pos<1 || pos>M) throw new RuleParseException("Invalid order: illegal value " + pos);
		if (h.contains(pos)) throw new RuleParseException("Invalid order: duplicate value " + pos);
		h.add(pos);
	    }
	}
    }

    /** Initializes an order based on a bracket list (from a rule file) */
    Order(Expression.BracketList bex) throws RuleParseException {
	for(Expression a: bex) {
	    Vector<Integer> v=new Vector<>();
	    if (a instanceof Expression.Num) {
		v.add(((Expression.Num)a).nVal);
	    } else if (a instanceof Expression.BracketList) {
		for(Expression b: (Expression.BracketList)a) {
		    if (b instanceof Expression.Num) {
			v.add(((Expression.Num)b).nVal);
		    } else throw new RuleParseException("Cannot parse as an order: " + bex);
		}
	    }
	    add(v);
	}
	validate();
    }
       
    
    Order(PositionSelector mode) {
	if (mode==PositionSelector.B) {
	    for(int y=1; y<=Board.N; y++) {
		Vector<Integer> v=new Vector<>();
		for(int x=1; x<=Board.N; x++) v.add( Board.xynum(x,y));
		add(v);
	    }
	} else if (mode==PositionSelector.L) {
	    for(int x=1; x<=Board.N; x++) {
		Vector<Integer> v=new Vector<>();
		for(int y=1; y<=Board.N; y++) v.add( Board.xynum(x,y));
		add(v);
	    }		    
	} else if (mode==PositionSelector.L1) { // English
	    for(int y=Board.N; y>=1; y--) {
		for(int x=1; x<=Board.N; x++) {
		    Vector<Integer> v=new Vector<>();
		    v.add( Board.xynum(x,y));
		    add(v);
		}		
	    }
	} else if (mode==PositionSelector.L2) { // Hebrew
	    for(int y=Board.N; y>=1; y--) {
		for(int x=Board.N; x>=1; x--) {
		    Vector<Integer> v=new Vector<>();
		    v.add( Board.xynum(x,y));
		    add(v);
		}		
	    }
	} else if (mode==PositionSelector.L3) { // Chinese
	    for(int x=Board.N; x>=1; x--) {
		for(int y=Board.N; y>=1; y--) {
		    Vector<Integer> v=new Vector<>();
		    v.add( Board.xynum(x,y));
		    add(v);
		}		
	    }
	} else if (mode==PositionSelector.L4) { // Manchu
	    for(int x=1; x<=Board.N; x++) {
		for(int y=Board.N; y>=1; y--) {
		    Vector<Integer> v=new Vector<>();
		    v.add( Board.xynum(x,y));
		    add(v);
		}		
	    }
	} else if (mode==PositionSelector.NearestObject) {
	    Vector<Integer> w=new Vector<>();
	    for(int k=1; k<=Board.N*Board.N; k++) w.add( new Integer(k));
	    w.sort(new DistanceComparator());
	    Vector<Integer> v=null;
	    for(Integer q: w) {
		if (v==null) {
		    v=new Vector<>();
		} else if (distToCorner(q)!=distToCorner(v.get(0))) {
		    add(v);
		    v=new Vector<>();
		}
		v.add(q);	    
	    }
	    add(v);
	}	
    }

    private static void put(PositionSelector key, Order val) {
	predefinedOrders.put(key.toString(), val);
    }

    private static Order get(PositionSelector key) {
   	return predefinedOrders.get(key.toString());
    }

 
    /* Creates various Order objects for pre-defined orderings.
       <pre>
    L1,L2,L3,
	T,B,R,L,
	NearestObject, Farthest
	</pre>
    */
    private static void init() {
	for(PositionSelector key: PositionSelector.keys) {
	    put( key, new Order(key));
	}
	
	put( PositionSelector.T, get(PositionSelector.B).reverse());
	put( PositionSelector.R, get(PositionSelector.L).reverse());
	put( PositionSelector.Farthest, get(PositionSelector.NearestObject).reverse());
    }

    static {
	init();
    }


    /** In the present board configuration, which piece(s) would be eligible
	to be picked right now under this order?
	@param onBoard indicates which of the N*N cells are occupied. */
    public BitSet findEligiblePieces(BitSet onBoard) {
	BitSet result = new BitSet();
	for(Vector<Integer> v: this) {
	    //Logging.info("#FEP: " + result + " OR (" + Util.joinNonBlank(",", v) + ")");
	    BitSet q = Util.toBitSet(v);
	    q.and(onBoard);
	    result.or(q);
	    //Logging.info("#Gives: " + result);
	    if (!result.isEmpty()) return result;
	}
	// If none of the pieces of the board are listed in this Order, this
	// means that the Order is incomplete, and should be ignored; all
	// pieces are moveable
	return onBoard;
    }
    
}
