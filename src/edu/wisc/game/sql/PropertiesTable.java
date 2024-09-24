package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.wisc.game.util.*;


/** This is an auxiliary class, used to read the entire content of a properties
    file, and to create ImageObject objects for all image files in the
    directory.
 */
public class PropertiesTable extends HashMap<String,ImageObject> {

    /** All properties table known to the system. The keys are directories in which
	properties tables files live, rather than files themselves.  */
    private static HashMap<File, PropertiesTable> allPropertiesTables = new HashMap<>();

    
    boolean error;
    String errmsg;
    String path;

    /** maps a property name to the sets of possible values of that property */
    final private HashMap<String, Set<Object>> valueSets= new HashMap<>();

    
    public boolean getError() { return error; }
    public void setError(boolean _error) { error = _error; }
    
    public String getErrmsg() { return errmsg; }   
    public void setErrmsg(String _errmsg) { errmsg = _errmsg; }

    public String getPath() { return path; }
    public void setPath(String _path) { path = _path; }

    /** All features (properties) found in this table, with their values */
    HashMap<String, Set<Object>> allFeatures = new HashMap<>();

    
    /** The error object  */
    private PropertiesTable(boolean _error, String _errmsg) {
	setError(_error);
	setErrmsg(_errmsg);
    }

    /** Retrieves the properties table for a particular directory */
    static public PropertiesTable getPropertiesTable(File dir) {
	PropertiesTable t= allPropertiesTables.get(dir);
	if (t==null) {
	    t= new   PropertiesTable(dir);
	    if (!t.error) allPropertiesTables.put(dir, t);
	}
	return t;
    }
    
    /** Reads a properties file, creating and loading an ImageObject
	for every table entry. */
    private PropertiesTable(File dir) {
	this(false, "No error");
	File f = new File(dir, "properties.csv");
	setPath(f.getPath());
	try {

	    if (!f.exists()) throw new IOException("File does not exist: " + f);
	    if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	    CsvData csv = new CsvData(f, true, false, null);
	    if (csv.entries.length<2) throw new IOException("No data found in file: " + f);
	    CsvData.BasicLineEntry header =  (CsvData.BasicLineEntry)csv.entries[0];
	    int nCol = header.nCol();
	    if (nCol<1) throw new IOException("Empty header in a property file");
	    if (!header.getCol(0).replaceAll("^#", "").equals("image"))  throw new IOException("The name of the first column in a property file must be 'image'");


	    //System.out.println("DEBUG: creating properties table from CSV file f=" + f+", with " + csv.entries.length + " lines of date");

	    Vector<Set<Object>> v= new Vector<>();
	    for(int k=1; k<nCol; k++) {
		v.add(new HashSet<>());
	    }
	    
	    
	    for(int j=1; j<csv.entries.length; j++) {
		CsvData.BasicLineEntry line = (CsvData.BasicLineEntry)csv.entries[j];
		ImageObject z = mkImageObject(dir,  header, line);
		put(z.key, mkImageObject(dir,  header, line));

		for(int k=1; k<nCol; k++) {
		    String s = line.getCol(k);

		    Object value = s;
		    
		    // FIXME: maybe sometimes we don't want strings of digits converted
		    // to Integer... e.g "010" in Composite objects...
		    try {
			value = Integer.parseInt(s);
		    } catch(Exception ex) { }

		    v.get(k-1).add(value);
		}
		
	    }

	    for(int k=1; k<nCol; k++) {
		String name =header.getCol(k);
		Set<Object> values = v.get(k-1);
	    	allFeatures.put(name, values);
	    }


	} catch(Exception ex) {
	    setError(true);
	    setErrmsg( ex.getMessage());
	}

    }
    


    /** Initializes an ImageObject object from a line of a properties file 
	@param dir Directory in which the file is
	@param header The header line of the prop file
	@param line The line from the prop file describing the object to be created
     */
    private ImageObject mkImageObject(File dir, CsvData.BasicLineEntry header, CsvData.BasicLineEntry line) throws IOException {
	int nCol=header.nCol();
	if (nCol!=line.nCol()) throw new  IOException("Column count mismatch:\nHEADER=" + header + ";\nLINE=" + line);
	
	ImageObject io = null;
	for(int k=0; k<nCol; k++) {
	    String key =header.getCol(k);
	    key = key.replaceAll("^#", "");
	    String val = line.getCol(k);

	    if (k==0) {
		if (!key.equals("image"))  throw new  IOException("First column must be named 'image'");
		if (val==null || val.equals(""))  throw new  IOException("First column may not be empty: file name must be specified");
		io = ImageObject.mkBlankImageObjectPlain(dir, val);
	    } else {
		io.put(key, val);
	    }
	}
	return io;
    }


    /** This method is discovered by Reflect, and is then used when
	converting the table to a JSON structure (via Reflect), so
	that the Captive server can print the list of properties. */
    //    public HashMap<String,Object> getExtraFields() {
    //	return valueLists;
    //}
    
}
