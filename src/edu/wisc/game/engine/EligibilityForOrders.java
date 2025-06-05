package edu.wisc.game.engine;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;

/** At present, pieces in which cells are eligible for picking under each of
    the existing orders? (For example, which pieces are presently in
    the topmost occupied row? Which piece is the first in the English
    reading order?) This structure needs to be updated every time a
    piece is removed from the board.

    <p>
    This is an auxiliary class for edu.wisc.game.sql.Episode.

    <p>The key to the hash map is the name of the order; the value
    is a bitset with 37 bit positions, one per cell (plus an empty one
    at zero)
*/
public class EligibilityForOrders extends HashMap<String, BitSet>  {
    /** Finds pieces eligible for pick up under each orders based on
	the current board content

	@param onBoard Which cells are currently occupied?
    */
    void update(RuleSet rules, BitSet onBoard) {
	clear();
	// Which pieces may be currently picked under various ordering schemes?
	for(String name: rules.orders.keySet()) {
	    Order order = rules.orders.get(name);
	    BitSet eligible = order.findEligibleCells(onBoard);
	    put(name,  eligible);
	}
    }

    public EligibilityForOrders(RuleSet rules, BitSet onBoard) {
	super();
	update(rules, onBoard);			
    }

    public String toString() {
	Vector<String> v= new Vector<>();
	for(String key: keySet()) {
	    v.add("Eli(" + key+")="+ get(key));
	}
	return "[Eligibility: "+String.join("; " , v)+"]";
    }
    
}


