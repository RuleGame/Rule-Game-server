package edu.wisc.game.tools;
 
import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.svg.Curve;

/** An auxiliary  class for BuildCurves.
<p>
   Sample usage:
	    SvgPlot svg = new SvgPlot(W, H, maxX);
	    svg.doOverlap = false;
	    svg.adjustMaxY(curves, randomCurves, useExtra);
	    svg.addPlot(curves, randomCurves, useExtra);
	    String plot = svg.complete();
*/
class SvgPlot {
	Vector<String> sections = new Vector<>();
	int W, H;
	double maxX, maxY=0;
	boolean doOverlap = false;
	
	SvgPlot(int _W, int _H, int _maxX) {
	    W = _W;
	    H = _H;
	    maxX = roundUp(_maxX);
	}

	/** Should only be called once maxY is known

	    <P>
	    Elsewhere (in Curve.*), y0=H, yFactor = -H/maxY,  y=y0 * yFactor* mathY
	 */
	private Vector<String> mkGrid() {
	    //System.out.println("mkGrid maxX="+maxX+", maxY=" + maxY);
	    double xFactor = W/maxX;
	    Vector<String> v = new Vector<>();
	    v.add( "<rect width=\""+ W+ "\" height=\"" + H + "\" " +
		   "style=\"fill:rgb(240,240,240);stroke-width:1;stroke:rgb(0,0,0)\"/>"); 
	    for(int m: ticPoints((int)maxX)) {
		double x = m*xFactor, y=H+15;
		v.add("<text x=\"" +(x-10) + "\" y=\"" +y + "\" fill=\"black\">" +
		      m + "</text>");
		v.add("<line x1=\""+x+"\" y1=\""+H+"\" x2=\""+x+"\" y2=\"0\" stroke=\"black\" stroke-dasharray=\"3 5\"/>");
	    }

	    //System.out.println("mkGrid ti y=");
	    //System.out.println(Util.joinNonBlank(", ",     ticPoints((int)maxY)));
	    for(int m: ticPoints((int)maxY)) {

		//y= H -m*H/maxY);
		
		double x = 0, y= (int)( (maxY-m)*H/maxY);
		v.add("<text x=\"" +(x-20) + "\" y=\"" +y + "\" fill=\"black\">" +
		      m + "</text>");
		v.add("<line x1=\"0\" y1=\""+y+"\" x2=\""+W+"\" y2=\""+y+"\" stroke=\"black\" stroke-dasharray=\"3 5\"/>");
	    }
	    return v;
	}

	Vector<String> addLegendPair(String[] color, String[] key) {
	    Vector<String> v = new Vector<>();
	    int j=0;
	    int L = 25;
	    int x1 = 10, xmid = x1+L, x2 = x1+2*L, xt = x2+10;
	    int y = 25;
	    for(; j<2; j++) {
		v.add("<line x1=\""+x1+"\" y1=\""+y+"\" x2=\""+x2+"\" y2=\""+y+"\" stroke=\""+color[j]+"\" stroke-width=\"5\"/>");
		v.add("<text x=\"" +xt + "\" y=\"" +y + "\" fill=\"black\">" +
		      BuildCurves.basicKey(key[j]) + "</text>");	
		y += 30;
	    }

	    for(int k=0; k<2; k++) {
		int ax1 = x1 + k*L;
		int ax2 = ax1 + L;

		//v.add( Curve.mkMedianSvgPathElement(randomCurves, 0,H,xFactor, yFactor, colors[2],6,
		String dash = "0.01 8";
		String linecap = "round",
		    opacity = "0.4";
		
		v.add("<line x1=\""+ax1+"\" y1=\""+y+"\" x2=\""+ax2+"\" y2=\""+y+"\" stroke=\""+color[k]+"\" stroke-width=\"6\" " +
		      "stroke-dasharray=\""+dash+"\" "+
		      "stroke-linecap=\""+linecap+"\" "+
		      "stroke-opacity=\""+opacity+"\" />");

	    }
	    v.add("<text x=\"" +xt + "\" y=\"" +y + "\" fill=\"black\" >" +
		  "random player</text>");	
	    
	    sections.addAll(v);
	    return v;
	}

	
	String complete() {
	    int width = W+70;
	    int height = H+60;
	    
	    Vector<String> v = new Vector<>();
	    v.add( "<svg   xmlns=\"http://www.w3.org/2000/svg\" width=\"" +
		   width + "\" height=\"" + height +
		   "\" viewBox=\"-40 -20 " + (W+50) + " " + (H+40)+"\">");

	    v.addAll(mkGrid());
	    v.addAll(sections);
	    v.add( "</svg>");
	    return String.join("\n", v);
	}
	    
	/** This must be called before any plotting.
	    @param randomCurves If not empty, also plot the median of these curves
	*/
	void adjustMaxY(Curve[] curves, Curve[] randomCurves, boolean useExtra) {
	    if (curves.length==0) throw new IllegalArgumentException("No data have been collected");
		//return "No data have been collected";
	
	    for(Curve curve: curves) {
		maxY = Math.max(maxY, curve.getMaxY());
	    }
	    if (randomCurves.length>0) {
		maxY = Math.max(maxY, Curve.maxMedianY(randomCurves, useExtra));
	    }		
	}
    
	/** Produces a SVG element for a bundle of curves, plus their
	    median etc, and adds it to "sections".
	    @param colors {"green", "red", "blue", "lightgrey"} = colors of {the individual players curve, median, random, shading}
	    @param randomCurves If not empty, also plot the median of these curves
	    @param If this method is called multiple times (to draw multiple bundles of curves on the same plot), this is the sequence number (0, 1,...) of the current bundle  of curves
	*/
	void  addPlot(Curve[] curves, Curve[] randomCurves, boolean useExtra, String colors[], int sequenceNumber) {
	    //if (curves.length==0) return "No data have been collected";

	    maxY *= 1.01;	    // give some blank space above the highest curve
	    
	    double yFactor = -H/maxY; // getMaxY();
	    double xFactor = W/maxX;
	
	    Vector<String> v = new Vector<>();
	    boolean needShading =  (colors[3]!=null);

	    v.add( Curve.mkShading(curves, 0,H, xFactor, yFactor, useExtra,
				   (needShading? colors[3]: colors[0]), needShading, sequenceNumber * 8));

	    v.add( Curve.mkSvgNoOverlap(curves, 0, H, xFactor, yFactor, colors[0],1, !doOverlap));

	    v.add( Curve.mkMedianSvgPathElement(curves, 0,H,xFactor, yFactor, colors[1],3, null, null, null, useExtra));

	    if (randomCurves.length>0) {
		v.add( Curve.mkMedianSvgPathElement(randomCurves, 0,H,xFactor, yFactor, colors[2],6, "0.01 8", "round", "0.4", useExtra));
	    }
	    sections.addAll(v);
	}

    /** Compute a round number (with just one non-zero digit) that's
	greater or equal to x
    */
    static int roundUp(int x) {
	int m=1;
	while(m*10 < x)  m*=10;
	int j=(x/m) + 1;
	return m*j;
    }
    
    /** Tic points */
    static Integer[] ticPoints(int W) {
	int m = 1;
	while(m*10<W) {
	    m *= 10;
	}

	int step = (m*6 < W)? 2 : 1;
	
	Vector<Integer> v = new Vector<>();
	for(int j=step; j<=10 && m*j <=W; j+=step) {
	    v.add( m*j);
	}
	return v.toArray(new Integer[0]);
	
    }


}

