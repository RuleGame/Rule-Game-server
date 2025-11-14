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
	@param If not null, the "deterministic jitter", in the screen y coordinate
	@return Just the value of the "d" attibute of a PATH tag such
	as   <path d="M0 100 L10 100 L20 110 L30 140 L40 190 L50 260 L60 350 L70 260 L80 190" stroke="green" stroke-width="5"  fill="none" />
    */
    private String mkSvgPath(int x0, int y0, double xFactor, double yFactor, int [] offset) {
	Vector<String> v = new Vector<>();
	for(int i=0; i<y.length; i++) {
	    double sy = y0+yFactor*y[i];
	    if (offset!=null) sy -= offset[i];
	    String s = (i==0? "M":"L") + (x0+xFactor*i)+ " " + sy;
	    v.add(s);
	}
	return String.join(" " , v);
    }

    /** The extrapolated part, or null */
    private String mkExtraSvgPath(int x0, int y0, double xFactor, double yFactor, int [] offset) {
	if (extraX==null) return null;
	Vector<String> v = new Vector<>();
	int i=y.length-1; 
	double sy = y0+yFactor*y[i];
	if (offset!=null) sy -= offset[i];

	String s =  "M" + (x0+xFactor*i)+ " " + sy;
	v.add(s);
	s =  "L" + (x0+xFactor*extraX)+ " " + sy;
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
	//	final String dash[] =  {"", "2"};
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

	    //System.out.println("p("+j+")=" +p);
	    
	    if (p==null) continue;
	    
	    String s = "<path d=\"" +p + "\" " +
		"stroke=\""+color+"\" stroke-width=\""+strokWidth+"\"  ";
	    if (!dash[j].equals("")) s += "stroke-dasharray=\""+dash[j]+"\" ";
	    s += " fill=\"none\" />";

	    //System.out.println("s("+j+")=" +s);
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

    /** @return {f(x), f(x+0)} where f(x)=median(curves)(x) */
    private static double[] medianY(Curve[] curves, boolean useExtra, int x) {
	Vector<Double> ya=new Vector<>(), yb=new Vector<>();
	for(Curve c: curves) {
	    boolean extra = useExtra && c.extraX!=null && x<=c.extraX;
	    if (x<c.y.length) ya.add( c.y[x]);
	    else if (extra) ya.add( c.getLastY());
		
	    if (x+1<c.y.length) yb.add( c.y[x]);
	    else if (extra) yb.add( c.getLastY());
	}
	if (ya.size()==0) return null;
	double[] m = {Util.median(ya), 0};
	m[1] = (yb.size()==0) ? m[0]: 	Util.median(yb);
	return m;
    }


    private static String mkMedianSvgPath(Curve[] curves, int x0, int y0, double xFactor, double yFactor, boolean useExtra) {
	Vector<String> v = new Vector<>();

	for(int x=0; ; x++) {
	    double[] ym = medianY(curves, useExtra, x);
	    if (ym==null) break;

	    String s= (x==0?"M":"L") + (x0+xFactor*x)+" " + (y0+yFactor*ym[0]);
	    v.add(s);
	    
	    if (ym[1]!=ym[0]) { // discontinuity
		s = "M" + (x0+xFactor* x)+ " " + (y0+yFactor* ym[1]);
		v.add(s);		
	    }
	}	
	return String.join(" " , v);
    }

    
    public static double maxMedianY(Curve[] curves, boolean useExtra) {
	double m = 0;
	for(int x=0; ; x++) {
	    double[] ym = medianY(curves, useExtra, x);
	    if (ym==null) break;
	    m = Math.max(m, Math.max(ym[0],ym[1]));
	}
	return m;
    }


    /** The confidence interva calculations as per 
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
@param needShading: if true, shade the area; if false, just draw an error bar at the end

    */
    public static String mkShading(Curve[] curves, int x0, int y0, double xFactor, double yFactor, boolean useExtra, String color, boolean needShading) {
	Vector<String> w = new Vector<>();
	double barY[] = {0,0};
	int lastX = 0;

	String colorB = "orange";
	
	if (needShading) w.add( "<g stroke=\""+color+"\" stroke-width=\""+1+"\"  "+
	       "fill=\"" + color + "\" " +
	       "stroke-opacity=\"0.25\" " +
	       "fill-opacity=\"0.5\">");
	else w.add( "<g stroke=\""+color+"\" stroke-width=\""+5+"\" strike-opacity=\"0.5\">");
	//else w.add( "<g stroke=\""+colorB+"\" stroke-width=\""+3+"\">");


	for(int x=1; ; x++) {
	    Vector<Double> ya=new Vector<>(), yb=new Vector<>();
	    for(Curve c: curves) {
		boolean extra = useExtra && c.extraX!=null && x<=c.extraX;
		if (x<c.y.length) {
		    ya.add( c.y[x-1]);
		    yb.add( c.y[x]);
		} else if (extra) {
		    ya.add( c.getLastY());
		    yb.add( c.getLastY());
		}
	    }
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
		w.add( "<path d=\"" +q + "\">");
	    }
	}


	int r=10;
	if (!needShading && lastX > 0) { // error bar at the right end
	    double sx = x0+xFactor*lastX;
	    double sy[] = {y0+yFactor* barY[0], y0+yFactor* barY[1]};
	    for(int j=0; j<2; j++) {
		w.add("<line x1=\""+(sx-r)+"\" y1=\""+sy[j]+"\" x2=\""+(sx+r)+"\" y2=\""+sy[j]+ "\"/>");
	    }
	    w.add("<line x1=\""+sx+"\" y1=\""+sy[0]+"\" x2=\""+sx+"\" y2=\""+sy[1]+ "\"/>");
	}
	w.add( "</g>");
	return String.join("\n" , w);	
    }


    /** 
     */
    static public String mkSvgNoOverlap( Curve[] curves,
				  int x0, int y0, double xFactor, double yFactor,
				  String color, int strokWidth) {

	NoOverlap noo = new NoOverlap();
	Vector<String> v = new Vector<>();
	for(Curve cu: curves) {	    
	    v.add( cu.mkSvgPathElement2(x0,y0,xFactor, yFactor, color, strokWidth, noo));
	}
	return String.join("\n", v);
    }
    
}

