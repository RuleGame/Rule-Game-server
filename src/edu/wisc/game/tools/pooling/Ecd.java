package edu.wisc.game.tools.pooling;


import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.text.*;

import javax.persistence.*;

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
import edu.wisc.game.formatter.*;

import edu.wisc.game.sql.Episode.CODE;
*/

/*  Empirical cumulated distribution */
public class Ecd {

    /** Refers to the preceding conditions */
    final String key;
    double[] orderedSample;
    Vector<MwSeries> series = new Vector<>();
    double successRate;

    double getMaxMStar() {
	double m = 0;
	for(MwSeries ser: series) {
	    if (ser.getLearned() && ser.getMStar()>m) m = ser.getMStar();
	}
	return m;
    }

    double getMedianMStar() {
	if (learnedCnt==0) return 0;
	else if (learnedCnt % 2 == 0) {
	    return 0.5* (orderedSample[learnedCnt/2-1] +
			 orderedSample[learnedCnt/2]);
	} else {
	    return orderedSample[learnedCnt/2];
	}
    }

    int size() {
	return series.size();
    }

    public String toString() {
	String s = "(ECD("+key+")={";
	//s += Util.joinNonBlank(", ", orderedSample);
	for(int j=0; j<orderedSample.length; j++) {
	    double a = orderedSample[j];
	    if (a>=300) {
		s += " ...";
		break;
	    }
	    if (j>0) s += ", ";
	    s += ((int)a == a) ? ""+(int)a : "" + a;
	}
	
	
	s += "}, "+
	    " median mStar=" + getMedianMStar() + ", success rate=" +
	    learnedCnt + "/"  + size() + "=" + successRate + "}";
	return s;
	
    }
    
    Ecd(String _key) { key = _key; }

    void add(MwSeries ser) { series.add(ser); }

    int learnedCnt=0;
    
    /** Call this after all series have been added */
    void freeze() {
	orderedSample = new double[series.size()];
	int j=0;
	learnedCnt=0;
	for(MwSeries ser: series) {
	    orderedSample[j++] = ser.getMStar();
	    if (ser.getLearned()) learnedCnt++;
	}
	Arrays.sort(orderedSample);
	successRate = (double)learnedCnt / (double)orderedSample.length;
    }
    
    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	//System.err.println("For usage, see tools/analyze-transcripts-mwh.html\n\n");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }


    static NumberFormat fmt3d = new DecimalFormat("000");
    
    public static void main(String[] argv) throws Exception {

	 /** The target rule set name. Must be specified. */
	 String target = null;
	 Vector<String> importFrom = new Vector<>();


	 for(int j=0; j<argv.length; j++) {
	    String a = argv[j];

	    if  (j+1< argv.length && a.equals("-target")) {
		target = argv[++j];
	    } else if (j+1< argv.length && a.equals("-import")) {
		importFrom.add(argv[++j]);
	    }
	 }

	 
	if ( importFrom.size()==0) {
	    usage("Please use the -import option(s) to specify at least one input file");
	}

	if (target==null) {
	    usage("Please provide -target ruleSetName");
	}
	
	
	Vector<MwSeries> imported = new Vector<>();
	try {
			
	    for(String from: importFrom) {
		File g = new File(from);
		MwSeries.readFromFile(g, imported);
		//System.out.println("DEBUG: Has read " + imported.size() + " data lines");
		
	    }

	    Vector<MwSeries> data = new Vector<>();
	    for(MwSeries ser: imported) {
		if (ser.ruleSetName.equals(target)) data.add(ser);
	    }
	    //System.out.println("DEBUG: out of " + imported.size() + " data lines, found " + data.size() + " lines for the target rule set " + target);
	    TreeMap<String, Ecd> h = new TreeMap<>();
	    
	    for(MwSeries ser: data) {
		String key = ser.getLightKey();
		Ecd ecd = h.get(key);
		if (ecd==null) h.put(key, ecd = new Ecd(key));
		ecd.add(ser);
	    }

	    double xRange = 1, yRange=1;
	    for(Ecd ecd: h.values()) {
		ecd.freeze();
		xRange = Math.max(xRange, ecd.getMaxMStar());
		//yRange = Math.max(yRange, ecd.size());
	    }
	    xRange += 1;

	    int n = 0;
	    Vector<String> v = new Vector<>();
	    v.add( SvgEcd.drawFrame(xRange));

	    String[] colors = {"red", "green", "orange", "cyan", "eblue", "purple", "pink"};
	    
	    for(Ecd ecd: h.values()) {
		//System.out.println("Making SVG for " + ecd.orderedSample.length + " points");
		System.out.println("ECD="  + ecd);
		yRange = ecd.size();
		String color = colors[ n % colors.length];
		String z = SvgEcd.makeSvgEcd(color, ecd.orderedSample,
					     xRange, yRange);

		//System.out.println(z);
		v.add(z);

		Point.setScale( xRange, yRange);
		Point center = new Point( ecd.getMedianMStar(),
					  0.5*ecd.learnedCnt);
		z = SvgEcd.circle( center, 3, color);
		v.add(z);
		
		//String fname = "ecd-" + fmt3d.format(n);
		n++;

		//if (n>0) break;
	    }

	    String s = SvgEcd.outerWrap( String.join("\n", v));
	    String fname = "ecd";
	    File f = new File(fname + ".svg");
	    PrintWriter w = new PrintWriter(new      FileWriter(f));
	    w.println(s);
	    w.close();

	    
	    

	    
	} finally {
	    //String text = processor.getReport();
	    //System.out.println(text);
	}
	 
     }

    
}
