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

	final static int DX = 100, DY = 50,  xmargin=20, ymargin=20;

	/** @return [xsize, ysize] */
	int[] boxSize() {
	    return new int[] { 2*xmargin + DX*(level+1), 2*ymargin + DY*width };
	}
	    

	String toSvg() {
	    return toSvg(xmargin, ymargin);
	}
	
	/** The root is on the left. (So that width is vertical and height horizontal)
	    @param x0 top left corner
	    @param y0 top left corner
	*/
	String toSvg(final double x0, final double y0) {
	    
	    Vector<String> v = new Vector<>();
	    
	    //-- center of this node
	    final double y = y0 + DY * 0.5 * width;
	    
	    String labelColor = "red", distColor = "green", lineColor = "black";
	    NumberFormat fmt = new DecimalFormat("0.000");
    
	    v.add( SvgEcd.rawText( x0, y-1, label, labelColor));
	    if (children!=null) v.add( SvgEcd.rawText( x0, y+20, fmt.format(dist), distColor));

	
	    if (children!=null) {
		double y1 = y0;
		for(int j=0; j<2; j++) {
		    double x1 = x0 + DX * (level - children[j].level);
		    v.add( children[j].toSvg( x1, y1));

		    double py = y - 18 + 40*j;
		    double w = DY*children[j].width;
		    v.add( SvgEcd.rawLine( new Point(x0+20, py),
					   new Point(x1, y1 + 0.5*w),
					   lineColor));

		    y1 += w;
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
    static Node doClustering(Map<String, Ecd> h, DistMap ph, LabelMap lam, Linkage linkage) {
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
	    double minDist = 1;
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
		    double sim = nearest[0].ecd.computeSimilarity( nearest[1].ecd );
		    d = 1 - sim;
		} else throw new IllegalArgumentException();
		dist.put2(merged, n, d);
		dist.put2(n, merged, d);
	    }   

	    
	}
		
	return roots.get(0);
	
    }
	

    
    
}
