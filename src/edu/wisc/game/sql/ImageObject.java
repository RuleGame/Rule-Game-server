package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.Files;


/** Describes an image-and-properties-based object. For details, see
<a href="../../../../../proposal-object-properties.html">Image-and-property-based description of objects</a>
 */
public class ImageObject extends HashMap<String,String> {

    public String key;
    /** For static objects, this is the SVG file for the image. For
	dynamically generated ones, null.
     */
    public final File file;
    /** The SVG code for the image, if available. It is mandatory for
	dynamically generated ImageObjects (/composite).  For static
	ones, it may or may not be null.
     */
    public String getSvg() { return null; }

    private ImageObject(File _file) {
	file = _file;
	key = fileToKey(file);
    }

    /** Used by Composite */
    protected ImageObject(//String _key//, String _svg
			  ) {
	//key = _key;
	file = null;
    }
    
    /** The key is a relative path (under the main shapes dir) or absolute 
	path (for images elsewhere), case-sensitive and 
	complete with the extension */
    private static HashMap<String,ImageObject> allImageObjects = new HashMap<>();

    static synchronized public void clearTable() {
	allImageObjects.clear();
    }

    
    /** Enters this ImageObject into the master table */
    public void enlist() {
	allImageObjects.put(key, this);
    }
  

    /** A file path (relative to the shapes dir), including extension */ 
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

    /** This one is used by Composite */
    //    static synchronized public ImageObject mkBlankImageObjectPlain2() {
    //    }


    /** Retrieves the ImageObject for a specified path from the master table.
	If necessary, tries to add that object (and all other objects listed
	in the properties file in that directory) to the master table.
	@param dir If provided, plainPath is understood as being relative to it.
	@param plainPath The image path (relative to dir, or absolute
	if dir==null).  Must not contain wildcard. May contain an
	extension (e.g. ".png" or ".svg"); if it does not, ".svg" will
	be added.
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

	//System.out.println("DEBUG: properties table in dir=" + dir+" has " + pt.size() + " entries");

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


    /** Loads an image object, or a group of them, if a wildcard is given.
     */
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

    /** Loads an ImageObjects (or multiple ImageObjects, if wildcards are
	used) from a path under a specified directory.
	@param dir The root of the directory from which ImageObjects are
	to be loaded
	@param relativeWildCardPath A relative paths (relative to
	dir), which has been split into components (with '/' being the
	separator), possibly with wildcards in some components.
     */
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


    private static HashSet<String> nonImageExtensions = Util.array2set(new String[] {
	    "csv", "sh", "bat"	    
	});
    
    
    /** Ignore this file when encountering it in directory listings, because
	it is either the properties file (CSV), or other known non-image file.
     */
    private static boolean isIgnorableFile(String x) {
	String[] v = x.split("\\.");
	String q = v[v.length-1].toLowerCase();
	return x.endsWith("~") || nonImageExtensions.contains(q);
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

    public String listProperties() {
	String[] keys = keySet().toArray(new String[0]);
	Arrays.sort(keys);
	Vector<String> v = new Vector<>();
	for(String key: keys) {
	    v.add(key + ":" + get(key));
	}
 	return String.join(", ", v);
    }

    /** For use in ASCII graphics */
    public String symbol() {
	String[] w = key.split("/");
	return w[w.length-1].substring(0,1);
    }
	

    static abstract public class Generator {
	//public ImageObject getOne(Random random);
	abstract public String getOneKey(Random random);
 	public String asList() {
	    return "";
	}
	public String describeBrief() {
	    return "Some set of image-and-property-based objects";
	}
   }

     static public class PickFromList extends Generator {

	 /** Will be set as appropriate if specified in the CSV file "images" column. The array elements are keys used for the image lookup. */
	 final private String[] keys;
	 public String[] getKeys() { return keys;}
	
	public PickFromList(String[] _keys) { keys = _keys; }
	 public //ImageObject
	    String getOneKey(Random random) {
	    String imageKey = keys[random.nextInt(keys.length)];
	    return imageKey;
	}
	public String asList() {
	    return Util.joinNonBlank(";", keys);
	}
	public String describeBrief() {
	    return "Set of "+keys.length+" image-and-property-based objects";
	}

    }

    
}
