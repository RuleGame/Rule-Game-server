package edu.wisc.game.svg;

import java.io.*;
import java.util.*;

/** Information about one curve. The object stores the value of some function
    f(x) in N equally spaced points. */
public class Curve {
    final double[] y;
    Curve(int N) {
	y = new double[N+1];
    }
    public Curve(double [] _y) {
	y = _y;
    }
    /** The max value of y on the curve */
    public double getMaxY() {
	double maxY=0;
	for(int i=0; i<y.length; i++) {
	    maxY = Math.max(maxY, y[i]);
	}
	return maxY;
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
	return "<path d=\"" +mkSvgPath(x0, y0, xFactor, yFactor) + "\" " +
	    "stroke=\""+color+"\" stroke-width=\""+strokWidth+"\"  "+
	    "fill=\"none\" />";
	
    }
    public String mkSvgPathElement(int x0, int y0, double yFactor, String color, int strokWidth) {	
	return  mkSvgPathElement(x0, y0, 1.0, yFactor, color, strokWidth);
    }

    
}

