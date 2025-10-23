package edu.wisc.game.svg;

import java.io.*;
import java.util.*;

import  edu.wisc.game.util.Util;

/** Information about one curve. The object stores the value of some function
    f(x) in N equally spaced points. */
public class Curve {
    final double[] y;
    /** If non-null, the curve is extrapolated beyond its end point
	as a horizontal line, up to the specified X value
     */
    Double  extraX=null;
    //    Curve(int N) {
    //y = new double[N+1];
    //}
    public Curve(double [] _y) {
	y = _y;
    }
    public Curve(double [] _y, Double _extraX) {
	y = _y;
	extraX = _extraX;
    }
    /** The max value of y on the curve */
    public double getMaxY() {
	double maxY=0;
	for(int i=0; i<y.length; i++) {
	    maxY = Math.max(maxY, y[i]);
	}
	return maxY;
    }

    public double getLastY() {
	return y[ y.length-1];
    }
    
    /** Represents this curve as a SVG element. 
	The first point will be M x0 y0; after that, between two
	adjacent points,  dx = 1, dy *= yFactor. 
	@param yFactor Usually, negative
	@return Just the value of the "d" attibute of a PATH tag such
	as   <path d="M0 100 L10 100 L20 110 L30 140 L40 190 L50 260 L60 350 L70 260 L80 190" stroke="green" stroke-width="5"  fill="none" />
    */
    private String mkSvgPath(int x0, int y0, double xFactor, double yFactor) {
	Vector<String> v = new Vector<>();
	for(int i=0; i<y.length; i++) {
	    String s = (i==0? "M":"L") + (x0+xFactor*i)+ " " + (y0+yFactor*y[i]);
	    v.add(s);
	}
	return String.join(" " , v);
    }

    /** The extrapolated part, or null */
    private String mkExtraSvgPath(int x0, int y0, double xFactor, double yFactor) {
	if (extraX==null) return null;
	Vector<String> v = new Vector<>();
	int i=y.length-1; 
	String s =  "M" + (x0+xFactor*i)+ " " + (y0+yFactor*y[i]);
	v.add(s);
	s =  "L" + (x0+xFactor*extraX)+ " " + (y0+yFactor*y[i]);
	v.add(s);
	return String.join(" " , v);
    }

    /** Creates a curve that will be located within x from 0 to N, and within 
	y from 0 (top) to boxHeight, with a 10% gap on top.
	@return    <path d="..."   stroke="green" stroke-width="5"  fill="none" />
     */
    public String mkSvgPathElement( int boxHeight, String color, int strokWidth) {
	// the vertical scaling factor for the curve, computed so that a 10% gap
	// will remain between the top of the curve and the top of the thumbnail box
	double yFactor = -(double)boxHeight/(1.1 * getMaxY());
	return mkSvgPathElement(0, boxHeight, yFactor, color, strokWidth);
    }
	
    public String mkSvgPathElement(int x0, int y0, double xFactor, double yFactor,
				   String color, int strokWidth) {
	String s = "<path d=\"" +mkSvgPath(x0, y0, xFactor, yFactor) + "\" " +
	    "stroke=\""+color+"\" stroke-width=\""+strokWidth+"\"  "+
	    "fill=\"none\" />";

	String extraPath = mkExtraSvgPath(x0, y0, xFactor, yFactor);
	if (extraPath != null) s +=  "\n"+
				   "<path d=\"" + extraPath + "\" " +
				   "stroke=\""+color+"\" stroke-width=\""+strokWidth+"\"  "+
				   "stroke-dasharray=\"2\" " +
				   "fill=\"none\" />";
	
	return s;
	
    }

    /** @param useExtra If true, include the extrapolated sections of curves into the analysis */
    public static String mkMedianSvgPathElement(Curve[] curves, int x0, int y0, double xFactor, double yFactor,
						String color, int strokWidth, boolean useExtra) {
	return "<path d=\"" +mkMedianSvgPath(curves, x0, y0, xFactor, yFactor, useExtra) + "\" " +
	    "stroke=\""+color+"\" stroke-width=\""+strokWidth+"\"  "+
	    "fill=\"none\" />";
	
    }

    public String mkSvgPathElement(int x0, int y0, double yFactor, String color, int strokWidth) {	
	return  mkSvgPathElement(x0, y0, 1.0, yFactor, color, strokWidth);
    }


    private static String mkMedianSvgPath(Curve[] curves, int x0, int y0, double xFactor, double yFactor, boolean useExtra) {
	Vector<String> v = new Vector<>();

	for(int x=0; ; x++) {
	    Vector<Double> ya=new Vector<>(), yb=new Vector<>();
	    for(Curve c: curves) {
		boolean extra = useExtra && c.extraX!=null && x<=c.extraX;
		if (x<c.y.length) ya.add( c.y[x]);
		else if (extra) ya.add( c.getLastY());
		
		if (x+1<c.y.length) yb.add( c.y[x]);
		else if (extra) yb.add( c.getLastY());
	    }
	    if (ya.size()==0) break;
	    double ymeda = Util.median(ya);
	    String s = (x==0? "M":"L") + (x0+xFactor* x)+ " " + (y0+yFactor* ymeda);
	    v.add(s);
	    
	    if (yb.size()==0) break;
	    double ymedb = Util.median(yb);
	    if (ymedb!=ymeda) { // discontinuity
		s = "M" + (x0+xFactor* x)+ " " + (y0+yFactor* ymedb);
		v.add(s);		
	    }
	}	
	return String.join(" " , v);
    }

    
    
}

