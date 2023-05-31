package edu.wisc.game.tools.pooling;


//import java.io.*;
import java.util.*;
//import java.util.regex.*;
//import java.util.stream.*;
//import java.text.*;


//import edu.wisc.game.util.*;


/** Maps false.someThingXandY to X, etc. This is used in compact labeling
    of conditions.

    The underlying HashMap may map "some_foo" to "F"; the method map1()
    will map "true.some_foo" to "F", and "false.some_foo" to "F". The method
    mapCond() may map "true.some_foo:false.other_bar" to "Fb".
  
*/
public class LabelMap extends HashMap<String, Character> {

    /** @param s "outcome.ruleSetName", e.g. true.some_abe
	@return e.g. 'A'
     */
    char map1(String s) {
	String pt = "true.", pf = "false.";
	//	if (s.equals("")) {
	//  return "0";
	//	} else

	    if (s.startsWith(pt)) {
	    String key = s.substring(pt.length());
	    if (get(key)==null) throw new IllegalArgumentException("No label registered for key="  + key);
	    return get(key);
	} else 	if (s.startsWith(pf)) {
	    String key = s.substring(pf.length());
	    if (get(key)==null) throw new IllegalArgumentException("No label registered for key="  + key);
	    return Character.toLowerCase(get(key));
	} else  {
	    throw new IllegalArgumentException("Argument should beging with 'true.' or 'false.'. Instead, have '"  + s + "'");
	}
    }

    final static String SEP = ":";
    
    /**  Generates the label for a condition
	 @param cond E.g. "true.some_abe;false;other_bar", or empty string.
	@return e.g. "Ab". On an empty-string argument, "0" is returned.
    */
    String mapCond(String cond) {
	String s="";
	if (cond.length()==0) return "0";
	for(String a: cond.split(SEP)) {
	    s += map1(a);
	}
	return s;
    }

    /** Maps 'a' to "false.foo-a" or whatever it is.  */
    String letterToKey(char c) {
	char uc = Character.toUpperCase(c);
	for(String x: keySet()) {
	    char a = get(x);
	    if (a==uc) {
		return (c==uc ? "true." : "false.") + x;
	    }
	}
	throw new IllegalArgumentException("No key maps to label '" + c + "'");
    }
    
    /** The inverse of  mapCond() */
    String labelToCond(String label) {
	if (label.equals("0")) return "";
	Vector<String> v = new Vector<>();
	for(int j=0; j<label.length(); j++) {
	    v.add( letterToKey( label.charAt(j)));
	}
	return String.join(SEP, v);
    }

    /** @param conditions Each array element is of the form "true.ruleA:false.ruleB:..." etc.
     */
    LabelMap(String[] keys) {
	HashSet<String> h = new HashSet<>();
	for(String key: keys) {
	    for(String a: key.split(SEP)) {
		String b = a.replaceAll("^true.", "");
		b = b.replaceAll("^false.", "");
		h.add(b);
	    }
	}
	
	String[] z = h.toArray(new String[0]);
	char[] letters = assignLetters(z);
	for(int j=0; j<z.length; j++) {
	    if (letters[j]==0) throw new AssertionError("Have not assigned a letter to key no. " + j);
	    put(z[j], letters[j]);
	}
    }

    
    /** Creates a more or less intelligent mapping from strings in z[]
	to uppercase alphabet letters
    */
    static private char[] assignLetters(String [] z) {
	
	String[] w = new String[z.length];
	char[] letters = new char[z.length];
	if (z.length==0) return letters;
	

	int drop = z.length==1? 0: longestPrefix(z).length();
	for(int j=0; j<z.length; j++) {
	    String u = z[j].substring(drop).toUpperCase();
	    w[j] = stripPunctuation( u );
	    if (w[j].length()==0) w[j]=null;
	}
	HashSet<Character> usedLetters = new HashSet<>();

	
	// see if some of the first letters are unique
	while(true) {
	    HashMap<Character, Integer> fcnt = countFirstLetters(w);
	    //System.out.println("FL map=" + fcnt);
	    int newDone = 0, stripDone = 0;
	    for(int j=0; j<w.length; j++) {
		if (letters[j]!=0) continue;
		String q = w[j];
		if (q==null) continue;
		char x = q.charAt(0);
		int cnt = fcnt.get(x);
		if (cnt==1) {
		    letters[j] = x;
		    usedLetters.add(x);
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
	    if (letters[j]!=0) continue;
	    for(char c = 'A'; c<='Z'; c++) {
		if (!usedLetters.contains(c)) {
		    letters[j] = c;
		    usedLetters.add(c);
		    //System.out.println("Assigned letter[" + j +"]=" + letters[j] + " to key=" + w[j]);
		    w[j]=null;
		    break;
		}
	    }
	    if (letters[j]==0) throw new IllegalArgumentException("Ran out of alphabet letters for labels!");   
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
	LabelMap lam = new LabelMap(argv);
	for(String key: lam.keySet()) {
	    System.out.println( "Label(" + key+")='" + lam.get(key)  + "'");
	}

	for(String cond: argv) {
	    System.out.println( lam.mapCond(cond) + " --> " + cond);
	}
    }

}

