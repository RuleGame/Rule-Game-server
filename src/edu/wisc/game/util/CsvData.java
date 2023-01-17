package edu.wisc.game.util;


import java.io.*;
import java.util.*;
import java.text.*;


/** Represents the entire content of a CSV file. The file may consist
    of an optional header line and any number of data lines and
    (possibly) also comment lines. Comment lines may or may not be
    stored. Child classes add specific semantics for a particula type of
    CSV files.
 */
public class CsvData {

    /** Column count */
    private int colCnt=0;
    /** All data lines (except for, possibly, the first line), and
     * possibly also comment lines, from the file */
    public LineEntry[] entries;
    /** Unless noHeader=true, this is where we put the first line of the
	file. The leading '#', if present, is removed. */
    public BasicLineEntry header=null;
 
    /** Creates a new header line by appending some extra columns to 
	the stored header line of this file.
	@param extras ",f1,f2,..." */
    public String mkNewHeader(String extras) throws  IllegalInputException {
	if (header==null) {
	    throw new   IllegalInputException("No header found in CSV file");
	}
	return header + extras;
    }

  

    /** Child classes would override this, typically with a wrapper around the 
	constructor for an object that represents the content of a single line
    */
    protected LineEntry mkEntry(String[] csv, int colCnt) throws  IllegalInputException {
	return new BasicLineEntry(csv);
    }

    public CsvData(File csvFile) throws IOException, IllegalInputException  {
	this(csvFile, true, false, null);
    }

    private static boolean lengthMatch(int colCnt, int legalLengths[]) {
	if (legalLengths==null) return true;
	for(int le: legalLengths) {
	    if (colCnt==le) return true;
	}
	return false;
    }
    
    /** Creates a hash map that includes all entries from the CSV file that
	have keys (i.e., normally, all data lines). */
    public HashMap<String,LineEntry> toMap() {
	HashMap<String,LineEntry> h = new HashMap<String,LineEntry>();
	for(LineEntry e: entries) {
	    String key = e.getKey();
	    if (key==null) continue;
	    h.put(key,e);
	}
	return h;
    }

    private static FileReader openFile(File csvFile)  throws IOException {
 	if (!csvFile.canRead()) throw new  IOException("File '"+csvFile+"' does not exist or is not readable");
	return new FileReader(csvFile);
    }
	
            
    /** Creates a CsvData object from the content of a CSV file.
	@param csvFile File to read
	@param neverHeader If true, we don't expect the input file to connect a header; the file only should have data lines and optional comment lines. If false, we look at the first line of the file to figure if it's a header line or data line or comment line, and process it appropriately.
	@param keepComments If true, comment lines from the input file are stored in the object being created; otherwise, they are discarded.
	@param legalLengths If this is not null, it specifies how many columns the file's lines may contain. For example, {4,6} means that the lines must contain 4 or 6 columns.
     */
    public CsvData(File csvFile, boolean noHeader, boolean keepComments, int legalLengths[]) throws IOException, IllegalInputException  {
	this(csvFile, openFile(csvFile), noHeader,  keepComments,  legalLengths);
    }

    /** @param csvFile only passed so that the name of it can be used in error messages. May be null if the data come not from the file.
	@param r The data to read
     */
    public CsvData(File csvFile, Reader r, boolean noHeader, boolean keepComments, int legalLengths[]) throws IOException, IllegalInputException  {

	LineNumberReader reader = new LineNumberReader(r);
	int n=0;
	int errorCnt=0, warnCnt=0;
	Vector<LineEntry> v = new Vector<LineEntry>();
	String s= null;
	boolean isFirst=true;
	while((s= reader.readLine())!=null) {
	    s = s.trim();

	    if (s.equals("")) continue; // ignore blank lines (such as invisible lines of "\r") 

	    if (s.startsWith("#")) {
		if (keepComments) v.add(new CommentEntry(s.substring(1)));
		continue; // ignore comment lines
	    }
	    	   
	    if (isFirst) {
		if (s.length()==0) throw new IllegalInputException("Empty first line in " + csvFile);
		//boolean isHeader = !neverHeader && isHeaderLine(s);
		boolean isHeader = !noHeader;
		
		isFirst=false;
		String[] csv = ImportCSV.splitCSV(s);
		colCnt = csv.length;
		
		if (!lengthMatch(colCnt,legalLengths) ) {
		    throw new IllegalInputException("Unexpected header line or first line in " + csvFile + ". Found " + colCnt);
		}

		if (isHeader) {
		    s = s.replaceAll("^#", "");
		    final int nc = (legalLengths==null? -1 : colCnt);
		    header = (BasicLineEntry)mkEntry(csv, nc); 
		    continue;
		}
	    }


	    //System.out.println("Line "+(n+1)": " + s);
	    try {
		String[] csv = ImportCSV.splitCSV(s);
		// If legalLengths is null, columns can be of varied length
		// (This is used in the "specify XML file names" mode).
		final int nc = (legalLengths==null? -1 : colCnt);
		v.add(mkEntry(csv, nc));
		
	    } catch(IllegalInputException ex) {
		errorCnt ++;
		System.err.println("File '"+ csvFile +"', line " + reader.getLineNumber() + ": " + ex.getMessage());
	    }
	}
	if (warnCnt>0 || errorCnt>0 ) {
	    System.err.println("" + errorCnt + " errors, " + warnCnt + " warnings reported for input file " + csvFile);
	}
	if (errorCnt>0) {
	    throw new IllegalInputException("" + errorCnt + " errors found in input file " + csvFile);
	}
	entries = v.toArray(new LineEntry[0]);
    }

    public static interface LineEntry {
	/** The key associated with this data line. Typically, the key
	    of a data line is string from the first column. On the
	    other hand, comment lines don't have keys.
	*/
	public String getKey();
    }

    static public class BasicLineEntry implements LineEntry {
	/** The columns read from the CSV file */
	String [] csv;
	BasicLineEntry(String[] _csv) {
	    csv = _csv;
	}
	public int nCol() { return csv.length; }
	public String getKey() { return csv[0]; }
	/** @param j zero-based column index */
	public String getCol(int j) { return j<csv.length? csv[j]: null; }
	public Integer getColInt(int j) {
	    return j<csv.length &&  (csv[j]!=null) && (csv[j].length()>0) ? Integer.parseInt(csv[j]): null;
	}

	/** Picks the value from this line's column with the specified column name.
	    @param header This is where the column names are
	    @param name The desired column name
	    @param defVal The value to return if the header has no column with the desired name, or if this line is too short and does not have that many columns
	 */
	public String getColByName(BasicLineEntry header, String name, String defVal) {
	    for(int j=0; j<header.csv.length && j<csv.length; j++) {
		if (header.csv[j].equals(name)) return csv[j];
	    }
	    return defVal;
	}

	/** Requires the equality of the strings in all fields */
	public boolean equals(Object o) {
	    if (!(o instanceof BasicLineEntry)) return false;
	    BasicLineEntry e = (BasicLineEntry)o;
	    if (e.csv.length != csv.length) return false;
	    for(int j=0; j<csv.length; j++) {
		if (!e.csv[j].equals(csv[j])) return false;
	    }
	    return true;
	}

	
  	public String toString() {
	    String s = "";
	    for(String q: csv) {
		if (s.length()>0) s += ",";
		q = q.replaceAll("\"", "'");
		if (q.indexOf(",")>=0) q = "\""  + q + "\"";
		s += q;
	    }
	    return s;
	}
    }

  
    /** Stores a comment line from the CSV file. We may choose to store the
        comment lines so that we can read in a CSV file, do some modification 
	to each data line, and then write out the modified data lines along 
	with all the comment lines that appear here and there between the data
	lines.
    */
    public static class CommentEntry implements LineEntry {
	/** The entire text of the comment line */
	public final String text;
	CommentEntry(String _text) { text = _text; }
	/** Returns null, as comment lines don't have keys */
	public String getKey() { return null; }
    }

}
