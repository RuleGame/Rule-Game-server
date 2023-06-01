package edu.wisc.game.svg;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import  edu.wisc.game.formatter.*;

public class SvgEcd {

    static final int F=500, xmargin=30, ymargin=20;
    static final int H=F+ 2*ymargin;

    static final HTMLFmter fm =    HTMLFmter.htmlFmter;
 
    
    public static class Point implements Cloneable {

	public static void setScale(double _xRange, double _yRange) {
	    xRange =  _xRange;
	    yRange =  _yRange;
	}
	    
	private static double xRange=0, yRange=0;

	
	/** Coordinates with respect to the center of the image, with 
	    the Y axis going up */
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
	/** In SVG coordinates (with respect to the top left corner, 
	    Y going down)
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

	    return new Point((xmargin+(x*F)/xRange),
			     (ymargin+F-(y*F)/yRange));
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
    static String mkSvgParams(String... nameVal) {
	String s = "";
	if ((nameVal.length % 2)!=0) throw new IllegalArgumentException("odd number of params");
	Vector<String> v = new 	Vector<>();
	for(int j=0; j<nameVal.length; j+=2) {
	    v.add( nameVal[j] + "=\""+ nameVal[j+1]  +"\"");	    
	}
	return String.join(" ", v);
    }

    static String addSvgParams(String s, String... nameVal) {
	return s + " " + mkSvgParams(nameVal);
    }

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

    public static String rawText(double x, double y, String s, String color) {
	
	String para = mkSvgParams("x", ""+x,
				  "y", ""+y,
				  "font-family", "Arial, Helvetica, sans-serif");
				  
	if (color!=null) para = addSvgParams(para, "stroke", color);
	
	return fm.wrap("text", para, s);
    }
	       

    
    /**
       <rect x="120" width="100" height="100" rx="15" />
    */
    public static String drawFrame(double xRange) {
	String color="black";
	String para = mkSvgParams("x", ""+xmargin,
				  "y", ""+ymargin,
				  "width", ""+F,
				  "height", ""+F
				  //"stroke",color,
				  );

	Vector<String> v = new Vector<>();
	v.add( fm.wrap("rect", para, ""));

	int n = 10;
	for(int j=0; j<=n; j++) {
	    double x = xmargin;
	    double y = ymargin + F - (F*j)/n;
	    v.add( rawLine( new Point(x,y), new Point(x+10, y)));
	    v.add( rawText(x-xmargin+1, y,  "" + j/(double)n));
	}


	int xStep = chooseXStep(xRange);
	Point.setScale(xRange, 1.0);
	for(int j=0; j*xStep <= xRange; j++) {
	    Point p = new Point( j*xStep, 0);
	    double[] a = p.svgCoord();
	    Point raw = new Point( a[0], a[1]);
	    Point raw1 = new Point(raw.x, raw.y - 10);
	    v.add( rawLine( raw, raw1));
	    v.add( rawText(raw.x, raw.y+ ymargin -1, "" + (j*xStep)));
	}
	
	String s = String.join("\n", v);
	
	s = fm.wrap( "g", "color=\"" + color+"\"", s);
	return s;


	
    }

    static String rawLine(Point a, Point b) {
	return rawLine(a,b,null);
    }
    public static String rawLine(Point a, Point b, String color) {
	String para = mkSvgParams( "x1", ""+a.x,
				   "y1", ""+a.y,
				   "x2", ""+b.x,
				   "y2", ""+b.y);
	if (color!=null) para = addSvgParams(para,"stroke", color);
	return fm.wrap("line", para, "");
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
	
	String para = mkSvgParams( "points", String.join(" ", q));
	if (color!=null) para = addSvgParams(para,"stroke", color);
	return fm.wrap("polyline", para, "");
    }

    /*  <circle cx="50" cy="50" r="50" /> */
    public static String circle(Point center, double radius, String color) {
	return rawCircle(center.rawPoint(), radius, color);
    }
    public static String rawCircle(Point center, double radius, String color) {
	String para = mkSvgParams( "cx", ""+center.x,
				   "cy", ""+center.y,
				   "r", ""+radius);

	if (color!=null) para = addSvgParams(para,"stroke", color);
	return fm.wrap("circle", para, "");
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
	return outerWrap(s,H,H);
    }
    static public String outerWrap(String s, double W, double H) {
	String svgExtra = mkSvgParams("xmlns", "http://www.w3.org/2000/svg",
				      "width", ""+W,
				      "height", ""+H,
				      "viewBox", ""+W+" "+H,
				      "fill", "none",
				      "stroke", "currentColor",
				      "stroke-width", "" +1,
				      "stroke-linecap", "round",
				      "stroke-linejoin", "round",
				      "class", "feather feather-arrow");

	s = fm.wrap("svg", svgExtra, s);
	return s;
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
