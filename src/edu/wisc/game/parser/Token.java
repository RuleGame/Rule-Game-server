package edu.wisc.game.parser;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.Util;

/** A token represents an element of the input text. Used in parsing rules. */
public class Token {
    
    public enum Type { NUMBER, ID, STRING, COMMA,
		       MULT_OP, ADD_OP,
		       CMP /* == , <=, <, >, >=  */,
		       UNARY_OP, OPEN, CLOSE, EQUAL /* = */, COLON, DOT, DOTDOT};
    public final Type type;
    public char cVal=0;
    public String sVal=null;
    public int nVal=0;

    /** @return The string value if it is an ID, or null otherwise */
    public String asId() {
	return type==Type.ID ? sVal : null;
    }

    public boolean equals(Object o) {
	if (!(o instanceof Token)) return false;
	Token t = (Token)o;
	return type == t.type && cVal == t.cVal && nVal == t.nVal &&
	    Util.same(sVal, t.sVal);
	
    }

    public String toString() {
	return  "" + type + "." + toSrc();
    }

    public String toSrc() {
	 return
	     (type == Type.NUMBER) ? ""+nVal:
	     (type == Type.ID) ? sVal:
	     (type == Type.CMP) ? sVal:
	     (type == Type.STRING) ? '"' + sVal + '"':
	     ""+cVal;
    }   

    /** Re-imagines the token as an element of a command line. Used 
	for compatibility purposes in the Captive Game Server. */
    public String toArgv() {
	 return
	     (type == Type.NUMBER) ? ""+nVal:
	     (type == Type.ID) ? sVal:
	     (type == Type.STRING) ? sVal:
	     (type == Type.CMP) ? sVal:
	     ""+cVal;
    }   

    /** Only used to create a few constants such as Token.EQQ and
	Token.DOTDOT. This constructor should only be used once for
	each type, so that the resulting objects can be compared with
	the '==' operator.
	@param _sVal Something like  "==", "<=", ">=", ">", "<" , etc
     */
    private Token(Type _type, String _sVal) { //throws RuleParseException {
	type = _type;
	sVal = _sVal;
	if (sVal.length()==1) cVal=sVal.charAt(0);
    }


    /** Init based on the first character */
    private static Token mkToken(char c) throws RuleParseException {
	Token t =
	    (c== '=')? EQUAL:
	    (c== '<')? LT:
	    (c== '>')? GT:
	    (c== '!')? BANG:
	    (c== '*')? STAR:
	    (c== ',')? COMMA:
	    null;
	if (t!=null) return t;
	
	Token.Type type =
	    Character.isDigit(c)? Type.NUMBER:
	    Character.isJavaIdentifierStart(c)? Type.ID:
	    c==':'? Type.COLON:
	    c=='.'? Type.DOT:
	    c=='+' || c=='-'? Type.ADD_OP:
	    c=='*' || c=='/' || c=='%' ? Type.MULT_OP:
	    c=='(' || c=='['? Type.OPEN:
	    c==')' || c==']'? Type.CLOSE:
	    c=='"' ? Type.STRING:
	    null;
	if (type==null) throw new RuleParseException("Illegal character: "+ c);
	
	t = new Token(type,  (type==Type.STRING)? "" :  "" + c);
	t.cVal = c; // this will be corrected in t.complete()
	return t;
    }

    private static Token wrapToken(char c) {
	try {
	    return mkToken(c);
	} catch(Exception ex) { return null; }
    }
    
    static final Token EQUAL = new Token(Type.EQUAL, "=");
    static final Token EQQ = new Token(Type.CMP, "==");
    static final Token LT = new Token(Type.CMP, "<");
    static final Token LE = new Token(Type.CMP, "<=");
    static final Token GT = new Token(Type.CMP, ">");
    static final Token GE = new Token(Type.CMP, ">=");
    static final Token NE = new Token(Type.CMP, "!=");
    static final Token DOTDOT = new Token(Type.DOTDOT, "..");
    static final Token BANG = new Token(Type.UNARY_OP, "!"); 
    static final Token STAR = new Token(Type.MULT_OP, "*");
    static final Token COMMA = new Token(Type.COMMA, ",");

    
    /** Sets other fields based on type and sVal */
    private void complete() {
	if (type==Type.NUMBER) {
	    nVal = Integer.parseInt(sVal);
	    cVal=0;
	} else if (type==Type.ID || type==Type.STRING
		   || type==Type.CMP || type==Type.DOTDOT ) {
	    cVal=0;
	} else {
	    cVal=sVal.charAt(0);
	}
    }

    
    //--- Used by the tokenizer
    private static class Tokenizer {
	private final Vector<Token> result = new Vector<>();

	private Token currentToken=null;

	/** Tokenizes a line of text */
	Tokenizer(String x) throws RuleParseException {
	    for(int i=0; i<x.length() && !commentHasStarted; i++) {		
		addC(x.charAt(i));
	    }
	    flush();
	}
	
	private  void  flush() {
	    if (currentToken==null) return;	
	    currentToken.complete();
	    result.add(currentToken);
	    currentToken = null;	
	}

	private boolean commentHasStarted = false;
	
	private void  addC(char c) throws RuleParseException {
	    if (currentToken!=null) {
		if (Character.isDigit(c) && currentToken.type==Type.NUMBER ||
		    Character.isJavaIdentifierPart(c) && currentToken.type==Type.ID) {
		    currentToken.sVal += c;
		    return;
		} else if (currentToken.type==Type.STRING) {
		    if (c=='"')  {
			flush();
			return;
		    }  else {
			currentToken.sVal += c;
			return;
		    }
		} else if (currentToken==EQUAL && c=='=') {
		    // Instead of EQUAL '=' it is now EQQ '=='		    
		    currentToken=EQQ;
		    flush();
		    return;
		} else if (currentToken==LT && c=='=') {
		    // Instead of LT '<' it is now LE '<='		    
		    currentToken=LE;
		    flush();
		    return;
		} else if (currentToken==GT && c=='=') {
		    // Instead of GT '>' it is now GE '>='		    
		    currentToken=GE;
		    flush();
		    return;
		} else if (currentToken==BANG && c=='=') {
		    // Instead of BANG '!' it is now NE '!='		    
		    currentToken=NE;
		    flush();
		    return;
		} else if (currentToken.type==Type.DOT && c=='.') {
		    // Instead of DOT '.' it is now DOTDOT '..'		    
		    currentToken=DOTDOT;
		    flush();
		    return;
		} else {
		    flush();
		}
	    }
	    if (Character.isWhitespace(c)) {
		flush();
		return;
	    } else if (c=='#') { // comment ends the line
		commentHasStarted = true;
		return;
	    }
	    currentToken= mkToken(c);	
	}
    }
	
    public static Vector<Token> tokenize(String x) throws RuleParseException {
	Tokenizer t = new Tokenizer(x);
	return t.result;
	//return (Token[])t.result.toArray(new Token[0]);
    }

    static public String toString(Vector<Token> tokens) {
	Vector<String> v = new Vector<>();
	for(Token t: tokens) {
	    v.add(t.toString());
	}
	return String.join(" ", v);
   }

    public boolean isOpenParen() {
	return type==Token.Type.OPEN && cVal=='(';
    }

    
    public static void main(String[] argv) throws IOException,  RuleParseException {

	HashMap<String, Integer> h = new HashMap<>();
	h.put("one", 1);
	h.put("two", 2);
    	h.put("three", 3);
	h.put("four", 4);
	HashMap<String, HashSet<Integer>> hh = new HashMap<>();
	Expression.VarMap2 hh2 = new Expression.VarMap2();
	for(String key: h.keySet()) {
	    HashSet<Integer> z = new HashSet<>();
	    z.add(h.get(key));
	    hh.put(key, z);


	    HashSet<Object> z2 = new HashSet<>();
	    z2.add(h.get(key));
	    hh2.put(key, z2);

	}

	InputStream in = System.in;
	LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));
	String s;
	while((s=reader.readLine())!=null) {
	    Vector<Token> q= tokenize(s);
	    System.out.println(toString(q));

	    try {
		//Expression ex = Expression.mkExpression(q);
		Expression ex = Expression.mkLongestArithmeticExpression(q);
		     
		System.out.println("E=" + ex);
		System.out.println("Class=" + ex.getClass());
		
		if (ex instanceof Expression.ArithmeticExpression) {
		    Expression.ArithmeticExpression ae = (Expression.ArithmeticExpression)ex;
		    //HashSet<Integer> hv = ae.evalSet(hh);
		    HashSet<Object> hv = ae.evalSet2(hh2);
		    System.out.print("Eval=[" + Util.joinNonBlank(", ",hv) +
				     "]");
		    //for(Object x: hv) System.out.print(" " + x);
		    System.out.println();
		} else {
		    System.out.println("Not an arithmetic expression");
		}
	    } catch(RuleParseException ex) {
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }
 	    
	}
    }

      
    
}
