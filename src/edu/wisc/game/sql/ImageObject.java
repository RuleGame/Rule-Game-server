package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

//import java.io.Serializable;  
//import javax.xml.bind.annotation.XmlElement; 
//import javax.xml.bind.annotation.XmlRootElement; 
//import javax.xml.bind.annotation.XmlTransient; 

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.Files;


/** Describes an image-and-properties-based object 
 */
public class ImageObject extends HashMap<String,String> {

    public final String key;
    public final File file;
    private ImageObject(File _file) {
	file = _file;
	key = fileToKey(file);
    }

    
    /** The key is a relative path (under the main shapes dir) or absolute 
	path (for images elsewhere), case-sensitive and 
	complete with the extension */
    private static HashMap<String,ImageObject> allImageObjects = new HashMap<>();

    static synchronized public void clearTable() {
	allImageObjects.clear();
    }

    
    /** Enters this ImageObject to the master table */
    private void enlist() {
	allImageObjects.put(key, this);
    }
  

    private static String fileToKey(File f) {
	String key = f.toString();
	final String prefix = Files.shapesDir() + "/";
	return key.startsWith(prefix)? key.substring(prefix.length()) : key;
    }


    /** Creates an ImageObject for a specified image file, but without
	its properties set. This method is to be used by the PropertiesTable
	class, followed by setting the ImageObject's properties.

	@param plainName The file name, e.g. "foo.jpg" or "foo",  found in the property file. It does not contain the directory path, and may or may not have the extension. */
    static synchronized ImageObject mkBlankImageObjectPlain(File dir, String plainName)
	throws IOException
    {
	if (plainName.indexOf("/")>=0 ||plainName.indexOf("\\")>=0 ||
	    plainName.startsWith(".")) throw new IllegalArgumentException("Invalid plain file name: '"+plainName+"'");
	if (!plainName.matches(".*\\.[a-zA-Z0-9]+")) {
	    plainName += ".svg";	    
	}
	File f=new File(dir, plainName);
	if (!f.exists())  throw new IOException("Image file '"+f+"' does not exist");
	if (!f.isFile() || !f.canRead())  throw new IOException("Image file '"+f+"' is not readable (or is not a regular file)");
	return new ImageObject(f);

    }

    /** Retrieves the ImageObject for a specified path from the master table.
	If necessary, tries to add that object (and all other objects listed
	in the properties file in that directory) to the master table.
	@param dir If provided, plainPath is understood as being relative to it.
     */
    public static synchronized ImageObject obtainImageObjectPlain(File dir, String plainPath, boolean allowMissing) {
	if (!plainPath.matches(".*\\.[a-zA-Z0-9]+")) {
	    plainPath += ".svg";
	}

	
	File f =
	    (dir!=null) ? new File(dir, plainPath):
	    plainPath.startsWith("/") ?new File(plainPath):
	    new File(Files.shapesDir(),plainPath);
	return obtainImageObjectPlain(f, allowMissing);
    }
    
    public static synchronized ImageObject obtainImageObjectPlain(File f) {
	return obtainImageObjectPlain(f, false);
    }
    
    /** @param allowMissing If true, simply return null, rather than throw exception, when the file is not listed in the prop file
     */
    public static synchronized ImageObject obtainImageObjectPlain(File f, boolean allowMissing) {
	String key = fileToKey(f);
	ImageObject z = allImageObjects.get(key);
	if (z!=null) return z;

	// Read the properties table from the relevant directory,
	// and put all ImageObjects into our master table
	if (!f.canRead()) throw new IllegalArgumentException("No image file exists: " +f);
	File dir = f.getParentFile();
	PropertiesTable pt = new PropertiesTable(dir);

	System.out.println("DEBUG: properties table in dir=" + dir+" has " + pt.size() + " entries");

	if (pt.error) {
	    throw new  IllegalArgumentException("Error reading property table for dir="+dir+": " + pt.errmsg);
	}

	
	for(ImageObject x: pt.values()) {
	    x.enlist();
	}
	z = allImageObjects.get(key);
	if (z==null && ! allowMissing)  throw new IllegalArgumentException("No valid entry for file=" + f + " (key="+key+") found in the properties file in directory=" + dir);
	return z;
    }

    
    public  static Vector<ImageObject> obtainImageObjects(String wildCardPath) {

	if (wildCardPath==null || wildCardPath.length()==0)  throw new IllegalArgumentException("Image path not specified or empty");

	File dir;
	if (wildCardPath.startsWith("/")) {
	    dir = new File("/");
	    wildCardPath = wildCardPath.substring(1);
	} else {
	    dir = Files.shapesDir();
	}
	return obtainImageObjects3(dir, Util.array2vector(wildCardPath.split("/")));
	
    }

    static private Vector<ImageObject> obtainImageObjects3(File dir, Vector<String> relativeWildCardPath) {
	Vector<ImageObject> v = new Vector<>();
	while( relativeWildCardPath.size()>0 && !mayBeWild(relativeWildCardPath.get(0))) {
	    String q = relativeWildCardPath.get(0);
	    if (relativeWildCardPath.size()==1) { // has to be a file
		v.add( obtainImageObjectPlain(dir, q, true));
		return v;
	    } else {  // has to be a directory component
		dir = new File(dir, q);
		if (!dir.isDirectory()) throw new IllegalArgumentException("No such directory: " + dir);
		relativeWildCardPath.remove(0);
	    }
	}

	if ( relativeWildCardPath.size()==0) return v;
	String q = relativeWildCardPath.get(0);
	relativeWildCardPath.remove(0);
	if (q.equals("**")) { // include all files from the subtree
	    if (relativeWildCardPath.size()>0)  throw new IllegalArgumentException("The element '**' can only be used as the last element of the path");
	    return getAllFilesFromTree( dir); 
	}

	Pattern pat = wildCardToRegex(q);
	
	String[] children = dir.list();
	for(String x: children) {
	    if (isIgnorableFile(x)) continue;
	    File f = new File(dir, x);
	    if (f.isFile() && relativeWildCardPath.size()==0) {

		Matcher m = pat.matcher(x);
		if (m.matches() || x.endsWith(".svg") &&
		    pat.matcher(x.replaceAll("\\.svg$","")).matches()) {
		    ImageObject io = obtainImageObjectPlain(f, false);
		    v.add(io);
		}
	    }  else if  (f.isDirectory() && relativeWildCardPath.size()>0) {
		Matcher m = pat.matcher(x);
		if (m.matches()) {
		    v.addAll(  obtainImageObjects3(f, relativeWildCardPath));
		}
	    } 
	    if (v.size()>TOO_MANY) throw new IllegalArgumentException("The wildcard expression requires too many files (more than " + TOO_MANY+")");
	}
	
	return v;
    }

    final static int TOO_MANY = 1000;
    
    /** Recurse the directory tree and include all files from all visited directories */
    static private Vector<ImageObject> getAllFilesFromTree(File dir) {
	Vector<ImageObject> v = new Vector<>();
	String[] children = dir.list();
	for(String x: children) {
	    if (isIgnorableFile(x)) continue;
	    File f = new File(dir, x);
	    if (f.isFile()) {
		ImageObject io = obtainImageObjectPlain(f, true);
		if (io!=null) v.add(io); // ignore files not listed in prop file
	    }  else if  (f.isDirectory()) {
		v.addAll( getAllFilesFromTree(f));
	    }
	    if (v.size()>TOO_MANY) throw new IllegalArgumentException("The wildcard expression requires too many files (more than " + TOO_MANY+")");
	}
	return v;
    }

    /** Ignore this file when encountering it in directory listings */
    private static boolean isIgnorableFile(String x) {
	return x.endsWith(".csv");
    }
    
    private static boolean mayBeWild(String x) {
	for(char c: x.toCharArray()) {
	    if (!(Character.isLetterOrDigit(c) || c == '_' || c=='-')) return true;
	}
	return false;
    }

    private static Pattern wildCardToRegex(String wild) throws IllegalArgumentException {
	StringBuffer b = new StringBuffer();
	for(char c: wild.toCharArray()) {
	    if (c == '\\') throw new IllegalArgumentException("Backslash (\\) is not allowed in path names");
	    else if (c== '?') b.append(".");
	    else if (c== '*') b.append(".*");
	    else if (c== '.') b.append("\\.");
	    else b.append(c);
	}
	return Pattern.compile(b.toString());
    }

    /*
    private static boolean wildCardMatch(String wild, String s) {
	while(true) {
	    if (wild.equals(s)) return true;
	    if (wild.length()==0 || s.length()==0) return false;
	    char w0 = wild.characteAt(0);
	    String w1 = wild.substring(1);
	    char c0 = s.characteAt(0);
	    String c1 = s.substring(1);
	    if (w0 == '?' || w0==c0)  {
		wild=w1;
		s=c1;
		continue;
	    } else if (w0 == '*') {
		for(int k=0; k<=s.length; k++) {
		    if (wildCardMatch
		}
	    }
    }
    */


  
    /** Reads a properties file. */
    /*
    public readPropertiesFile(File dir) {
	File f = new File(dir, "properties.csv");
	try {

	    if (!f.exists()) throw new IOException("File does not exist: " + f);
	    if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	    CsvData csv = new CsvData(f, true, false, null);
	    if (csv.entries.length<2) throw new IOException("No data found in file: " + f);
	    CsvData.BasicLineEntry header =  (CsvData.BasicLineEntry)csv.entries[0];
	    //int nCol = header.nCol();

	    for(int j=1; j<csv.entries.length; j++) {
		CsvData.BasicLineEntry line = (CsvData.BasicLineEntry)csv.entries[j];
		add(new ParaSet( header, line));
	    }


	} catch(Exception ex) {
	    setError(true);
	    setErrmsg( ex.getMessage());
	}

    }
    */
    
    
}
