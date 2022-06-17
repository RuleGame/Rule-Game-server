package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.Util;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.parser.Expression.ArithmeticExpression;
import edu.wisc.game.parser.Expression.VarMap2;


/** A RuleSet describes the rules of a game. */
public class RuleSet {

    /** The file from which this RuleSet was read. This is an optional
	field, and is non-zero only in RuleSet objects that have actually
	been read from files. It is only used for reporting.
     */
    File file = null;
    
    /** An optional verbal description of the rule set, which may have
	been found in the comment lines on top of the rule set file.
     */
    public Vector<String> description=new Vector<>();
    
    /** The list of "legacy" variables that can be used in the bucket
	expression. (More variables were added later, such as "p.*"  */
    public enum BucketSelector {
	p, pc, ps,
	//Nearby= into the nearest bucket
	Nearby,
	//Remotest=into the farthest bucket
	Remotest}


    /** Represents the restrictions on the positions from which game pieces
	can be picked */
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

    /** A BucketList represents the information about the destination
	buckets given in the "buckets" field of an atom. It is a list of
	arithmetic expressions, each of which can be a numeric constant
	or a more complicated expressions making use of the "bucket arithmetic"
	and "bucket variables".
    */
    public static class BucketList extends Vector<ArithmeticExpression>{
	/** Star is allowed in Kevin's syntax, and means "any bucket" */
	BucketList( Expression.Star star) throws RuleParseException {
	    for(int j=0; j<4; j++) {
		add(new Expression.Num(j));
	    }
	}

	
	BucketList(ArithmeticExpression ae)  {
	    add(ae);
	}

	public String toString() {
	    return "[" + Util.joinNonBlank(",",this)  + "]";	       
	}

	public String toSrc() {
	    Vector<String> v = new Vector<>();
	    for(ArithmeticExpression ex: this) {
		v.add(ex.toSrc());
	    }
	    String s = String.join(",",v);
	    if (v.size()>1) s = "[" +s+ "]";
	    return s;
	}

	/** To which destinations can a piece be taken? (Used in GS1 thru 4)
	    @param varMap Information about p, ps, pc, Nearby etc for the piece
	    under consideration*/
	public BitSet destinations(Expression.VarMap varMap) {
	    BitSet q= new BitSet(Board.buckets.length);
	    
	    for(ArithmeticExpression ae: this) {
		//		System.out.println("DEBUG: EVAL " + ae + " for vars=" + varMap);
		Set<Integer> h = ae.evalSet(varMap);
		//		System.out.println("DEBUG: RESULT=(" + Util.joinNonBlank(",", h));
		h = Expression.moduloNB(h);
		q.or( Util.toBitSet(h));
	    }
	    return q;
	}

	/** Used in GS5 */
	public boolean destinationAllowed(VarMap2 varMap, int bucketNo) {
	    for(ArithmeticExpression ae: this) {
		//		System.out.println("DEBUG: EVAL " + ae + " for vars=" + varMap);
		Set<Object> h = ae.evalSet2(varMap);
		//		System.out.println("DEBUG: RESULT=(" + Util.joinNonBlank(",", h));
		if (Expression.moduloNB2(h).contains(bucketNo)) return true;
	    }
	    return false;
	}
	
	/** The list of variables mentioned in this BucketSelector */
	public HashSet<String> listAllVars() {
	    HashSet<String> r = new HashSet<>();
	    for(ArithmeticExpression ae: this) {
		r.addAll(ae.listAllVars());
	    }
	    return r;	    
	}

	
    }

    /** a0 &le; x &le; a1 */
    private static class Range {
	int a0, a1;
	Range(int _a0, int _a1) {
	    a0 = _a0;
	    a1 = _a1;
	}
	boolean contains(int x) {
	    return a0<=x && x<=a1;
	}
	public String toString() {
	    return "[" + a0 + ".." + a1 + "]";
	}
    }
    
    /** A  PropertyCondition object describes the set of restrictions
	imposed on a particular property of an image-and-property-based
	object. The restrictions can be expressed in terms of a set of 
	accepted string values, or a range of accepted integer values.
     */
    private static class PropertyCondition {
	HashSet<String> acceptedValues = new HashSet<>();
	HashSet<Range> acceptedRanges = new HashSet<>();
	/** GS5 */
	ArithmeticExpression exp = null;
	boolean accepts0(String s, VarMap2  varMap) {
	    if (s.equals("*") || acceptedValues.contains(s)) return true;
	    if (acceptedRanges.size()>0) {
		try {
		    int x = Integer.parseInt(s);
		    for(Range r: acceptedRanges) {
			if (r.contains(x)) return true;
		    }
		} catch( NumberFormatException ex) {}
	    }
	    if (exp != null && varMap !=null) { // GS5
		Set<Object> w = exp.evalSet2(varMap);
		if (Util.eachToString(w).contains(s))  return true;
	    }

	    return false;
	}
	boolean accepts(String s, VarMap2  varMap) {
	    boolean r= accepts0(s,  varMap);
	    //System.out.println("DEBUG: PC.accepts(" +s+", cond="+this+", varMap=" +varMap+"), PC_result=" + r);
	    return r;
	}

	
	void add(String s) {

	    //System.out.println("DEBUG: cond.add(" + s +")");

	    
	    acceptedValues.add(s);
	}
 	void addRange(int a0, int a1) {
	    acceptedRanges.add(new Range(a0,a1));
	}
	public String toString() {
	    Vector<String> v = new Vector<>();
	    v.addAll(acceptedValues);
	    for(Range r: acceptedRanges) v.add("R="+r.toString());
	    if (exp!=null) v.add( "E="+exp.toString());
	    String s=String.join(", ", v);		
	    if (v.size()!=1) s = "["+s+"]";
	    return s;
	}	
    }

    
    /** Syntax:(counter,shape,color,position,bucketFunctions)
	<p>
	Example: (10,square,*,*,[1,2]) (10,*,blue,10,[2,3])
    */
    public static class Atom {
	/** -1 means "no limit" */
	public final int counter;
	/** For shape-and-color pieces, determines acceptable shapes. Null means "no restriction" */
	/** If null, there are no shape restrictons; if empty or non-empty array, then either one of the elements must match, or (since GS5) permission may be granted via propertyConditions instead) */
	public final Piece.Shape[] shapes;
	/** Alternative for GS5 */
	//ArithmeticExpression shapesExp;
	//final
	/** For shape-and-color pieces, determines acceptable colors. Null means "no restriction" */
	public Piece.Color[] colors;
	/** Alternative for GS5 */
	//	ArithmeticExpression colorsExp;
	public PositionList plist;       	
	public BucketList bucketList;
	/** Contains values sets, ranges, or expressions from all
	    "propName:..." fields (including "shape:" and "color:"),
	    as well as the "code:..." field.
	 */
	HashMap<String,PropertyCondition> propertyConditions=new HashMap<>();

	//ArithemeticExpression code = null;
	
	public String toString() {
	    String s= "(" + counter + ","  +showList(shapes) + "," + showList(colors)+",";
	    for(String key: propertyConditions.keySet()) {
		s += key + ":" + propertyConditions.get(key) + ",";
	    }
	    
	    s += plist +  "," + bucketList + ")";
	    return s;
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

	/** Does this atom have some conditions related to properties
	    other than shape and color? */
	private boolean hasNonSCCond() {
	    for(String key: propertyConditions.keySet()) {
		if (!key.equals("shape") && !key.equals("color")) return true;
	    }
	    return false;
	}

	
	/** Format as the source code of the rules set */
	public String toSrc() {
	    if (hasNonSCCond()) {
		Vector<String> v = new Vector<>();
		v.add("count:" +  (counter<0? "*" : ""+counter));
		for(String key: propertyConditions.keySet()) {
		    v.add(key + ":" + propertyConditions.get(key));
		}
	
		v.add("pos:" + plist.toSrc());
		v.add("bucket:"+ bucketList.toSrc());
		return "(" + String.join(", ", v) + ")";
	    } else {
		return "(" +  (counter<0? "*" : ""+counter) +
		    "," + showList(shapes) +
		    "," + showList(colors) +
		    "," + plist.toSrc() +  "," + bucketList.toSrc() + ")";
	    }
	}

	/** Used in lite rule sets */
	Atom(Piece.Shape[] _shapes,  Piece.Color[] _colors) {
	    counter = -1;
	    shapes = _shapes;
	    colors = _colors;
	    try {
		bucketList = new BucketList( Expression.STAR);
	    } catch (RuleParseException ex) {}
	}
	
	Atom(Expression.ParenList pex, TreeMap<String, Order> orders) throws RuleParseException {
	    this(pex,orders, null);
	}

       
	/** Syntax: either
	    (counter,shape,color,position,bucketFunctions) or
	    (domain:value, domain:value, ....).  Note that since GS
	    3.000, the constructor processes the properties "shape"
	    and "color" twice, putting them both into the main
	    property table (which will be applied to any IPB objects)
	    and into the original (GS 1) "colors" and "shapes" lists
	    (which will be applied to the traditional shape-and-color
	    objects).

	    @param agen If not null, use it to expand certain variables, such as X
	 */
	Atom(Expression.ParenList pex, TreeMap<String, Order> orders, AutomaticRuleGenerator agen) throws RuleParseException {
	    int colonCnt=0;
	    for(Expression g: pex) {
		if (g instanceof Expression.ColonExpression) colonCnt++;
	    }

	    boolean colonized=(colonCnt==pex.size());

	    HashMap<String, Expression> arms=new HashMap<>();
	    
	    if (colonized) {
		for(Expression _g: pex) {
		    Expression.ColonExpression g = (Expression.ColonExpression)_g;
		    String key=g.prefix.toString();
		    if (arms.get(key)!=null)  throw new RuleParseException("An atom has multiple clauses with the same prefix '" + key+"'");
		    arms.put(key, g.arex);			
		}
	    } else {
		if (colonCnt>0) throw new RuleParseException("An atom must not combine colon-based and colon-less clauses: size="+ pex.size() +": " + pex);
	    
		if (pex.size()!=5)  throw new RuleParseException("Expected either a 'colonized' tuple or a tuple with 5 elements; found "+ pex.size() +": " + pex);
		arms.put("count", pex.get(0));
		arms.put("shape", pex.get(1));
		arms.put("color", pex.get(2));
		arms.put("pos",   pex.get(3));
		arms.put("bucket", pex.get(4));
	    }
	    

	    Expression g = arms.remove("count"); // count
	    if (g!=null && agen!=null)  g = agen.interpret(g, "count");
	    	    
	    if (g==null || g instanceof Expression.Star) {
		counter = -1;
	    } else if (g instanceof Expression.Num) {
		counter = ((Expression.Num)g).nVal;
	    } else {
		throw new RuleParseException("Counter is not a star or number: " + pex);
	    }

	    
	    g = arms.remove("pos"); // position
	    if (g!=null && agen!=null) g = g.map(agen.mkMapper("pos"));
	    
	    plist = new PositionList(g, orders);
	    //System.out.println("plist=" + plist);
	    g = arms.remove("bucket"); // bucket
	    if (g!=null && agen!=null) g = g.map(agen.mkMapper("bucket"));
	    if (g instanceof ArithmeticExpression)  {
		bucketList = new BucketList((ArithmeticExpression)g);
	    } else if (g instanceof Expression.Star || g==null) {
		// for compatibility with Kevin's syntax
		bucketList = new BucketList((Expression.Star)g);
	    } else {
		throw new RuleParseException("Buckets must be specified by a bracket list. Instead, found "+g+" in: " + pex);
	    }

	    // Properties...
	    for(String key: arms.keySet()) {
		g = arms.get(key);
		if (g!=null && agen!=null) g = g.map(agen.mkMapper(key));
		if (g instanceof Expression.Star) continue;
		PropertyCondition cond = new PropertyCondition();

		if (key.equals("code")) {
		    // For the code: field, only an AEX is expected
		    if (!(g instanceof ArithmeticExpression)) throw new RuleParseException("The value in the 'code:' field makes no sense here: " + g);
		    cond.exp = (ArithmeticExpression)g;		    
		} else 	if (g instanceof Expression.QualifiedId) {
		    // GS5
		    cond.exp = (ArithmeticExpression)g;
		} else if (g instanceof Expression.Id || g instanceof Expression.Num) {
		    if (g instanceof Expression.QualifiedId) throw new RuleParseException("Cannot use Id.Id ("+g+") for prop values");
		    //System.out.println("DEBUG: cond.add(AE=" + g +")");
		    
		    cond.add( g.toString());
		} else if (g instanceof Expression.BracketList &&
			   ((Expression.BracketList) g).isGS3List()) {

		    for(Expression h: (Expression.BracketList)g) {
			if (h instanceof Expression.Id || h instanceof Expression.Num) {
			    if (h instanceof Expression.QualifiedId) throw new RuleParseException("Cannot use Id.Id ("+h+") for shapes");
			    //System.out.println("DEBUG: cond.add(simple=" + g +")");
			    cond.add( h.toString());
			} else  {
			    throw new RuleParseException("Invalid value list element ("+h+") for property "+key+" in: " + pex);
			}
		    }
		} else if (g instanceof Expression.RangeExpression) {
		    Expression.RangeExpression r=(Expression.RangeExpression)g;
		    cond.addRange( r.a0.nVal, r.a1.nVal);		    
		} else if (g instanceof ArithmeticExpression) {
		    // GS5
		    cond.exp = (ArithmeticExpression)g;
		} else {
		    throw new RuleParseException("Invalid value ("+g+") for property '"+key+"' in: " + pex);
		}		      
		propertyConditions.put(key, cond);
	    }

	    g = arms.remove("shape"); // shape
	    if (g!=null && agen!=null) g = g.map(agen.mkMapper("shape"));
	    // System.out.println("Setting shapes from " + g);
	    if (g==null || g instanceof Expression.Star) {
		shapes = null;
	    } else if (g instanceof Expression.Id && !(g instanceof Expression.QualifiedId)) {
		if (g instanceof Expression.QualifiedId) throw new RuleParseException("Cannot use Id.Id ("+g+")  for shapes");
		String s = g.toString();
		shapes = new Piece.Shape[] { Piece.Shape.findShape(s)};
	    } else if (g instanceof Expression.BracketList	&&
		       ((Expression.BracketList) g).isSimpleIdList()) {
		Vector<Piece.Shape> w = new Vector();
		for(Expression h: (Expression.BracketList)g) {
		    if (h instanceof Expression.Id && !(h instanceof Expression.QualifiedId)) {
			String s = h.toString();
			w.add(Piece.Shape.findShape(s));
		    } else  {
			throw new RuleParseException("Invalid shape ("+h+") in: " + pex);
		    }
		}
		shapes = w.toArray(new Piece.Shape[0]);
	    } else if (g instanceof ArithmeticExpression) {
		// GS5: this will be reflected in propertyConditions
		shapes = new Piece.Shape[0]; 
	    } else {    
		throw new RuleParseException("Invalid shape expression ("+g+") in: " + pex);
	    }
	    //System.out.println("shapes=" + showList(shapes));
	    g = arms.remove("color");// color
	    if (g!=null && agen!=null) g = g.map(agen.mkMapper("color"));
	    if (g==null || g instanceof Expression.Star) {
		colors = null;
	    } else if (g instanceof Expression.Id) {
		String s = g.toString();
		colors = new Piece.Color[]{ Piece.Color.findColor(s)};
	    } else if (g instanceof Expression.BracketList	&&
		       ((Expression.BracketList) g).isSimpleIdList()) {
		Vector<Piece.Color> w = new Vector();
		for(Expression h: (Expression.BracketList)g) {
		    if (h instanceof Expression.Id) {
			String s = h.toString();
			w.add(Piece.Color.findColor(s));
			} else  {
			throw new RuleParseException("Invalid color ("+h+") in: " + pex);
		    }
		}
		colors = w.toArray(new Piece.Color[0]);
	    } else if (g instanceof ArithmeticExpression) {
		// GS5: this will be reflected in propertyConditions
		colors = new Piece.Color[0]; 
	    } else  {
		throw new RuleParseException("Invalid color ("+g+") in: " + pex);
	    }

	    //System.out.println("colors=" + showList(colors));	    
	}

	/** Creates a "lite" atom based on this atom. The lite atom
	    is unmetered, and has no restriction on buckets.
	 */
	Atom mkLite() {
	    Atom a=new Atom(shapes,colors);
	    a.plist = plist;
	    a.propertyConditions=propertyConditions;
	    return a;
	}
	
	/** Used when converting Kevin's JSON to our server format */
	void forceOrder(String orderName) {
	    plist.forceOrder(orderName);
	}


	boolean acceptsShape(Piece.Shape x,  VarMap2  varMap) {
	    //System.out.println("DEBUG: acceptsShape("+x+", varMap=" + varMap+")");
	    if (shapes==null) return true;
	    for(Piece.Shape y: shapes) {
		if (x==y) return true;
	    }
	    
	    PropertyCondition cond = propertyConditions.get("shape");
	    boolean r=  (cond!=null)  && (varMap!=null) && cond.accepts(x.toString() //.toLowerCase()
									, varMap);
	    //System.out.println("DEBUG: acceptsShape, cond="+cond +", AS_result=" +r);			       
	    return  r;
	}

	boolean acceptsColor(Piece.Color x, VarMap2  varMap) {
	    if (colors==null) return true;
	    for(Piece.Color y: colors) {
		if (x==y) return true;
	    }
	    PropertyCondition cond = propertyConditions.get("color");
	    return  (cond!=null)  && (varMap!=null) && cond.accepts(x.toString(), varMap);
	}

	

	/** Does this atom accept a specified piece, based on its
	    shape, color, and (in GS3+) any other properties? Since
	    GS5, the "code:" field of the atom is also taken into account.
	    @param varMap Map with variable values for expression evaluations. Maybe null before GS5.
	*/
	public boolean acceptsColorShapeAndProperties(Piece p, VarMap2  varMap) {

	    //System.out.println("DEBUG:ACSAP(" + p +", atom=" + this);
	    
	    PropertyCondition code = propertyConditions.get("code");
	    if (code!=null && varMap!=null) {
		if (code.exp==null) throw new IllegalArgumentException("The 'code:' field w/o an expression");		
		if (code.exp.evalSet2(varMap).isEmpty()) return false;
	    }

	    ImageObject io = p.getImageObject();
	    if (io==null) { // shape-and-color tuple object
		//System.out.println("DEBUG:ACSAP:as=" +  acceptsShape(p.xgetShape(), varMap) +", ac=" + acceptsColor(p.xgetColor(), varMap));
	
		
		return acceptsShape(p.xgetShape(), varMap) &&
		    acceptsColor(p.xgetColor(), varMap);
	    } else { // image-and-property-based object
		for(String key:  propertyConditions.keySet()) {
		    if (key.equals("code")) continue;
		    PropertyCondition cond = propertyConditions.get(key);
		    String s = io.get(key);
		    if (s==null || !cond.accepts(s, varMap)) return false;
		}
		return true;
	    }
	       
	}

    }

    
    /** A row object represents the content of one line of the rule set
	description file, i.e. the optional global counter and 
	one or several rules */
    public static class Row extends Vector<Atom> {
	/** The default value, 0, means that there is no global limit in this row */
	public final int globalCounter;


	/** Only used to construct lite rows */
	Row() {
	    globalCounter=0;
	}
	
	/** Creates a row of the rule set from a parsed line of the rule set file.
	    @param tokens One line of the rule set file, parsed into tokens
	    @param orders Contains all existing orderings
	 */
	Row(Vector<Token> tokens, TreeMap<String, Order> orders)
	    throws RuleParseException {
	    this(tokens, orders, null);
	}

	Row(Vector<Token> tokens, TreeMap<String, Order> orders, AutomaticRuleGenerator agen) throws RuleParseException {

	    
	    int gc=-1;
	    boolean first=true;
	    
	    // fixme: keep using ex!
	    while(tokens.size()>0) {
		//Expression ex = Expression.mkExpression(tokens);
		Expression ex = Expression.mkCounterOrAtom( tokens);
		
		
		if (first) {
		    first=false;
		    if (agen!=null && ex instanceof  Expression.Id)  ex = agen.interpret(ex, "count");
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
		add(new Atom(pex, orders, agen));		    
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
	/** Lists all properties, and all property values, used in this row.
	    This is primarily used for correctness checking during 
	    experiment plan validation.
	 */
	TreeMap<String,TreeSet<String>> listAllPropValues() {
	    TreeMap<String,TreeSet<String>> propValues = new TreeMap<>();
	    for(Atom atom: this) {
		for(String p: atom.propertyConditions.keySet()) {
		    TreeSet<String> h = propValues.get(p);
		    if (h==null) propValues.put(p, h=new TreeSet<String>());
		    PropertyCondition cond = atom.propertyConditions.get(p);
		    h.addAll( cond.acceptedValues);
		}
	    }
	    return propValues;
	}
	
        /** The list of variables mentioned in the bucket expressions in this row */
	//public HashSet<String> listAllBucketVars() {
	  //  HashSet<String> r = new HashSet<>();
	  //  for(Atom atom: this) {		
	  //       r.addAll(atom.bucketList.listAllVars());
	  //  }
	  //  return r;	    
	//}	    
	    
    }

    /** All orders */
    public TreeMap<String, Order> orders =  new TreeMap<>();
    /** All rows of this rule set */
    public Vector<Row> rows = new Vector<>();

    /** Used for "lite" sets */
    private RuleSet() {}


    //	Row(Vector<Token> tokens, TreeMap<String, Order> orders, AutomaticRuleGenerator agen) throws RuleParseException {
    
    public RuleSet(String ruleText) throws RuleParseException {
	this( ruleText, null);
    }
    
    public RuleSet(String ruleText, AutomaticRuleGenerator agen) throws RuleParseException {
	this( ruleText.split("\n"), agen);
    }

    /** Creates a RuleSet based on the content of a rule set file. 
	The file may contain some (optional) custom order definition
	lines, followed by one or more rule lines.
	@param rr The lines  from the rule set file */
    public RuleSet(String[] rr) throws RuleParseException {
	this(rr,null);
    }
    public RuleSet(String[] rr, AutomaticRuleGenerator agen) throws RuleParseException {

	boolean topCommentEnded=false;

	
	for(String r: rr) {
	    r = r.trim();
	    if (r.startsWith("#") || r.length()==0) {
		if (!topCommentEnded) {
		    String s = r.replaceAll("^#+-* *", "");
		    if (s.length()>0) description.add(s);
		}

		
		continue; // skip comment lines and blank lines
	    }
	    topCommentEnded=true;
	    
	    Vector<Token> tokens= Token.tokenize(r);
	    if (tokens.isEmpty()) throw new RuleParseException("No data found in the line: " + r);
	    Token a=tokens.get(0);
	    if (rows.size()==0 && a.type==Token.Type.ID &&
		a.sVal.equalsIgnoreCase("Order")) { // order line:
		// Order Name=[1,2,3,....]
		tokens.remove(0);
		if (tokens.isEmpty() || tokens.get(0).type!=Token.Type.ID )  throw new RuleParseException("Missing order name in an order line: " + r);
		String name = tokens.remove(0).sVal;
		if (tokens.isEmpty() ||  tokens.get(0).type!=Token.Type.EQUAL)  throw new RuleParseException("Missing equal sign in an order line: " + r);
		tokens.remove(0);
		// Expects a bracket list of integers
		Expression.BracketList ex = Expression.mkBracketList(tokens);
		if (!tokens.isEmpty()) throw new RuleParseException("Invalid order description: " +r);	
		orders.put(name, new Order(ex));
		continue;
	    }
		
	    Row row;
	    try {
		row = new Row(tokens, orders, agen);
	    } catch(RuleParseException ex) {
		throw new RuleParseException("Failed to parse rule row: " + r, ex);
	    }

	    
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
    
    /** Lists all properties, and all property values, used in this rule set. 
	This is primarily used for correctness checking during 
	the experiment plan validation.
    */
    public TreeMap<String,TreeSet<String>> listAllPropValues() {
	TreeMap<String,TreeSet<String>> propValues = new TreeMap<>();
	for(Row row: rows) {
	    TreeMap<String,TreeSet<String>> w =  row.listAllPropValues();
	    for(String p: w.keySet()) {
		TreeSet<String> h = propValues.get(p);
		if (h==null) propValues.put(p, h=new TreeSet<String>());
		h.addAll(w.get(p));
	    }
	}
	return propValues;
    }

    /** The list of variables mentioned in the bucket expressions in this row */
    public HashSet<String> listAllBucketVars() {
	HashSet<String> r = new HashSet<>();
	for(Row row: rows) {
	    for(Atom atom: row) {		
	         r.addAll(atom.bucketList.listAllVars());
	    }
	}		
	return r;	    
    }	    
	
	
    /** Creates a "lite" rule set based on this rule set. A lite set is one
	which has all atoms in the same row, nothing is metered, and 
	all destination  buckets are allowed. Such sets are used in 
	stalemate testing.
     */
    RuleSet mkLite() {
	RuleSet x = new RuleSet();
	x.orders = orders;
	x.rows = new Vector<Row>();
	Row r = new Row();
	for(Row row: rows) {
	    for(Atom a: row) {
		r.add( a.mkLite());
	    }
	}
	x.rows.add(r);
	return x;
 
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
