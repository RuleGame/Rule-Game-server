package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import jakarta.json.*;


import jakarta.xml.bind.annotation.XmlElement; 
import jakarta.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Piece;
import edu.wisc.game.sql.ImageObject;
import edu.wisc.game.svg.Composite;

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
    /** Will be set as appropriate if specified in the CSV file "images" column. The array elements are keys used for the image lookup. 
     Otherwise, null */
    public ImageObject.Generator imageGenerator=null;
    //    public String[] images=null;

    /** For JSON */
    public String getColors() {
	return Util.joinNonBlank(";", colors);
    }
     public String getShapes() {
	return Util.joinNonBlank(";", shapes);
    }
    public String getImages() {
    //	return images==null? null: Util.joinNonBlank(";", images);
	return imageGenerator.asList();
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
	semicolon-separated list of wildcard expressions. Together,
	they determine the set of ImageObjects from which 
	random boards can be constructed for the episodes played
	pursuant to this ParaSet.
     */
    public static ImageObject.Generator parseImages(String val) throws IOException {
	if (val==null) return null;
	val = val.trim();
	String[] ss = val.split(";");
	Vector<String> namesPlain = new Vector<>();
	Vector<Composite> compo = new Vector<>();  
	HashSet<String> h = new HashSet<>();
	if (ss.length>0) {
	    for(String s: ss) {
		Vector<ImageObject> v = ImageObject.obtainImageObjects(s);
		for(ImageObject io: v) {
		    String z = io.key;
		    if (h.contains(z)) continue;
		    h.add(z);
		    if (io instanceof Composite) compo.add((Composite)io);
		    else namesPlain.add(z);
		}
	    }
	}
	if (h.size()==0) return null;
	
	if (compo.size()>0) {
		if (namesPlain.size()>0) throw new IllegalArgumentException("A game cannot combine traditional and composite ImageObjects. Choose one or the other, but not both");
	       return new Composite.Generator(compo.toArray(new Composite[0]));
	} else {		
	       String q[] = namesPlain.toArray(new String[0]);
	       Arrays.sort(q);
	       return new ImageObject.PickFromList(q);
	}
	
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

    /** Initializes a ParaSet object from one line of a trial list file.
	Empty cells are ignored.
    */
    ParaSet(CsvData.BasicLineEntry header, CsvData.BasicLineEntry line) throws IOException {
	int nCol=header.nCol();
	if (nCol!=line.nCol()) throw new  IOException("Column count mismatch:\nHEADER=" + header + ";\nLINE=" + line);
	for(int k=0; k<nCol; k++) {
	    String key =header.getCol(k);
	    String val = line.getCol(k);
	    if (val==null || val.length()==0) continue;
	    
	    if (key.equals("colors")) {
		//System.out.println("DEBUG: column key=" + key+", val=" + val);
		Piece.Color[] _colors = parseColors(val);
		if (_colors != null) colors = _colors;	
	    } else if (key.equals("shapes")) {
		//System.out.println("DEBUG: parseShapes(" + val+")");
		Piece.Shape[] _shapes = parseShapes(val);
		//System.out.println("DEBUG: parseShapes(" + val+") done");
		if (_shapes!=null) shapes = _shapes;			
	    } else if (key.equals("images")) {
		imageGenerator=parseImages(val);
	    } else typedPut(key, val);
	}
    }

    private ParaSet() {}

    /** Makes a para set with just 1 column. This is used in "R:" dynamic 
	experiment plans.
	@param ruleSetName  This is in the format normally seen in trial
	list files, i.e. a file name relative to the main rule set directory,
	without extension. Other formats are also possible, as long
	as they are understood by Files.rulesFile() 
     */
    static ParaSet ruleNameToParaSet(String ruleSetName) {
	ParaSet q = new ParaSet();
	q.put("rule_id", ruleSetName);
	return q;
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
	if (val==null) return null;
	val = val.trim();
	String s= val.toLowerCase();
	return
	    (s.equals("true")||s.equals("false")) ? put(key,Boolean.valueOf(s)):
	    s.matches("[0-9]+") ? 	    put(key, Integer.valueOf(s)) :
	    s.matches("[0-9]*\\.[0-9]+") ?    put(key, Double.valueOf(s)) :
	    put(key, val);
    }

    private static File findFile(String name) throws IOException {
	if (name==null) throw new IOException("File name not specified");
	File base = new File(Files.inputDir, "param");
	String ext = ".csv";
	if (!name.endsWith(ext)) name += ext;
	return  new File(base, name);
    }

    /** Reads a ParaSet from a CSV file with key-val columns.
	This method is mostly obsolete now, since we read parameters from
	trial list files instead. It can, however, be "revived" for
	other applications, e.g. providing parameters for an automatic rule
	generator.
     */
    public ParaSet(String name) throws IOException,  IllegalInputException {
	this(findFile(name));
	put("name", name);
    }


    private static CsvData fileToCsv(File f)  throws IOException,  IllegalInputException {
	if (!f.exists()) throw new IOException("File does not exist: " + f);
	if (!f.canRead()) throw new IOException("Cannot read file: " + f);
	return new CsvData(f, true, false, null);
   }
	

    /**Processes ParaSet data that have been already read into string.

       @param s A multi-line string, which looks like the content of a
       traditional ParaSet CSV file. This can come over HTTP, for example.
     */
    public static ParaSet textToParaSet(String s)  throws IOException,  IllegalInputException {
	CsvData csv = new CsvData(null, new StringReader(s), true, false, null);
 	return new ParaSet(csv);
    }
    
    public ParaSet(File f)  throws IOException,  IllegalInputException {
	this( fileToCsv(f));
    }
    
    public ParaSet( CsvData csv)  throws IOException{
	
	put("error", false);
	put("errmsg", "No error");
	try {
	    for(CsvData.LineEntry e: csv.entries) {
		String key = e.getKey();
		String val = ((CsvData.BasicLineEntry)e).getCol(1);
		if (val==null) continue;
		val = val.trim();
		if (val.length()==0) continue;
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

    public boolean getBoolean(String key, Boolean defVal) {
	Object o = get(key);
	if (o==null) return defVal;
	else if (o instanceof Boolean) return  (Boolean)o;
	else throw new IllegalArgumentException("Parameter set has not a boolean value ("+o+") for key="+key);
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
	} else 	if (o instanceof Double) {
	    Double q = (Double)get(key);	    
	    return q.doubleValue();
	} else 	throw new IllegalArgumentException("The value of parameter named '"+key + "' cannot be converted to Double");
    }
    
    public int getInt(String key, boolean optional, int defaultValue) {
	Object o = get(key);
	if (o==null) {
	    if (optional) return defaultValue;
	    else throw new IllegalArgumentException("Parameter set has no variable named "+key);
	}
	if (o instanceof Integer) {
	    Integer q = (Integer)get(key);	    
	    return q.intValue();
	} else 	throw new IllegalArgumentException("The value of parameter named '"+key + "' cannot be converted to Integer");
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
    
    public boolean getCont() {
	return getBoolean("continue", false);
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
	if (imageGenerator==null) return;
	if (!(imageGenerator instanceof  ImageObject.PickFromList)) return;
	ImageObject.PickFromList g = (ImageObject.PickFromList)imageGenerator;
	for(String key: g.getKeys()) {
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

    public String toString() {
	Vector<String> v= new Vector<>();
	for(String key: keySet()) {
	    v.add(key +": "+get(key));
	}
	return "ParaSet{\n" + String.join("\n", v) + "\n}";
    }

    /** Augments this parameter set by the values from the modifier ParaSet. In
	case of matching column names, the values from the modifier replace the
	orginal ones.
     */
    void modifyBy(ParaSet modifier) {
	for(String key: modifier.keySet()) {
	    put(key, modifier.get(key));
	}
    }
    
    static private ParaSet mkLegacy() {
	ParaSet para = new ParaSet();
	para.shapes = Piece.Shape.legacyShapes;
	para.colors = Piece.Color.legacyColors;

	return para;
    }

    /** Various incentive schemes available to experiment designers. */
    public enum Incentive { BONUS, DOUBLING };

    /** Returns the name of the incentive scheme in use in this para set,
	or null if none is apparenly is in effect. This is determined
	by the presence or absence of necessary parameters.
    */
    public  Incentive getIncentive() {
	try {
	    int n  = getInt("activate_bonus_at");
	    if (n>=0) return  Incentive.BONUS;
	} catch(Exception ex) {}
	try {
	    int n  = getInt("x2_after");
	    if (n>=0) return  Incentive.DOUBLING;
	} catch(Exception ex) {}
	return null;	
    }

    /** Checks whether the parameters related to incentive schemes are
	consistent (that is, you don't have a parameter from one
	scheme and and another parameter from a different scheme).
     */
    public void checkIncentive() throws IllegalInputException {
	Incentive inc = getIncentive();
	Vector<String> names = new Vector<>();
	if (inc!=Incentive.BONUS) {
	    names.addAll( Util.array2vector("activate_bonus_at", "clear_how_many", "bonus_extra_pts"));
	}
	
	if (inc!=Incentive.DOUBLING) {
	    names.addAll( Util.array2vector("x2_after", "x4_after"));
	} else {
	    if (getInt("x2_after") >= getInt("x4_after")) throw new IllegalInputException("Check the paramters x2_after and x4_after. Both must be present, and the former must be smaller than the latter");
	}

	for(String key: names) {
	    Object o = get(key);
	    if (o!=null) {
		String msg = "The parameter set is thought to have ";
		msg += (inc==null)?"no incentive scheme, ":
		    "incentive scheme of type "+inc + ", ";
		msg += "but it also contains inappropriate parameter " + key +"=" + o;
		throw new IllegalInputException(msg);
	    }
	}
    }

    /** A dummy ParaSet object that contains legacy colors and shapes. This is
	used e.g. as a default context in the automatic rule generation.
    */
    static final public ParaSet legacy = mkLegacy();

    
    /** Returns the reward for an episode with a given number of errors,
	computed by the Kantor-Lupyan formula.


	The  Kantor-Lupyan formula  for the  reward computation  is at
	https://www.desmos.com/calculator/9nyuxjy7ri .  The actual min
	(asymptotic)  is smin;  the  actual  max (atd=0)  is smin  +
	(smax-smin)/(1+exp(-2*b)), which is a bit smaller than smax


	@param errors The number of errors the player has made
	in the episode. This can be a fractional number, if
	failed pick attempts are counted with a weight less
	than 1.0. The value Double.POSITIVE_INFINITY if allowed,
	in order to compute the lower bound of possible reward.
    */
    public int kantorLupyanReward(double errors)     {

	double smax = getDouble("max_points");
	double smin = getDouble("min_points");
	double b = getDouble("b");

	if (errors==Double.POSITIVE_INFINITY) return (int)smin;
	return (int)Math.round( smin + (smax-smin)/(1.0 + Math.exp(b*(errors-2))));
    }

    /** @return (lowerBound, upperBound)
     */
    public int[] kantorLupyanRewardRange(double errors) 
    {
	int[] range = { kantorLupyanReward(Double.POSITIVE_INFINITY), kantorLupyanReward(errors)};
	return range;
    }

    
    
}
			     
