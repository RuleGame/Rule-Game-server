package edu.wisc.game.svg;

import java.io.*;
import java.util.*;

import  edu.wisc.game.util.Util;

/** Information about one curve. The object stores the value of some function
    f(x) in N equally spaced points. */
public class Curve {
    final double[] y;
    /** The extrapolated section of the curve (to be drawn in a
	different style) is from y[startExtra] to the end of y[].
	Curves without an extrapolated section have startExtra=y.length-1
    */
    final int startExtra;

    /** 
	Controls how the main section and the extrapolated section (if
	any) of this curve are drawn. A null element means "skip", ""
	means solid line */
    String[] dash = {"", "2"};
    public String[] getDash() { return dash; }
    public void setDash(String[] _dash) { dash = _dash; }

    //    Curve(int N) {
    //y = new double[N+1];
    //}
    public Curve(double [] _y) {
	y = _y;
	startExtra=y.length-1;
    }

    public Curve(double [] _y, int _startExtra) {
	y = _y;
	startExtra = _startExtra;
    }

    /** The max value of y on the curve */
    public double getMaxY() {
	double maxY=0;
	for(int i=0; i<y.length; i++) {
	    maxY = Math.max(maxY, y[i]);
	}
	return maxY;
    }

    double getLastY() {
	return y[ y.length-1];
    }

    //    Vector<String> debugLabels = null;

    
    /** Represents the "main" (non-extrapolated) part of the curve as a SVG element. 
	The first point will be M x0 y0; after that, between two
	adjacent points,  dx = 1, dy *= yFactor. 
	@param yFactor Usually, negative
	@param If not null, the "deterministic jitter", in the screen y coordinate
	@return Just the value of the "d" attibute of a PATH tag such
	as   <path d="M0 100 L10 100 L20 110 L30 140 L40 190 L50 260 L60 350 L70 260 L80 190" stroke="green" stroke-width="5"  fill="none" />
    */
    private String mkSvgPath(int x0, int y0, double xFactor, double yFactor, int [] offset) {
	Vector<String> v = new Vector<>();
	//debugLabels = new Vector<>();
	boolean start = true;
	for(int i=0; i<=startExtra; i++) {
	    double sx = (x0+xFactor*i);
	    double sy = y0+yFactor*y[i];
	    if (offset!=null) sy -= offset[i];
	    
	    String s = (start? "M":"L") + sx+ " " + sy;
	    start = false;
	    v.add(s);

	    //	    s = "<text x=\"" +sx + "\" y=\"" +sy + "\" fill=\"black\">" +    y[i] + "</text>";
	    //debugLabels.add(s);
	}
	return String.join(" " , v);
    }

    /** The extrapolated part, or null */
    private String mkExtraSvgPath(int x0, int y0, double xFactor, double yFactor, int [] offset) {
	if (startExtra==y.length-1) return null;
	Vector<String> v = new Vector<>();
	boolean start = true;
	for(int i=startExtra; i<y.length; i++) {
	    double sx = (x0+xFactor*i);
	    double sy = y0+yFactor*y[i];
	    if (offset!=null) sy -= offset[i];	    
	    String s = (start? "M":"L") + sx+ " " + sy;
	    start = false;
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
	return mkSvgPathElement2(x0, y0, xFactor, yFactor,
				 color, strokWidth, null);
    }

    /**
       @param noo if non-null, used to provide "deterministic jitter"
       of overlapping horizontal segments 
	    
    */
    public String mkSvgPathElement2(int x0, int y0, double xFactor, double yFactor,
				    String color, int strokWidth, NoOverlap noo) {

	Vector<String> v = new Vector<>();
	int[] offset = (noo==null)? new int[y.length] :   noo.add(this);

	for(int j=0; j<2; j++) {

	    if (dash[j]==null) continue;
	    
	    String p = (j==0)?
		mkSvgPath(x0, y0, xFactor, yFactor, offset):
		mkExtraSvgPath(x0, y0, xFactor, yFactor, offset);

	    if (p==null) continue;
	    
	    String s = "<path d=\"" +p + "\" " +
		"stroke=\""+color+"\" stroke-width=\""+strokWidth+"\"  ";
	    if (!dash[j].equals("")) s += "stroke-dasharray=\""+dash[j]+"\" ";
	    s += " fill=\"none\" />";

	    v.add(s);
	}	    
	return String.join("\n", v);	
    }

    /** @param useExtra If true, include the extrapolated sections of curves into the analysis */
    public static String mkMedianSvgPathElement(Curve[] curves, int x0, int y0, double xFactor, double yFactor,
						String color, int strokWidth, String dash, String linecap, String opacity,
						boolean useExtra) {
	String s = "<path d=\"" +mkMedianSvgPath(curves, x0, y0, xFactor, yFactor, useExtra) + "\" " +
	    "stroke=\""+color+"\" stroke-width=\""+strokWidth+"\"  ";
	if (dash!=null && !dash.equals(""))  s += "stroke-dasharray=\""+dash+"\" ";
	if (linecap!=null) s+= "stroke-linecap=\""+linecap+"\" ";
	if (opacity!=null) s+= "stroke-opacity=\""+opacity+"\" ";
	s += "fill=\"none\" />";
	return s;
	
    }

    public String mkSvgPathElement(int x0, int y0, double yFactor, String color, int strokWidth) {	
	return  mkSvgPathElement(x0, y0, 1.0, yFactor, color, strokWidth);
    }

    // zzzz
    /** @return {f(x), f(x+0)} where f(x)=median(curves)(x) */
    private static class MedianY {
	final double[] m = {0,0};
	final int curveCnt;
	MedianY(Curve[] curves, boolean useExtra, int x) {
	    Vector<Double> ya=new Vector<>(), yb=new Vector<>();
	    for(Curve c: curves) {
		boolean extra = useExtra && x<c.y.length;
		if (x<=c.startExtra || extra) ya.add( c.y[x]);
		if (x+1<=c.startExtra || extra) yb.add( c.y[x]);
		
	    }
	    curveCnt = ya.size();
	    if (ya.size()==0) return;
	    m[0] = Util.median(ya);
	    m[1] = (yb.size()==0) ? m[0]: 	Util.median(yb);
	}
    }


    private static String mkMedianSvgPath(Curve[] curves, int x0, int y0, double xFactor, double yFactor, boolean useExtra) {
	Vector<String> v = new Vector<>();
	//	System.out.println("mkMedian: in");


	int breakPoint=suggestBreakPoint(curves);
	int curveCnt = 0;

	
	for(int x=0; ; x++) {
	    MedianY ym = new MedianY(curves, useExtra, x);
	    if (ym.curveCnt==0) break;

	    if (x<=breakPoint) curveCnt = ym.curveCnt;
	    // stop shading once curves start disappearing, to avoid
	    // a "drooping end" of the median
	    if (x>breakPoint && ym.curveCnt<curveCnt) break;
	    
	    String s= (x==0?"M":"L") + (x0+xFactor*x)+" " + (y0+yFactor*ym.m[0]);
	    v.add(s);
	    
	    if (ym.m[1]!=ym.m[0]) { // discontinuity
		s = "M" + (x0+xFactor* x)+ " " + (y0+yFactor* ym.m[1]);
		v.add(s);		
	    }
	}
	//	System.out.println("mkMedian: have "+v.size());
	return String.join(" " , v);
    }

    /** @return The max Y value of the median curve of the specified bundle
	of curves */
    public static double maxMedianY(Curve[] curves, boolean useExtra) {
	double m = 0;
	for(int x=0; ; x++) {
	    MedianY ym = new MedianY(curves, useExtra, x);
	    if (ym.curveCnt==0) break;
	    m = Math.max(m, Math.max(ym.m[0],ym.m[1]));
	}
	return m;
    }


    /** The confidence interval calculations as per 
https://www.statology.org/confidence-interval-for-median/
<pre>
     We can use the following formula to calculate the upper and lower bounds of a confidence interval for a population median:

j: nq  –  z * sqrt( nq(1-q))
k: nq  +  z * sqrt(nq(1-q))

where:

n: The sample size
q: The quantile of interest. For a median, we will use q = 0.5.
z: The z-critical value
We round j and k up to the next integer. The resulting confidence interval is between the jth and kth observations in the ordered sample data.

z=1.96 for the 95% confidence interval
</pre>

I subtract 0.5 from j and k, in order to have a symmetric interval centered
on  n/2 - 0.5 (the zero-based indexes ranging from 0 to n-1)

<p> Or see the ref here:
https://www-users.york.ac.uk/~mb55/intro/cicent.htm

@param color the shading color
@param needShading: if true, shade the area; if false, just draw an error bar at the end of the shading zone
@param barOffset: if we draw error bars, shift it to the right by this much
    */
    public static String mkShading(Curve[] curves, int x0, int y0, double xFactor, double yFactor, boolean useExtra, String color, boolean needShading, int barOffset) {
	Vector<String> w = new Vector<>();
	double barY[] = {0,0};
	int lastX = 0;

	String colorB = "orange";
	
	if (needShading) w.add( "<g stroke=\""+color+"\" stroke-width=\""+1+"\"  "+
	       "fill=\"" + color + "\" " +
	       "stroke-opacity=\"0.25\" " +
	       "fill-opacity=\"0.5\">");
	else w.add( "<g stroke=\""+color+"\" stroke-width=\""+5+"\" strike-opacity=\"0.5\">");


	int breakPoint=suggestBreakPoint(curves);
	int curveCnt = 0;


	for(int x=1; ; x++) {
	    Vector<Double> ya=new Vector<>(), yb=new Vector<>();
	    for(Curve c: curves) {
		boolean extra = useExtra && x<c.y.length;
		if (x<=c.startExtra || extra) {
		    ya.add( c.y[x-1]);
		    yb.add( c.y[x]);
		}
	    }

	    if (x<=breakPoint) curveCnt = ya.size();
	    // stop shading once curves start disappearing, to avoid
	    // a "drooping end" of the median // zzz
	    if (x>breakPoint && ya.size()<curveCnt) break;
	    
	    if (ya.size()<5) break;
	    Double[] a = ya.toArray(new Double[0]);
	    Arrays.sort(a);
	    Double[] b = yb.toArray(new Double[0]);
	    Arrays.sort(b);

	    final int n = a.length;
	    final double z = 1.96;
	    //j: nq
	    double r = z * Math.sqrt( n * 0.25);		
	    int j0 =  (int) Math.round((n-1)*0.5 - r);
	    int j1 =  (int) Math.round((n-1)*0.5 + r);
	    double[] low = {a[j0], barY[0]=b[j0]}, high = {a[j1], barY[1]=b[j1]};	       	
	    lastX = x;
	    
	    if (needShading) {
		String[] v = {
		    "M" + (x0+xFactor*(x-1))+ " " + (y0+yFactor* low[0]),
		    "L" + (x0+xFactor*x)+ " " + (y0+yFactor* low[1]),
		    "L" + (x0+xFactor*x)+ " " + (y0+yFactor* high[1]),
		    "L" + (x0+xFactor*(x-1))+ " " + (y0+yFactor* high[0])};
	    
		String q = Util.joinNonBlank(" " , v);
		w.add( "<path d=\"" +q + "\"/>");
	    }
	}


	int r=10;
	if (!needShading && lastX > 0) { // error bar at the right end
	    double sx = x0+xFactor*lastX + barOffset;
	    double sy[] = {y0+yFactor* barY[0], y0+yFactor* barY[1]};
	    for(int j=0; j<2; j++) {
		w.add("<line x1=\""+(sx-r)+"\" y1=\""+sy[j]+"\" x2=\""+(sx+r)+"\" y2=\""+sy[j]+ "\"/>");
	    }
	    w.add("<line x1=\""+sx+"\" y1=\""+sy[0]+"\" x2=\""+sx+"\" y2=\""+sy[1]+ "\"/>");
	}
	w.add( "</g>");
	return String.join("\n" , w);	
    }

	// The heuristic: if, at some point above 80% of the
	// length of the longest curve, some curves start disappear
	// (the AAIH_C phenomenon, because the curves' length varies
	// slightly), then stop shading at that point.
    static private int suggestBreakPoint(Curve[] curves) {
	int maxLen = 0;
	for(Curve c: curves) maxLen = Math.max(maxLen, c.y.length);
	int breakPoint = (int)(0.8 * maxLen);
	return breakPoint;
    }

    /** Draws several curves, optionally adjusted with the "no-overlap" feature
	@param noOverlap If true, turn on the "no-overlap" adjustment, so
	that overlapping (coinciding) horizontal segments of two or several
	curves would be slightly shifted relative to each other (stacked one
	on top of the other), so that a bundle of coinciding curve segments
	would be visually different from just a single curve.
     */
    static public String mkSvgNoOverlap( Curve[] curves,
					 int x0, int y0, double xFactor, double yFactor,
					 String color, int strokWidth, boolean noOverlap) {

	NoOverlap noo = (noOverlap? new NoOverlap() : null);
	Vector<String> v = new Vector<>();
	for(Curve cu: curves) {	    
	    v.add( cu.mkSvgPathElement2(x0,y0,xFactor, yFactor, color, strokWidth, noo));
	    //v.addAll(cu.debugLabels);
	}
	return String.join("\n", v);
    }
    
}

