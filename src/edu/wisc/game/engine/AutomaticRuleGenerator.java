package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.json.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.reflect.*;
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

	Expression pickOne(boolean preferNum) throws RuleParseException {
	    if (preferNum) {
		if (nVal!=null) return new Expression.Num( pickInt());
		else if (sVal!=null) return new Expression.Id( pickId());
	    } else {
		if (sVal!=null) return new Expression.Id( pickId());
		else if (nVal!=null) return new Expression.Num( pickInt());
	    }
	    
	    throw new RuleParseException("No data");
	}
	    
	
    }


    /** Cache for processed param values */
    private HashMap<String,TableValue> h = new HashMap<>();

    /** Looks up and processes a parameter value */
    TableValue getValue(String x) throws RuleParseException {
	try {
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
	} catch (RuleParseException ex) {
	    System.err.println("Error while getting param value for key=" + x);
	    throw ex;
	}
    }

    /** Ensures that the para table has the default (legacy) values for 
	"colors" and "shapes", if none are supplied in the CSV file.
    */
    public AutomaticRuleGenerator(Long seed, ParaSet _para)  throws RuleParseException{
	para = _para;
	random = (seed==null)? new RandomRG() :new RandomRG(seed);
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
	@param domain The prefix used in the property-based syntax for rules, such as "count", "shape", "color", "pos", "bucket", or a custom property name
	@return an Expression (numeric or Id; different preferences for different domains)
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

	
	boolean preferNum = domain.equals("count")||domain.equals("bucket");
	return q.pickOne(preferNum);

	/**
	if (domain.equals("count")) {
	    return new Expression.Num( q.pickInt());
	} else if (domain.equals("color") ||
		   domain.equals("shape") ||
		   domain.equals("pos")) { // symbolic
	    return  new Expression.Id( q.pickId());
	} else if (domain.equals("bucket")) { // numeric
	    return  new Expression.Num( q.pickInt());
	} else { // have to guess
	    return  q.pickOne(preferNum);
	}
	*/
		   
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

    
    private final RandomRG random;
    private static boolean verbose=false;
	    
    public static void main(String[] argv) throws IOException,  IllegalInputException, RuleParseException {

	String outPath = "tmp";
	Long seed = null;
	
	Vector<String> va = new Vector<String>();
	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];
	    if (a.equals("-verbose")) {
		verbose = true;
	    } else if (a.equals("-seed") && j+1<argv.length) {
		seed = new Long(argv[++j]);
	    } else if (a.equals("-out") && j+1<argv.length) {
		outPath= argv[++j];
	    } else {
		va.add(a);
	    }
	}

	argv = va.toArray(new String[0]);

	
	if (argv.length!=3) usage();
	String paraPath = argv[0];
	String rulePath = argv[1];
	int n = Integer.parseInt(argv[2]);


	File base = new File(outPath);
	if (!base.exists()) {
	    if (!base.mkdirs()) usage("Cannot create output directory: " + base);
	}
	if (!base.isDirectory() || !base.canWrite()) usage("Not a writeable directory: " + base);
	System.out.println("Output directory: " +base);

	
	System.out.println("Para file " +paraPath );

       
	ParaSet para = new ParaSet(new File(paraPath));
	
	AutomaticRuleGenerator ag = new  AutomaticRuleGenerator(seed, para);


	System.out.println("Variable substitutions are drawn from the following sets:");
	for(String  s: ag.varReport()) {
	    System.out.println(s);
	}

	System.out.println("Reading file " + rulePath);
	File f = new File(rulePath);
	String text = Util.readTextFile(f);

	try {
	    System.out.println("--- The template ----");
	    RuleSet rules0 = new RuleSet(text);
	    System.out.println(rules0.toSrc());
	} catch (Exception ex) {}

	int outCnt = 0;
	NumberFormat nfmt = new DecimalFormat("000");
	
	
	for(int j=0; j<n*10; j++) {
	    System.out.println("--- Auto rule set No. " + j + " -----------------");
	    RuleSet rules = new RuleSet(text, ag);
	    System.out.println(rules.toSrc());

	    StalemateTester tester = new  StalemateTester(rules);
	    Board stalemated = tester.canStalemate( Piece.Shape.legacyShapes,
						    Piece.Color.legacyColors,
						    null);
	    if (stalemated!=null) {
		System.out.println("The rule set above can stalemate");

		JsonObject jo = JsonReflect.reflectToJSONObject(stalemated, true);

		
		System.out.println("Sample stalemate board: " + jo);
		
	    } else {

		String s = f.getName().replaceAll("\\.txt$","") + "." + nfmt.format(outCnt) + ".txt";
		File g = new File(base,s);
		System.out.println("No stalemate; saving file to " + g);		
		PrintWriter w = new PrintWriter(new FileWriter(g));
		w.println(rules.toSrc());
		w.close();
		
		
		outCnt++;
		
	    }
	    if (outCnt==n) break;
	    
	}
	if (outCnt<n) System.out.println("Could not generate all " + n+" requested rule sets; only produced " + outCnt);
	else  System.out.println("Saved " + outCnt + " rule sets to files in " + base);

    }

    public Vector<String> varReport(//ParaSet para
)throws  RuleParseException {
	Vector<String> v = new Vector<>();
	HashSet<String> w = new HashSet<>();
	for(String key: h.keySet()) {
	    v.add(key + " = " + getValue(key));
	    w.add(key);
	}
	//	System.out.println(".... wait, there is more ....");
	for(String key: para.keySet()) {
	    if (key.startsWith("err")) continue;
	    if (w.contains(key)) continue;	    
	    v.add(key + " = " + getValue(key));
	    w.add(key);	    
	}
	return v;
    }


    
}
