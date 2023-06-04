package edu.wisc.game.tools.pooling;


import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.text.*;

import javax.persistence.*;

import org.apache.commons.math3.stat.inference.*;

import edu.wisc.game.util.*;
import edu.wisc.game.tools.*;
import edu.wisc.game.tools.MwByHuman.MwSeries;
import edu.wisc.game.tools.MwByHuman.PrecMode;
import edu.wisc.game.svg.*;
import edu.wisc.game.svg.SvgEcd.Point;
/*
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.math.*;
*/
import edu.wisc.game.formatter.*;


/*  Hierarchical agglomerative clustering of ECD objects */
public class Clustering {

    static double beta = 0.5;
    
    /** Level-0 nodes are leaves (contains one ECD object).
	Higher-level nodes have 2 children each.
     */
    static class Node {
	final Ecd ecd;
	Node[] children;
	final int level;
	final int width;
	final String label;
	final double dist;
	Node(Ecd _ecd) {
	    ecd = _ecd;
	    children = null;
	    level = 0;
	    width = 1;
	    label = ecd.label;
	    dist = 0;
	}
	Node(Node a, Node b, double _dist) {
	    ecd = a.ecd.merge(b.ecd);
	    children = new Node[] { a, b};
	    level = Math.max( a.level, b.level) + 1;
	    width = a.width + b.width;
	    label = a.label + "+" + b.label;
	    dist=_dist;
	}

	public String toString() {
	    if (children==null) return label;
	    String s = "" + dist + "( "+ Util.joinNonBlank(", ", children)+")";
	    return s;
	}

	final static boolean useDistance = true;
	final static int DX = 500, // 100,
	    DY = (useDistance? 60:50),  leftmargin=10, rightmargin=30, ymargin=20;

	/** @return [xsize, ysize] */
	int[] boxSize() {
	    int[] bb = {leftmargin + rightmargin +
			(int)Math.round(useDistance?
					DX:
					//DX*(maxDistance()+1):
					DY*(level+1)),
			2*ymargin +		 DY*width };
	    return bb;
	}


	/** Lists the "biggest small clusters"
	    @param results Put them all here
	 */
	void listPools(Vector<Ecd> results) {
	    if (level==0 || dist<beta) results.add(ecd);
	    else {
		for(Node child: children) child.listPools(results);
	    }
	}
	
	/** The "height" (sum of distances) of the longest "path to a leaf"
	    from this node. This was used to properly size the display window.
	    Deprecated later.
	*/
	private double maxDistance()	{
	    double d = dist;
	    if (children!=null) d += Math.max(children[0].maxDistance(),
					      children[1].maxDistance());
	    return d;
	}
	    

	String toSvg() {
	    return toSvg(leftmargin, ymargin, width*DY);
	}

	/** The y-offset of the center of this node */
	private double dyc(double ysize) {
	    return (useDistance && children!=null) ?
		(ysize * children[0].width)/width :
		0.5 * ysize;
	}

	
	/** The root is on the left. (So that width is vertical and height horizontal)
	    @param x0 top left corner
	    @param y0 top left corner
	    @param ysize How much vertical space is that tree allowed to take
	*/
	String toSvg(final double x0, final double y0, final double ysize) {

	    
	    Vector<String> v = new Vector<>();
	    
	    //-- center of this node
	    final double y = y0 + dyc(ysize);
	    
	    String labelColor = "red", distColor = "green", lineColor = "black";
	    NumberFormat fmt = new DecimalFormat("0.000");

	    final double yt = y-3; // (level==0)? y+4: y-1;
	    v.add( SvgEcd.rawText( x0+3, yt, label, labelColor));
	    if (children==null) v.add( SvgEcd.rawText( x0, y+18, ""+ecd.size(), "black"));
	    else v.add( SvgEcd.rawText( x0+3, y+18, fmt.format(dist), distColor));
	    
	    final int sw= 3;
	
	    if (children==null) {
		Point p1 =  new Point(x0, y-14),p2 =  new Point(x0, y+14);
		String s = SvgEcd.rawLine( p1, p2, lineColor);
		s = SvgEcd.fm.wrap( "g", "stroke-width=\"" + sw+"\"", s);
		v.add(s);
	
	    } else {
		double y1 = y0;
		for(int j=0; j<2; j++) {
		    double h, x1;
		    if (useDistance) {
			//h = dist * DX;
			//x1 = x0 + h;
			x1 = leftmargin + DX * (1 - children[j].dist);
		    } else {
			h = DX * (level - children[j].level);
			x1 = x0 + h;
		    }

		    //-- width of the child
		    double cw = (ysize * children[j].width)/width;
		    v.add( children[j].toSvg( x1, y1, cw));
		    //-- center of the child
		    double y2  = y1 + children[j].dyc(cw);
		    
		    //double w = DY*children[j].width;

		    if (useDistance) {
			Point root =  new Point(x0, y),
			    p1 = new Point(x0, y2),
			    p2 = new Point(x1, y2);
			String s = SvgEcd.rawLine( root, p1, lineColor) + "\n"+
			    SvgEcd.rawLine( p1, p2, lineColor);

			if (dist<beta) s = SvgEcd.fm.wrap( "g", "stroke-width=\"" + sw+"\"", s);
			v.add(s);
		    } else {
			double px = 20;
			double py = y - 18 + 40*j;
		    
			v.add( SvgEcd.rawLine( new Point(x0+px, py),
					       new Point(x1, y2),
					       lineColor));
		    }

		    y1 += cw;
		}	    
	    }
	    return String.join("\n", v);
	    
	}
	
    }

    /** Controls the method for computing dist( {A,B}, C)
     */
    enum Linkage { MAX, MERGE };

    /** As per PK, 2023-05-25: Distance(a,b) = 1-sim(a,b)
	  The distance d(C,(A,B )) is the larger of d(C,A) and d(C,B).
	  ("Maximum or complete-linkage clustering", as per Wikipedia)
     */
    static Node doClustering(Map<String, Ecd> h, Linkage linkage) {

	final boolean useMin = true;
	DistMap ph = Ecd.computeSimilarities(h, useMin);
	
	Vector<Node> roots = new Vector<>();
	for(Ecd ecd: h.values()) {
	    roots.add(new Node(ecd));
	}

	//-- distances between all pairs of nodes
	DistMap dist = new DistMap();
	for(Node n1: roots) {
	    for(Node n2: roots) {
		if (n2==n1) continue;
		Double d = ph.get2(n1,n2);
		if (d==null) {
		    d = ph.get2(n2,n1);
		}
		if (d==null) throw new AssertionError("Don't know dist(" + n1.label + "," + n2.label+")");
		dist.put2(n1,n2, 1.0 - d);
	    }
	}

	//-- Keep merging, until just 1 root remains
	while( roots.size()>1) {
	    int jmin=0, kmin=0;
	    double minDist = 2;
	    //-- find the lowest-distance pair
	    //System.out.println("Roots.size="+ roots.size());
	    for(int j=0; j<roots.size(); j++) {
		Node n1 = roots.get(j);
		for(int k=j+1; k<roots.size(); k++) {
		    Node n2 = roots.get(k);
		    Double d = dist.get2(n1,n2);
		    if (d==null) throw new AssertionError("No distance recorded for:\nj="+j+": " + n1 + " and \nk="+k+": " + n2);
		    if (d < minDist) {
			jmin = j;
			kmin = k;
			minDist = d;
		    }
		}
	    }
	    if (jmin==kmin) throw new AssertionError();
	    Node[] nearest = {roots.get(jmin), roots.get(kmin) };
	    
	    //-- the merged node 
	    Node merged = new Node(nearest[0],nearest[1], minDist);
	    //System.out.println("Merged "+ jmin +", " + kmin);
	    roots.set(jmin, merged);
	    roots.remove(kmin);
	    //System.out.println("Roots (size="+roots.size()+") are:");
	    //for(int j=0; j<roots.size(); j++) {
	    //	System.out.println("Root["+j+"]="+roots.get(j));
	    //}
	    //-- distances from the new merged node to all other nodes
	    for(Node n: roots) {
		if (n==merged) continue;
		double d;
		if (linkage == Linkage.MAX)  {
		    d = Math.max( dist.get2(n, nearest[0]),
				  dist.get2(n, nearest[1]));
		} else if (linkage == Linkage.MERGE)  {
		    double sim = merged.ecd.computeSimilarity( n.ecd, useMin);
		    d = 1 - sim;
		} else throw new IllegalArgumentException();
		dist.put2(merged, n, d);
		dist.put2(n, merged, d);
	    }   

	    
	}
		
	return roots.get(0);
	
    }
	

    
    
}
