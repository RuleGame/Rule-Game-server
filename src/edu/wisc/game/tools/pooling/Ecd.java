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


/*  Empirical cumulated distribution */
public class Ecd {

    /** Refers to the preceding conditions */
    final String key;
    /** AbC */
    final String label;
    
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
	String s = "(ECD(label="+label+", cond='" +key+"')={";
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
	    learnedCnt + "/"  + size() + "=" + successRate + ")";
	return s;
	
    }
    
    Ecd(String _key, String _label) {
	key = _key;
	label = _label;
    }

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

    /** In "science" coordinates */    
    Point getCenter() {
	return new Point( getMedianMStar(),  0.5*learnedCnt);
    }

    /** Produces a new ECD that combines this sample with another sample */
    Ecd merge(Ecd o) {
	Ecd merged = new Ecd(key + "+" + o.key, label+ "+" + o.label);
	merged.series.addAll(series);
	merged.series.addAll(o.series);
	merged.freeze();
	return merged;
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

    static double alpha = 0.05;
    static String target = null;
    
    public static void main(String[] argv) throws Exception {

	File csvOutDir=null;
	
	/** The target rule set name. Must be specified. */
	Vector<String> importFrom = new Vector<>();
	

	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];
	    
	    if  (j+1< argv.length && a.equals("-target")) {
		target = argv[++j];
	    } else if (j+1< argv.length && a.equals("-import")) {
		importFrom.add(argv[++j]);
	    } else if (j+1< argv.length && a.equals("-csvOut")) {
		csvOutDir = new File(argv[++j]);
	    } else if (j+1< argv.length && a.equals("-alpha")) {
		alpha =  Double.parseDouble(argv[++j]);
	    }
	}

	 
	if ( importFrom.size()==0) {
	    usage("Please use the -import option(s) to specify at least one input file");
	}

	if (target==null) {
	    usage("Please provide -target ruleSetName");
	}
	String base = target.replaceAll("/", "-");
	
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

	    Set<String> keys = new HashSet<>();
	    for(MwSeries ser: data) {
		String key = ser.getLightKey();
		keys.add(key);
	    }

	    LabelMap lam = new LabelMap( keys.toArray(new String[0]));

	    //-- Maps labels to ECD objects
	    TreeMap<String, Ecd> h = new TreeMap<>();

	    for(MwSeries ser: data) {
		String key = ser.getLightKey();
		//System.out.println("key='" + key+ "'");
		String label = lam.mapCond(key);
		Ecd ecd = h.get(label);
		if (ecd==null) h.put(label, ecd = new Ecd(key, label));
		ecd.add(ser);
	    }

	    // Eigenvalue analysis
	    Fmter plainFm = new Fmter();
	    MwByHuman processor = new MwByHuman(PrecMode.EveryCond,
						10, 300, plainFm);
	    processor.savedMws.addAll(data);

	    System.out.println("=== Target "+target+" ===");
	    
	    // M-W test on the data from savedMws
	    if (csvOutDir==null) {
		csvOutDir = new File(base + "-ev");
	    }	
	    System.out.println("MW eigenvalue data are in " + csvOutDir);
	    
	    processor.processStage2(true, false, csvOutDir);



	    double xRange = 1, yRange=1;
	    HashSet<String> toDiscard=new HashSet<>();
	    for(String label: h.keySet()) {
		Ecd ecd =h.get(label);
		ecd.freeze();

		if (ecd.size()<=1) {
		    toDiscard.add(label);
		}
		
		xRange = Math.max(xRange, ecd.getMaxMStar());
		//yRange = Math.max(yRange, ecd.size());
	    }

	    for(String label: toDiscard) {
		System.out.println("Removing the small-sample ECD (because KS won't like it): " + h.get(label));
		h.remove(label);
	    }

	    
	    xRange += 1;

	    System.out.println("=== Legend for target "+target+" ===");
	    for(Ecd ecd: h.values()) {
		System.out.println( ecd);
	    }
	   

	    String[] colors = {"red", "green", "orange", "cyan", "blue", "purple", "pink"};
	    
	    Vector<String> v = drawAllCurves(h, xRange, yRange, colors, lam, null);
	    	    

	    String fname = base + "-ecd-basic";
	    writeSvg(fname, v);

	    Vector<String> hbLabels = new Vector<>();
	    DistMap ph = analyzeSimilarities(h, lam, hbLabels);
	    colors = new String[] {"red"};
	    v = drawAllCurves(h, xRange, yRange, colors, lam, hbLabels);
	    fname = base + "-ecd-hb";
	    writeSvg(fname, v);

	    System.out.println("=== Clustering ===");

	    Clustering.Linkage links[] ={ Clustering.Linkage.MAX,
					  Clustering.Linkage.MERGE};

	    for(Clustering.Linkage linkage: links) {
		Clustering.Node root = Clustering.doClustering(h, ph, lam, linkage);
		System.out.println("Dendrogram for linkage=" + linkage + ":");
		System.out.println(root);

		int[] box = root.boxSize();
		String s = root.toSvg();
		s  =  SvgEcd.outerWrap(s, box[0], box[1]);
		writeSvg(base + "-tree-" + linkage, s);
		
	    }


	} finally {
	    //String text = processor.getReport();
	    //System.out.println(text);
	}
	 
     }

    static private void writeSvg(String fnameBase, Vector<String> v) throws IOException {
	String s = SvgEcd.outerWrap( String.join("\n", v));
	writeSvg(fnameBase, s);
    }
    
    static private void writeSvg(String fnameBase, String s) throws IOException {

	File f = new File(fnameBase + ".svg");
	PrintWriter w = new PrintWriter(new      FileWriter(f));
	w.println(s);
	w.close();
    }


    static final MannWhitneyUTest mw = new MannWhitneyUTest();
    static final KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
    double computeSimilarity(Ecd o) {
	double	mwp = mw.mannWhitneyUTest(orderedSample,o.orderedSample);
	//System.out.println("mannWhitneyUTest(" + fmtArg(x)+")=" + mwp);
	double ksp = ks.kolmogorovSmirnovTest(orderedSample,o.orderedSample);
	//double kspf = ks.kolmogorovSmirnovTest(x[0],x[1], false);
	double p = Math.max(mwp, ksp);
	return p;
    }
    
    /** @return The upper triangular matrix (label1 &lt; label2) of similarities between different ECDs.
	
     */
    static private DistMap  analyzeSimilarities(Map<String, Ecd> h, LabelMap lam, Vector<String> hbLabels) {

	DistMap ph = new DistMap();

	System.out.println("=== p-Values ===");
	for( String label1: h.keySet()) {
	    Ecd ecd1 = h.get(label1);
	    for( String label2: h.keySet()) {
		Ecd ecd2 = h.get(label2);
		
		double p = ecd1.computeSimilarity( ecd2 );
		System.out.print("\tp("+label1+","+label2+")="+p);
		if (label1.compareTo(label2)<0) {
		    ph.put2(label1,label2, p);
		}
	    }
	    System.out.println();
	}

	Vector<String> order = new Vector<>();
	order.addAll(ph.keySet());
	order.sort((o1,o2)-> (int)Math.signum( ph.get(o1)-ph.get(o2)));

	int nPairs = order.size();


	//-- Holm-Bonferroni process 
	int nHBPairs = 0;
	for( ; nHBPairs < nPairs; nHBPairs++) {
	    String o = order.get( nHBPairs );
	    double p = ph.get(o);
	    double m = nPairs - nHBPairs;
	    if (p >= alpha/m) break;
	    hbLabels.add(o);
	}

	System.out.println("=== Ordered similarities between ECDs ("+nPairs+" pairs): ===");
	int k=0;
	for(String o: order) {
	    String s="";
	    double m = nPairs - k;

	    if (k<nHBPairs) s += "[HB] ";	    
	    s += "p(" + o + ")="  + ph.get(o);
	    if (k<nHBPairs) s += " < " + (alpha/m);
	    System.out.println(s);
	    k++;
	}

	if (nHBPairs==0) {
	    System.out.println("The Holm-Bonferroni process with alpha="+alpha+" has selected none of the above pairs");
  	} else {
	    System.out.println("The Holm-Bonferroni process with alpha="+alpha+" has selected the first "+nHBPairs+" of the above pairs. They are marked with [HB]");
	}

	return ph;
	
    }
    
    static Vector<String> drawAllCurves(Map<String, Ecd> h, double xRange, double yRange, String colors[], LabelMap lam,
					Vector<String> hbLabels
					) {
	if (hbLabels==null) hbLabels = new Vector<>();
	int n = 0;
	Vector<String> v = new Vector<>();
	v.add( SvgEcd.drawFrame(xRange));
	    
	
	for(Ecd ecd: h.values()) {
	    //System.out.println("Making SVG for " + ecd.orderedSample.length + " points");
	    //	    System.out.println( ecd);
	    yRange = ecd.size();
	    String color = colors[ n % colors.length];
	    String z = SvgEcd.makeSvgEcd(color, ecd.orderedSample,
					 xRange, yRange);
	    
	    //System.out.println(z);
	    v.add(z);
	    
	    Point.setScale( xRange, yRange);
	    Point center = ecd.getCenter();
	    z = SvgEcd.circle( center, 3, color);
	    v.add(z);
	    Point rawCenter = center.rawPoint();
	    z = SvgEcd.rawText( rawCenter.x, rawCenter.y+20, ecd.label, color);
	    v.add(z);
	    
	    //String fname = "ecd-" + fmt3d.format(n);
	    n++;
	}

	// Links between the HB pairs
	//System.out.println("Will show "+hbLabels.size() + " HB pairs");
	for(String pair: hbLabels) {
	    String[] q = pair.split(",");
	    Ecd[] e = new Ecd[2];
	    Point[] rawCenters = new Point[2];
	    for(int j=0; j<2; j++) {
		e[j] = h.get(q[j]);
		Point.setScale( xRange, e[j].size());
		rawCenters[j] = e[j].getCenter().rawPoint();
		//v.add(  SvgEcd.rawCircle( rawCenters[j], 5, "black"));
	    }
	    //System.out.println("Linking " + pair);
	    v.add( SvgEcd.rawLine(rawCenters[0], rawCenters[1], "green"));
	}

	
	return v;
    }
    
}
