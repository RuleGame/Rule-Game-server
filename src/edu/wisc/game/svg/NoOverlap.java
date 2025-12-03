package edu.wisc.game.svg;

import java.io.*;
import java.util.*;

import  edu.wisc.game.util.Util;

/** An auxiliary class for Curve; used to help "jitter" some curves overlapping
    sections, so that they won't overlap.
*/
class NoOverlap extends HashMap<Double, NoOverlap.Horizontal> {
    static class Segment {
	final int x0, x1;
	int offset=0;
	Segment(int _x0, int _x1) {
	    x0 = _x0;
	    x1 = _x1;
	}
	/** @return true if the two segment overlap over some length
	    (not just at a single end point) */
	boolean overlaps(Segment q) {
	    return (q.x0 <= x0) ?  q.x1 > x0:
		(q.x0 < x1);
	}
    }

    /** Stores information about all horizontal curve segments that 
	are located at a particular y */
    static class Horizontal extends Vector<Segment> {
	/** math coordinates */
	double y;
	Horizontal(double _y) {	    
	    y = _y;
	}
	int doAdd(Segment seg) {
	    int maxOffset = -1;
	    for(Segment q: this) {
		if (q.overlaps(seg)) {
		    maxOffset = Math.max(maxOffset, q.offset);
		}
	    }
	    seg.offset = maxOffset+1;
	    add(seg);
	    return seg.offset;	       
	}
    }

    /** @return an array with the recommended integer offset for each
	point of the curve. */
    int[] add(Curve c) {
	int[] offset = new int[c.y.length];
	for(int j=0; j<c.y.length-1 ; j++) {
	    int x0 = j, x1 = j+1;
	    double y0 =  c.y[j];
	    double y1 = (j+1 < c.y.length)? c.y[j+1]: y0;

	    if (y1 != y0) continue;

	    Double key = new Double(y0);
	    Horizontal h = get(key);
	    if (h==null) put(key, h=new Horizontal(y0));
	    int d = h.doAdd(new Segment(x0,x1));
	    if (d!=0) {
		offset[j] = d;
		if (j+1<c.y.length) offset[j+1] = d;
	    }
	}

	return offset;
    }

}
