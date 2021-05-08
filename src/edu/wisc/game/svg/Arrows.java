package edu.wisc.game.svg;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import  edu.wisc.game.formatter.*;

public class Arrows {

    static final int R=20, margin=4;
    static final int H=2*(R+margin);

    static final HTMLFmter fm =    HTMLFmter.htmlFmter;
 
    
    private static class Point {
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

    /** <line x1="7" y1="17" x2="17" y2="7"></line> */   
    static String line(Point _a, Point _b) {
	String[] a = _a.svgCoordString(), b = _b.svgCoordString();
	String para = mkSvgParams( "x1", a[0],
				   "y1", a[1],
				   "x2", b[0],
				   "y2", b[1]);
	return fm.wrap("line", para, "");
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
    private static String makeSvg(String color, double alpha) {
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

	

	    

    
}
