package edu.wisc.game.svg;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import  edu.wisc.game.formatter.*;

/** Utilities for generating SVG plots for ECD data */
public class SvgEcd {

    static final boolean needToLabelAxis = true;

    /** The size of the data field (in SVG coordinates) */
    static final int F=500;
    static final int lineHeight=20, ticLabelWidth=30;
    static final int xmarginL=ticLabelWidth + (needToLabelAxis? lineHeight:0), xmarginR=30,
	ymarginT=20,
	ymarginB= (needToLabelAxis? 2: 1) * lineHeight;
    /** The height of the image */
    static final int H=F+ ymarginT + ymarginB;
    static final int W=F+ xmarginL + xmarginR;

    static final public HTMLFmter fm =    HTMLFmter.htmlFmter;
 



    public static class Point implements Cloneable {

	public static void setScale(double _xRange, double _yRange) {
	    xRange =  _xRange;
	    yRange =  _yRange;
	}

	/** The range of the "science coordinates" that maps to the F x F
	    square in the SVG coordinates */
	private static double xRange=0, yRange=0;

	
	/** The "science coordinates", i.e. coordinates with respect
	    to the center of the image, with the Y axis going up */
	public double x,y;
	public Point(double _x, double _y) {
	    x=_x;
	    y=_y;
	}

	public Point copy() {
	    try {
		return (Point)clone();
	    } catch(CloneNotSupportedException ex) {
		throw new AssertionError();
	    }
		   
	}
	
	/** Counterclockwise rotation 
	    @param alpha in radians
	*/
	Point rotate(double alpha) {
	    double qx =x*Math.cos(alpha) - y*Math.sin(alpha);
	    double qy =y*Math.cos(alpha) + x*Math.sin(alpha);
	    return new Point(qx,qy);
	}
	/** In SVG coordinates of the point (with respect to the top
	    left corner, Y going down)
	*/
	public double[] svgCoord() {
	    Point p = rawPoint();
	    double[] xy = {p.x, p.y};
	    return xy;
	}
	/** Converts this Point with "science" coordinates to one with raw (svg) ones */
	public Point rawPoint() {
	    if (xRange <=0) throw new IllegalArgumentException("xRange not set");
	    if (yRange <=0) throw new IllegalArgumentException("yRange not set");

	    return new Point((xmarginL+(x*F)/xRange),
			     (ymarginT+F-(y*F)/yRange));
	}

	String[] svgCoordString() {
	    double[] q = svgCoord();
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

   
    /** name="val" .... */
    static class SvgElement {
	final String tag;
	String body = "";
	/** Parameters */
	private Vector<String> v = new 	Vector<>();
	SvgElement(String _tag) {
	    this(_tag, "");
	}
	SvgElement(String _tag, String _body) {
	    tag = _tag;
	    body = _body;
	}
		
	
	void addParams(String... nameVal) {
	    if ((nameVal.length % 2)!=0) throw new IllegalArgumentException("odd number of params");
	    for(int j=0; j<nameVal.length; j+=2) {
		v.add( nameVal[j] + "=\""+ nameVal[j+1]  +"\"");	    
	    }
	}

	public String toString() {
	    String para = String.join(" ", v);
	    return fm.wrap(tag, para, body);
	}

    }

    

    /** Chooses a suitable spacing between x tic labels */
    private static int chooseXStep(double xRange) {
	int step = 1;
	while(true) {
	    if (step * 10 >= xRange) return step;
	    step *= 2;
	    if (step * 10 >= xRange) return step;
	    step *= 5;
	}
    }

    public static String rawText(double x, double y, String s) {
	return rawText(x,y,s,null);
    }

    /** Puts some text into the specified position on the SVG canvas
	@param x The x position, in SVG coordinates
	@param y The y position, in SVG coordinates
     */
    public static String rawText(double x, double y, String s, String color) {
	return  rawTextE(x,y,s,color).toString();
    }
    
    public static SvgElement rawTextE(double x, double y, String s, String color) {
	SvgElement e = new SvgElement("text", s);
	// sans-serif vs. monospace
	e.addParams("x", ""+x,
		    "y", ""+y,
		    "font-family", "monospace"
		    //"font-family", "Arial, Helvetica, monospace"
		    );
				  
	if (color!=null) e.addParams("stroke", color);
	return e;

    }
	       

    
    /**
        Draws the frame around the data field, and prints the
	tic labels.
       <rect x="120" width="100" height="100" rx="15" />
    */
    public static String drawFrame(double xRange) {
	String color="black";
	SvgElement e = new SvgElement("rect");
	e.addParams("x", ""+xmarginL,
		    "y", ""+ymarginT,
		    "width", ""+F,
		    "height", ""+F
		    //"stroke",color,
		    );

	Vector<String> v = new Vector<>();
	v.add( e.toString());

	int n = 10;
	for(int j=0; j<=n; j++) {
	    double x = xmarginL;
	    double y = ymarginT + F - (F*j)/n;
	    v.add( rawLine( new Point(x,y), new Point(x+10, y)));
	    v.add( rawText(x-ticLabelWidth+1, y,  "" + j/(double)n));
	}


	int xStep = chooseXStep(xRange);
	Point.setScale(xRange, 1.0);
	for(int j=0; j*xStep <= xRange; j++) {
	    Point p = new Point( j*xStep, 0);
	    double[] a = p.svgCoord();
	    Point raw = new Point( a[0], a[1]);
	    Point raw1 = new Point(raw.x, raw.y - 10);
	    v.add( rawLine( raw, raw1));
	    v.add( rawText(raw.x, raw.y+ lineHeight -4, "" + (j*xStep)));
	}

	if (needToLabelAxis) {

	    String ylabel = "Cumulative fraction of all participants";
	    int x0 = lineHeight - 4, y0 = ymarginT + F - 100;
	    SvgElement el = rawTextE(x0, y0, ylabel, null);
	    el.addParams( "transform", "rotate(-90 "+x0+" " +y0+")");
	    v.add( el.toString());

	    // ZZZ
	    String xlabel = "m* values of participants";
	    v.add( rawText(xmarginL + 100, F+ymarginT+2*lineHeight-4, xlabel));
	}
	
	String s = String.join("\n", v);       
	s = fm.wrap( "g", "color=\"" + color+"\"", s);
	return s;


	
    }

    static String rawLine(Point a, Point b) {
	return rawLine(a,b,null);
    }
    public static String rawLine(Point a, Point b, String color) {
	SvgElement e = new SvgElement("line");
		    
	e.addParams( "x1", ""+a.x,
		     "y1", ""+a.y,
		     "x2", ""+b.x,
		     "y2", ""+b.y);
	if (color!=null) e.addParams("stroke", color);
	return e.toString();
    }

    /** <line x1="7" y1="17" x2="17" y2="7"></line> */   
    static String line(Point _a, Point _b) {
	return line(_a,_b,null);
    }
    public static String line(Point _a, Point _b, String color) {
	return rawLine(_a.rawPoint(), _b.rawPoint(), color);
    }

    /** <polyline points="0,100 50,25 50,75 100,0" />
     */
    static String polyline(Point[] v) {
	return polyline(v, null);
    }
    static String polyline(Point[] v, String color) {
	String q[] = new String[v.length];
    	for(int j=0; j<v.length; j++) {
	    q[j]  = String.join(",", v[j].svgCoordString());
    	}

    //	(Vector<Point>) {
	//	v.stream().map( a-> String.join(",", a.svgCoordString()))
	SvgElement e = new SvgElement("polyline");
	e.addParams( "points", String.join(" ", q));
	if (color!=null) e.addParams("stroke", color);
	return e.toString();
    }

    /*  <circle cx="50" cy="50" r="50" /> */
    public static String circle(Point center, double radius, String color) {
	return rawCircle(center.rawPoint(), radius, color);
    }
    public static String rawCircle(Point center, double radius, String color) {
	SvgElement e = new SvgElement("circle");
	e.addParams( "cx", ""+center.x,
		     "cy", ""+center.y,
		     "r", ""+radius);

	if (color!=null) e.addParams("stroke", color);
	return e.toString();
    }

    
/*
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feather feather-arrow-up-right">
<g color="orange">
  <line x1="7" y1="17" x2="17" y2="7"></line>
  <polyline points="10 10 17 7 14 14"></polyline>
</g>
</svg>
     */
    public static String makeSvgEcd(String color, double[] sample,
				     double xRange, double yRange) {

	Point.setScale(xRange, yRange);
	
	StringBuffer z = new StringBuffer();

	Point a = new Point(0,0);
	Vector<Point> vp = new Vector<>();
	vp.add(a.copy());

	for(int j = 0; j< sample.length;) {

	    // horizontal segment, if needed
	    double x2 = Math.min(sample[j],xRange);
	    if (x2 != a.x) {
		a.x  = x2;
		vp.add(a.copy());
	    }

	    if (a.x == xRange) break;

	    // vertical segment
	    for( ; j<sample.length && sample[j]==a.x; j++) {
		a.y ++;
	    }

	    vp.add(a.copy());
	}

	if (a.x < xRange) {
	    a.x =xRange;
	    vp.add(a.copy());
	}

	z.append(polyline( vp.toArray(new Point[0]), color));

	String s = z.toString();
	//String s = fm.wrap( "g", "color=\"" + color+"\"", s);
	return s;
    }

    static public String outerWrap(String s) {
	return outerWrap(s,W,H);
    }
    static public String outerWrap(String s, double W, double H) {
	
	SvgElement e= new SvgElement("svg", s);
	e.addParams("xmlns", "http://www.w3.org/2000/svg",
		    "width", ""+W,
		    "height", ""+H,
		    "viewBox", ""+W+" "+H,
		    "fill", "none",
		    "stroke", "currentColor",
		    "stroke-width", "" +1,
		    "stroke-linecap", "round",
		    "stroke-linejoin", "round",
		    "class", "feather feather-arrow");
	return e.toString();
    }
    
    static NumberFormat fmt3d = new DecimalFormat("000");

    /*
    public static void main(String argv[]) throws IOException {	
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
    }
    */
	

	    

    
}
