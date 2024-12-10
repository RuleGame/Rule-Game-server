package edu.wisc.game.tools.pooling;

import java.io.*;
import java.util.*;
import edu.wisc.game.util.*;


/** Maps "false.someThingXandY" to "X", etc. This is used in compact labeling
    of conditions.

    <p>
    The underlying HashMap may map "some_foo" to "F"; the method map1()
    will map "true.some_foo" to "F", and "false.some_foo" to "F". The method
    mapCond() may map "true.some_foo:false.other_bar" to "Fb".
  
*/
public class LabelMap extends HashMap<String, String> {

    /** @param s "outcome.ruleSetName", e.g. true.some_abe
	@param noPrefix If true, there is no "outcome.prefix". This is the case
	with the last element of the key (the target name) in the cross-target mode.
	@return e.g. 'A' or 'a', depending on the prefix. (Always uppercase if no prefix)
     */
    String map1(String s, boolean noPrefix) {
	String pt = "true.", pf = "false.";
	//	if (s.equals("")) {
	//  return "0";
	//	} else

	if (noPrefix) {
	    String key = s;
	    return get(key);
	} else	if (s.startsWith(pt)) {
	    String key = s.substring(pt.length());
	    if (get(key)==null) throw new IllegalArgumentException("No label registered for key="  + key);
	    return get(key);
	} else 	if (s.startsWith(pf)) {
	    String key = s.substring(pf.length());
	    if (get(key)==null) throw new IllegalArgumentException("No label registered for key="  + key);
	    return get(key).toLowerCase();
	} else  {
	    throw new IllegalArgumentException("Argument should beging with 'true.' or 'false.'. Instead, have '"  + s + "'");
	}
    }

    final static String SEP = ":";
    
    /**  Generates the label for a condition
	 @param cond E.g. "true.some_abe;false.other_bar", or empty string.
	@return e.g. "Ab". On an empty-string argument, "0" is returned.
    */
    String mapCond(String cond) {
	String s="";
	if (cond.length()==0) return "0";
	String[] v=cond.split(SEP);
	for(int j=0; j<v.length; j++) {
	    boolean noPrefix = (crossTarget && j==v.length-1);

	    //if (!v[j].startsWith("true.") && !v[j].startsWith("false.")) System.out.println("ct="+crossTarget+", cond="+cond+", cond["+j+"/"+v.length+"]=" + v[j]);
	    
	    s += map1(v[j], noPrefix);
	}
	return s;
    }

    /** Maps 'a' to "false.foo-a" or whatever it is.  */
    String letterToKey(char c, boolean noPrefix) {
	char uc = Character.toUpperCase(c);
	for(String x: keySet()) {
	    String a = get(x);
	    if (a.equals(""+uc)) {
		return (noPrefix) ? x:
		 (c==uc ? "true." : "false.") + x;
	    }
	}
	throw new IllegalArgumentException("No key maps to label '" + c + "'");
    }
    
    /** The inverse of  mapCond() */
    String labelToCond(String label) {
	if (label.equals("0")) return "";
	Vector<String> v = new Vector<>();
	for(int j=0; j<label.length(); j++) {
	    boolean noPrefix = crossTarget && j==label.length()-1;
	    v.add( letterToKey( label.charAt(j), noPrefix));
	}
	return String.join(SEP, v);
    }

    final boolean crossTarget;

    /** Creates a map that uniquely assigns a one-uppercase-letter key
	to each string from keys[]

       @param conditions Each array element is of the form "true.ruleA:false.ruleB:..." etc.


       @param _crossTarget If true, the last element of each key string is the target name, and contains no "true."/"false." prefix

       @parm f If not null, the CSV file with "#alphaLabel,ruleName" pairs. Labels from this file are used in preference to automatically generated

     */
    LabelMap(String[] keys, boolean _crossTarget, File f) throws IOException, IllegalInputException {


	if (f!=null) {
	    if (!f.exists()) throw new IOException("Label file does not exist: " + f);
	    CsvData  csv= new CsvData(f, false, false, new int[] {2});
	    for(CsvData.LineEntry _e: csv.entries) {
		CsvData.BasicLineEntry e = (CsvData.BasicLineEntry)_e;
		String letter = e.getKey();
		String rule = e.getCol(1);
		put(rule, letter);
	    }
	}
	
	crossTarget = _crossTarget;
	HashSet<String> h = new HashSet<>();
	for(String key: keys) {
	    for(String a: key.split(SEP)) {
		String b = a.replaceAll("^true.", "");
		b = b.replaceAll("^false.", "");
		h.add(b);
	    }
	}
	
	String[] z = h.toArray(new String[0]);
	String[] letters = assignLetters(z, new HashSet<String>(values()));
	for(int j=0; j<z.length; j++) {
	    if (containsKey(z[j])) continue; // maybe there is a value from file already
	    if (letters[j]==null) throw new AssertionError("Have not assigned a letter to key no. " + j);
	    put(z[j],letters[j]);
	}
    }

    
    /** Creates a more or less intelligent mapping from strings in z[]
	to uppercase alphabet letters
    */
    static private String[] assignLetters(String [] z, HashSet<String> usedLetters) {
	
	if (usedLetters==null) usedLetters = new HashSet<>();
	
	String[] w = new String[z.length];
	String[] letters = new String[z.length];
	if (z.length==0) return letters;
	

	int drop = z.length==1? 0: longestPrefix(z).length();
	for(int j=0; j<z.length; j++) {
	    String u = z[j].substring(drop).toUpperCase();
	    w[j] = stripPunctuation( u );
	    if (w[j].length()==0) w[j]=null;
	}


	
	// see if some of the first letters are unique
	while(true) {
	    HashMap<Character, Integer> fcnt = countFirstLetters(w);
	    //System.out.println("FL map=" + fcnt);
	    int newDone = 0, stripDone = 0;
	    for(int j=0; j<w.length; j++) {
		if (letters[j]!=null) continue;
		String q = w[j];
		if (q==null) continue;
		char x = q.charAt(0);
		int cnt = fcnt.get(x);
		if (cnt==1 && !usedLetters.contains(""+x)) {
		    letters[j] = ""+x;
		    usedLetters.add(""+x);
		    //System.out.println("Assigned unique letter[" + j +"]=" + letters[j] + " to key=" + w[j]);
		    w[j]=null;
		    newDone ++;
		} else {
		    stripDone++;
		    w[j] = 	stripPunctuation(w[j].substring(1));
		    if (w[j].length()==0) w[j]=null;
		}
	    }
	    if (newDone==0 && stripDone==0) break;
	}
	
	for(int j=0; j<w.length; j++) {
	    if (letters[j]!=null) continue;
	    for(char c = 'A'; c<='Z'; c++) {
		if (!usedLetters.contains(""+c)) {
		    letters[j] = ""+c;
		    usedLetters.add(""+c);
		    //System.out.println("Assigned letter[" + j +"]=" + letters[j] + " to key=" + w[j]);
		    w[j]=null;
		    break;
		}
	    }
	    if (letters[j]==null) throw new IllegalArgumentException("Ran out of alphabet letters for labels!");   
	}
	return letters;
	    
    }

    static private HashMap<Character, Integer> countFirstLetters(String [] w) {
	HashMap<Character, Integer> h = new HashMap<>();
	for(String q: w) {
	    if (q==null || q.length()==0) continue;
	    char x = q.charAt(0);
	    int n = (h.get(x)==null)? 0: h.get(x);
	    h.put(x, n+1);
	}
	return h;
    }
	
    /** Removes non-letter characters from the beginning of a given string
	@param s A string that has already been converted to upper case.
    */
    static private String stripPunctuation(String s) {
	while(s.length()>0 && !Character.isUpperCase(s.charAt(0))) {
	    s = s.substring(1);
	}
	return s;
    }


    static private String longestPrefix(String [] z) {
	if (z.length==0) throw new IllegalArgumentException("Empty array");
	String p = z[0];
	for(String q: z) {
	    if (q.startsWith(p)) continue;
	    int j=0;
	    while(j<q.length() && j<p.length() &&
		  q.charAt(j)==p.charAt(j)) {
		j++;
	    }
	    p = p.substring(0,j);
	}
	return p;
    }

    public static void main(String[] argv) throws Exception {

	String labelFile = null;

	int j=0;
	for(; j<argv.length; j++) {
	    String a = argv[j];	    
	    if  (j+1< argv.length && a.equals("-labels")) {
		labelFile = argv[++j];
	    } else break;
	}

	System.out.println("labelFile=" +labelFile + ", j=" +j);
	
	/*
	Vector<String>v = new Vector<>();
	for(; j<argv.length; j++) {
	    String a = argv[j];
	    v.add(a);
	    
	    }
v.toArray(new String[0])
	*/
	String a[] = Arrays.copyOfRange(argv, j, argv.length);	
	File lf = (labelFile!=null)? new File(labelFile): null;

	System.out.println("a=" + Util.joinNonBlank(";", a));
	
	LabelMap lam = new LabelMap(a, false, lf);
	for(String key: lam.keySet()) {
	    System.out.println( "Label(" + key+")='" + lam.get(key)  + "'");
	}

	for(String cond: a) {
	    System.out.println( lam.mapCond(cond) + " --> " + cond);
	}
    }

}

