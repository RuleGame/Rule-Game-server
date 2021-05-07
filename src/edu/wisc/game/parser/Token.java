package edu.wisc.game.parser;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.Util;

/** A token represents an element of the input text. Used in parsing rules. */
public class Token {
    
    //static void foo1(){};

    public enum Type { NUMBER, ID, STRING, COMMA, MULT_OP, ADD_OP, EQQ /* == */, UNARY_OP, OPEN, CLOSE, EQUAL /* = */, COLON, DOT, DOTDOT};
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
	     (type == Type.EQQ) ? sVal:
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
	     (type == Type.EQQ) ? sVal:
	     ""+cVal;
    }   

    /** Init based on the first character */
    private Token(char c) throws RuleParseException {
	type =
	    Character.isDigit(c)? Type.NUMBER:
	    Character.isJavaIdentifierStart(c)? Type.ID:
	    c==','? Type.COMMA:
	    c==':'? Type.COLON:
	    c=='.'? Type.DOT:
	    c=='+' || c=='-'? Type.ADD_OP:
	    c=='*' || c=='/' || c=='%' ? Type.MULT_OP:
	    c=='!' ? Type.UNARY_OP:
	    c=='(' || c=='['? Type.OPEN:
	    c==')' || c==']'? Type.CLOSE:
	    c=='"' ? Type.STRING:
	    c=='=' ? Type.EQUAL:
	    null;
	if (type==null) throw new RuleParseException("Illegal character: " + c);
	cVal=c;
	sVal= (type==Type.STRING)? "" :  "" + c;
    }

    /** Only used for Type.EQQ */
    private Token(Type _type, String _sVal) { //throws RuleParseException {
	type = _type;
	sVal = _sVal;
    }


    private static Token wrapToken(char c) {
	try {
	    return new Token(c);
	} catch(Exception ex) { return null; }
    }
    
    static final Token EQQ = new Token(Type.EQQ, "==");
    static final Token DOTDOT = new Token(Type.DOTDOT, "..");
    static final Token BANG = wrapToken('!');
    static final Token STAR = wrapToken('*');
    
    /** Sets other fields based on type and sVal */
    private void complete() {
	if (type==Type.NUMBER) {
	    nVal = Integer.parseInt(sVal);
	    cVal=0;
	} else if (type==Type.ID || type==Type.STRING
		   || type==Type.EQQ || type==Type.DOTDOT ) {
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
		} else if (currentToken.type==Type.EQUAL && c=='=') {
		    // Instead of EQUAL '=' it is now EQQ '=='		    
		    currentToken=EQQ;
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
	    currentToken= new Token(c);	
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

    public static void main(String[] argv) throws IOException,  RuleParseException {

	HashMap<String, Integer> h = new HashMap<>();
	h.put("one", 1);
	h.put("two", 2);
    	h.put("three", 3);
	h.put("four", 4);
	HashMap<String, HashSet<Integer>> hh = new HashMap<>();
	for(String key: h.keySet()) {
	    HashSet<Integer> z = new HashSet<>();
	    z.add(h.get(key));
	    hh.put(key, z);
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
		    HashSet<Integer> hv = ae.evalSet(hh);
		    System.out.print("Eval=");
		    for(Integer x: hv) System.out.print(" " + x);
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
