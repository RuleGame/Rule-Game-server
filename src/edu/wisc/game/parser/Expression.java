package edu.wisc.game.parser;

import java.io.*;
import java.util.*;
import java.text.*;

public interface Expression {

    public String toSrc();

    abstract static class ExList extends Vector<Expression> implements Expression {
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

    };

    static public class ParenList extends ExList {
 	ParenList( Vector<Expression> v) {
	    super(v);
	}
	public String toString() {
	    return toString('(', ')');
	}
 	public String toSrc() {
	    return toSrc('(', ')');
	}
    };
    
    static public class BracketList extends ExList implements ArithmeticExpression {
	//final boolean isArithmetic;
	
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
    };


    /** An arithmetic expression is composed of variables, constants,
	and arithmetic operations; parentheses can be used for
	ordering operations.  The value of the expression can be
	computed for any given set of variable values (unless a division
	by zero etc happens).
    */
    public static interface ArithmeticExpression extends Expression {
	/** Evaluates this expression for the given values of the variables
	    involved.
	    @param h The values of the variables 
	    @return the value of the expression, or null if the expression
	    uses a variable whose value is not in h
	*/
	//Integer eval(HashMap<String, Integer> h);
	/** Can be used when the arguments can have multiple values.
	    @param h The hash map that contains for each variable the possible
	    set of its values.
	    @return the set of the possible values of the expression, or an empty set if the expression  uses a variable whose value is not in h
	 */
	HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> h);
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
	//public Integer eval(HashMap<String, Integer> h) {
	//    return nVal;
	//}
	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> h) {
	    HashSet<Integer> hr=new HashSet<>();
	    hr.add(nVal);
	    return hr;
	}
	public String toString() {
	    return "" + nVal;
	}
	public String toSrc() { return toString(); }
	public HashSet<String> listAllVars() { return new HashSet<String>(); }
    }

    /** A variable. In GS 2.0. a STRING is treated as a variable name as 
	well, in order to deal with quoted shape names such as "au/kangaroo".
     */
    public static class Id implements ArithmeticExpression  {
	final public String sVal;
	Id(Token t) throws RuleParseException {
	    if (t.type!=Token.Type.ID &&
		t.type!=Token.Type.STRING)
		throw new  RuleParseException("Not an id");
	    sVal = t.sVal;
	}
	//public Integer eval(HashMap<String, Integer> h) {
	//    return h.get(sVal);
	//}
	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> h) {
	    HashSet<Integer> q= h.get(sVal);
	    return q==null? new HashSet<Integer>() : q;
	}
	public String toString() {
	    return sVal;
	}
	public String toSrc() { return toString(); }
	public HashSet<String> listAllVars() {
	    HashSet<String> h = new HashSet<String>();
	    h.add(sVal);
	    return h;
	}
    }

    /** The operator is Token.EQQ */
    public static class EqualityExpressionOld implements ArithmeticExpression  {
	//    public static class EqualityExpression implements ArithmeticExpression  {
	ArithmeticExpression[] aa = new ArithmeticExpression[2];
	public String toString() {
	    Vector<String> v = new Vector<>();
	    v.add(aa[0].toString());
	    v.add(Token.EQQ.toString());
	    v.add(aa[1].toString());
	    String s = String.join(" ", v);
	    s= "("+ s + ")";
	    return s;   
	}

	//public Integer eval(HashMap<String, Integer> h) {
	//    Integer[] ss = { aa[0].eval(h), aa[1].eval(h)};
	//    boolean e = (ss[0]!=null && ss[1]!=null && ss[0].equals(ss[1]));
	//    return e? 1 : 0;
	//}

	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> hh) {
	    HashSet<Integer> hs0 = aa[0].evalSet(hh), hs1=aa[1].evalSet(hh);

	    if (hs0.size()==0 || hs1.size()==0) return hs0;	

	    HashSet<Integer> hr = new HashSet<>();
	    
	    for(Integer s: hs0) {
		for(Integer q: hs1) {
		    boolean e = (s!=null && q!=null && s.equals(q));
		    //if (e) hr.add(1);
		    hr.add(e? 1: 0);
		}
	    }
	    return hr;
	}
	
	
	public String toSrc() {
	    Vector<String> v = new Vector<>();
	    v.add(aa[0].toSrc());
	    v.add("==");
	    v.add(aa[1].toSrc());
	    String s = String.join(" ", v);
	    s= "("+ s + ")";
	    return s; 	    
	}
	public HashSet<String> listAllVars() {
	    HashSet<String> h = new HashSet<String>();
	    for(ArithmeticExpression a: aa) h.addAll( a.listAllVars());
	    return h;
	}
    }

    
    /** Additive or multiplicative */
    abstract static class  SerialExpression extends Vector<ArithmeticExpression>  	implements ArithmeticExpression {
	/** All operators are of the same type, either additive or multiplicative. this.size()==ops.size()+1 */
	Vector<Token> ops = new Vector<>();
	
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

	/*
	public Integer eval(HashMap<String, Integer> h) {
	    Integer s = firstElement().eval(h);
	    if (s==null) return null;
	    for(int j=1; j<size(); j++) {
		Integer q =get(j).eval(h);
		if (q==null) return null;
		Token op = ops.get(j-1);
		if (q.intValue()==0 && (op.cVal == '/' || op.cVal == '%')) {
		    return null; // no result from division by zero
		}
		if (op.cVal == '+') s += q;
		else if (op.cVal == '-') s -= q;
		else if (op.cVal == '*') s *= q;
		else if (op.cVal == '/') s /= q;
		else if (op.cVal == '%') s %= q;
		else throw new IllegalArgumentException("Illegal operation " + op + " in an additive or mutiplicative expression");
	    }
	    return s;
	}
	*/
       
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
			int r;
			if (q.intValue()==0 && (op.cVal == '/' || op.cVal == '%')) {
			    continue; // no result from division by zero
			}

			if (op.equals(Token.EQQ)) {
				if (s.equals(q)) r = 1;
				else continue;
			} else if (op.cVal == '+') r = s+q;
			else if (op.cVal == '-') r = s-q;
			else if (op.cVal == '*') r = s*q;
			else if (op.cVal == '/') r = s/q;
			else if (op.cVal == '%') r = s%q;
			else throw new IllegalArgumentException("Illegal operation " + op + " in an additive or multiplicative expression");
			hr.add(r);
		    }
		}
		hs = hr;
	    }
	    return hs;
	}


    }


    /** Has exactly two operands, and the operator is '==' */
    public static class EqualityExpression extends SerialExpression  {
	//ArithmeticExpression[] aa = new ArithmeticExpression[2];
	EqualityExpression(ArithmeticExpression a, ArithmeticExpression b) {
	    add(a);
	    add(b);
	    ops.add(Token.EQQ);
	}
	       

	//	public Integer eval(HashMap<String, Integer> h) {
	//	    Integer[] ss = { aa[0].eval(h), aa[1].eval(h)};
	//	    boolean e = (ss[0]!=null && ss[1]!=null && ss[0].equals(ss[1]));
	//	    return e? 1 : 0;
	//	}

	public HashSet<Integer> evalSet(HashMap<String, HashSet<Integer>> hh) {
	    HashSet<Integer> hs0 = get(0).evalSet(hh), hs1=get(1).evalSet(hh);

	    if (hs0.size()==0 || hs1.size()==0) return hs0;	

	    boolean e=false;
	    for(Integer s: hs0) {
		for(Integer q: hs1) {
		    e = (e || (s!=null && q!=null && s.equals(q)));
		}
	    }
	    HashSet<Integer> hr = new HashSet<>();
	    if (e) hr.add(1);
	    return hr;
	}
	
    }


    public static class AdditiveExpression extends  SerialExpression  {

	AdditiveExpression() {}
	AdditiveExpression(ArithmeticExpression x) {
	    add(x);
	}

	/** Wraps a given expression into an AdditiveExpression if needed */
	/*
	private static AdditiveExpression asAddi(ArithmeticExpression ex)  {
	    return (ex instanceof AdditiveExpression)?
		(AdditiveExpression)ex :
		new  AdditiveExpression(ex);
	}
	*/

	/** Checks if a given expression (which is assumed to be a HAE) is
	    followed by one or more additive terms, and puts 
	    them together in one Additive expression */
	/*
	static ArithmeticExpression expandAsAddi(ArithmeticExpression x, Vector<Token> tokens) throws RuleParseException {

	    while(tokens.size()>0 && tokens.firstElement().type==Token.Type.ADD_OP){
		AdditiveExpression y = asAddi(x);
		x = y;
		y.ops.add(tokens.remove(0));

		if (tokens.isEmpty()) throw new  RuleParseException("Unexpected end of additive expression");
		ArithmeticExpression b = mkHAE(tokens);
		y.add(b);
	    }
	    return x;
	}
	*/

    }

    public static class MultiplicativeExpression extends  SerialExpression  {
	MultiplicativeExpression() {}

	MultiplicativeExpression(ArithmeticExpression x) {
	    add(x);
	}

	/** Wraps a given expression into a MultiplicativeExpression if needed */
	/*
	private static MultiplicativeExpression asMulti(ArithmeticExpression ex)  {
	    return (ex instanceof MultiplicativeExpression)?
		(MultiplicativeExpression)ex :
		new  MultiplicativeExpression(ex);
	}
	*/

	/** Checks if a given expression (which is assumed to be a HAE) is
	    followed by one or more multiplicative terms, and puts 
	    them together in one Multiplicative expression */
	/*
	static ArithmeticExpression expandAsMulti(ArithmeticExpression x, Vector<Token> tokens) throws RuleParseException {
	    while(tokens.size()>0 && tokens.firstElement().type==Token.Type.MULT_OP) {
		MultiplicativeExpression y = asMulti(x);
		x = y;
		y.ops.add(tokens.remove(0));
		ArithmeticExpression b = getFirstHAE(tokens);
		y.add(b);
	    }
	    return x;
	}
	*/
	
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
    };

    final Star STAR = new Star();

    /** Extracts one of the sections of a rule line: either the leading
	counter (int or star), or one of the atoms (paren lists that
	may include arithmetic expressions or stars)
     */
    static Expression mkCounterOrAtom(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of expression");

	Token a = tokens.firstElement();

	if (a.equals(Token.STAR)) {
	    tokens.remove(0);
	    return STAR;
	} else if (a.type==Token.Type.NUMBER) {
	    tokens.remove(0);
	    return new Num(a);	    
	} else if (a.type==Token.Type.OPEN && a.cVal=='(') {
	    tokens.remove(0);
	    Vector<Expression> v = new Vector<>();
	    while(tokens.size()>0) {
		a = tokens.firstElement();
		Expression z;
		if (a.equals(Token.STAR)) {
		    tokens.remove(0);
		    z = new Star();
		} else {
		    z = mkLongestArithmeticExpression(tokens);
		}
		v.add(z);
		
		if (tokens.size()==0) throw new  RuleParseException("Unexpected end of a paren list expression");
		Token b = tokens.firstElement();
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

    /** Creates the longest ArithmeticExpression starting at the beginning of the tokens array. 
	<pre>
	E := E4
	E4 :=  E3  |  E3==E3
	E3 :=  E2  |  E2+E2+...
	E2 :=  E1  |  E1*E1...
	E1 :=  (E)  |  Id  | Num |  [E4,E4,...]
    */
    static ArithmeticExpression mkLongestArithmeticExpression(Vector<Token> tokens) throws RuleParseException {
	return mkLongestE4( tokens);
    }

    private static ArithmeticExpression mkLongestE4(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E4-type arithmetic expression)");
	ArithmeticExpression q = mkLongestE3(tokens);
	if (tokens.size()==0) return q;
	Token a = tokens.firstElement();
	if (!a.equals(Token.EQQ)) return q;
	tokens.remove(0);	    
	ArithmeticExpression q2 = mkLongestE3(tokens);
	return new EqualityExpression( q, q2);
    }

    private static ArithmeticExpression mkLongestE3(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E3-type arithmetic expression)");
	ArithmeticExpression q = mkLongestE2(tokens);
	AdditiveExpression y = new AdditiveExpression(q);
	while(tokens.size()>0 &&  tokens.firstElement().type==Token.Type.ADD_OP) {
	    y.ops.add(tokens.remove(0));
	    if (tokens.isEmpty()) throw new  RuleParseException("Unexpected end of additive expression");
	    ArithmeticExpression b =  mkLongestE2(tokens);
	    y.add(b);
	}
	return (y.size()>1) ? y : q;
    }

    private static ArithmeticExpression mkLongestE2(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E2-type arithmetic expression)");
	ArithmeticExpression q = mkLongestE1(tokens);
	MultiplicativeExpression y = new MultiplicativeExpression(q);
	while(tokens.size()>0 &&  tokens.firstElement().type==Token.Type.MULT_OP) {
	    y.ops.add(tokens.remove(0));
	    if (tokens.isEmpty()) throw new  RuleParseException("Unexpected end of additive expression");
	    ArithmeticExpression b =  mkLongestE1(tokens);
	    y.add(b);
	}
	return (y.size()>1) ? y : q;
    }
    
    /** 	E1 :=  (E)  |  Id  | Num |  [E4,E4,...] */ 
    static ArithmeticExpression mkLongestE1(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of line. (Expected an E1-type arithmetic expression)");
	Token a = tokens.firstElement();

	if (a.type == Token.Type.ID) {
	    tokens.remove(0);
	    return new Id(a);
	} else if (a.type == Token.Type.STRING) {
	    // quoted strings are allowed, as if they were IDs, to better handle the "shape" column
	    tokens.remove(0);
	    return new Id(a);
	} else if (a.type == Token.Type.NUMBER) {
	    tokens.remove(0);
	    return new Num(a);
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

    
    /** Creates the longest expression starting at the beginning of the tokens array. */
    /*
    static Expression oldMkExpression(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of expression");
	Token a = tokens.firstElement();

	if (a.type==Token.Type.MULT_OP && a.cVal=='*') {
	    tokens.remove(0);
	    return new Star();
	} else if (a.type==Token.Type.OPEN) {
	    Token open = a;
	    tokens.remove(0);
	    Vector<Expression> v = new Vector<>();
	    while(tokens.size()>0) {
		Expression z = mkExpression(tokens);
		v.add(z);
		if (tokens.size()==0) throw new  RuleParseException("Unexpected end of expression");
		Token b = tokens.firstElement();
		if (b.type==Token.Type.COMMA) {
		    tokens.remove(0);
		    continue;
		} else 	if (b.type==Token.Type.CLOSE) {
		    tokens.remove(0);
		    if (open.cVal=='(' && b.cVal==')') {
			if (v.size()==1 && v.get(0) instanceof ArithmeticExpression) {
			    // (E) : A single arithmetic expression surrounded by paren
			    return mkArithmeticExpression((ArithmeticExpression)v.get(0), tokens);
			} else {
			    // (E1,E2,...)
			    return new ParenList(v);
			}			    
		    } else if (open.cVal=='[' && b.cVal==']') {
			// [E1] or [E1,E2...]
			return new BracketList(v);
		    } else {
			throw new RuleParseException("Paren/bracket mismatch: " + open.cVal + "..." + b.cVal);
		    }
		}
	    }
	    throw new  RuleParseException("Missing closing paren/bracket: no match for " + open);
	} else {
	    return mkArithmeticExpression(tokens);
	}	
    }
    */
    
    //    private
    /*
	static ArithmeticExpression mkArithmeticExpression(Vector<Token> tokens) throws RuleParseException {
	ArithmeticExpression a = mkHAE(tokens);
	return mkArithmeticExpression(a, tokens);
    }
    */

    //private
    /*
	static ArithmeticExpression mkArithmeticExpression(ArithmeticExpression firstHAE, Vector<Token> tokens) throws RuleParseException {

	ArithmeticExpression w = MultiplicativeExpression.expandAsMulti(firstHAE, tokens);	// (firstHAE) * x
	return AdditiveExpression.expandAsAddi(w, tokens);
    }
    */

    /** Pulls in the longest "high-precedence expression", such as 
	a primitive, a multiplicative expression, or a parenthesized expression */
    //private
    /*
	static ArithmeticExpression mkHAE(Vector<Token> tokens) throws RuleParseException {
	ArithmeticExpression a = getFirstHAE(tokens);
	return MultiplicativeExpression.expandAsMulti(a, tokens);
    }
    */

    /** Pulls in the shortest "high-precedence expression" */
    //private
    /*
	static ArithmeticExpression getFirstHAE(Vector<Token> tokens) throws RuleParseException {
	if (tokens.size()==0) throw new RuleParseException("Unexpected end of expression");
	Token a = tokens.firstElement();
	Vector<Expression> v = new Vector<>();
	if (a.type == Token.Type.ID) {
	    tokens.remove(0);
	    return new Id(a);
	} else if (a.type == Token.Type.STRING) {
	    // in GS 2.0, quoted shapes ("au/crocodile") are allowed, and
	    // are intepreted as IDs.
	    tokens.remove(0);
	    return new Id(a);
	} else if (a.type == Token.Type.NUMBER) {
	    tokens.remove(0);
	    return new Num(a);
	} else if (a.type==Token.Type.OPEN && a.cVal=='(') {
	    Expression parList = mkExpression(tokens);
	    if (parList instanceof ArithmeticExpression) {
		return (ArithmeticExpression)parList;
	    } else if ((parList instanceof  ParenList) && ((ParenList)parList).size()==1) {
		Expression ex =  ((ParenList)parList).get(0);
		if (ex instanceof  ArithmeticExpression) {
		    return  (ArithmeticExpression)ex;
		} else {
		    throw new RuleParseException("Expected arithmetic expression, found: " + ex);
		}
	    } else {
		throw new RuleParseException("Expected (arithmetic), found: " +
					 parList.getClass() + ": " +
					 parList);
	    }
	} else {
	    throw new RuleParseException("Expected primitive or (arithmetic), but not found it");
	}
    }
    */    

}
