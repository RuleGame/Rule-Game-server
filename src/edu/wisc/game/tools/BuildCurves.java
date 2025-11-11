package edu.wisc.game.tools;
 
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.text.*;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.math.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.svg.Curve;

import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.tools.MwSeries.MoveInfo;
import edu.wisc.game.sql.ReplayedEpisode.RandomPlayer;



/** As
 * requested by PK, 2025-10
 */
public class BuildCurves extends MwByHuman {

    protected final CurveMode curveMode;
    protected final CurveArgMode curveArgMode;
    protected final MedianMode medianMode;

    /** Used in processing random player runs */
    private final MakeMwSeries randomMakeMwSeries;

    
    /** Info about each episode gets added here */
    //    public Vector<MwSeries> savedMws = new Vector<>();

    /** @param  _targetStreak this is how many consecutive error-free moves the player must make (e.g. 10) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _targetR the product of R values of a series of consecutive moves should be at least this high) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
     */
    public BuildCurves(RandomPlayer _randomPlayerModel,
		       PrecMode _precMode,
		       CurveMode _curveMode,
		       CurveArgMode _curveArgMode,
		       MedianMode _medianMode,
		       int _targetStreak, double _targetR, double _defaultMStar, Fmter _fm) {
	super( _precMode, _targetStreak, _targetR, _defaultMStar, _randomPlayerModel, _fm);
	curveMode =  _curveMode;
	curveArgMode =  _curveArgMode;
	medianMode = _medianMode;
	randomMakeMwSeries = new MakeMwSeries(target,  PrecMode.Ignore,  300,  1e9, _defaultMStar);
    }

    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("For usage, see tools/build-curves.html\n\n");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }


    static boolean doRandom = true;
    Vector<MwSeries> randomMws = new Vector<>();


    /** (Being rewritten for BuildCurves. Note that in MoveInfo data, successful
	picks are excluded from the record, as they are generally ignorable
	in the relevant analyses).

<p>
Saves the data (the summary of a series) for a single (player, ruleSet) pair. The saved data is put into an MwSeries object, which is then appened to savedMws.

	In some cases, a series can be skipped (not saved). This is the case
	if only the data for a specific target is requested (target!=null),
	or if we only want the data for "Naive" players.

	<p>This method is called from AnalyzeTranscripts.analyzePlayerRecord(),
	overriding the eponymous method in that class.
	
	@param section A vector of arrays, each array representing the recorded
	moves for one episode. In its entirety, <tt>section</tt> describes all episodes
	in the series (i.e., for one rule set). Before returning, this method clears this array.
	@param includedEpisodes All non-empty episodes played by this player in this rule set. This array must be aligned with section[]. Before returning, this method clears this array.
    */
  
    protected void saveAnyData(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
			       Vector<EpisodeHandle> includedEpisodes)
	throws  IOException, IllegalInputException,  RuleParseException {

	if (includedEpisodes.size()==0) return; // empty series (all "give-ups")

	EpisodeHandle eh = includedEpisodes.firstElement();
	Vector<Board> boardHistory = null;
	P0andR p0andR =  computeP0andR(section, eh.para, eh.ruleSetName, boardHistory);

	if (eh.neededPartnerPlayerId != null) {
	    System.out.println("For " +eh.playerId + ", also make partner entry for " + eh.neededPartnerPlayerId);
	    for(int j=0; j<2; j++) {
		MwSeries ser= makeMwSeries.mkMwSeries(section, includedEpisodes, p0andR, j, true);
		if (ser!=null) savedMws.add(ser);
	    }
	} else {
	    System.out.println("For " +eh.playerId + ", no partner needed");
	    MwSeries ser =  makeMwSeries.mkMwSeries(section, includedEpisodes, p0andR, -1, true);
	    if (ser!=null) savedMws.add(ser);
	}


	if (doRandom) { // zzz play a few random episodes with the same board
	    try {
	    Vector<MwSeries> sers =
		RandomPlay.randomPlay(section,
				      includedEpisodes,
				      -1,
				      randomPlayerModel,
				      randomMakeMwSeries);
	    randomMws.addAll(sers);
	    } catch(CloneNotSupportedException ex) {}
	}
	
	section.clear();
	includedEpisodes.clear();
    }

    /** Various things that may be used to draw curves */
    /*
    static class MoveInfo {
	boolean success;
	double p0;
    }
    *
    /** Besides the aggregate information that MwSeries contains, this
	also has per-move data used to draw curves */
    /*
    static class MwSeries2 extends MwSeries {
	MoveInfo[] moveInfo;
	MwSeries2(EpisodeHandle o, Set<String> ignorePrec, int chosenMover) {
	    super(o, ignorePrec, chosenMover);
	}
    }
    */				      

    /** Creates an MwSeries object for a (player,rule set)
	interaction, and adds it to savedMws, if appropriate.

	@param section All transcript data for one series of episodes
	(i.e. one rule set), split into subsections (one per episode)

        @param chosenMover If it's -1, the entire transcript is
	analyzed. (That's the case for 1PG and coop 2PG). In adve 2PG,
	it is 0 or 1, and indicates which partner's record one extracts
	from the transcript.
     */
    /*
    private MwSeries fillMwSeries(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
				  Vector<EpisodeHandle> includedEpisodes,
				  double[] rValues, int chosenMover)
	throws  IOException, IllegalInputException,  RuleParseException {

	// this players successes and failures on the rules he's done
	EpisodeHandle eh = includedEpisodes.firstElement();
	MwSeries2 ser = new MwSeries2(eh, ignorePrec, chosenMover);
	fillMwSeriesMain( section,	 includedEpisodes, rValues, chosenMover, ser);



	
	return ser;
    }
    */


    public static void main(String[] argv) throws Exception {
	RunParams p = new RunParams(argv);
	//MwByHuman processor = p.mkProcessor();
	Fmter plainFm = new Fmter();
	BuildCurves processor = new BuildCurves(p.randomPlayerModel,
						p.precMode, p.curveMode, p.curveArgMode, p.medianMode,
						p.targetStreak, p.targetR, p.defaultMStar, plainFm);
	
	try {
	    if (p.importFrom.size()==0) {
	    // Extract the data from the transcript, and put them into savedMws
		processor.processStage1(p.plans, p.pids, p.nicknames, p.uids);
		
		if (p.exportTo!=null) {
		    File gsum=new File(p.exportTo);		    
		    exportMws(gsum, processor.savedMws);
		    if (doRandom) {
			gsum=new File( randomMwsFileName(p.exportTo));
			exportMws(gsum, processor.randomMws);		
		    }
		    
		}
	    } else {
		processor.savedMws.clear();		
		processor.randomMws.clear();
		for(String from: p.importFrom) {
		    File g = new File(from);
		    MwSeries.readFromFile(g, processor.savedMws);
		    System.out.println("DEBUG: Has read " + processor.savedMws.size() + " MWS data lines");
		    
		    if (doRandom) {
			g=new File(randomMwsFileName(from));
			MwSeries.readFromFile(g ,processor.randomMws);
			System.out.println("DEBUG: Has read " + processor.randomMws.size() + " random MWS data lines");
		    }		    
		}
	    }

	    //processor.printCurveData();

	    processor.doCurves();
	    
	    
	    
	} finally {
	    String text = processor.getReport();
	    System.out.println(text);
	}
    }

    private static String randomMwsFileName(String fname) {
	String f1 = fname.replaceAll("\\.csv$", ".random.csv");
	if (f1.equals(fname)) throw new IllegalArgumentException("Cannot convert file name: "+fname);
	return f1;
		
    }

    /** Generates plots for all "experiences" and all requested
	plottting modes, and writes them to SVG files.

	After that, creates index.html in each directory, so that the
	whole thing can be uploaded to a web server and navigated in a
	browser

	*/
    void doCurves() throws IOException {

	File d = new File("out");
	CurveMode[] modes = {curveMode};
	CurveArgMode[] argModes = {curveArgMode};
	if (curveMode==CurveMode.ALL) {
	    modes=CurveMode.class.getEnumConstants();
	}

	if (curveArgMode==CurveArgMode.ALL) {
	    argModes=CurveArgMode.class.getEnumConstants();
	}
	
	for(CurveArgMode argMode: argModes) {
	    if (argMode==CurveArgMode.ALL) continue;
	    for(CurveMode mode: modes) {
		if (mode==CurveMode.ALL) continue;
		HashMap<String, OneKey>  h = doPlots(mode, argMode);
		File dm = new File(d, mode.toString() + "_" + argMode);
		for(String key: h.keySet()) {
		    OneKey z = h.get(key);
		    File f = new File(dm, simplifyKey(key) + ".svg");
		    f.getParentFile().mkdirs();
		    Util.writeTextFile(f, z.plot);
		}
	    }
	}
	FileUtil.mkIndexes(d);
    }

    /** Replaces "dir/r1:dir/r2:dir/r3" with "dir/r1.r2.r3", in order to 
	be able to create a more manageable file name */
    private static String simplifyKey(String key) {
	String v[] = key.split(":");
	if (v.length<2) return key;
	// remove the dir part from all segments other than the very first one
	for(int j=1; j<v.length; j++) {
	    v[j] = v[j].replaceFirst(".*/", "");
	}
	return String.join(".", v);
    }


    
    /** The data for one key */
    static class OneKey extends Vector<MwSeries> {
	String plot;
	Curve[] curves = {};
	
	Curve[] mkCurves(CurveMode mode, CurveArgMode argMode, double maxX) {
	    curves = new Curve[size()];
	    int k = 0;
	    for(MwSeries ser: this) {
		curves[k++] = mkCurve(ser, mode, argMode, maxX);
	    }
	    return curves;
	}	 
    }


    static class DataMap extends 	HashMap<String, OneKey> {
	void mkCurves(CurveMode mode, CurveArgMode argMode, double maxX) {
	    for(String key: keySet()) {
		OneKey z = get(key);
		z.mkCurves( mode, argMode, maxX);
	    }
	}
    }

    /** Breaks down the list of series by the key */
    private static DataMap prepMaps(Vector<MwSeries> all, PrecMode precMode) {
	DataMap h = new DataMap();
	for(MwSeries ser: all) {
	    String key = ser.getKey(precMode);
	    OneKey z = h.get(key);
	    if (z==null) {
		h.put(key, z=new OneKey());
	    }
	    z.add(ser);
	}
	return h;
    }

    
    /** Produces a plot for each key that appears in savedMws.
	@return a HashMap that maps each key to a OneKey structure that includes the corresponding plot
     */
    private HashMap<String, OneKey>  doPlots(CurveMode mode, CurveArgMode argMode) {
	int H=600;
	int W=600;
	int maxX = (argMode==CurveArgMode.C)? roundUp(findMaxC(savedMws)):  (int)defaultMStar;
	
	DataMap h = prepMaps(savedMws, precMode);
	h.mkCurves(mode, argMode, maxX);
	DataMap hr = doRandom? prepMaps(randomMws, PrecMode.Ignore): null;
	if (doRandom) hr.mkCurves(mode, argMode, maxX);
	
	String[] results = new String[h.size()];

	for(String key: h.keySet()) {
	    OneKey z = h.get(key);
	    Curve[] randomCurves = {};
	    if (doRandom) {
		OneKey w = hr.get( MwSeries.keyToIgnoreKey(key));
		if (w!=null) randomCurves = w.curves;
	    }
	    
	    boolean useExtra =(medianMode==MedianMode.Extra);
	    SvgPlot svg = new SvgPlot(W, H, maxX);
	    svg.adjustMaxY(z.curves, randomCurves, useExtra);
	    svg.addPlot(z.curves, randomCurves, useExtra);
	    z.plot = svg.complete();
	}
	return h;
    }

    /** @param keys Two keys. Both curve sets are to be put into the same plot. */
    private String  doDoublePlot(CurveMode mode, CurveArgMode argMode, String keys[]) {
	int H=600;
	int W=600;
	int maxX = (argMode==CurveArgMode.C)? roundUp(findMaxC(savedMws)):  (int)defaultMStar;

	DataMap h = prepMaps(savedMws, precMode);
	h.mkCurves(mode, argMode, maxX);
	DataMap hr = doRandom? prepMaps(randomMws, PrecMode.Ignore): null;
	if (doRandom) hr.mkCurves(mode, argMode, maxX);

	boolean useExtra =(medianMode==MedianMode.Extra);
	SvgPlot svg = new SvgPlot(W, H, maxX);

	Curve[][] curves = new Curve[2][], randomCurves=new Curve[2][];
	for(int j=0; j<2; j++) {
	    String key = keys[j];
	    OneKey z = h.get(key);
	    if (z==null) throw new IllegalArgumentException("This key does not occur in the data: " + key);
	    curves[j]= z.curves;
	    randomCurves[j]= (doRandom)? new Curve[0]:  hr.get( MwSeries.keyToIgnoreKey(key)).curves;

	    svg.adjustMaxY(curves[j], randomCurves[j], useExtra);
	}
	
	for(int j=0; j<2; j++) {
	    svg.addPlot(curves[j], randomCurves[j], useExtra);
	}
	return svg.complete();
    }


    
    private static int findMaxC(Vector<MwSeries> savedMws) {
	int n = 0;
	for(MwSeries ser: savedMws) {
	    int q = findMaxC(ser);
	    if (q>n) {
		//System.out.println("maxC( " + ser.getKey(precMode) + " )=" + q);
		n = q;
	    }
	}
	return n;
    }

    /** How many pieces were removed in this series? */
    private static int findMaxC(MwSeries ser) {
	int n=0;
	for(MoveInfo mi: ser.moveInfo) {
	    if (mi.success)	n++;
	}
	return n;
    }

    /** Paul's suggestion on dash arrays (2025-10-28):
	<ul>
	<li>dotted lines, all the way along, for people who don't meet the criterion

	<li>dashed before, and solid after the (m*,w*) point for people who do meet the criterion. Actually, since we are plotting GOOD moves, not ALL moves, I should not call it m*.  Better is to call the axis something like c - for correct moves, And the criterion point becomes (c".w*). 
	</ul>
    */
    private static Curve mkCurve(MwSeries ser, CurveMode mode, CurveArgMode argMode, double maxX) {

	int n = (argMode ==  CurveArgMode.C)? findMaxC(ser):  ser.moveInfo.length;
	
	double [] yy = new double[n+1];
	yy[0] = 0;
	double sumE=0, sumZP=0;
	int j=1;
	int q=0, lastQ=0;
	for(int m=0; m<ser.moveInfo.length; m++) {
	    MoveInfo mi = ser.moveInfo[m];
	    if (!mi.success)	sumE++;
	    else q++;
	    sumZP += 1-mi.p0;

	    double aai = (sumZP==0)? 0: sumE/sumZP;

	    double y;
	    if (mode==CurveMode.W) 	    y = sumE;
	    else if (mode==CurveMode.AAI) 	    y = aai;
	    else if (mode==CurveMode.AAIB) 	    y = aai * (m+1);
	    else throw new IllegalArgumentException("curveMode=" + mode);

	    if (argMode ==  CurveArgMode.M) {	    
		yy[j++] = y;
	    } else if (argMode ==  CurveArgMode.C) {
		if (q>lastQ) yy[j++] = y;
		lastQ = q;
	    } else throw new IllegalArgumentException("curveArgMode=" + argMode);
	}
	if (j!= n+1)  {
	    System.out.println("ERROR here: " + ser.toCsv());
	    System.out.println(Util.joinNonBlank(" ", ser.moveInfo));
	    System.out.println("yy[] size mismatch for mode=" + mode + ":" + argMode+": expect " + (n+1) +", found " + j);
	    //System.exit(1);
	    throw new IllegalArgumentException("yy[] size mismatch for mode=" + mode + ":" + argMode+": expect " + (n+1) +", found " + j);
	}
	Double extraX = (mode==CurveMode.W || mode==CurveMode.AAIB) && ser.getLearned()? maxX: null;
	Curve c =  new Curve(yy, extraX);
	String [] dash = ser.learned? new String[] {"2", ""}:	new String[] {"1", null};
	c.setDash(dash);
	return c;
    }

    /** Sample usage:
	    SvgPlot svg = new SvgPlot(W, H, maxX);
	    svg.adjustMaxY(curves, randomCurves, useExtra);
	    svg.addPlot(curves, randomCurves, useExtra);
	    String plot = svg.complete();
    */
    static class SvgPlot {
	Vector<String> sections = new Vector<>();
	int W, H;
	double maxX, maxY=0;
	
	SvgPlot(int _W, int _H, double _maxX) {
	    W = _W;
	    H = _H;
	    maxX = _maxX;
	}

	/** Should only be called once maxY is known */
	private Vector<String> mkGrid() {
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

	    for(int m: ticPoints((int)maxY)) {
		double x = 0, y= (int)( (maxY-m)*H/maxY);
		v.add("<text x=\"" +(x-20) + "\" y=\"" +y + "\" fill=\"black\">" +
		      m + "</text>");
		v.add("<line x1=\"0\" y1=\""+y+"\" x2=\""+W+"\" y2=\""+y+"\" stroke=\"black\" stroke-dasharray=\"3 5\"/>");
	    }
	    return v;
	}

	
	String complete() {
	    int width = W+70;
	    int height = H+60;
	    
	    Vector<String> v = new Vector<>();
	    v.add( "<svg   xmlns=\"http://www.w3.org/2000/svg\" width=\"" +
		   width + "\" height=\"" + height +
		   "\" viewBox=\"-20 -20 " + (W+50) + " " + (H+40)+"\">");
	    
	    maxY +=2; // give some blank space above the highest curve

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
	    @param randomCurves If not empty, also plot the median of these curves
	*/
	void  addPlot(Curve[] curves, Curve[] randomCurves, boolean useExtra) {
	    //if (curves.length==0) return "No data have been collected";

	    double yFactor = -H/maxY; // getMaxY();
	    double xFactor = W/maxX;
	
	    Vector<String> v = new Vector<>();
			
	    v.add( Curve.mkShading(curves, 0,H, xFactor, yFactor, useExtra));
	    
	    v.add( Curve.mkSvgNoOverlap(curves, 0, H, xFactor, yFactor, "green",1));
	
	    v.add( Curve.mkMedianSvgPathElement(curves, 0,H,xFactor, yFactor, "red",3, null, null, null, useExtra));

	    if (randomCurves.length>0) {
		v.add( Curve.mkMedianSvgPathElement(randomCurves, 0,H,xFactor, yFactor, "blue",6, "0.01 8", "round", "0.4", useExtra));
	    }
		
	    //return v;
	    sections.addAll(v);
	}
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

    /** Prints the curve data from savedMws */
    private void printCurveData() {
	PrintStream wsum = System.out;
	// new PrintWriter(new FileWriter(gsum, false));
	wsum.println( MwSeries.header);

	for(int j=0; j < savedMws.size(); j++) {
	    MwSeries ser = savedMws.get(j);
	    wsum.println(ser.toCsv());		    
	    wsum.println(Util.joinNonBlank(" ", ser.moveInfo));
	}

	if (wsum!=System.out) 	wsum.close();
    }

	
	

	
    
}
