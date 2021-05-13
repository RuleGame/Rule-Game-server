package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Piece;
import edu.wisc.game.sql.ImageObject;

@XmlRootElement(name = "ParaSet") 

/** A parameter sets contains the top-level information needed to configure a series of episodes, including a reference to the rule set, the rules for generating initial boards, and various control options and display options. It is initialized from one line of the trial list file.

<pre>
rule_id,max_boards,min_points,max_points,activate_bonus_at,min_objects,max_objects,min_shapes,max_shapes,min_colors,max_colors,f,m,n,b,clear_how_many,bonus_extra_pts,clearing_threshold,feedback_switches,stack_memory_depth,stack_memory_show_order,grid_memory_show_order
TD-01,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
TD-02,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
TD-03,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
TD-04,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
TD-05,5,2,10,2,4,6,4,4,3,4,4,9,1,1.5,2,3,1.3,fixed,6,FALSE,FALSE
</pre>
Additional columns
<pre>
colors,shapes,pick_cost
RED;PINK;ORANGE,SUN;MOON;STAR,0.5
</pre>
or
<pre>
images
animals/*;weapons/boomerang-*
</pre>
*/
    
public class ParaSet extends HashMap<String, Object> {

    /** Will be set as appropriate if specified in the CSV file "colors" column */
    public Piece.Shape[] shapes = Piece.Shape.legacyShapes;
    public Piece.Color[] colors = Piece.Color.legacyColors;
    /** Will be set as appropriate if specified in the CSV file "images" column */
    public String[] images=null;
    

    /** For JSON */
    public String getColors() {
	return Util.joinNonBlank(";", colors);
    }
     public String getShapes() {
	return Util.joinNonBlank(";", shapes);
    }
    public String getImages() {
	return images==null? null: Util.joinNonBlank(";", images);
    }

    /** Parses a semicolon-separated list of shapes.
	@val A semicolon-separated list of shapes. A null, or an empty string, are allowed as well.
	@return An array of Shape values, or null if val is null or empty.
	@throws IOException On a parsing problem (invalid shape names)
     */
    public static Piece.Shape[] parseShapes(String val) throws IOException {
	if (val==null) return null;
	val = val.trim();
    	String[] ss = val.split(";");
	if (ss.length>0) {
	    Vector<Piece.Shape> shapes = new Vector<>();
	    for(int j=0; j<ss.length;j++) {
		String s = ss[j].trim();
		if (s.endsWith("/*")) { // every file in a directory
		    String base = s.substring(0, s.length()-2);
		    if (!isGoodColorName(base)) throw new IOException("Invalid shape subdirectory name '"+base+"', in '"+s+"'");
		    File d = new File(Files.shapesDir(), base);
		    if (!d.exists() || !d.isDirectory() || !d.canRead()) {
			throw new IOException("Cannot look for shapes in " + d +", because there is no such directory, or it is not readable");
		    }

		    for(String z: Files.listInputs(d, ".svg")) {
			Piece.Shape c = Piece.Shape.findShape(base+"/" + z);
			shapes.add(c);			
		    }
		    
		} else {
		    if (!isGoodColorName(s)) throw new IOException("Invalid shape name '"+s+"'");
		    Piece.Shape c = Piece.Shape.findShape(s);
		    shapes.add(c);
		}
	    }
	    //Logging.info("ParaSet: loaded " + shapes.length + " custom shapes");
	    return shapes.toArray(new Piece.Shape[0]);
	} else return null;
    }

    public static Piece.Color[] parseColors(String val) throws IOException {
	if (val==null) return null;
	val = val.trim();
	String[] ss = val.split(";");
	if (ss.length>0) {
	    Piece.Color[] colors = new Piece.Color[ss.length];
	    for(int j=0; j<ss.length;j++) {
		String s = ss[j].trim();
		if (!isGoodColorName(s)) throw new IOException("Invalid color name '"+s+"'");
		Piece.Color c = Piece.Color.findColor(s);
		colors[j] = c;
	    }
	    //Logging.info("ParaSet: loaded " + colors.length + " custom colors");
	    return colors;
	} else  return null;
    }

    /** Parses the content of the "images" column. It may contain a
	semicolon-separated list of wildcard expressions.
     */
    public static String[] parseImages(String val) throws IOException {
	if (val==null) return null;
	val = val.trim();
	String[] ss = val.split(";");
	Vector<String> w = new Vector<>();
	if (ss.length>0) {
	    HashSet<String> h = new HashSet<>();
	    for(String s: ss) {
		Vector<ImageObject> v = ImageObject.obtainImageObjects(s);
		for(ImageObject io: v) {
		    String z = io.key;
		    if (h.contains(z)) continue;
		    h.add(z);
		    w.add(z);
		}
	    }
	}
	if (w.size()==0) return null;
	String q[] =  w.toArray(new String[0]);
	Arrays.sort(q);
	return q;
    }

    
    /*
   private int x;
 
       public int getX() { return x; }
  @XmlElement 
    public void setX(int _x) { x = _x; }


    private boolean error;
    private String errmsg;

    public boolean getError() { return error; }
    @XmlElement
    public void setError(boolean _error) { error = _error; }

    public String getErrmsg() { return errmsg; }
    @XmlElement
    public void setErrmsg(String _errmsg) { errmsg = _errmsg; }
    */

    /** Initializes a ParaSet object from a line of trial list file */
    ParaSet(CsvData.BasicLineEntry header, CsvData.BasicLineEntry line) throws IOException {
	int nCol=header.nCol();
	if (nCol!=line.nCol()) throw new  IOException("Column count mismatch:\nHEADER=" + header + ";\nLINE=" + line);
	for(int k=0; k<nCol; k++) {
	    String key =header.getCol(k);
	    String val = line.getCol(k);

	    //System.out.println("DEBUG: column key=" + key+", val=" + val);

	    if (key.equals("colors") && val!=null) {
		Piece.Color[] _colors = parseColors(val);
		if (_colors != null) colors = _colors;	
	    } else if (key.equals("shapes") && val!=null) {
		//System.out.println("DEBUG: parseShapes(" + val+")");
		Piece.Shape[] _shapes = parseShapes(val);
		//System.out.println("DEBUG: parseShapes(" + val+") done");
		if (_shapes!=null) shapes = _shapes;			
	    } else if (key.equals("images") && val!=null) {
		String[] _images = parseImages(val);
		if (_images !=null) images = _images;
	    } else typedPut(key, val);
	}
    }

    private static boolean isRegular(char c) {
	return (Character.isLetterOrDigit(c) || c=='_');
    }

    /** Color names and shape names should be alphanumeric, with "-"
	and "/" allowed in reasonable positions (between regular
	chars). */
    private static boolean isGoodColorName(String s) {
	if (s.length()==0) return false;
	boolean wasRegular = false;
	for(int j=0; j<s.length(); j++) {
	    char c = s.charAt(j);
	    boolean isRegular =  isRegular(c);
	    boolean ok = isRegular ||
		(c=='-' || c=='/') && wasRegular && j+1<s.length();
	    if (!ok) return false;
	    wasRegular=isRegular;
	}
	return true;
    }

    /** Converts the value to an object of a (likely) proper type, and 
	puts it into this HashMap */
    private Object typedPut(String key, String val) {
	val = val.trim();
	String s= val.toLowerCase();
	return
	    (s.equals("true")||s.equals("false")) ? put(key,Boolean.valueOf(s)):
	    s.matches("[0-9]+") ? 	    put(key, Integer.valueOf(s)) :
	    s.matches("[0-9]*\\.[0-9]+") ?    put(key, Double.valueOf(s)) :
	    put(key, val);
    }

    /** Reads a ParaSet from a CSV file with key-val columns.
	This method is obsolete now, since we read parameters from
	trial list files instead.
     */
    ParaSet(String name) {
	put("error", false);
	put("errmsg", "No error");
	put("name", name);
	try {
	    if (name==null) throw new IOException("File name not specified");
	    File base = new File(Files.inputDir, "param");
	    String ext = ".csv";
	    if (!name.endsWith(ext)) name += ext;
	    File f= new File(base, name);
	    if (!f.exists()) throw new IOException("File does not exist: " + f);
	    if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	    CsvData csv = new CsvData(f, true, false, null);
	    for(CsvData.LineEntry e: csv.entries) {
		String key = e.getKey();
		String val = ((CsvData.BasicLineEntry)e).getCol(1);
		if (val==null) continue;
		val = val.trim();
		typedPut(key, val);		    
	    }
	} catch(Exception ex) {
	    put("error", true);
	    put("errmsg", ex.getMessage());
	}

    }

    public int getInt(String key) {
	Integer o = (Integer)get(key);
	if (o==null) throw new IllegalArgumentException("Parameter set has no variable named "+key);
	return o.intValue();
    }

    public double getDouble(String key) {
	return getDouble(key, false, 0);
    }

    /**
       @param optional If true, this method will not throw an exception, and will return defaultValue, if the parameter is absent in the set
       @param defaultValue Only used if optional==true
     */
    public double getDouble(String key, boolean optional, double defaultValue) {
	Object o = get(key);
	if (o==null) {
	    if (optional) return defaultValue;
	    else throw new IllegalArgumentException("Parameter set has no variable named "+key);
	}
	if (o instanceof Integer) {
	    Integer q = (Integer)get(key);	    
	    return q.intValue();
	} else {
	    Double q = (Double)get(key);	    
	    return q.doubleValue();
	}
	
    }

    public int getMaxBoards() {
	return getInt("max_boards");
    }

    public String getRuleSetName() {
	return (String)get("rule_id");
    }

    public double getClearingThreshold() {
	Double x = getDouble("clearing_threshold");
	return x;
    }

    /** The cost of a pick attempt, in terms of the cost of a move. The default is 1.0. An early proposal called this param "pick_cost", but then I realized that Paul had planned for it all along, under a different name. */
    public double getPickCost() {
	Double x = getDouble("free_wrong_cost", true, 1.0);
	return x;
    }

    /** Is the cost of a pick attempt an integer? */    
    public boolean  pickCostIsInt() {
	double x = getPickCost();
	return x == (double)(int)x;
    }
    

    /** Makes sure that this parameter set's color list (used for generating
	random boards) only contains valid colors (present in the color map)
     */
    public void checkColors(ColorMap cm) throws IOException {
	if (colors==null) return;
	for( Piece.Color color: colors) {
	    if (!cm.hasColor(color)) throw new IOException("Color " + color + " is not in the color map");
	}

    }
    
   /** Makes sure that this parameter set's shape list (used for generating
	random boards) only contains valid shapes (for which SVG files exist)
     */
    public void checkShapes() throws IOException {
	if (colors==null) return;
	for( Piece.Shape shape: shapes) {
	    File f = Files.getSvgFile(shape);
	    if (!f.canRead())  throw new IOException("For shape "+shape+",  Cannot read shape file: " + f);
	}

    }


    public void checkImages() throws IOException {
	if (images==null) return;
	for(String key: images) {
	    ImageObject io = ImageObject.obtainImageObjectPlain(null, key, false);
	}
    }

    
    /** True if the player is not told which pieces are movable.
	(free = no objects are marked with X. Seeking to move an object is counted as some fraction of a move.)
    */
    public boolean isFeedbackSwitchesFree() {
	String s = get("feedback_switches").toString();
	return s!=null && s.toLowerCase().startsWith("free");
    }

}
			     
