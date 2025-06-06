package edu.wisc.game.util;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

/** Auxiliary methods for manipulating hash tables etc */
public class Util {

    /** Converts an array into a hash map. This is used because Java, unlike Perl,
	does not have an easy-to-use literal to initialize a hash map.
	@param a An array with the content (key1, val1, key2, val2, ...)
    */
    public static HashMap<String,String> array2map(String[] a) {
	if ((a.length % 2)!=0) throw new IllegalArgumentException("To initialize a hash map, you need an even number of elements!");
	HashMap<String,String> h = new HashMap<String,String>();
	for(int i=0; i<a.length; i+=2) {
	    h.put(a[i], a[i+1]);
	}
	return h;
    }

    /** Can also take String[] a */
    public static HashSet<String> array2set(String... a) {
	HashSet<String> h = new HashSet<String>();
	for(String x:a) h.add(x);
	return h;
    }

    public static HashSet<Integer> array2set(int... a) {
	HashSet<Integer> h = new HashSet<Integer>();
	for(int x:a) h.add(x);
	return h;
    }

    //public static <T> Vector<T> array2vector(T[] a) {
    //	Vector<T> v = new  Vector<>();
    //	for(T x:a) v.add(x);
    //	return v;  	
    //}
    public static <T> Vector<T> array2vector(T... a) {
	Vector<T> v = new  Vector<>();
	for(T x:a) v.add(x);
	return v;  	
    }

    public static Vector<Integer> array2vector(int... a) {
	Vector<Integer> v = new  Vector<>();
	for(int x:a) v.add(x);
	return v;  	
    }

    public static <T> Vector<Object> loseType(Vector<T> a) {
	Vector<Object> v = new  Vector<>();
	v.addAll(a);
	return v;  	
    }

    public static <T> Set<Object> loseType(Set<T> a) {
	Set<Object> v = new  HashSet<>();
	v.addAll(a);
	return v;  	
    }

    
    
    /*
    public static HashSet<String> array2set(String[] a) {
	HashSet<String> h = new HashSet<String>();
	for(String x:a) h.add(x);
	return h;
    }
    */

    /** Only uses non-null non-blank strings */
    public static <T> String joinNonBlank(String sep, Collection<T> v) {
	Vector<String> w = new Vector<>();
	for(T q: v)  {
	    if (q==null) continue;
	    String s = q.toString().trim();
	    if (s.length()>0) w.add(s);
	}
	return join(sep, w);
    }

    public static <T> String joinNonBlank(String sep, T[] z) {
	Vector<String> w = new Vector<>();
	for(T _q: z)  {
	    if (_q==null) continue;
	    String q = _q.toString();
	    q = q.trim();
	    if (q.length()==0) continue;
	    w.add(q);
	}
	return join(sep, w);  
    }

    public static <T> String joinNonBlank(String sep, int[] z) {
	Integer v[] = new Integer[z.length];
	for(int j=0; j<z.length; j++) v[j] = z[j];
	return joinNonBlank(sep,v);
    }
    public static <T> String joinNonBlank(String sep, double[] z) {
	Double v[] = new Double[z.length];
	for(int j=0; j<z.length; j++) v[j] = z[j];
	return joinNonBlank(sep,v);
    }

    public static <T> String joinNonBlank(String sep, NumberFormat fmt, double[] z) {
	String v[] = new String[z.length];
	for(int j=0; j<z.length; j++) v[j] =fmt.format(z[j]);
	return joinNonBlank(sep,v);
    }

    
    /*
   public static <T> String joinNonBlank(String sep, String[] z) {
	Vector<String> w = new Vector<>();
	for(String q: z)  {
	    if (q==null) continue;
	    q = q.trim();
	    if (q.length()==0) continue;
	    w.add(q);
	}
	return join(sep, w);  
    }
    */

    
    public static <T> String join(String sep, Vector<T> v) {
	String s="";
	for(T q: v)  {
	    if (s.length()>0) s+= sep;
	    s += q.toString();
	}
	return s;
   }
    /*
    public static String join(String sep, Vector<String> v) {
	return join(sep, v.toArray(new String[0]));
    }
    */
    public static <T> String join(String sep, T x[]) {
	String s="";
	for(int i=0; i<x.length; i++) {
	    if (i>0) s+= sep;
	    s += x[i].toString();
	}
	return s;
    }

    public static boolean foundAny(String text, String[] keywords) {
	for(String key: keywords) {
	    if (text.indexOf(key)>=0) return true;
	}
	return false;
    }

    public static boolean foundAnyWordStart(String text, String[] keywords) {
	for(String key: keywords) {
	    int k = text.indexOf(key);
	    if (k==0 || k>0 && !Character.isLetterOrDigit(text.charAt(k-1)))	    return true;
	}
	return false;
    }

    
    public static boolean startsWithAny(String text, String[] keywords) {
	for(String key: keywords) {
	    if (text.startsWith(key)) return true;
	}
	return false;
    }

   

    /** Creates a new array containing the same elements as x, but in
	reversed order. We can't use <tt>new T[x.length]</tt> ("generic array
	creation"), but we can piggyback on Arrays.copyOf()!
     */
    static public <T> T[] reverseArray(T[] x) {
	T[] y = Arrays.copyOf(x, x.length);
	int j=y.length;
	for(T q: x) y[--j] = q;
	return y;
    }

    /** "cow" -&gt; "cows" */
    static public String plural(String x, int n) {
	if (n<=1) return x;
	else return x+"s";
    }


    static private class RomanNumeral {
	private final int n0;
	private int n;
	private String a(String q, int d) {
	    String s="";
	    while(n>=d) {
		s += q;
		n -= d;
	    }
	    return s;
	}
	private final String result;
	/** 
	    https://docs.oracle.com/javase/specs/jls/se7/html/jls-15.html#jls-15.7
The Java programming language guarantees that the operands of operators appear to be evaluated in a specific evaluation order, namely, from left to right.

	*/
	RomanNumeral(int _n) {
	    n = n0 = _n;
	    if (n<=0 || n>=4000) throw new IllegalArgumentException("Don't know how to convert " + n + " to a Roman numeral");
	    result = 
		a("m", 1000) +
		a("cm", 900) +
		a("d", 500) +
		a("cd", 400) +
		a("c", 100) +
		a("xc", 90) +
		a("l", 50) +
		a("xl", 40) +
		a("x", 10) +
		a("ix", 9) +
		a("v", 5) +
		a("iv", 4) +
		a("i", 1);
	}
	public String toString() { return result; }
    }

    /** Converting to a roman numeral 
	@return a lowercase Roman numeral, e.g. "mcmxli" for 1941
     */
    static public String roman(int n) {
	RomanNumeral r = new RomanNumeral(n);
	return r.toString();
    }

    /** Returns true if both objects are non-nulls and equals() return true,
	or if both are nulls. */
    static public boolean same(Object a, Object b) {
	return a==null? b==null : b!=null && a.equals(b);
    }

    /** Reads an entire text file into a string */
    static public String readTextFile(File f) throws IOException {
	FileReader r = new FileReader(f);
	StringBuffer b = new StringBuffer();
	int x=0;
	while((x=r.read())>=0) b.append( (char)x);
	r.close();
	return b.toString();
    }

    /** Writes a string into a new text file */
    static public void writeTextFile(File f, String data) throws IOException {
	PrintWriter w = new PrintWriter(new FileWriter(f));
	w.print(data);
	w.close();
    }

    /** Capitalizes the first character of a string */
    static public String cap1(String s) {
	if (s==null || s.length()==0) return s;
	return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    /** Is any value set?
	@return The "OR" of all elements */
    static public boolean anySet(boolean[] w) {
	for(boolean b: w) {
	    if (b) return true;
	}
	return false;
    }


    /** Reads a 2-column CSV file ("key,value"), and puts the data from the file into a hash map. The keys are interepreted as members of a specified enum type.
	@param keyType The values in the first column of the CSV table will be interpreted as constants of this enum type
	@param table If not null, put the data into this table (after clearing it); if null, create a new table
	@param csvFile The file to load the data from 
     */
    /*
    public static <K extends Enum<K>> HashMap<K, String>  initTable(Class<K> keyType, HashMap<K, String> table, File csvFile) throws IOException//, IllegalInputException
    {
	if (table==null) table = new HashMap<K, String>();
	else table.clear();
	CsvData csv = new CsvData(csvFile);
	for(CsvData.LineEntry _e: csv.entries) {
	    CsvData.BasicLineEntry e= (CsvData.BasicLineEntry )_e;
	    K ju = Enum.valueOf(keyType, e.getKey());
	    String val = e.getCol(1);
	    table.put(ju, val);
	}
	return table;
    }
    */

    /*
    public static <K extends Enum<K>> HashMap<K, Double>  initTableDouble(Class<K> keyType, HashMap<K, Double> table, File csvFile) throws IOException//, IllegalInputException
    {
	if (table==null) table = new HashMap<K, Double>();
	else table.clear();

	HashMap<K, String> h =  initTable( keyType, null, csvFile);
	for(K key: h.keySet()) {
	    double x = new Double(h.get(key));
	    table.put(key, x);
	}
	return table;
    }
    */

    /** Converts a vector of integers to a BitSet 
	@return A BitSet with bit i set for every i in v. */
    public static BitSet toBitSet(Collection<Integer> v) {
  	BitSet result = new BitSet();
	for(int i: v) result.set(i);
	return result;
    }

    /** @return an array containing the indexes of the "on" bits */
    public static int[] listBits(BitSet b) {
	int[] a = new int[b.cardinality()];
	int j=0;
	for (int i = b.nextSetBit(0); i >= 0; i = b.nextSetBit(i+1)) {
	    a[j++] = i;
	    //if (i == Integer.MAX_VALUE) {
	    //	throw new IllegalArgumentException("BitSet is too big");
		//break; // or (i+1) would overflow
	    //}
	}
	return a;
    }

    public static <T> HashSet<String> eachToString(Collection<T> v) {
	HashSet<String> h = new HashSet<>();
	for(T x: v) h.add(x.toString());
	return h;
    }

    public static String stackToString(Exception ex) {
	StringWriter sw = new StringWriter();
	ex.printStackTrace(new PrintWriter(sw));
	return sw.toString();
    }

    /** File copy statistics */
    public static class CopyInfo {
	public int linesIn=0, linesOut=0, n=0;
	public void add(CopyInfo z) {
	    linesIn += z.linesIn;
	    linesOut += z.linesOut;
	    n += z.n;
	}
    }
    
    /** Copies the content of file f to file g, skipping any duplicate lines
	(even if the two identical lines are not next to each other).
	This is used to remove duplicates in transcript files.
	@return a statistics structure
    */
    static public CopyInfo copyFileUniqueLines(File f, File g) throws IOException {
	HashSet<String> h = new HashSet<>();
	FileReader in = new FileReader(f);	
	LineNumberReader​ r = new LineNumberReader​(in);
	PrintWriter w = new PrintWriter(new FileWriter(g));
	String line = null;
	CopyInfo ci=new CopyInfo();
	ci.n = 1;
	while((line= r.readLine())!=null) {
	    ci.linesIn++;
	    if (h.contains(line)) continue;
	    ci.linesOut++;
	    h.add(line);
	    w.println(line);
	}
	in.close();
	w.close();
	return ci;
    }

    public static <T> Vector<T> joinSubsections(Vector<T[]> w) {
	Vector<T> v = new Vector<>();
	for(T[] a: w) {
	    for(T t: a) {
		v.add(t);
	    }
	}
	return v;
    }
    
    public static <T> int sumLen(Vector<T[]> w) {
	int len=0;
	for(T[] a: w) {
	    len+= a.length;
	}
	return len;
    }


    

}
