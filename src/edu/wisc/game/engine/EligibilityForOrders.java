package edu.wisc.game.engine;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;

/** At present, which pieces are eligible for picking under each of
    the existing orders? This structure needs to be updated every time
    a piece is removed from the board.

    <p>
    This is an auxiliary class for edu.wisc.game.sql.Episode.
*/
public class EligibilityForOrders extends HashMap<String, BitSet>  {
    /** Finds pieces eligible for pick up under each orders based on
	the current board content */
    void update(RuleSet rules, BitSet onBoard) {
	clear();
	// Which pieces may be currently picked under various ordering schemes?
	for(String name: rules.orders.keySet()) {
	    Order order = rules.orders.get(name);
	    BitSet eligible = order.findEligiblePieces(onBoard);
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


