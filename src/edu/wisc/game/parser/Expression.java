package edu.wisc.game.parser;

import java.io.*;
import java.util.*;
import java.text.*;
import edu.wisc.game.util.Util;
import edu.wisc.game.sql.Episode;

public interface Expression {

   
    static Integer toInteger(Object o) {
	if (o instanceof Integer) return (Integer) o;
	/*else if (o instanceof String) {
	    try {
		return new Integer((String)o);
	    } catch(Exception ex) { return null; }
	    } */
	else return null;	
    }

    /** A Mapper is something that can take a variable
	(Expression.Id) and produce another Expression (e.g. by
	substituting the value of the variable). This is used 
	in automatic rule generation
    */
    public static interface Mapper {
	Expression apply(Expression ex)  throws RuleParseException;
    }

    public Expression map(Mapper mapper)  throws RuleParseException;
    
    public String toSrc();
    /** Can be overridden as needed */
    //    public String toString() { return toSrc(); }

    abstract static class ExList extends Vector<Expression> implements Expression {
	private ExList() {}
	ExList( Vector<Expression> v) {
	    super(v);
	}
	public String toString(char open, char close) {
	    Vector<String> v = new Vector<>();
	    for(Expression e: this) v.add(e.toString());
	    return ""+ open + String.join(", ", v) + close;
	}
	public String toSrc(char open, char close) {
	    Vector<String> v = new Vector<>();
	    for(Expression e: this) v.add(e.toSrc());
	    return ""+ open + String.join(", ", v) + close;
	}

	/** Applies the mapper to each component */
	Vector<Expression> doMap(Mapper mapper)   throws RuleParseException{
	    Vector<Expression> w = new Vector<>();
	    for(Expression e: this) w.add( e.map(mapper));
	    return w;
	}

    };

    static public class ParenList extends ExList {
 	private ParenList() {}
 	ParenList( Vector<Expression> v) {
	    super(v);
	}
	public String toString() {
	    return toString('(', ')');
	}
 	public String toSrc() {
	    return toSrc('(', ')');
	}

	/** Applies the mapper to each component */
	public ParenList map(Mapper mapper)   throws RuleParseException{
	    ParenList e = new  ParenList(doMap( mapper));
	    return (ParenList)mapper.apply(e);
	}

    };
    
    static public class BracketList extends ExList implements ArithmeticExpression {
	
 	private BracketList()  {}
 	BracketList( Vector<Expression> v)  throws RuleParseException{
	    super(v);
   	    for(Expression x: v) {
		if (!(x instanceof ArithmeticExpression))  throw new RuleParseException("A bracket expression must consist of arithmetic expressions only. This one isn't: " + x); 
	    }
	}

	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> h) {
	    HashSet<Integer> r = new HashSet<>();
	    for(Expression _x: this) {
		ArithmeticExpression x = (ArithmeticExpression)_x;
		r.addAll(x.evalSet(h));
	    }
	    return r;
	}


	public HashSet<Object> evalSet2(VarMap2 h) {
	    HashSet<Object> r = new HashSet<>();
	    for(Expression _x: this) {
		ArithmeticExpression x = (ArithmeticExpression)_x;
		r.addAll(x.evalSet2(h));
	    }
	    return r;
	}

	
	public HashSet<String> listAllVars() {
	    HashSet<String> r = new HashSet<>();
	    for(Expression _x: this) {
		ArithmeticExpression x = (ArithmeticExpression)_x;
		r.addAll(x.listAllVars());
	    }
	    return r;	    
	}

	
	public String toString() {
	    return toString('[', ']');
	}
 	public String toSrc() {
	    return toSrc('[', ']');
	}

	/** Applies the mapper to each component */
	public BracketList map(Mapper mapper)   throws RuleParseException{
	    BracketList e = new BracketList(doMap( mapper));
	    return (BracketList)mapper.apply(e);
	}

	/** Is this a list suitable for shapes, colors, and other properties
	    in GS 3? That is, a list consisting of just simple (not qualified)
	    IDs and integers. If it isn't, it has to be interpreted as a GS5
	    expression. */
	public boolean isGS3List() {
	    for(Expression x: this) {
		if (x instanceof QualifiedId) return false;
		if (!(x instanceof Id || x instanceof Num)) return false;
	    }
	    return true;			    
	}

	/** Is this just a list of simple (not qualified) IDs? This is
	    what's allowed for shapes in colors before GS5.
	 */
	public boolean isSimpleIdList() {
	    for(Expression x: this) {
		if (x instanceof QualifiedId) return false;
		if (!(x instanceof Id)) return false;
	    }
	    return true;			    
	}

    };

    /** A HashMap storing a set of values of a given type T for easch key */
    static public class MapTo<T>  extends HashMap<String, HashSet<T>> {
	/** Adds a value to the list of values associated with a specified key */
	public boolean addValue(String key, T val) {
	    HashSet<T> h = get(key);
	    if (h==null) put(key, h=new HashSet<>());
	    return h.add(val);
	}

	public String toString() {
	    Vector<String> v = new Vector<>();
	    for(String key: keySet()) {
		v.add("["+key+":"+ Util.joinNonBlank(",",  get(key) /*w*/)+ "]");
	    }
	    return String.join(" ", v);
	}

	/** @param key A variable name, such as "p", "pc", "ps", or "propName.propValue" */
	public void pu( String /*BucketSelector*/ key, T k) {
	    HashSet<T> h = new  HashSet<>();
	    h.add(k);
	    put(key/*.toString()*/, h);
	}
	

	
    }

    static public class VarMap extends MapTo<Integer> {}

    /** Values are String or Integer */
    static public class PropMap extends HashMap<String,Object>{
	/** Stores a String value, as it is, or (if possible) converted to 
	    Integer */
	public Object putString(String key, String s) {
	    try {
		return super.put(key, new Integer(s));
	    } catch(Exception ex) { 
		return super.put(key, s);
	    }
	}

	
	public Object put(String key, Object o) {
	    if (o instanceof String) {
		return  putString(key, (String)o);
	    } else if (o instanceof Integer) {
		return super.put(key, o);
	    } else {
		throw new IllegalArgumentException("Illegal property type ("+o.getClass()+"): " + o);
	    }
	}
    };
    
    /** Objects in question may be Integer, String, or PropMap
	(ImageObject or equivalent) */
    static public class VarMap2 extends MapTo<Object> {
	public boolean addValue(String key, Object val) {
	    if (val==null) throw new IllegalArgumentException("An attempt to include a null value for key="+key);
	    else if (val instanceof Integer) {}
	    else if (val instanceof String) {
		try { val = new Integer((String)val); }
		catch(Exception ex) {}
	    } else if (val instanceof PropMap) {}
	    else {
		throw new IllegalArgumentException("An attempt to include a value of an inappropriate type(" + val.getClass() +") into a variable to be used in set arithmetic: key=" +key+", val=" + val);
	    }
	    return super.addValue(key, val);
	}	

    }
    

    /** An arithmetic expression is composed of variables, constants,
	and arithmetic operations; parentheses can be used for
	ordering operations.  The value of the expression can be
	computed for any given set of variable values (unless a division
	by zero etc happens).
    */
    public static interface ArithmeticExpression extends Expression {
	/** Evaluates this expression for the given values of the variables
	    involved. Can be used when the arguments can have multiple values.
	    @param h The hash map that contains for each variable the possible
	    set of its values.
	    @return the set of the possible values of the expression, or an empty set if the expression  uses a variable whose value is not in h
	 */
	HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> h);
	HashSet<Object> evalSet2(VarMap2 h);
	HashSet<String> listAllVars();

    }

    /** A numeric constant */
    public static class Num implements ArithmeticExpression {
	final public int nVal;
	Num(Token t) throws RuleParseException {
	    if (t.type!=Token.Type.NUMBER) throw new RuleParseException("Not a number");
	    nVal = t.nVal;
	}
	public Num(int n) {
	    nVal = n;
	}

	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> h) {
	    HashSet<Integer> hr=new HashSet<>();
	    hr.add(nVal);
	    return hr;
	}
	public HashSet<Object> evalSet2(VarMap2 h) {
	    HashSet<Object> hr=new HashSet<>();
	    hr.add(nVal);
	    return hr;
	}

	public String toString() {
	    return "" + nVal;
	}
	public String toSrc() { return toString(); }
	public HashSet<String> listAllVars() { return new HashSet<String>(); }

	public Expression map(Mapper mapper)   throws RuleParseException{
	    return mapper.apply(this);
	}
	
    }

    /** A variable. In GS 2.0. a STRING is treated as a variable name as 
	well, in order to deal with quoted shape names such as "au/kangaroo".
     */
    public static class Id implements ArithmeticExpression  {
	final public String sVal;
	/** Set to true in those rare cases when we use Id to represent
	    a quoted string (used for shapes in GS2) */
	final boolean quoted;
	Id(Token t) throws RuleParseException {
	    if (t.type!=Token.Type.ID &&
		t.type!=Token.Type.STRING)
		throw new  RuleParseException("Not an id");
	    sVal = t.sVal;
	    quoted = (t.type==Token.Type.STRING);
	}
	/** @param s Must be a proper ID, not a quoted string */
	public Id(String s) throws RuleParseException {
	    sVal = s;
	    quoted = false;
	}

	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> h) {
	    HashSet<Integer> q= h.get(sVal);
	    return q==null? new HashSet<Integer>() : q;
	}
	public HashSet<Object> evalSet2(VarMap2 h) {
	    HashSet<Object> r = new HashSet<>();
	    if (quoted) {
		r.add(sVal);
	    } else {
		HashSet<Object> q= h.get(sVal);
		if (q != null) r = q;
	    }
	    return r;
	}

	
	public String toString() {
	    return toSrc();
	}
	public String toSrc() {
	    return quoted? "\""+ sVal+"\"" : sVal;
	}
	public HashSet<String> listAllVars() {
	    HashSet<String> h = new HashSet<String>();
	    h.add(toString());
	    return h;
	}
	public Expression map(Mapper mapper)   throws RuleParseException{
	    return mapper.apply(this);
	}
    }

    /** A.B */
    public static class QualifiedId extends Id {
	final public Id prefix;
	QualifiedId(Id _prefix, Token t2) throws RuleParseException  {
	    super(t2);
	    prefix = _prefix;
	}
	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> h) {
	    HashSet<Integer> q= h.get(prefix + "."+ sVal);
	    return q==null? new HashSet<Integer>() : q;
	}
	/** Trying different interpretations */
	public HashSet<Object> evalSet2(VarMap2 h) {
	    HashSet<Object> v = new HashSet<>();
	    HashSet<Object> z = h.get( toString() );
	    if (z!=null)   v.addAll( z);
	    z = prefix.evalSet2(h);
	    for(Object o: z) {
		if (o instanceof PropMap) {
		    Object a =  ((PropMap)o).get(sVal); // Integer or String
		    if (a!=null) {
			v.add( a);
		    }
		}
	    }
	    return v;

	}

	public String toString() {
	    String s = prefix.toString() + "." + sVal;
	    if (quoted) s = "\"" + s + "\"";
	    return s;
	}  
	public String toSrc() {
	    return toString();
	}


    }

    /** !E evaluates to [1] if E is an empty set, or to [] otherwise */
    static class NegationExpression implements ArithmeticExpression {
	final ArithmeticExpression body;
	NegationExpression(ArithmeticExpression _body) {
	    body = _body;
	}
	
	public String toString() {
	    return "{!"+ body + "}";
	}	
	public String toSrc() {
	    return "!"+ body.toSrc();	 	    
	}
	
	
	/** Lists all variable names used in this expression */
	public HashSet<String> listAllVars() {
	    return body.listAllVars();
	}

     
	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> hh) {
	    HashSet<Integer> hs = body.evalSet(hh);
	    HashSet<Integer> h = new HashSet<>();
	    if (hs.isEmpty()) h.add(1);
	    return h;
	}

	public HashSet<Object> evalSet2(VarMap2 hh) {
	    HashSet<Object> hs = body.evalSet2(hh);
	    HashSet<Object> h = new HashSet<>();
	    if (hs.isEmpty()) h.add(1);
	    return h;	    
	}
	
	/** Applies the mapper to each component */
	public Expression map(Mapper mapper)   throws RuleParseException{
	    ArithmeticExpression body1 =
		(ArithmeticExpression)mapper.apply(body);	    
	    NegationExpression e = new NegationExpression( body1 );
	    return mapper.apply(e);
	}

	
    }


    
    /** Additive or multiplicative */
    abstract static class  SerialExpression extends Vector<ArithmeticExpression>  	implements ArithmeticExpression {
	/** All operators are of the same type, either additive or multiplicative. this.size()==ops.size()+1 */
	Vector<Token> ops = new Vector<>();
	
	SerialExpression() {
	    super();
	}
	
	SerialExpression(Vector<Token> _ops, Vector<ArithmeticExpression> v) {
	    super(v);
	    ops = _ops;
	}

	
   	public String toString() {
	    Vector<String> v = new Vector<>();
	    v.add(firstElement().toString());
	    for(int j=0; j<ops.size(); j++) {
		v.add(ops.get(j).toString());
		v.add(get(j+1).toString());
	    }
	    return "{"+ String.join(" ", v) + "}";
	}
	
	public String toSrc() {
	    Vector<String> v = new Vector<>();
	    v.add(firstElement().toSrc());
	    for(int j=0; j<ops.size(); j++) {
		v.add(ops.get(j).toSrc());
		v.add(get(j+1).toSrc());
	    }
	    String s = String.join(" ", v);
	    if (v.size()>1) 	    s= "("+ s + ")";
	    return s; 
	    
	}
	
	
	/** Lists all variable names used in this expression */
	public HashSet<String> listAllVars() {
	    HashSet<String> h = new HashSet<String>();
	    for(ArithmeticExpression a: this) h.addAll( a.listAllVars());
	    return h;
	}

	private static Integer doOp(Token op, Object _s, Object _q) {
	    Integer s =  toInteger(_s), q = toInteger(_q);

	    if (s==null) throw new IllegalArgumentException("Attempt to apply operation " + op + " to a non-number ("+_s.getClass()+"): " + _s);
	    if (q==null) throw new IllegalArgumentException("Attempt to apply operation " + op + " to a non-number ("+_q.getClass()+"): " + _q);
					
	    Integer r;
	    if (q.intValue()==0 && (op.cVal == '/' || op.cVal == '%')) {
		return null; // no result from division by zero
	    }

	    
	    //if (op.equals(Token.EQQ)) {
	    //	if (s.equals(q)) r = 1;
	    //	else continue;
	    //} else

	    

	    if (op.cVal == '+') r = s+q;
	    else if (op.cVal == '-') r = s-q;
	    else if (op.cVal == '*') r = s*q;
	    else if (op.cVal == '/') r = s/q;
	    else if (op.cVal == '%') r = s%q;
	    else throw new IllegalArgumentException("Illegal operation " + op + " in an additive or multiplicative expression");
	    return r;
	}
	
	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> hh) {
	    HashSet<Integer> hs = firstElement().evalSet(hh);
	    for(int j=1; j<size(); j++) {
		if (hs.size()==0) return hs;	
		HashSet<Integer> hq =get(j).evalSet(hh);
		if (hq.size()==0) return hq;
		Token op = ops.get(j-1);

		HashSet<Integer> hr = new HashSet<>();

		for(Integer s: hs) {		    
		    for(Integer q: hq) {
			Integer r = doOp(op, s,q);
			if (r!=null) hr.add(r);
		    }
		}
		hs = hr;
	    }
	    return hs;
	}

	public HashSet<Object> evalSet2(VarMap2 hh) {
	    HashSet<Object> hs = firstElement().evalSet2(hh);
	    for(int j=1; j<size(); j++) {
		if (hs.size()==0) return hs;	
		HashSet<Object> hq =get(j).evalSet2(hh);
		if (hq.size()==0) return hq;
		Token op = ops.get(j-1);

		HashSet<Object> hr = new HashSet<>();

		for(Object s: hs) {
		    for(Object q: hq) {
			Integer r = doOp(op, s, q);
			if (r!=null) hr.add(r);
		    }
		}
		hs = hr;
	    }
	    return hs;
	    
	}
	
	/** Applies the mapper to each component */
	Vector<ArithmeticExpression> doMap(Mapper mapper)  throws RuleParseException {
	    Vector<ArithmeticExpression> w = new Vector<>();
	    for(ArithmeticExpression e: this) {
		w.add( (ArithmeticExpression)e.map(mapper));
	    }
	    //	    System.out.println("|w|=" + w.size());
	    return w;
	}

	
    }


    /** Has exactly two operands, and the operator is '==' */
    public static class ComparisonExpression extends SerialExpression  {
	//ArithmeticExpression[] aa = new ArithmeticExpression[2];
	ComparisonExpression(Token token, ArithmeticExpression a, ArithmeticExpression b) {
	    add(a);
	    add(b);
	    //ops.add(Token.EQQ);
	    ops.add(token);
	}
	
	/** Carries out numeric comparison (as per the operator in
	    this ComparisonExpression) if both values are non-null and
	    represent the same number. Otherwise just returns false. */
	private boolean cmp(Integer s, Integer q) {
	    if (s==null || q==null) return false;
	    Token t = ops.get(0);

	    if  (t==Token.EQQ) return s.equals(q);
	    
	    int cmp = s.compareTo(q);
	    return
		(t==Token.LT)? cmp < 0:
		(t==Token.LE)? cmp <= 0:
		(t==Token.GT)? cmp > 0:
		(t==Token.GE)? cmp >= 0:
		false;		    
	}
	
	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> hh) {
	    HashSet<Integer> hs0 = get(0).evalSet(hh), hs1=get(1).evalSet(hh);

	    if (hs0.size()==0 || hs1.size()==0) return hs0;	
	    Token t = ops.get(0);

	    boolean e=false;
	    for(Integer s: hs0) {
		if (e) break;
		for(Integer q: hs1) {
		    if (e) break;
		    e = cmp(s,q);		    
		}
	    }
	    HashSet<Integer> hr = new HashSet<>();
	    if (e) hr.add(1);
	    return hr;
	}	


	public HashSet<Object> evalSet2(VarMap2 hh) {
	    HashSet<Object> hs0 = get(0).evalSet2(hh), hs1=get(1).evalSet2(hh);

	    if (hs0.size()==0 || hs1.size()==0) return hs0;	
	    Token t = ops.get(0);

	    boolean e=false;
	    for(Object s: hs0) {
		if (e) break;
		Integer is = toInteger(s);
		for(Object q: hs1) {
		    if (e) break;
		    e = (t==Token.EQQ)?  s.equals(q):
			cmp(is, toInteger(q));
		}
	    }
	    HashSet<Object> hr = new HashSet<>();
	    if (e) hr.add(1);
	    return hr;	    
	}
	
	/** Applies the mapper to each component */
	public Expression map(Mapper mapper)   throws RuleParseException{
	    Vector<ArithmeticExpression> w = doMap( mapper);
	    ComparisonExpression e = new ComparisonExpression(ops.get(0), w.get(0),w.get(1));
	    return mapper.apply(e);
	}

    }


    public static class AdditiveExpression extends  SerialExpression  {
	AdditiveExpression() {}
	AdditiveExpression(ArithmeticExpression x) {
	    add(x);
	}
	AdditiveExpression(Vector<Token> _ops, Vector<ArithmeticExpression> v) {
	    super(_ops, v);
	}

	/** Applies the mapper to each component */
	public Expression map(Mapper mapper)  throws RuleParseException {
	    AdditiveExpression e = new AdditiveExpression(ops, doMap( mapper));
	    //System.out.println("Assembled additive = " + e);
	    return mapper.apply(e);
	}

	
    }

    public static class MultiplicativeExpression extends  SerialExpression  {
	MultiplicativeExpression() {}
	MultiplicativeExpression(ArithmeticExpression x) {
	    add(x);
	}
	MultiplicativeExpression(Vector<Token> _ops, Vector<ArithmeticExpression> v) {
	    super( _ops, v);
	}


	/** Applies the mapper to each component */
	public Expression map(Mapper mapper)  throws RuleParseException {
	    MultiplicativeExpression e = new MultiplicativeExpression(ops, doMap( mapper));
	    return mapper.apply(e);
	}

	
    }

    /** Id:ArithmeticExpression; used in GS 3 */
    public static class ColonExpression implements Expression  {
	public final Id prefix;
	public final Expression arex;
	ColonExpression (Id _prefix, Expression _arex) {
	    prefix=_prefix;
	    arex=_arex;
	}
	public String toSrc() {
	    return prefix.toSrc() + ":" + arex.toSrc();
	}
	public String toString() {
	    return prefix.toString() + ":" + arex.toString();
	}
	public Expression map(Mapper mapper)   throws RuleParseException{
	    ColonExpression e=new ColonExpression( (Id)prefix.map(mapper), arex.map(mapper));
	    return  mapper.apply(e);
	}
    }

    /** [Num..Num] */
    public static class RangeExpression implements Expression  {
	public final Num a0, a1;
	RangeExpression(Num _a0, Num _a1) {
	    a0=_a0;
	    a1=_a1;
	}
	public String toSrc() {
	    return "[" + a0.toSrc() + ".." + a1.toSrc() + "]";
	}
	public String toString() { return toSrc(); }

	public Expression map(Mapper mapper)   throws RuleParseException{
	    RangeExpression e=new RangeExpression( (Num)a0.map(mapper),(Num)a1.map(mapper));
	    return  mapper.apply(e);
	}
    }
    
    /** A Star expression is simply "*". (Used in rule description for
	counters, or to mean "Any"). */
    public static class Star  implements Expression {
	public String toString() {
	    return "*";
	}  
	public String toSrc() {
	    return toString();
	}  
	public Expression map(Mapper mapper)   throws RuleParseException{
	    return mapper.apply(this);
	}
    };

    final Star STAR = new Star();
    
    /** Extracts one of the sections of a rule line: either the leading
	counter (int or star), or one of the atoms (paren lists that
	may include arithmetic expressions or stars)
     */
    static Expression mkCounterOrAtom(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of expression");
	//	System.out.println("DEBUG: mkCounterOrAtom, tokens={" + Util.joinNonBlank(" ", tokens) + "}");
	Token a = tokens.remove(0);
	//System.out.println("DEBUG: poppped a=" +a);
	
	if (a.equals(Token.STAR)) {
	    return STAR;
	} else if (a.type==Token.Type.NUMBER) {
	    return new Num(a);	    
	} else if (a.isOpenParen()) {
	    
	    // Now, expect a comma-separated list of ArEx or ColonEx
	    Vector<Expression> v = new Vector<>();
	    while(!tokens.isEmpty()) {
		a = tokens.get(0);
		if (a.equals(Token.STAR)) {
		    tokens.remove(0);
		    v.add( new Star());
		} else if (v.size()==0 && a.type==Token.Type.CLOSE && a.cVal==')'){
		    // an empty atom
		} else if (tokens.size()>=2 &&
			   a.type==Token.Type.ID &&
			   tokens.get(1).type==Token.Type.COLON) {
		    Id prefix = new Id(a);
		    tokens.remove(0); // prefix 
		    tokens.remove(0); // :
		    //System.out.println("DEBUG: Found prefix " + prefix + ":, rest={" + Util.joinNonBlank(" ", tokens) + "}");
		    Expression y = mkRangeExpression(tokens);
		    if (y==null) y = mkLongestArithmeticExpression(tokens);
		    //System.out.println("DEBUG: Found CE=" + prefix + ":" + y +", rest={" + Util.joinNonBlank(" ", tokens) + "}");
		    v.add(  new ColonExpression(prefix, y));
		} else {
		    v.add(  mkLongestArithmeticExpression(tokens));
		}
		
		if (tokens.isEmpty()) throw new  RuleParseException("Unexpected end of a paren list expression");
		Token b = tokens.get(0);
		if (b.type==Token.Type.COMMA) {
		    tokens.remove(0);
		    continue;
		} else 	if (b.type==Token.Type.CLOSE && b.cVal==')') {
		    tokens.remove(0);
		    return new ParenList(v);
		} else {
		    throw new  RuleParseException("Unexpected end of a parenthesized list: instead of a comma or a closing paren, found " + b);
		}	
	    }
	    throw new  RuleParseException("Unexpected end of a parenthesized list: line ended without closing paren");
	} else {
	    throw new  RuleParseException("Expected a counter or an atom (a paren list), found " + a);
	}
    }


    /** If the given sequence of tokens starts with a range expression,
	extracts it; otherwise, returns null */
    static RangeExpression mkRangeExpression(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()<5) return null;
	if (!(tokens.get(0).type==Token.Type.OPEN && tokens.get(0).cVal=='[')) return null;
	if (tokens.get(1).type!=Token.Type.NUMBER) return null;
	if (!tokens.get(2).equals(Token.DOTDOT)) return null;
	if (tokens.get(3).type!=Token.Type.NUMBER) return null;
	if (!(tokens.get(4).type==Token.Type.CLOSE && tokens.get(4).cVal==']')) return null;
	tokens.remove(0);
	Num a0 = new Num(tokens.remove(0));
	tokens.remove(0);
	Num a1 = new Num(tokens.remove(0));
	tokens.remove(0);
	return new RangeExpression(a0,a1);
    }

    
    /** Creates the longest ArithmeticExpression starting at the beginning of the tokens array. 
	<pre>
	E := E5
	E5 :=  E4  |  E4==E4
	E4 :=  E3  |  E3+E3+...
	E3 :=  E2  |  E2*E2...
	E2 :=  E1  |  !E2
	E1 :=  (E)  |  Id.Id | Id  | Num |  [E4,E4,...]
    */
    static ArithmeticExpression mkLongestArithmeticExpression(Vector<Token> tokens) throws RuleParseException {
	return mkLongestE5( tokens);
    }

    //    private
    static ArithmeticExpression mkLongestE5(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E5-type arithmetic expression)");
	ArithmeticExpression q = mkLongestE4(tokens);
	if (tokens.size()==0) return q;
	Token a = tokens.firstElement();
	
	if (a.type != Token.Type.CMP) return q;
	tokens.remove(0);	    
	ArithmeticExpression q2 = mkLongestE4(tokens);
	return new ComparisonExpression(a, q, q2);
    }

    //private
    static ArithmeticExpression mkLongestE4(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E4-type arithmetic expression)");
	ArithmeticExpression q = mkLongestE3(tokens);
	AdditiveExpression y = new AdditiveExpression(q);
	while(tokens.size()>0 &&  tokens.firstElement().type==Token.Type.ADD_OP) {
	    y.ops.add(tokens.remove(0));
	    if (tokens.isEmpty()) throw new  RuleParseException("Unexpected end of additive expression");
	    ArithmeticExpression b =  mkLongestE3(tokens);
	    y.add(b);
	}
	return (y.size()>1) ? y : q;
    }

    /** E3 :=  E2  |  E2*E2... */
    //private
    static ArithmeticExpression mkLongestE3(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E3-type arithmetic expression)");
	ArithmeticExpression q = mkLongestE2(tokens);
	MultiplicativeExpression y = new MultiplicativeExpression(q);
	while(tokens.size()>0 &&  tokens.firstElement().type==Token.Type.MULT_OP) {
	    y.ops.add(tokens.remove(0));
	    if (tokens.isEmpty()) throw new  RuleParseException("Unexpected end of additive expression");
	    ArithmeticExpression b =  mkLongestE2(tokens);
	    y.add(b);
	}
	return (y.size()>1) ? y : q;
    }
    
    /** E2 :=  E1  |  !E2 */
    //private
    static ArithmeticExpression mkLongestE2(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E2-type arithmetic expression)");
	if (tokens.firstElement().equals(Token.BANG)) {
	    tokens.remove(0);
	    ArithmeticExpression q = mkLongestE2(tokens);
	    return new NegationExpression(q);
	} else {
	    return mkLongestE1(tokens);
	}
    }
   /** 	E1 :=  (E) | Id.Id |  Id  | Num | -Num |  [E,E,...] */ 
    static ArithmeticExpression mkLongestE1(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E1-type arithmetic expression)");
	Token a = tokens.firstElement();

	if (a.type == Token.Type.ID) {
	    tokens.remove(0);
	    Id id = new Id(a);
	    while(tokens.size()>=2 && tokens.get(0)==Token.DOT && tokens.get(1).type == Token.Type.ID) {
		tokens.remove(0);
		Token b = tokens.remove(0);
		id = new QualifiedId(id,b);
	    }
	    return id;	
	} else if (a.type == Token.Type.STRING) {
	    // quoted strings are allowed, as if they were IDs, to better handle the "shape" column
	    tokens.remove(0);
	    return new Id(a);
	} else if (a.type == Token.Type.NUMBER) {
	    tokens.remove(0);
	    return new Num(a);
	} else if (a.type==Token.Type.ADD_OP && a.cVal=='-') {
	    tokens.remove(0);
	    if (tokens.size()==0) throw new  RuleParseException("Minus sign at the end of expression");
	    Token b = tokens.firstElement();
	    if (b.type==Token.Type.NUMBER) {
		tokens.remove(0);
		return new Num(-b.nVal);
	    } else {
		throw new  RuleParseException("Minus sign not followed by a number; instead, found " + b);
	    }		    
	} else if (a.type==Token.Type.OPEN && a.cVal=='(') {
	    tokens.remove(0);	    
	    ArithmeticExpression q = mkLongestArithmeticExpression(tokens);
	    if (tokens.size()==0) throw new  RuleParseException("Unexpected end of a parenthesized expression");
	    Token b = tokens.firstElement();
	    if (b.type==Token.Type.CLOSE && b.cVal==')') {
		tokens.remove(0);
		return q;
	    } else {
		throw new  RuleParseException("Unexpected end of a parenthesized expression: instead of a closing paren, found " + b);
	    }
	} else if (a.type==Token.Type.OPEN && a.cVal=='[') {
	    return mkBracketList(tokens);
	} else {
	    throw new RuleParseException("Expected primitive or (arithmetic) or [list], but instead found " +a);
	}
    }

    static BracketList mkBracketList(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected a bracket list [...]");
	Token a = tokens.firstElement();
	if (a.type==Token.Type.OPEN && a.cVal=='[') {
	    tokens.remove(0);
	    Vector<Expression> v = new Vector<>();
	    while(tokens.size()>0) {
		Expression z = mkLongestArithmeticExpression(tokens);
		v.add(z);
		if (tokens.size()==0) throw new  RuleParseException("Unexpected end of bracket list");
		Token b = tokens.firstElement();
		if (b.type==Token.Type.COMMA) {
		    tokens.remove(0);
		    continue;
		} else 	if (b.type==Token.Type.CLOSE && b.cVal==']') {
		    tokens.remove(0);
		    return new BracketList(v);
		} else {
		    throw new  RuleParseException("Expected ']', found "+b);
		}	
	    }
	    throw new  RuleParseException("Unexpected end of bracket list");
	} else {
	    throw new RuleParseException("Expected [list], but instead found " +a);
	}
    }


    /** Translates all elements of the set to the [0..NBU-1] range,
	as appropriate for bucket numbers
     */
    public static HashSet<Integer> moduloNB(Set<Integer> h0) {
	HashSet<Integer> h = new HashSet<>();
	for(int x: h0) {
	    x %= Episode.NBU;
	    if (x<0) x+=Episode.NBU;
	    h.add(x);
	}
	return h;
    }

    public static HashSet<Integer> moduloNB2(Set<Object> h0) {
	HashSet<Integer> h = new HashSet<>();
	for(Object _x: h0) {
	    if (!(_x instanceof Integer)) continue;
	    Integer x = (Integer)_x;
	    x %= Episode.NBU;
	    if (x<0) x+=Episode.NBU;
	    h.add(x);
	}
	return h;
    }

}
