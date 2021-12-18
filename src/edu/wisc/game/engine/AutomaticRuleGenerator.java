package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.rest.ParaSet;

/** An AutomaticRuleGenerator is used to create multiple rule set
    files based on the same pattern.


*/
public class AutomaticRuleGenerator {

    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("Usage: AutomaticRuleGenerator paraFile ruleFile n");
		System.err.println("For usage info, please see:\n");
	//	System.err.println("http://sapir.psych.wisc.edu:7150/w2020/analyze-transcripts.html");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }
    

    /** This is not a normal para set, but a config file with the
	value sets for X-variables. The following line formats are allowed:

<pre>
color,"(Red,Pink,Orange)"
shape,"(Cross,Arrow)"
X,"(T,B,L,R)"
Xc,1
Xc,"(1,2,5)"
Xc,[1..3]
</pre>
     */
    private ParaSet para;

    /** Represents one value from the para set */
    class TableValue {
	String[] sVal;
	int[] nVal;
	TableValue(Number o) throws RuleParseException {
	    if (o instanceof Integer) {
		nVal = new int[] {((Integer)o).intValue()};
	    } else if (o instanceof Double) {
		// FIXME: imprecise, but OK for our case
		nVal = new int[] {(int)((Double)o).doubleValue()};
		//	    } else {
		//		sVal = new String[] { o.toString(); }
	    } else {
		throw new RuleParseException("Wrong data type: " + o.getClass());
	    }
		       
	}

	/** Initializes from a pre-parsed list of strings */
	TableValue(Vector<String> v) throws RuleParseException {
	    if (v.size()==0)  throw new RuleParseException ("Empty list");
	    sVal = v.toArray(new String[0]);
	}
	TableValue(int[] _nVal) throws RuleParseException {
	    nVal = _nVal;
	}

	/** Parses a string as a list or range, and Initializes accordingly */
	TableValue(String s) throws RuleParseException {
	    s = s.replaceAll(";", ",");
	    s = s.replaceAll(":", "..");
	    Vector<Token> tokens= Token.tokenize(s);
	    if (tokens.size()==0) throw new RuleParseException ("Empty value");


	    //System.out.println("Processing s=" + s +"; t0=" + tokens.get(0));
	    
	    // [n1..n2]
	    Expression.RangeExpression re = Expression.mkRangeExpression(tokens);
	    if (re!=null) {
		if (tokens.size()>0) throw new RuleParseException ("Extraneous stuff after [...] in: " + s);
		int n0 = re.a0.nVal, n1 = re.a1.nVal;
		nVal=new int[n1-n0+1];
		for(int j=0; j<nVal.length; j++) nVal[j] = n0+j;
		return;
	    }

	    // (a1,a2,...)
	    if (tokens.get(0).isOpenParen()) {
		Expression e = Expression.mkCounterOrAtom(tokens);
		//System.out.println("Parsed as e=" + e);

		if (!(e instanceof Expression.ParenList))  throw new RuleParseException ("Cannot parse paren list in: " + s);
		if (tokens.size()>0) throw new RuleParseException ("Extraneous stuff after (...) in: " + s);
		Expression.ParenList ple = (Expression.ParenList)e;
		if (ple.size()==0)  throw new RuleParseException ("Empty list");
		boolean allNum = true;
		for(Expression u: ple) {
		    allNum = (allNum &&  (u instanceof Expression.Num));
		}
		if (allNum) {
		    nVal = new int[ple.size()];
		    for(int j=0; j<nVal.length; j++) {
			nVal[j] = ((Expression.Num)ple.get(j)).nVal;
		    }
		} else {
		    sVal = new String[ple.size()];
		    for(int j=0; j<sVal.length; j++) {
			if (ple.get(j) instanceof Expression.Id) {
			    sVal[j] = ((Expression.Id)ple.get(j)).sVal;
			} else  throw new RuleParseException ("Paren expression too complicated: " + s);
		    }
		}
		return;
	    }

	    // n  or var
	    if (tokens.size()==1) {
		Token t = tokens.get(0);
		if (t.type==Token.Type.NUMBER) {
		    nVal = new int[] { t.nVal };
		    return;
		} else if (t.type==Token.Type.ID) {
		    sVal = new String[] { t.sVal };
		    return;
		}
	    }
	    throw new RuleParseException ("Cannot parse the expression: "  + s);		    
	}


	public String toString() {
	    if (nVal!=null) return "(" + Util.joinNonBlank(",", nVal) + ")";
	    else if (sVal!=null) return "(" + Util.joinNonBlank(",", sVal) + ")";
	    else return "No data!";
	}
	
	int pickInt() throws RuleParseException {
	    if (nVal==null) throw new RuleParseException ("Cannot convert table value to int: " + this);
	    return nVal[ random.nextInt( nVal.length) ];
	}

	String pickId() throws RuleParseException {
	    if (sVal==null) throw new RuleParseException ("Cannot convert table value to string: " + this);
	    return sVal[ random.nextInt( sVal.length) ];
	}

	Expression pickOne() throws RuleParseException {
	    if (nVal==null) return new Expression.Num( pickInt());
	    else if (sVal==null) return new Expression.Id( pickId());
	    else throw new RuleParseException("No data");
	    //return null;
	}
	    
	
    }


    /** Cache for processed param values */
    private HashMap<String,TableValue> h = new HashMap<>();

    /** Looks up and processes a parameter value */
    TableValue getValue(String x) throws RuleParseException {
	TableValue q = h.get(x);
	if (q!=null) return q;
	Object o = para.get(x);
	if (o==null) return null;
	if (o instanceof Number) {
	    q = new TableValue((Number)o);
	} else 	if (o instanceof String) {
	    q = new TableValue((String)o);
	}  else {
	    throw new RuleParseException("Wrong data type: " + o.getClass());
	}
	h.put(x,q);
	return q;
    }

    /** Ensures that the para table has the default (legacy) values for 
	"colors" and "shapes", if none are supplied in the CSV file.
    */
    AutomaticRuleGenerator(ParaSet _para)  throws RuleParseException{
	para = _para;
	if (getValue("color")==null) {
	    Vector<String> v = new Vector<>();
	    for(Piece.Color w: Piece.Color.legacyColors) v.add(w.toString());
	    h.put("color", new TableValue(v));
	} 
	if (getValue("shape")==null) {
	    Vector<String> v = new Vector<>();
	    for(Piece.Shape w: Piece.Shape.legacyShapes) v.add(w.toString());
	    h.put("shape", new TableValue(v));
	}
	
	if (getValue("pos")==null) {
	    Vector<String> v = new Vector<>();
	    for(Order.PositionSelector w: Order.PositionSelector.class.getEnumConstants()) {
		v.add(w.toString());
	    }
	    h.put("pos", new TableValue(v));	
	}
	if (getValue("bucket")==null) {
	    int[] v = new int[Episode.NBU];
	    for(int j=0; j<v.length; j++) v[j]=j;
	    h.put("bucket", new TableValue(v));	
	}
	
   }
    
    /** Does this expression look like the name of an expandable variable?
     */
    boolean recognize(Expression g) {
	return (g instanceof Expression.Id && ((Expression.Id)g).sVal.startsWith("X"));
    }

    /** Replaces an id such as "X" with a random value drawn
	from an approriate set.
	@param e Expression to convert. If null is given, null is returned.
	@param domain The prefix used in the property-based syntax for rules, such as "count", "shape", "color", "pos", "bucket" etc
    */
    Expression interpret(Expression e, String domain) throws RuleParseException {
	//System.out.println("-- Interpreting '"+domain+"' on " + e);
	if (e==null) return e;
	if (!recognize(e)) return e;//throw new RuleParseException("This is not an auto-replaceable var: " + e);


	//	System.out.println("Substituting " +domain + ":" + e.toSrc());
	
	String x =  ((Expression.Id)e).sVal;
	TableValue q = getValue(x);
	// If no value for "X", then look for the value for "color" etc
	if (q==null) q = getValue(domain);  

	if (q==null) throw new RuleParseException("Var " + x + " for domain '"+domain+ "' has not been given a value set or range");
	
	if (domain.equals("count")) {
	    return new Expression.Num( q.pickInt());
	} else if (domain.equals("color") ||
		   domain.equals("shape") ||
		   domain.equals("pos")) { // symbolic
	    return  new Expression.Id( q.pickId());
	} else if (domain.equals("bucket")) { // numeric
	    return  new Expression.Num( q.pickInt());
	} else { // have to guess
	    return  q.pickOne();
	}
		   
    }


    class AuMapper implements Expression.Mapper  {
	final String domain;
	AuMapper(String _domain) {
	    domain = _domain;
	}
    
	/** For Expression.Mapper */
	public Expression apply(Expression e) throws RuleParseException {
	    //System.out.println("Applying '"+domain+"' to " + e);
	    if (recognize(e)) {
		return interpret(e,domain);
	    } else return e;
	}
    }


    AuMapper mkMapper(String domain) {
	return new AuMapper( domain);
    }

    

   private static RandomRG random;
	
    public static void main(String[] argv) throws IOException,  RuleParseException {
	random = new RandomRG();

	if (argv.length!=3) usage();
	String paraPath = argv[0];
	String rulePath = argv[1];
	int n = Integer.parseInt(argv[2]);
	System.out.println("Para file " +paraPath );
	ParaSet para = new ParaSet(new File(paraPath));
	
	AutomaticRuleGenerator ag = new  AutomaticRuleGenerator(para);


	System.out.println("Variable substitutions are drawn from the following sets:");
	for(String key: ag.h.keySet()) {
	    System.out.println(key + " = " + ag.getValue(key));
	}
	System.out.println(".... wait, there is more ....");
	for(String key: para.keySet()) {
	    if (key.startsWith("err")) continue;
	    System.out.println(key + " = " + ag.getValue(key));
	}	


	System.out.println("Reading file " + rulePath);
	String text = Util.readTextFile(new File(rulePath));

	try {
	    System.out.println("--- The template ----");
	    RuleSet rules0 = new RuleSet(text);
	    System.out.println(rules0.toSrc());
	} catch (Exception ex) {}

	for(int j=0; j<n; j++) {
	    System.out.println("--- Auto rule set No. " + j + " -----------------");
	    RuleSet rules = new RuleSet(text, ag);
	    System.out.println(rules.toSrc());
	}

    }
    
}
