package edu.wisc.game.parser;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.Util;

/** A token represents an element of the input text. Used in parsing rules. */
public class Token {
    
    public enum Type { NUMBER, ID, STRING, COMMA, MULT_OP, ADD_OP, OPEN, CLOSE, EQUAL};
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
	     ""+cVal;
    }   

    /** Init based on the first character */
    private Token(char c) throws RuleParseException {
	type =
	    Character.isDigit(c)? Type.NUMBER:
	    Character.isJavaIdentifierStart(c)? Type.ID:
	    c==','? Type.COMMA:
	    c=='+' || c=='-'? Type.ADD_OP:
	    c=='*' || c=='/' || c=='%' ? Type.MULT_OP:
	    c=='(' || c=='['? Type.OPEN:
	    c==')' || c==']'? Type.CLOSE:
	    c=='"' ? Type.STRING:
	    c=='=' ? Type.EQUAL:
	    null;
	if (type==null) throw new RuleParseException("Illegal character: " + c);
	cVal=c;
	sVal= (type==Type.STRING)? "" :  "" + c;
    }
    /** Sets other fields based on type and sVal */
    private void complete() {
	if (type==Type.NUMBER) {
	    nVal = Integer.parseInt(sVal);
	    cVal=0;
	} else if (type==Type.ID || type==Type.STRING) {
	    cVal=0;
	} else {
	    cVal=sVal.charAt(0);
	}
    }

    
    //--- Used by the tokenizer
    static class Tokenizer {
	final Vector<Token> result = new Vector<>();

	private Token currentToken=null;

	Tokenizer(String x) throws RuleParseException {
	    for(int i=0; i<x.length(); i++) {
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
		} else {
		    flush();
		}
	    }
	    if (Character.isWhitespace(c)) {
		flush();
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

	InputStream in = System.in;
	LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));
	String s;
	while((s=reader.readLine())!=null) {
	    Vector<Token> q= tokenize(s);
	    System.out.println(toString(q));

	    try {
		Expression ex = Expression.mkExpression(q);
		
		System.out.println("E=" + ex);
		
		if (ex instanceof Expression.ArithmeticExpression) {
		    Expression.ArithmeticExpression ae = (Expression.ArithmeticExpression)ex;
		    
		    System.out.println("Eval=" + ae.eval(h));
		}
	    } catch(RuleParseException ex) {
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }
 	    
	}
    }

      
    
}
