package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.Util;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;

/** A RuleSet describes the rules of a game. */
public class RuleSet {

    /** The list of variables that can be used in the bucket
	expression */
    public enum BucketSelector {
	p, pc, ps,
	//Nearby= into the nearest bucket
	Nearby,
	//Remotest=into the farthest bucket
	Remotest}

    
    public static class PositionList {
	/** If true, there is no restriction */
	private boolean any;
	Vector<Integer> list1 = new Vector<>();
	/** The keys can be  PositionSelector names or user-defined order
	    names */
	Vector<String> list2 = new Vector<>();

	public String toString() {
	    return "[" + Util.joinNonBlank(",", list1) + "; " +
		Util.joinNonBlank(",",list2) + "]";	       
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
	/** @param ex A parsed expression, which may be "*", a or "L1", or contain some numerical positions or order name.
	    @param The dictionary of all known orders
	 */
	PositionList(Expression ex, TreeMap<String, Order> orders)  throws RuleParseException {
	    any = (ex instanceof Expression.Star);
	    if (any) return;
			    
	    if (ex instanceof Expression.BracketList) {
		for(Expression z: (Expression.BracketList)ex) {
		    addAll( new PositionList(z, orders));
		}
	    } else if (ex instanceof Expression.Num) {
		list1.add( ((Expression.Num)ex).nVal);
	    } else if (ex instanceof Expression.Id) {
		String s = ((Expression.Id)ex).sVal;
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

    /** A BucketList represents the information about the destination
	buckets given in the "buckets" field of an atom.
    */
    public static class BucketList extends Vector<Expression.ArithmeticExpression>{
	/** Star is allowed in Kevin's syntax, and means "any bucket" */
	BucketList( Expression.Star star) throws RuleParseException {
	    for(int j=0; j<4; j++) {
		add(new Expression.Num(j));
	    }
	}

	/*
	BucketList( Expression.BracketList bex) throws RuleParseException {
	    for(Expression ex: bex) {
		if (!(ex instanceof Expression.ArithmeticExpression)) 	throw new RuleParseException("Invalid bucket specifier (not a symbol or an arithmetic expression): " + ex);
		
		Expression.ArithmeticExpression g = (Expression.ArithmeticExpression)ex;

		add(g);
	    }
	}
	*/
	
	BucketList(Expression.ArithmeticExpression num)  {
	    add(num);
	}

	public String toString() {
	    return "[" + Util.joinNonBlank(",",this)  + "]";	       
	}

	public String toSrc() {
	    Vector<String> v = new Vector<>();
	    for(Expression.ArithmeticExpression ex: this) {
		v.add(ex.toSrc());
	    }
	    String s = String.join(",",v);
	    if (v.size()>0) s = "[" +s+ "]";
	    return s;
	}

	/** To which destinations can a piece be taken?
	    @param varMap Information about p, ps, pc, Nearby etc for the piece
	    under consideration*/
	public BitSet destinations( HashMap<String, HashSet<Integer>> varMap) {
	    BitSet q= new BitSet(Board.buckets.length);
	    
	    for(Expression.ArithmeticExpression ae: this) {
		Set<Integer> h = ae.evalSet(varMap);
		h = Expression.moduloNB(h);
		q.or( Util.toBitSet(h));
	    }
	    return q;
	}
	
    }

    
    /** Syntax:(counter,shape,color,position,bucketFunctions)
	<p>
	Example: (10,square,*,*,[1,2]) (10,*,blue,10,[2,3])
    */
    public static class Atom {
	/** -1 means "no limit" */
	public final int counter;
	/** null means "no restriction" */
	public final Piece.Shape[] shapes;
	//final
	/** null means "no restriction" */
	public Piece.Color[] colors;
	public PositionList plist;       	
	public BucketList bucketList;


	public String toString() {
	    return "(" + counter + ","  +showList(shapes) + "," + showList(colors)+","+
		plist +  "," + bucketList + ")";	       
	}


	private static <T> String showList(T [] v) {
	    if (v==null) return "*";
	    Vector<String> w = new Vector<>();
	    for(T t: v) {
		String s = t.toString();
		if (s.indexOf("/")>=0) s="\"" + s + "\"";
		w.add(s);
	    }
	    String q = String.join(",", w);
	    if (v.length!=1) q = "["+q+"]";
	    return q;		
	}
	
	/** Format as the source code of the rules set */
	public String toSrc() {
	    return "(" +  (counter<0? "*" : ""+counter) +
		"," + showList(shapes) +
		"," + showList(colors) +
		"," + plist.toSrc() +  "," + bucketList.toSrc() + ")";
	}
	
	/** Syntax:(counter,shape,color,position,bucketFunctions)     */
	Atom(Expression.ParenList pex, TreeMap<String, Order> orders) throws RuleParseException {
	    if (pex.size()!=5)  throw new RuleParseException("Expected a tuple with 5 elements; found "+ pex.size() +": " + pex);

	    Expression g = pex.get(0); // count
	    if (g instanceof Expression.Star) {
		counter = -1;
	    } else if (g instanceof Expression.Num) {
		counter = ((Expression.Num)g).nVal;
	    } else {
		throw new RuleParseException("Counter is not a star or number: " + pex);
	    }

	    g = pex.get(1); // shape
	    // System.out.println("Setting shapes from " + g);
	    if (g instanceof Expression.Star) {
		shapes = null;
	    } else if (g instanceof Expression.Id) {
		String s = ((Expression.Id)g).sVal;
		shapes = new Piece.Shape[] { Piece.Shape.findShape(s)};
	    } else if (g instanceof Expression.BracketList) {
		Vector<Piece.Shape> w = new Vector();
		for(Expression h: (Expression.BracketList)g) {
		    if (h instanceof Expression.Id) {
			String s = ((Expression.Id)h).sVal;
			w.add(Piece.Shape.findShape(s));
		    } else  {
			throw new RuleParseException("Invalid shape ("+h+") in: " + pex);
		    }
		}
		shapes = w.toArray(new Piece.Shape[0]);
	    } else  {
		throw new RuleParseException("Invalid shape ("+g+") in: " + pex);
	    }
	    //System.out.println("shapes=" + showList(shapes));
	    g = pex.get(2); // color
	    if (g instanceof Expression.Star) {
		colors = null;
	    } else if (g instanceof Expression.Id) {
		String s = ((Expression.Id)g).sVal;
		colors = new Piece.Color[]{ Piece.Color.findColor(s)};
	    } else if (g instanceof Expression.BracketList) {
		Vector<Piece.Color> w = new Vector();
		for(Expression h: (Expression.BracketList)g) {
		    if (h instanceof Expression.Id) {
			String s = ((Expression.Id)h).sVal;
			w.add(Piece.Color.findColor(s));
		    } else  {
			throw new RuleParseException("Invalid color ("+h+") in: " + pex);
		    }
		}
		colors = w.toArray(new Piece.Color[0]);
	    } else  {
		throw new RuleParseException("Invalid color ("+g+") in: " + pex);
	    }
	    //System.out.println("colors=" + showList(colors));
	    g = pex.get(3); // position
	    plist = new PositionList(g, orders);
	    //System.out.println("plist=" + plist);
	    g = pex.get(4); // buckets
	    if (g instanceof Expression.ArithmeticExpression)  {
		bucketList = new BucketList((Expression.ArithmeticExpression)g);
		// } else if (g instanceof Expression.BracketList) {
		//bucketList = new BucketList((Expression.BracketList)g);
	    } else if (g instanceof Expression.Star) {
		// for compatibility with Kevin's syntax
		bucketList = new BucketList((Expression.Star)g);
	    } else {
		throw new RuleParseException("Buckets must be specified by a bracket list. Instead, found "+g+" in: " + pex);
	    }
		      
	}
	
	/** Used when converting Kevin's JSON to our server format */
	void forceOrder(String orderName) {
	    plist.forceOrder(orderName);
	}


	boolean acceptsShape(Piece.Shape x) {
	    if (shapes==null) return true;
	    for(Piece.Shape y: shapes) {
		if (x==y) return true;
	    }
	    return false;	  
	}
	boolean acceptsColor(Piece.Color x) {
	    if (colors==null) return true;
	    for(Piece.Color y: colors) {
		if (x==y) return true;
	    }
	    return false;	  
	}

	/** Does this atom accept a specified piece, based on its
	    shape and color? */
	public boolean acceptsColorAndShape(Piece p) {
	    return acceptsShape(p.xgetShape()) &&
		acceptsColor(p.xgetColor());
	}

    }

    
    /** A row object represents the content of one line of the rule set
	description file, i.e. the optional global counter and 
	one or several rules */
    public static class Row extends Vector<Atom> {
	/** The default value, 0, means that there is no global limit in this row */
	public final int globalCounter;
	/** Creates a row of the rule set from a parsed line of the rule set file.
	    @param tokens One line of the rule set file, parsed into tokens
	    @param orders Contains all existing orderings
	 */
	Row(Vector<Token> tokens, TreeMap<String, Order> orders)
	    throws RuleParseException {
	    
	    int gc=-1;
	    boolean first=true;
	    
	    // fixme: keep using ex!
	    while(tokens.size()>0) {
		//Expression ex = Expression.mkExpression(tokens);
		Expression ex = Expression.mkCounterOrAtom( tokens);

		if (first) {
		    first=false;
		    if (ex instanceof Expression.Star) {
			gc = -1;
			continue;
		    } else if (ex instanceof Expression.Num) {
			gc = ((Expression.Num)ex).nVal;
			continue;
		    } 
		}
		
		if (!(ex instanceof Expression.ParenList)) throw new RuleParseException("Expected a paren list; found " + ex);
		Expression.ParenList pex = (Expression.ParenList)ex;
		add(new Atom(pex, orders));		    
	    }

	    globalCounter= gc;
	}
	
	public String toString() {
	    return "(globalCounter="+globalCounter+") "+Util.joinNonBlank(" ",this);	       
	}
	
	/** Format as the source code of this row */
	public String toSrc() {
	    Vector<String> v = new Vector<>();
	    if (globalCounter>0) v.add( "" + globalCounter);
	    for(Atom atom: this) v.add( atom.toSrc());
	    return String.join(" ",v);
	}
	
	/** Used when converting Kevin's JSON to our server format */
	void forceOrder(String orderName) {
	    for(Atom atom: this) atom.forceOrder(orderName);
	}

	/** Lists all shapes used in this row. */
	HashSet<Piece.Shape> listAllShapes() {
	    HashSet<Piece.Shape> h = new HashSet<>();
	    for(Atom atom: this) {
		if (atom.shapes==null) continue;
		for(Piece.Shape shape: atom.shapes) h.add(shape);
	    }
	    return h;
	}
	/** Lists all colors used in this row. */
	HashSet<Piece.Color> listAllColors() {
	    HashSet<Piece.Color> h = new HashSet<>();
	    for(Atom atom: this) {
		if (atom.colors==null) continue;
		for(Piece.Color color: atom.colors) h.add(color);
	    }
	    return h;
	}
	
    }

    /** All orders */
    public TreeMap<String, Order> orders =  new TreeMap<>();
    /** All rows of this rule set */
    public Vector<Row> rows = new Vector<>();
    
    public RuleSet(String ruleText) throws RuleParseException {
	this( ruleText.split("\n"));
    }

    /** Creates a RuleSet based on the content of a rule set file. 
	The file may contain some (optional) custom order definition
	lines, followed by one or more rule lines.
	@param rr The lines  from the rule set file */
    public RuleSet(String[] rr) throws RuleParseException {
	for(String r: rr) {
	    r = r.trim();
	    if (r.startsWith("#") || r.length()==0) continue; // skip comment lines and blank lines


	    Vector<Token> tokens= Token.tokenize(r);
	    if (tokens.size()==0) throw new RuleParseException("No data found in the line: " + r);
	    if (rows.size()==0 && tokens.get(0).type==Token.Type.ID &&
		tokens.get(0).sVal.equalsIgnoreCase("Order")) { // order line:
		// Order Name=[1,2,3,....]
		tokens.remove(0);
		if (tokens.size()==0 || tokens.get(0).type!=Token.Type.ID )  throw new RuleParseException("Missing order name in an order line: " + r);
		String name = tokens.get(0).sVal;
		tokens.remove(0);
		if (tokens.size()==0 ||  tokens.get(0).type!=Token.Type.EQUAL)  throw new RuleParseException("Missing equal sign in an order line: " + r);
		tokens.remove(0);
		// Expects a bracket list of integers
		Expression.BracketList ex = Expression.mkBracketList(tokens);
		if (tokens.size()>0) throw new RuleParseException("Invalid order description: " +r);	
		orders.put(name, new Order(ex));
		continue;
	    }
		
	    Row row = new Row(tokens, orders);
	    if (row.size()==0)  throw new RuleParseException("No rules found in this line: "+r);
	    rows.add(row);

	}
	if (rows.size()==0) throw new RuleParseException("The rule set contains no rules");
    }

    public String toString() {
	Vector<String> v = new Vector<>();
	for(String name: orders.keySet()) {
	    v.add("Order " + name + "=" + orders.get(name));
	}
	String s = String.join("\n", v);
	if (v.size()>0) s+="\n";
	return s +    Util.joinNonBlank("\n",rows);	       
    }

    /** Format as the source code of the rules set */
    public String toSrc() {
	ReportedSrc rr = new ReportedSrc();
	Vector<String> v = new Vector<>();
	v.addAll(rr.orders);
	v.addAll(rr.rows);
	return String.join("\n", v);
    }

    public ReportedSrc reportSrc() {	return new ReportedSrc();    }


    /** This is used for pretty-printing in the GUI client. It describes
	a rule set in a way similar to the text format used in the 
	rule description file.
     */
    public class ReportedSrc {
	Vector<String> orders=new Vector<>();
	Vector<String> rows=new Vector<>();

	/** A vector of strings, each of which describes one order defined 
	    for this rules set */
	public Vector<String> getOrders() { return orders; }
	/** A vector of strings, each of which describes one line of 
	    this rules set */
        public Vector<String> getRows() { return rows; }
	
	ReportedSrc() {
	    for(Map.Entry<String,Order> e: RuleSet.this.orders.entrySet()) {
		orders.add("Order " + e.getKey() + "=" +  e.getValue());
	    }
	    for(Row row: RuleSet.this.rows) {
		rows.add(  row.toSrc());
	    }
	}
    }

    
    /** Used when converting Kevin's JSON to our server format */
    void forceOrder(String orderName) {
	for(Row row: rows) row.forceOrder(orderName);
    }

    /** Lists all shapes used in this rule set. */
    public HashSet<Piece.Shape> listAllShapes() {
	HashSet<Piece.Shape> h = new HashSet<>();
	for(Row row: rows) h.addAll(row.listAllShapes());
	return h;
    }
    
    /** Lists all colors used in this rule set. */
    public HashSet<Piece.Color> listAllColors() {
	HashSet<Piece.Color> h = new HashSet<>();
	for(Row row: rows) h.addAll(row.listAllColors());
	return h;
    }
	
    
    public static void main(String[] argv) throws IOException,  RuleParseException {
	System.out.println("Have " + argv.length + " files to read");
	for(String a: argv) {
	    File f = new File(a);
	    System.out.println("Reading file " + f);
	    String text = Util.readTextFile(f);
	    RuleSet rules = new RuleSet(text);
	    System.out.println(rules);
	}

    }
}
