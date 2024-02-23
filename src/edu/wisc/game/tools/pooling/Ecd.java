package edu.wisc.game.tools.pooling;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.text.*;


import org.apache.commons.math3.stat.inference.*;
//import org.apache.commons.math4.legacy.astat.inference.*;

import edu.wisc.game.util.*;
import edu.wisc.game.tools.*;
import edu.wisc.game.tools.MwByHuman.MwSeries;
import edu.wisc.game.tools.MwByHuman.PrecMode;
import edu.wisc.game.svg.*;
import edu.wisc.game.svg.SvgEcd.Point;
import edu.wisc.game.sql.Episode;
import edu.wisc.game.formatter.*;

import edu.wisc.game.tools.pooling.Clustering.Node;

/*  Empirical cumulated distribution (ECD) */
public class Ecd {

    
    /** Refers to the preceding conditions */
    final String key;
    /** AbC */
    final String label;
    
    double[] orderedSample;
    Vector<MwSeries> series = new Vector<>();
    double successRate;

    String printSample() {
	//String printSample(boolean doJitter) {
	double [] a = //doJitter? jitter(orderedSample):
	    orderedSample;
	return "{" + Util.joinNonBlank(", ", a) + "}";
    }
    
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

    // Just for testing
    //private static void truncate(double a[]) {
    //	for(int j=0; j<a.length; j++) if (a[j]>90) a[j]=90;
    //}

    /** Call this after all series have been added */
    void freeze() {
	orderedSample = new double[series.size()];
	int j=0;
	learnedCnt=0;
	for(MwSeries ser: series) {
	    orderedSample[j++] = ser.getMStar();
	    if (ser.getLearned()) learnedCnt++;
	}

	//truncate(orderedSample);

	Arrays.sort(orderedSample);
	successRate = (double)learnedCnt / (double)orderedSample.length;
    }

    /** In "science" coordinates */    
    Point getCenter() {
	return new Point( getMedianMStar(),  0.5*learnedCnt);
    }

    /** Produces a new ECD that combines this sample with another
	sample. The key and the label for the new sample is formed
	using the '+' character. */
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
    
    /** Different arrangements depending on whether we compare across
	targets or only within one target. In the latter case, the
	target does not need to be part of the key.
     */
    private static String getKey(MwSeries ser) {
	return target==null?
	    ser.getKey(PrecMode.EveryCond) : 	    ser.getLightKey();
    }
	
    
    static NumberFormat fmt3d = new DecimalFormat("000");

    static double alpha = 0.05;
    static String target = null;
   
    /* How  the similarity is computed from the MW and KS p-values:
       using one of them, or the min or max of the two. The default
       was Max for HB and Min for clustering.
    */
    enum SimMethod { MW, KS, Min, Max };
    static SimMethod simHB = SimMethod.Max;
    static SimMethod simClustering = SimMethod.Min;

    /** If non-zero, this is used as "deterministic jitter" to remove ties
	for KS */
    static double untie = 0;
    
    /** If this flag is turned on, we'll check whether KS gives
	symmetric resuts, and if not, will report the details on the
	asymmetric pairs. This option was used to troubleshoot Paul's
	"asymmetric results" report (2023-11-16).
     */
    static boolean checkSym = false;
    

    static private RandomRG random = null;
    
    public static void main(String[] argv) throws Exception {

	File csvOutDir=null;
	
	/** The target rule set name. Must be specified. */
	Vector<String> importFrom = new Vector<>();
	Long seed = null;

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
	    } else if (j+1< argv.length && a.equals("-beta")) {
		Clustering.beta =  Double.parseDouble(argv[++j]);
	    } else if (j+1< argv.length && a.equals("-sim")) {
		simHB = simClustering = Enum.valueOf(SimMethod.class, argv[++j]);
	    } else if (j+1< argv.length && a.equals("-simHB")) {
		simHB =  Enum.valueOf(SimMethod.class, argv[++j]);
	    } else if (j+1< argv.length && a.equals("-simClustering")) {
		simClustering =  Enum.valueOf(SimMethod.class, argv[++j]);
	    } else if (j+1< argv.length && a.equals("-untie")) {
		untie =  Double.parseDouble(argv[++j]);
	    } else if (j+1< argv.length && a.equals("-seed")) {
		seed =  Long.parseLong(argv[++j]);
	    } else if ( a.equals("-checkSym")) {
		checkSym = true;
	    }
	}

	 
	if ( importFrom.size()==0) {
	    usage("Please use the -import option(s) to specify at least one input file");
	}

	if (target==null) {
	    System.out.println("Comparing all targets");
	    //usage("Please provide -target ruleSetName");
	}

	random = (seed==null)? new RandomRG() :new RandomRG(seed);
	if (seed!=null) 	System.out.println("Seed=" + seed);
	
	System.out.println("Similarity for HB (before and after clustering): "+simHB);
	System.out.println("Similarity for clustering: "+simClustering);
	
	//-- the base for output file names
	String base = (target==null)? "everything" : target.replaceAll("/", "-");
	
	Vector<MwSeries> imported = new Vector<>();
	try {
			
	    for(String from: importFrom) {
		File g = new File(from);
		MwSeries.readFromFile(g, imported);
		//System.out.println("DEBUG: Has read " + imported.size() + " data lines");
		
	    }

	    Vector<MwSeries> data = new Vector<>();
	    for(MwSeries ser: imported) {
		if (target==null || ser.ruleSetName.equals(target)) data.add(ser);
	    }
	    //System.out.println("DEBUG: out of " + imported.size() + " data lines, found " + data.size() + " lines for the target rule set " + target);

	    Set<String> keys = new HashSet<>();
	    for(MwSeries ser: data) {
		String key = getKey(ser);
		keys.add(key);
	    }

	    LabelMap lam = new LabelMap( keys.toArray(new String[0]), target==null);

	    //-- Maps labels to ECD objects
	    TreeMap<String, Ecd> h = new TreeMap<>();

	    for(MwSeries ser: data) {
		String key = getKey(ser);
		//System.out.println("key='" + key+ "'");
		String label = lam.mapCond(key);
		Ecd ecd = h.get(label);
		if (ecd==null) h.put(label, ecd = new Ecd(key, label));
		ecd.add(ser);
	    }
	    freezeTable(h);

	    // Eigenvalue analysis
	    Fmter plainFm = new Fmter();
	    MwByHuman processor = new MwByHuman(PrecMode.EveryCond,
						10, 300, plainFm);
	    processor.savedMws.addAll(data);

	    String ta = (target==null ?  "cross-target comparison" : "Target " + target);

	    
	    System.out.println("=== "+ta+" ===");
	    
	    // M-W test on the data from savedMws
	    if (csvOutDir==null) {
		csvOutDir = new File(base + "-ev");
	    }	
	    System.out.println("MW eigenvalue data are in " + csvOutDir);
	    
	    processor.processStage2(true, false, csvOutDir);


	    if (untie>0) {
		jitterAll(h);
	    }
	

	    
	    //	    base += "-" + simMethod;
	    ecdAnalysis(base, h, lam, false);

	    System.out.println("=== Clustering ===");

	    Clustering.Linkage links[] ={ Clustering.Linkage.MAX,
		//Clustering.Linkage.MERGE
	    };

	    for(Clustering.Linkage linkage: links) {
		Node root = Clustering.doClustering(h, linkage, simClustering);
		System.out.println("Dendrogram for linkage=" + linkage +
				   ", beta="+Clustering.beta+":");
		System.out.println(root);

		int[] box = root.boxSize();
		String s = root.toSvg();
		s  =  SvgEcd.outerWrap(s, box[0], box[1]);
		//writeSvg(base + "-tree-" + linkage, s);
		writeSvg(base + "-tree", s);

		//--- use pooled ECDs
		Vector<Node> pools = new Vector<>();
		root.listPools(pools);

		h.clear();
		for(Node node: pools) {
		    //String label = lam.mapCond(key);
		    h.put(node.ecd.label, node.ecd);
		}
		
		ecdAnalysis(base, h, lam, true);

		//-- EV on pooled ECDs
		
		Vector<MwSeries> pooledData = new Vector<>();
		HashMap<String,String> leafToPooled = new HashMap<>();
		for(Node pool: pools) {
		    String[] q = pool.label.split("\\+");
		    if (pool.level > 0 && q.length<2) throw new AssertionError("Wrong parsing? " + pool.label + " --> ("+String.join(",", q)+")");
		    for(String z: q) leafToPooled.put( z, pool.label);
		}
		//System.out.println("leafToPooled=" + leafToPooled);
		
		processor.savedMws.clear();
		int excludedCnt = 0;
		for(MwSeries ser: data) {
		    String key = getKey(ser);
		    String label = lam.mapCond(key);
		    String pooledLabel = leafToPooled.get(label);
		    if ( pooledLabel == null) {
			//throw new AssertionError("Cannot find the pooled label for " + label);
			excludedCnt++;
		    }
		    ser.setForcedKey(pooledLabel);
		    processor.savedMws.add(ser);
		}
		if (excludedCnt>0) System.out.println("Excluded " + excludedCnt + " players from pooling, likely because they belonged to a 'singleton' condition, and thus the KS p-value was not computable for their condition");
		
		//System.out.println("=== Target "+target+" ===");
	    
		// M-W test on the data from savedMws
		csvOutDir = new File(base + "-pooled-ev");
		System.out.println("MW eigenvalue tables for pooled data are in " + csvOutDir);
	    
		processor.processStage2(true, false, csvOutDir);
		

		
	    }

	    

	} finally {
	    //String text = processor.getReport();
	    //System.out.println(text);
	}
	 
     }

    /** Freezes (completes computations) in each ECD, and discards
	very small ECDs (which cannot be handled by KS) */
    private static void freezeTable(Map<String, Ecd> h) {
	    
	HashSet<String> toDiscard=new HashSet<>();
	for(String label: h.keySet()) {
	    Ecd ecd =h.get(label);
	    ecd.freeze();
	    if (ecd.size()<=1) toDiscard.add(label);
	}
	
	for(String label: toDiscard) {
	    System.out.println("Removing the small-sample ECD (because KS won't like it): " + h.get(label));
	    h.remove(label);
	}
    }

    /** Given a set of ECDs, draw each one, carry out HB analysis, and
	then draw the connections between the "really different"  ones.

	@param pooled True if the analysis is carried out on pooled ECDs, rather
	than original ones. This parameter does not affect computations; it
	is only used for generating file names,  printing correct messages, etc.
	
    */
    static void ecdAnalysis(String base, Map<String, Ecd> h, LabelMap lam, boolean pooled) throws IOException {

	double xRange = 1, yRange=1;
	for(Ecd ecd: h.values()) {
	    xRange = Math.max(xRange, ecd.getMaxMStar());
	}
	
	xRange += 1;
	
	String sp = pooled? "(pooled)":"";
	String tasp = (target==null ?  "cross-target comparison" : "target " + target) +
	    " " + sp;
	System.out.println("Human learning analysis in Game Server ver. " + Episode.version);
	System.out.println("=== Legend for "+tasp+" ===");
	    
	for(Ecd ecd: h.values()) {
	    System.out.println( ecd);
	}
	   

	String[] colors = {"red", "green", "orange", "cyan", "blue", "purple", "pink"};	    
	Vector<String> v = drawAllCurves(h, xRange, yRange,colors,null);
	String base2 = base + "-ecd";
	if (pooled) base2 += "-pooled" + simClustering;
	String fname = base2 + "-basic";
	writeSvg(fname, v);


	
	Vector<String> hbLabels = analyzeSimilarities(h, pooled);
	//DistMap ph = 
	colors = new String[] {"red"};
	v = drawAllCurves(h, xRange, yRange, colors, hbLabels);
	fname = base2 + "-hb" + simHB;
	writeSvg(fname, v);
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

    /** Slightly modifies all Ecd.orderedSample arrays, to keep ties unlikely */
    private static void jitterAll(Map<String, Ecd> h) {
	for(Ecd ecd: h.values()) jitterOne(ecd.orderedSample);
    }

    /** Adds a random value, in the range [-untie, untie] to each element of
	the array; then re-orders the array to keep it in ascending order.
    */
    private static void jitterOne(double[] a) {
	for(int j=0; j<a.length; j++) {
	    a[j] += untie * ( 2*random.nextDouble()-1);
	}
	Arrays.sort(a);
    }
    
    /** Modifies array a[] a bit if it has ties. Controlled by this.untie
	@param a A non-descending sequence
	@return A strictly ascending sequence (either a[], or a jittered version of it)
     */
    /*
    private double[] jitter(double[] a) {

	if (untie==0) return a;
	double b[] = new double[a.length];
	double adj = 0;
	for(int j=a.length-1; j>=0; j--) {
	    b[j] = a[j];
	    if (j<a.length-1) {
		if (b[j]>=b[j+1]) b[j] = b[j+1]-untie;
	    }
	}
	return b;					     
    }
    */
    
    private double myKS(double[] a, double[] b) {
	//return ks.kolmogorovSmirnovTest(jitter(a),jitter(b));
	return ks.kolmogorovSmirnovTest(a,b);
    }

    /** Symmetrize the KS p0-value, to deal with jittering */
    private double myKS_sim(double[] a, double[] b) {
	//return ks.kolmogorovSmirnovTest(jitter(a),jitter(b));
	return (ks.kolmogorovSmirnovTest(a,b) +
		ks.kolmogorovSmirnovTest(b,a))/2; 
    }

    
    private double myKSS(double[] a, double[] b) {
	//return ks.kolmogorovSmirnovStatistic(jitter(a),jitter(b));
	return ks.kolmogorovSmirnovStatistic(a,b);
    }
    

    
    /** Computes the similarity between this ECD and another ECD.
        @param simMethod How to compute the similarity based on MW
	and/or KS p-values. The default is Max.
     */
    double computeSimilarity(Ecd o, SimMethod simMethod) {
	double	mwp = mw.mannWhitneyUTest(orderedSample,o.orderedSample);
	//System.out.println("mannWhitneyUTest(" + fmtArg(x)+")=" + mwp);
	double ksp = myKS_sim(orderedSample,o.orderedSample);

	if (checkSym) { // KS symmetry checking
	    double oksp = myKS_sim(o.orderedSample,orderedSample);
	    if (Math.abs(oksp-ksp) > 1e-4 * (oksp+ksp)) {
		System.out.println("KS asymmetry noticed for "+label+"=" + printSample() + ", "+o.label+"=" + o.printSample());
		System.out.println("KS("+label+","+o.label+")=" + ksp +
				   " (kss=" + myKSS(orderedSample,o.orderedSample)+
				   "); KS("+o.label+","+label+")=" + oksp +
				   " (kss=" + myKSS(o.orderedSample,orderedSample) +")"
				   );
	    }


	    double	mwp2 = mw.mannWhitneyUTest(o.orderedSample,orderedSample);

	    if (Math.abs(mwp2-mwp) > 1e-4 * (mwp2+mwp)) {
		System.out.println("MW asymmetry noticed for "+label+"=" + printSample() + ", "+o.label+"=" + o.printSample());
		System.out.println("MW("+label+","+o.label+")=" + mwp + "; MW("+o.label+","+label+")=" + mwp2);
	    }

	}


	
	//double kspf = ks.kolmogorovSmirnovTest(x[0],x[1], false);
	switch (simMethod) {
	case KS:
	    return ksp;
	case MW:
	    return mwp;
	case Min:
	    return Math.min(mwp, ksp);
	case Max:
	    return Math.max(mwp, ksp);
	}
	throw new IllegalArgumentException("simMethod=" + simMethod);
    }


    /** Computes the similarity matrix. (Only the upper triangular section
	is filled).
	
	The default is useMin=false, i.e. using the max of the two p-values

	@return The upper triangular matrix (label1 &lt; label2) of similarities between different ECDs.
    */
    static DistMap computeSimilarities(Map<String, Ecd> h, SimMethod simMethod) {

	DistMap ph = new DistMap();

	System.out.println("=== p-Values ===");
	for( String label1: h.keySet()) {
	    Ecd ecd1 = h.get(label1);
	    Vector<String> v = new Vector<>();
	    for( String label2: h.keySet()) {
		Ecd ecd2 = h.get(label2);
		
		double p = ecd1.computeSimilarity( ecd2, simMethod);
		v.add("p("+label1+","+label2+")="+p);
		if (label1.compareTo(label2)<0) {
		    ph.put2(label1,label2, p);
		}
	    }
	    System.out.println(Util.joinNonBlank("\t", v));
	}
	return ph;
    }
    
    /**
       @param pooled True if we are working with pooled ECDs, rather
       than original ones. This parameter does not affect
       computations, but is used to generate correct messages

       @return The HB results: ECDs that are really different from the "naive" ECD
	
     */
    static private Vector<String> analyzeSimilarities(Map<String, Ecd> h, boolean pooled) {
	
	DistMap ph = computeSimilarities(h, simHB);

	//-- HB for all pairs
	Vector<String> order = new Vector<>();
	order.addAll(ph.keySet());
	String msg = "Comparing all pairs";
	if (pooled) msg += " (pooled)";
	holmBonferroni(ph, order, msg);

	//-- HB for only (0,*) pairs. We still use the triangular
	//-- matrix stored in ph, benefitting from the fact that "0"
	//-- is alphabetically before all letters; thus, ph("0",
	//-- "someOtherLabel") is always stored.
	order.clear();
	for(String pairLabel: ph.keySet()) {
	    if (pairLabel.startsWith("0,")) order.add(pairLabel);	    
	}
	return holmBonferroni(ph, order, "Comparing to naive series only");	
    }

    /** @return The list of "really different" pairs of curves into it
     */
    static private Vector<String> holmBonferroni(DistMap ph, Vector<String> order,  String msg) {
	Vector<String> hbLabels = new Vector<>();
	
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

	System.out.println("=== Ordered similarities between ECDs ("+msg +"; "+
			   nPairs+" pairs): ===");
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
	return hbLabels;
    }

    /** Draws several ECD curves, in monochrome or in multiple colors.
	@param h The curves to draw
	@param colors The colors to use for the curves. If the array has only one color, all curves will be in this color.
	@param hbLabels If not null, this is used to identify HB links between "really different" curves
     */
    static Vector<String> drawAllCurves(Map<String, Ecd> h,
					double xRange, double yRange, String colors[], 
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
	if (hbLabels!=null) {
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
	}
	
	return v;
    }
    
}
