package edu.wisc.game.svg;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import  edu.wisc.game.formatter.*;
import  edu.wisc.game.util.*;

public class Composite {

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

    static String elt(String name, String... nameVal) {
	return "<"+name + " " + mkSvgParams(nameVal) +
	    //" " + mkSvgParams("fill", "currentcolor") +
 	    "/>";
    }
    
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

	

    
    private static class Point {


	//    static final int H=2*(R+margin);


	
	/** Coordinates with respect to the center of the image, with 
	    the Y axis going up */
	double x,y;
	Point(double _x, double _y) {
	    x=_x;
	    y=_y;
	}
	/** Counterclockwise rotation 
	    @param alpha in radians
	*/
	Point rotate(double alpha) {
	    double qx =x*Math.cos(alpha) - y*Math.sin(alpha);
	    double qy =y*Math.cos(alpha) + x*Math.sin(alpha);
	    return new Point(qx,qy);
	}
	/** In SVG coordinates (with respect to the top left corner, 
	    Y going down)
	*/
	int[] svgCoord() {
	    int[] xy = {(int)Math.round(R+margin+x),  (int)Math.round(R+margin-y)};
	    return xy;
	}
	String[] svgCoordString() {
	    int[] q = svgCoord();
	    String[] xy = {""+q[0], ""+q[1]};
	    return xy;
	}
	String svgString() {
	    return String.join(" ", svgCoordString());
	}

	static Point[] rotate(Point[] a, double alpha) {
	    Point[] b = new Point[a.length];
	    for(int j=0; j<a.length; j++) {
		b[j] = a[j].rotate(alpha);
	    }
	    return b;
	}
	    
    }


    /** <line x1="7" y1="17" x2="17" y2="7"></line> */   
    static String line(Point _a, Point _b) {
	String[] a = _a.svgCoordString(), b = _b.svgCoordString();
	String para = mkSvgParams( "x1", a[0],
				   "y1", a[1],
				   "x2", b[0],
				   "y2", b[1]);
	return fm.wrap("line", para, "");
    }


    /** The description of a single composite object, or a class
	of such objects, obtained by parsing its name
	(which may contain wildcards)
    */
    static class Description {
	boolean vertical=false;
	boolean wild = false;
	final String name;
	final static int ANY=0;
	/** Arrays of N=3 elements which contain values 1 2 3, or ANY */
	int[] sizeRank={3,3,3}, bright={3,3,3};
	/** 'q', 't' etc, or 'r', 'g', 'b'. May also contain '?' */
	String[] shapes=new String[N], colors=new String[N];
	
	public String toString() {
	    String s = "[Description: "+(vertical? "vertical" : "horizontal")+", sizes=" + Util.joinNonBlank("",sizeRank) +
		", brightness=" + Util.joinNonBlank("",bright) +
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
	private static int[] parseVal(String val) {
	    
	    int [] q = new int[N];
	    if (val.equals("*")) return new int[]{ANY, ANY, ANY};
	    int k=0;
	    for(int j=0; j<val.length(); j++) {
		if (k>=N)  throw new IllegalArgumentException("String too long: " + val);
		char c = val.charAt(j);
		if (c == '?') q[k++] = ANY;
		else if (c>='1' && c<='3') q[k++] = 1 + (c - '1');
		else if (c=='*' && j+1==val.length()) { // '*' at the end of string
		    while(k<N)  q[k++] = ANY;
		}
		else throw new IllegalArgumentException("Character " + c + " is not allowed. String=" + val);
	    }
	    if (k<N)  throw new IllegalArgumentException("String too short: " + val);
	    return q;
	}

	
	Description(String _name) {
	    name =  _name;

	    for(int j=0; j<N; j++) {
		shapes[j] = "q";
		colors[j] = "r";
	    }
	    
	    
	    final String prefix = "/composite/";
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
		    if (s.equals("h")) vertical=false;
		    else if (s.equals("v")) vertical=true;
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
		    if (c == 'r' || c=='g' || c=='b' || c=='?' ) colors[k] = "" + c;
		    else  throw new IllegalArgumentException("Illegal color char '" + c +"' in component="+s+", in name=" + name);
		    c = s.charAt(1);
		    if (c == 'q' || c=='t' || c=='s' || c=='c' || c=='?') shapes[k] = "" + c;
		    else  throw new IllegalArgumentException("Illegal shape char '" + c +"' in component="+s+", in name=" + name);
		    k++;
		} else  throw new IllegalArgumentException("Extra component="+s+" in name=" + name);			
	    }
		    
	}

	    String makeSvg() {
		Vector<String> v = new Vector<>();
		for(int j=0; j<N; j++) {
		    Element e = new Element(shapes[j], colors[j], sizeRank[j], bright[j]);
		    int cx = H/2, cy=H/2;
		    int delta = 2*(R+margin) * (j-1);
		    if (vertical) cy += delta;
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



	
    }

    /**
<?xml version="1.0" standalone="no"?>
<svg width="600" height="500" version="1.1" xmlns="http://www.w3.org/2000/svg">

  <rect x="10" y="10" width="80" height="80" stroke="black" fill="red"
      fill-opacity="1" stroke-width="8"/>
</svg>

     */
    private static String makeSvg(String name) {
	StringBuffer b = new StringBuffer("<?xml version=\"1.0\" standalone=\"no\"?>\n");
	String svgExtra = mkSvgParams("xmlns", "http://www.w3.org/2000/svg",
				      "width", ""+H,
				      "height", ""+H,
				      "version", "1.1");


	//	final String prefix = "/composite/";
	//	if (!name.startsWith(prefix)) throw new IllegalArgumentException("Illegal name: " + name + ". Names must start with " + prefix);
	//	name = name.substring(prefix.length());
	Description d = new 	Description(name);
	String body = d.makeSvg();
	b.append( fm.wrap( "svg", svgExtra, body) + "\n");	
	return b.toString();
    }

    
/*
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feather feather-arrow-up-right">
<g color="orange">
  <line x1="7" y1="17" x2="17" y2="7"></line>
  <polyline points="10 10 17 7 14 14"></polyline>
</g>
</svg>
*/    /**

               C
       A---------B
               D
     */
    private static String makeSvg0(String color, double alpha) {
	String svgExtra = mkSvgParams("xmlns", "http://www.w3.org/2000/svg",
				      "width", ""+H,
				      "height", ""+H,
				      "viewBox", ""+H+" "+H,
				      "fill", "none",
				      "stroke", "currentColor",
				      "stroke-width", "" +2,
				      "stroke-linecap", "round",
				      "stroke-linejoin", "round",
				      "class", "feather feather-arrow");

	Point[] a0 = {new Point(-R, 0),
		     new Point(R, 0), 
		     new Point(0.6*R, 0.2*R), 
		     new Point(0.6*R, -0.2*R) };

	
	Point[] a = Point.rotate(a0, alpha);
	
	String z = line(a[0], a[1]) + line(a[2], a[1]) + line(a[3], a[1]);

	z = fm.wrap( "g", "color=\"" + color+"\"", z);
	z = fm.wrap("svg", svgExtra, z);
	return z;
    }

    static NumberFormat fmt3d = new DecimalFormat("000");


    
	
    public static void main(String argv[]) throws IOException {
	for(String a: argv) {
	    //Description d = new Description(a);
	    System.out.println( makeSvg(a));
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

	

	    

    
}
