package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.Util;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.parser.Expression.ArithmeticExpression;
import edu.wisc.game.parser.Expression.VarMap2;

/** Represents the restrictions on the positions from which game pieces
    can be picked. An auxiliary class for RuleSet */
public class PositionList {
    /** If true, there is no restriction */
    private boolean any;
    /** List of specific board positions */
    Vector<Integer> list1 = new Vector<>();
    /** The keys can be  PositionSelector names or user-defined order
	names */
    Vector<String> list2 = new Vector<>();
    
    public String toString() {
	return "[" + Util.joinNonBlank(",", list1) + "; " +
	    Util.joinNonBlank(",",list2) + "]";	       
    }

    public boolean isTrivial() {
	return any;
    }
    
    public String toSrc() {
	if (any) return "*";
	    
	String q[]= new String[]{ Util.joinNonBlank(",", list1),	    
				  Util.joinNonBlank(",", list2)};
	
	String s = Util.joinNonBlank(",", q);
	if (list1.size()+list2.size()>1) s = "[" + s + "]";	       
	return s;
    }

	
    private void addAll(PositionList q) throws RuleParseException {
	if (any || q.any) throw new RuleParseException("When describing position lists, '*' cannot be used in combinations");
	list1.addAll(q.list1);
	list2.addAll(q.list2);
    }
	
    /** @param ex A parsed expression (found in a "pos:..."
	section of an atom), which may be "*", a or "L1", or
	contain some numerical positions or order name.
	@param The dictionary of all known orders
    */
    PositionList(Expression ex, TreeMap<String, Order> orders)  throws RuleParseException {
	any = (ex==null || ex instanceof Expression.Star);
	if (any) return;
	
	if (ex instanceof Expression.BracketList) {
	    for(Expression z: (Expression.BracketList)ex) {
		addAll( new PositionList(z, orders));
	    }
	} else if (ex instanceof Expression.Num) {
	    list1.add( ((Expression.Num)ex).nVal);
	} else if (ex instanceof Expression.Id) {
	    String s = ex.toString();
	    // validate as the name of a predefined or custom order 
	    if (!orders.containsKey(s)) {
		if (Order.predefinedOrders.containsKey(s)) {
		    orders.put( s, (Order.predefinedOrders.get(s)));
		}
		
	    }
	    list2.add(s);		       
	} else {
	    throw new RuleParseException("Invalid position specifier: " + ex);
	}
	if (list1.size()==0 && list2.size()==0) throw new RuleParseException("No position list specified! ex=" + ex);
    }
    
    /** Does this position list presently allow picking a piece from the
	specified position?
	@param  eligibleForEachOrder What positions are now "in front"
	of each order */
    public boolean allowsPicking(int pos,
				 HashMap<String, BitSet> eligibleForEachOrder) {
	if (any) return true;
	for(int k: list1) {
	    if (k==pos) return true;
	}
	for(String orderName: list2) {
	    BitSet eligible =  eligibleForEachOrder.get(orderName);
	    
	    if ( eligible == null) throw new IllegalArgumentException("Unknown order name ("+orderName+") - should have been caught before!");
	    if (eligible.get(pos)) return true;
	}
	return false;
    }

	
    /** Used when converting Kevin's JSON to our server format. Only 
	apply the new order to atoms that do not have a position list or
	an order already.
    */
    void forceOrder(String orderName) {
	if (any) {
	    any = false;
	    if (list1.size()>0 || list2.size()>0) throw new IllegalArgumentException("Cannot force an order on this PositionList, because it is already non-empty");
	    list2.add(orderName);
	} 
    }
	
}


