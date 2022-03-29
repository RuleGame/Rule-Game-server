package edu.wisc.game.svg;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import  edu.wisc.game.formatter.*;
import  edu.wisc.game.util.*;
import  edu.wisc.game.sql.ImageObject;

/** The description of a single composite ImageObject, or a family
    of such objects, obtained by parsing its name
    (which may contain wildcards). Unlike other ImageObject, 
    Composite ones are dynamically generated, because 
    they are drawn from a large space.

<p>
    A Composite object may be a "concrete" one (has no
    wildcards, and describes exactly one ImageObject),
    or a "family" one (has wildcards that match a group
    of objects). The sample() method can be used to
    randomly draw a concrete object from a family.
*/
public class Composite extends ImageObject {

    /** How many elements in a composite image */
    final static int N=3;
    
    /** Max size of a single element */
    final static int R=20;
    static final int margin=1;

    /** Size of the entire SVG */
    final static int H=N*2*(R+margin);
        
    static final HTMLFmter fm =    HTMLFmter.htmlFmter;

    /** name="val" .... */
    static String mkSvgParams(String... nameVal) {
	String s = "";
	if ((nameVal.length % 2)!=0) throw new IllegalArgumentException("odd number of params");
	Vector<String> v = new 	Vector<>();
	for(int j=0; j<nameVal.length; j+=2) {
	    v.add( nameVal[j] + "=\""+ nameVal[j+1]  +"\"");	    
	}
	return String.join(" ", v);
    }

    private static String elt(String name, String... nameVal) {
	return "<"+name + " " + mkSvgParams(nameVal) +
 	    "/>";
    }
    
    /** A single element (circle, square, star, triangle) of the composite image */
    private static class Element {

	private static final double[] trianglePP = {0, -1,   Math.sqrt(0.75), 0.5, -Math.sqrt(0.75), 0.5};
	private static final double[] starPP = {0, -1,
						Math.sin(0.2 * Math.PI), Math.cos(0.2 * Math.PI),
						-Math.sin(0.4 * Math.PI), -Math.cos(0.4* Math.PI),
						Math.sin(0.4 * Math.PI), -Math.cos(0.4 * Math.PI),
						-Math.sin(0.2 * Math.PI), Math.cos(0.2 * Math.PI)};

	/** @return "x1 y1 x2 y2 x3 y3 ..." */
	private static String mkPolygonPara(int cx, int cy, int r,  double[] pp) {
	    StringBuffer b=new StringBuffer();
	    for(int j=0; j<pp.length; j+=2) {
		int x = cx + (int)(r*pp[j]);
		int y = cy + (int)(r*pp[j+1]);
		if (b.length()>0) b.append(" ");
		b.append((b.length()>0? " ":"") + x + " " + y);
	    }
	    return b.toString();
	   
	}

	
	final String shape;
	final String color;
	final int sizeRank;
	final int bright;
	Element(String _shape, String _color, int _sizeRank, int _bright) {
	    shape = _shape;
	    bright = _bright;
	    color =  _color.equals("r") ? "red":
		_color.equals("g") ? "green":
		_color.equals("b") ? "blue": null;
	    if (color==null)  throw new IllegalArgumentException("Illegal color: " + _color);
	    
	    sizeRank = _sizeRank;
	}
	

	/**
<g color="red">
  <rect x="120" y="20" width="60" height="60" fill="currentcolor"/>
</g>
<g color="green">
  <circle cx="150" cy="150" r="50"  fill="currentcolor"/>
</g>
<g color="blue">
  <polygon points="150 210 190 280 110 280"  fill="currentcolor"/>
</g>
<g color="orange">
  <polygon points="250 120   260 145   280 145   265 160   270 180  250 170   230 180  235 160    220 145 240 145"
  fill="currentcolor"/>
</g>

@param cell "radius" (half the size of the cell)

	 */
	String makeSvg(int cx, int cy) {
	    String name;
	    int r = (int)( (1 - 0.3*(3-sizeRank))  *R);
	    String[] q;
	    if (shape.equals("c"))  {
		name = "circle";
		q = new String[] {"cx", ""+cx, "cy", ""+cy, "r", ""+r};
	    } else if  (shape.equals("q"))  {
		name = "rect";
		q = new String[] { "x", ""+(cx-r), "y", ""+(cy-r),
				   "width", ""+(2*r),
				   "height", ""+(2*r)};
	    } else if  (shape.equals("t"))  {
		name = "polygon";
		q = new String[] { "points", mkPolygonPara(cx, cy, r, trianglePP)};
	    } else if  (shape.equals("s"))  {
		name = "polygon";
		q = new String[] { "points", mkPolygonPara(cx, cy, r, starPP)};
	    } else throw new IllegalArgumentException("Illegal shape: " + shape);

	    Vector<String> a = Util.array2vector(q);
	    a.add("fill");
	    a.add(color);
	    if (bright!=3) {
		a.add( "fill-opacity");
		double opa = (bright==1)? 0.15: 0.4;
		a.add("" + opa);
	    }
	    String w = elt(name, a.toArray(new String[0]));

	    w += "\n" +  elt("rect", "x", ""+(cx-R), "y", ""+(cy-R),
			     "width", ""+(2*R), "height", ""+(2*R), "fill-opacity", "0",
			     "stroke", "black", "stroke-width", "1");	       
	    return w;	    
	}
	
    }
	

    enum Orientation {
	VERTICAL, HORIZONTAL, ANY;
	String toLetter() {
	    return  (this == VERTICAL) ? "v":
		(this == HORIZONTAL) ? "h": "?";
	}
	/** If this is a wildcard, randomly picks a "concrete" orientation */
	Orientation sample(Random random) {
	    return (this==ANY) ?
		(random.nextBoolean()? VERTICAL: HORIZONTAL): this;
	}
		       
    };
    Orientation orientation;
    boolean wild = false;
    //String name;
    final static int ANY=0;
    /** Size and brightness levels range from 1 thru M. M is the default */
    final static int M=3;
    final static String MMM= initMMM();

    /** "MMM" */
    static private String initMMM() {
	char[] a=new char[N];
	Arrays.fill( a, (char)('0' + M));
	return new String(a);
    }
    
    /** Arrays of N=3 elements which contain values 1 2 3, or ANY */
    int[] sizeRank={M,M,M}, bright={M,M,M};
    /** 'q', 't' etc, or 'r', 'g', 'b'. May also contain '?' */
    String[] shapes=new String[N], colors=new String[N];
    final static String[] allShapes = {"c", "s", "q", "t"}, allColors={"r","g","b"};
    
    private String svg;
    public String getSvg() { return svg; }

    
    final static String prefix = "/composite/";

    
    private static String joinInt(int [] x) {
	Vector<String> v = new Vector<>();
	for(int a: x) {
	    v.add(	a==ANY? "?" : ""+a);
	}
	return String.join("",v);
    }
    
    /** @return "/s=123" etc, or empty string for all default values */
    private static String field(String key, int [] x) {
	String s = joinInt(x);
	return s.equals(MMM)?  "": "/" + key + "=" +s;	    
    }
    
    /** Computes a name based on stored parameters. This is useful during 
	random sample generation */
    private String mkName() {
	String s = prefix + orientation.toLetter();
	s += field("d", sizeRank);
	s += field("b", bright);
	for(int j=0; j<N; j++) {
	    s += "/" + 	colors[j] + shapes[j];
	}	    
	return s;	       
    }
    
    public String toString() {
	String s = "[Composite ImageObject Description: orientation="+orientation+", sizes=" + joinInt(sizeRank) +
	    ", brightness=" + joinInt(bright) +
	    ", pieces=";
	Vector<String> v = new Vector<>();
	for(int j=0; j<N; j++) {
	    v.add(		colors[j] + shapes[j] );
	}
	s += String.join(" ",v);
	return s;
    }

    /** @param val Could be "123", "*", "1*", "1?2" etc. 
	@return Input split into 3 digits (possibly ANY)
    */
    private int[] parseVal(String val) {
	
	int [] q = new int[N];
	if (val.equals("*")) {
	    wild=true;
	    return new int[]{ANY, ANY, ANY};
	}
	int k=0;
	for(int j=0; j<val.length(); j++) {
	    if (k>=N)  throw new IllegalArgumentException("String too long: " + val);
	    char c = val.charAt(j);
	    if (c == '?') {
		wild = true;
		q[k++] = ANY;
	    }
	    else if (c>='1' && c<='0'+M) q[k++] = c - '0';
	    else if (c=='*' && j+1==val.length()) { // '*' at the end of string
		wild = true;
		while(k<N)  q[k++] = ANY;
	    }
	    else throw new IllegalArgumentException("Character " + c + " is not allowed. String=" + val);
	}
	if (k<N)  throw new IllegalArgumentException("String too short: " + val);
	return q;
    }


    /** Common initializer for all constructors */
    private Composite() {
	super();
	sizeRank = new int[N];
	Arrays.fill( sizeRank, M);
	bright = new int[N];		 
	Arrays.fill( bright, M);
    }
    
    /** Creates a new "concrete" Composite object patterned on a particular object that has
	wildcards */
    private Composite(Composite p, Random random) {
	this();
	orientation = p.orientation.sample(random);
	for(int j=0; j<N; j++) {
	    sizeRank[j] = (p.sizeRank[j]==ANY)? 1+random.nextInt(M) : p.sizeRank[j];
	    bright[j] = (p.bright[j]==ANY)? 1+random.nextInt(M) : p.bright[j];
	}
	for(int j=0; j<N; j++) {
	    shapes[j] = p.shapes[j].equals("?")? allShapes[random.nextInt(allShapes.length)] : p.shapes[j];
	    colors[j] = p.colors[j].equals("?")? allColors[random.nextInt(allColors.length)] : p.colors[j];
	}
	key = mkName();
	svg = wild? null: makeFullSvg();
    }

    /** How many distinct concrete Composite ImageObjects does this Composite
	object describe? The result is based on the number of wildcards in 
	this object.
     */
    public int familySize() {
  	if (!wild) return 1;
	int n = 1;
	if (orientation==Orientation.ANY) n*=2;
	for(int j=0; j<N; j++) {
	    if (sizeRank[j]==ANY) n *= M;
	    if (bright[j]==ANY)  n *= M;
	}
	for(int j=0; j<N; j++) {
	    if (shapes[j].equals("?")) n *= allShapes.length;
	    if (colors[j].equals("?")) n *= allColors.length;
	}	
	return n;
    }
  
    /** If this is a wildcard description, generates a "concrete" (non-wildcard)
	desription of a matching composite object */
    Composite sample( Random random) {
	if (!wild) return this;
	Composite q = new Composite(this, random);
	return q;
    }


    /** Constructs a concrete or "family" Composite object based
	on a name string.
	@param name E.g. "/composite/h/d=???/b=123/gq/gq/gq"
    */
    private Composite(String name) {
	this();	
	key = name;
	for(int j=0; j<N; j++) {
	    shapes[j] = "q";
	    colors[j] = "r";
	}
	    
	    
	if (!name.startsWith(prefix)) throw new IllegalArgumentException("Illegal name: " + name + ". Names must start with " + prefix);
	String [] q = name.substring(prefix.length()).split("/");
	if (q.length<1) throw new IllegalArgumentException("name too short: " + name);
	
	int k=0;
	
	boolean first = true;
	
	for(String s: q) {
	    s = s.trim();
	    if (s.equals("")) throw new IllegalArgumentException("Empty name component: you must have put two slashes next to each other in name=" + name);
	    //System.out.println("first=" + first+", element=" +  s);
	    
	    if (first) {
		if (s.equals("h")) orientation=Orientation.HORIZONTAL;
		else if (s.equals("v")) orientation=Orientation.VERTICAL;
		else if (s.equals("?")) {
		    orientation=Orientation.ANY;
		    wild =true;
		}
		else throw new IllegalArgumentException("Expected h or v, found '"+s+"' in name=" + name);
		first=false;
		continue;
	    }

	    	
	    if (s.indexOf("=")>=0) {  //   d=123
		String[] w = s.split("=");
		if (w.length==2) {
		    String key = w[0], val=w[1];
		    int[] vals = parseVal(val);
		    
		    //System.out.println(key + " = " +  Util.joinNonBlank("", vals));
		    
		    if (key.equals("d")) sizeRank=vals;
		    else if (key.equals("b")) bright=vals;
		    else  throw new IllegalArgumentException("Unknown key="+key+" in name=" + name);
		} else  throw new IllegalArgumentException("Cannot parse component="+s+" in name=" + name);		
	    } else if (k<N) {  // rq
		// System.out.println("piece = " +  s);
		if (s.length()!=2)  throw new IllegalArgumentException("Illegal length of component="+s+" (expected 2 chars, e.g. 'rq', found "+s+") in name=" + name);
		char c = s.charAt(0);
		wild = wild ||  (c=='?');
		if (c == 'r' || c=='g' || c=='b' || c=='?' ) colors[k] = "" + c;
		else  throw new IllegalArgumentException("Illegal color char '" + c +"' in component="+s+", in name=" + name);
		c = s.charAt(1);
		if (c == 'q' || c=='t' || c=='s' || c=='c' || c=='?') shapes[k] = "" + c;
		else  throw new IllegalArgumentException("Illegal shape char '" + c +"' in component="+s+", in name=" + name);
		k++;
	    } else  throw new IllegalArgumentException("Extra component="+s+" in name=" + name);			
	}
	svg = makeFullSvg();	
    }

	/** The SVG code for the composite image, not yet wrapped into  the top-level SVG element */
	String makeSvg() {
	    if (wild) {
		return fm.wrap("text",      mkSvgParams("x", "0", "y", "0"),
			       "Abstract");
	    }
	    Vector<String> v = new Vector<>();
	    for(int j=0; j<N; j++) {
		Element e = new Element(shapes[j], colors[j], sizeRank[j], bright[j]);
		int cx = H/2, cy=H/2;
		int delta = 2*(R+margin) * (j-1);
		if (orientation==Orientation.VERTICAL) cy += delta;
		else cx += delta;	    
		String s = e.makeSvg(cx, cy);	    
		v.add(s);
	    }
	    v.add(elt("rect", "x", "0", "y", "0",
		      "width", ""+H, "height", ""+H, "fill-opacity", "0",
		      "stroke", "black", "stroke-width", "1"));
	    
	    String s = String.join("\n",v);
	    
	    return s;
	}


    /** Produces the entire content of an SVG file describing the image for this ImageObject.
<code>
<?xml version="1.0" standalone="no"?>
<svg width="600" height="500" version="1.1" xmlns="http://www.w3.org/2000/svg">

  <rect x="10" y="10" width="80" height="80" stroke="black" fill="red"
      fill-opacity="1" stroke-width="8"/>
</svg>
</code>
     */
    private String makeFullSvg() {
	StringBuffer b = new StringBuffer("<?xml version=\"1.0\" standalone=\"no\"?>\n");
	String svgExtra = mkSvgParams("xmlns", "http://www.w3.org/2000/svg",
				      "width", ""+H,
				      "height", ""+H,
				      "version", "1.1");

	//	final String prefix = "/composite/";
	//	if (!name.startsWith(prefix)) throw new IllegalArgumentException("Illegal name: " + name + ". Names must start with " + prefix);
	//	name = name.substring(prefix.length());
	String body = "\n" + makeSvg() + "\n";
	b.append( fm.wrap( "svg", svgExtra, body) + "\n");	
	return b.toString();
    }
   
  
    //  static NumberFormat fmt3d = new DecimalFormat("000");
	
    public static void main(String argv[]) throws IOException {
	Random random = new Random();
	for(String a: argv) {
	    Composite d = new  Composite(a);
	    System.err.println("d=" + d);
	    int n = d.familySize();
	    System.err.println("Expands to " + n + " concrete objects");
	    Composite b = d.sample(random);
	    System.err.println("b=" + b);
	    System.out.println( b.makeFullSvg());
	}

	
	/*
	String color = argv[0];
	for(int deg=0; deg<360; deg+=15) {
	    String s = makeSvg(color, (Math.PI * deg)/180);
	    String fname = "arrow-" + color + "-" + fmt3d.format(deg);
	    File f = new File(fname + ".svg");
	    PrintWriter w = new PrintWriter(new      FileWriter(f));
	    w.println(s);
	    w.close();
	    System.out.println( fname + "," + color + "," + deg);
	}
	*/
    }



    /** A tool for drawing concrete ImageObjects from a family defined bya Composite object,
	or a union of such families.
     */
    static public class Generator extends ImageObject.Generator {

	/** The families */
	final private Composite[] compo;
	/** Family sizes, and their sum */
	final private int size[], sumSize;
	
	public String[] getKeys() {
	    String[] v = new String[compo.length];
	    for(int j=0; j<compo.length; j++) v[j] = compo[j].key;
	    return v;
	}
	
	public Generator( Composite[] _compo) {
	    compo = _compo;
	    size = new int[compo.length];
	    int sum=0;
	    for(int j=0; j<compo.length; j++) {
		sum += size[j] = compo[j].familySize();
	    }
	    sumSize = sum;
	    if (sumSize<1) throw new IllegalArgumentException("Appaently empty set of composite objects");
	}
	
	 public //ImageObject
	     String getOneKey(Random random) {
	     int k = random.nextInt(sumSize);
	     int sum=0;
	     int j=0;
	     while((sum += size[j])<=k) {
		 j++;
		 if (j>=compo.length) throw new IllegalArgumentException("Internal error");
	     }
	     Composite a = compo[j];
	     Composite b = a.sample( random );
	     b.enlist();
	     return b.key;
	}
	public String asList() {
	    return Util.joinNonBlank(";", getKeys());
	}
	public String describeBrief() {
	    return "Set of dynamically generated image-and-property-based objects";
	}

    }

    
}
