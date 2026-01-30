package edu.wisc.game.tools;
 
import java.io.*;
import java.util.*;
import java.util.regex.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.formatter.*;
import edu.wisc.game.svg.Curve;

import edu.wisc.game.tools.MwSeries.MoveInfo;
import edu.wisc.game.sql.ReplayedEpisode.RandomPlayer;

/** Drawing cumulative curves of human performance (error count W, or
    some derived metric, against the number of moves M or the number
    of correct moves C) for individual players, as well as the median
    (and its confidence interval) for a population playing a
    particular rule set. Pair plots (comparing players playing 2
    different rule sets) are also supported.

    <p>This class is derived from MwByHuman in order to reuse the
    infrastructure used for selecting a slice of data (e.g. everything
    related to a particular experiment) and dividing it into player
    populations associated with various "experiences".

    <p>
    As  requested by PK, 2025-10
 */
public class BuildCurves extends MwByHuman {

    protected final CurveMode curveMode;
    protected final CurveArgMode curveArgMode;
    protected final MedianMode medianMode;

    /** Used in processing random player runs */
    private final MakeMwSeries randomMakeMwSeries;

    /**  BuildCurves: do we annotate the longest flat section? */
    boolean doAnn = false;
    /** Allow coinciding curves to overlap (instead of shifting them a bit) */
    boolean doOverlap = false;

    /** @param  _targetStreak this is how many consecutive error-free moves the player must make (e.g. 10) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _targetR the product of R values of a series of consecutive moves should be at least this high) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
     */
    public BuildCurves(boolean _doRandom,
		       RandomPlayer _randomPlayerModel,
		       PrecMode _precMode,
		       CurveMode _curveMode,
		       CurveArgMode _curveArgMode,
		       MedianMode _medianMode,
		       int _targetStreak, double _targetR, double _defaultMStar, Fmter _fm) {
	super( _precMode, _targetStreak, _targetR, _defaultMStar, _randomPlayerModel, _fm);
	doRandom = _doRandom;
	curveMode =  _curveMode;
	curveArgMode =  _curveArgMode;
	medianMode = _medianMode;
	randomMakeMwSeries = new MakeMwSeries(target,  PrecMode.Ignore,  300,  1e9, _defaultMStar);
    }


    public BuildCurves(RunParams p, Fmter _fm) {
	super( p.precMode, p.targetStreak, p.targetR, p.defaultMStar, p.randomPlayerModel, _fm);

	doRandom = p.doRandom;
	curveMode =  p.curveMode;
	curveArgMode =  p.curveArgMode;
	medianMode = p.medianMode;
	randomMakeMwSeries = new MakeMwSeries(target,  PrecMode.Ignore,  300,  1e9, p.defaultMStar);

	doAnn = p.doAnn;
	doOverlap = p.doOverlap;
    }
    
    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("For usage, see tools/build-curves.html\n\n");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    boolean doRandom = false;
    private Vector<MwSeries> randomMws = new Vector<>();

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


	if (doRandom) { // play a few random episodes with the same board
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
    
    public static void main(String[] argv) throws Exception {
	RunParams p = new RunParams(argv);
	Fmter plainFm = new Fmter();
	BuildCurves processor = new BuildCurves(p.doRandom, p.randomPlayerModel,
						p.precMode, p.curveMode, p.curveArgMode, p.medianMode,
						p.targetStreak, p.targetR, p.defaultMStar, plainFm);
	processor.doAnn = p.doAnn;
	processor.doOverlap = p.doOverlap;
	try {
	    if (p.importFrom.size()==0) {
	    // Extract the data from the transcript, and put them into savedMws
		processor.processStage1(p.plans, p.pids, p.nicknames, p.uids);
		
		if (p.exportTo!=null) {  // export the data, if requested
		    File gsum=new File(p.exportTo);		    
		    exportMws(gsum, processor.savedMws);
		    if (p.doRandom) {
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
		    
		    if (p.doRandom) {
			g=new File(randomMwsFileName(from));
			MwSeries.readFromFile(g ,processor.randomMws);
			System.out.println("DEBUG: Has read " + processor.randomMws.size() + " random MWS data lines");
		    }		    
		}
	    }

	    //processor.printCurveData();
	    File d = new File("out");
	    processor.doCurves(d);
	    if (p.doPairs) {
		processor.doPairCurves();
	    }
	    
	    
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
	browser.

	@return A map that maps e.g. "W_C" to a DataMap that stores SVG file of "W_C" for various experiences.

	*/
    public HashMap<String, DataMap> doCurves(File d) throws IOException {
	HashMap<String, DataMap> result = new HashMap<>();
	CurveMode[] modes = {curveMode};
	CurveArgMode[] argModes = {curveArgMode};
	if (curveMode==CurveMode.ALL) {
	    modes=CurveMode.class.getEnumConstants();
	} else 	if (curveMode==CurveMode.NONE) {
	    modes=new CurveMode[0];
	    return result;
	}

	if (curveArgMode==CurveArgMode.ALL) {
	    argModes=CurveArgMode.class.getEnumConstants();
	} else if (curveArgMode==CurveArgMode.NONE) {
	    argModes=new CurveArgMode[0];
	    return result;
	}
	
	for(CurveArgMode argMode: argModes) {
	    if (argMode==CurveArgMode.ALL ||
		argMode==CurveArgMode.NONE) continue;
	    for(CurveMode mode: modes) {
		if (mode==CurveMode.ALL ||
		    mode==CurveMode.NONE) continue;

		boolean nonTrivial =  mode==CurveMode.AAIH;
		int maxX = (argMode==CurveArgMode.C)?
		    findMaxC(savedMws, nonTrivial):  findMaxM(savedMws);  

		if (nonTrivial)	System.out.println("DEBUG: nt="+nonTrivial+", maxX="+ maxX);
		
		DataMap h = new DataMap(savedMws, precMode);
		boolean needAnn = doAnn && mode == CurveMode.W;
		h.mkCurves(mode, argMode, maxX, needAnn);
		DataMap hr = doRandom? new DataMap(randomMws, PrecMode.Ignore): null;
		if (doRandom) hr.mkCurves(mode, argMode, maxX, false);

		//		System.out.println("call doPlots " + mode);
		h = doPlots(h, hr, mode, argMode, maxX);
		//		System.out.println("done doPlots " + mode);
		File dm = new File(d, mode.toString() + "_" + argMode);
		for(String key: h.keySet()) {
		    OneKey z = h.get(key);
		    z.file = new File(dm, simplifyKey(key) + ".svg");
		    z.file.getParentFile().mkdirs();
		    Util.writeTextFile(z.file, z.plot);
		}
		result.put(""+ mode + "_" + argMode, h);
	    }
	}
	FileUtil.mkIndexes(d);
	return result;
    }


    /** Creates a number of "pair plots", each pair plot comparing
	two distinct experiences of player populations (e.g. two rule sets)
	as two bundles of curves in the same image.
    */
    void doPairCurves() throws IOException {

	File d = new File("out/pairs");
	CurveMode[] modes = {curveMode};
	CurveArgMode[] argModes = {curveArgMode};

	if (curveMode==CurveMode.ALL) {
	    modes=CurveMode.class.getEnumConstants();
	} else 	if (curveMode==CurveMode.NONE) {
	    modes=new CurveMode[0];
	    return;
	}

	if (curveArgMode==CurveArgMode.ALL) {
	    argModes=CurveArgMode.class.getEnumConstants();
	} else if (curveArgMode==CurveArgMode.NONE) {
	    argModes=new CurveArgMode[0];
	    return;
	}

	
	for(CurveArgMode argMode: argModes) {
	    if (argMode==CurveArgMode.ALL ||
		argMode==CurveArgMode.NONE) continue;
	    for(CurveMode mode: modes) {
		if (mode==CurveMode.ALL ||
		    mode==CurveMode.NONE) continue;

		File dm = new File(d, mode.toString() + "_" + argMode);
		boolean nonTrivial =  mode==CurveMode.AAIH;
		int maxX = (argMode==CurveArgMode.C)? findMaxC(savedMws, nonTrivial):  findMaxM(savedMws);  
		
		DataMap h = new DataMap(savedMws, precMode);
		boolean needAnn = doAnn && mode == CurveMode.W;
		h.mkCurves(mode, argMode, maxX, needAnn);
		DataMap hr = doRandom? new DataMap(randomMws, PrecMode.Ignore): null;
		if (doRandom) hr.mkCurves(mode, argMode, maxX, needAnn);

		for(String key0: h.keySet()) {
		    for(String key1: h.keySet()) {
			if (key1.equals(key0)) continue;
			String keys[] = {key0, key1};			
			String plot =  doDoublePlot(h, hr, mode, argMode, maxX, keys);

			File f = new File(dm, simplifyKey(key0) + "-" + basicKey(key1) + ".svg");
			f.getParentFile().mkdirs();
			Util.writeTextFile(f, plot);
		    }
		}
	    }
	}
	FileUtil.mkIndexes(d.getParentFile());
    }


    
    /** Replaces "dir/r1:dir/r2:dir/r3" with "dir/r1.r2.r3", in order to 
	be able to create a more manageable file name for an "experience". */
    private static String simplifyKey(String key) {
	String v[] = key.split("[-:]");
	if (v.length<2) return key;
	// remove the dir part from all segments other than the very first one
	for(int j=1; j<v.length; j++) {
	    v[j] = v[j].replaceFirst(".*/", "");
	}
	return String.join(".", v);
    }

    /** Removes the directory part */
    static String basicKey(String key) {
	return key.replaceFirst(".*/", "");
    }

    
    /** An OneKey objects stores all the relevant data (all players'
	MwSeries and Curve objects, and, eventually, a plot displaying
	all these curves) for one experience key (e.g.  a rule set
	name).
    */
    static public class OneKey extends Vector<MwSeries> {
	String plot;
	Curve[] curves = {};
	/** If the plot has been written to file, here's a handle */
	public File file = null;
	
	Curve[] mkCurves(CurveMode mode, CurveArgMode argMode, int maxX, boolean needAnn) {
	    boolean nonTrivial =  mode==CurveMode.AAIH;
	    int maxXCurves = (argMode==CurveArgMode.C)? findMaxC(this, nonTrivial):  findMaxM(this);  
	    
	    curves = new Curve[size()];
	    int k = 0;
	    for(MwSeries ser: this) {
		curves[k++] = mkCurve(ser, mode, argMode, maxXCurves, needAnn);
	    }
	    return curves;
	}	 
    }

    /** A map that maps each experience key (e.g. a rule set name) to a
	OneKey object containing the curve data and the plot for that key.
    */
    static public class DataMap extends 	HashMap<String, OneKey> {

	/** Breaks down the list of series by the experience key, producing
	    a DataMap object which contains, for each experience key,
	    a OneKey object with all players' MwSeries data for that experience.

	    @param precMode controls how the "space of experiences" is organized
	*/
	DataMap(Vector<MwSeries> all, PrecMode precMode) {
	    for(MwSeries ser: all) {
		String key = ser.getKey(precMode);
		OneKey z = get(key);
		if (z==null) {
		    put(key, z=new OneKey());
		}
		z.add(ser);
	    }
	}

	void mkCurves(CurveMode mode, CurveArgMode argMode, int maxX, boolean needAnn) {
	    for(String key: keySet()) {
		OneKey z = get(key);
		z.mkCurves( mode, argMode, maxX, needAnn);
	    }
	}

    }
    
    /** Produces a plot for each key that appears in savedMws.
	@param h a PrepMap that has all players' MwSeries data for each key
	@param hr ditto, for the artificial random players
	@return a HashMap that maps each key to a OneKey structure that includes the corresponding plot
     */
    private DataMap  doPlots(DataMap h, DataMap hr, CurveMode mode, CurveArgMode argMode, int maxX) {
	//	String [] colors = {"green", "red", "blue"};
	String [] colors = {"green", "red", "blue", "grey"};

	int H=600;
	int W=600;
	
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
	    svg.doOverlap = doOverlap;
	    svg.adjustMaxY(z.curves, randomCurves, useExtra);
	    //System.out.println("doPlots: call addPlot "+ key);
	    svg.addPlot(z.curves, randomCurves, useExtra, colors, 0);

	    //System.out.println("doPlots: call complete "+ key);
	    z.plot = svg.complete();
	    //System.out.println("doPlots: done "+key);
	}
	return h;
    }

    /** Generates an SVG plot with 2 bundles of curves, for two specified experiences.
       @param keys Two experience keys. Both curve bundles are to be put into the same plot. */
    private String  doDoublePlot(DataMap h, DataMap hr,CurveMode mode, CurveArgMode argMode, int maxX, String keys[]) {
	String [][] colors = {{"red", "red", "red", null},
			      {"blue", "blue", "blue", null}};
	
	int H=600;
	int W=600;

	boolean useExtra =(medianMode==MedianMode.Extra);
	SvgPlot svg = new SvgPlot(W, H, maxX);
	svg.doOverlap = doOverlap;
	Curve[][] curves = new Curve[2][], randomCurves=new Curve[2][];
	for(int j=0; j<2; j++) {
	    String key = keys[j];
	    OneKey z = h.get(key);
	    if (z==null) throw new IllegalArgumentException("This key does not occur in the data: " + key);
	    curves[j]= z.curves;
	    randomCurves[j]= (doRandom)?hr.get( MwSeries.keyToIgnoreKey(key)).curves:  new Curve[0];

	    svg.adjustMaxY(curves[j], randomCurves[j], useExtra);
	}

	String mainColor[] = {colors[0][0], colors[1][0]};
	svg.addLegendPair(mainColor, keys);
	
	for(int j=0; j<2; j++) {
	    svg.addPlot(curves[j], randomCurves[j], useExtra, colors[j], j);
	}
	return svg.complete();
    }
    
    private static int findMaxM(Vector<MwSeries> savedMws) {
	int n = 0;
	for(MwSeries ser: savedMws) {
	    n = Math.max(n, ser.moveInfo.length);
	}
	return n;
    }

    private static int findMaxC(Vector<MwSeries> savedMws, boolean nonTrivial) {
	int n = 0;
	for(MwSeries ser: savedMws) {
	    int q = findMaxC(ser, nonTrivial);
	    if (q>n) {
		System.out.println("maxC(nt="+nonTrivial+")=" + q);
		n = q;
	    }
	}
	return n;
    }

    /** How many pieces were removed in this series?
	@parm nonTrivial If true, only count "non-trivial" successful
	moves, i.e. those where there was an opportunity for a wrong move.
     */
    private static int findMaxC(MwSeries ser, boolean nonTrivial) {
	int n=0;
	boolean needMu=true;
	double keepMu=0;
	String s = "";
	for(MoveInfo mi: ser.moveInfo) {
	    if (needMu) {
		keepMu = mi.mu;
		needMu = false;
	    }
	    if (mi.success) {
		if (nonTrivial) {
		    if (keepMu>1) n++;
		    s += (keepMu>1) ? "+":"-";
		} else {
		    n++;
		}
		needMu = true;
	    }
	}
	//	if (nonTrivial) System.out.println(set.getKey(PrecMode.Ignore) + ": " + s);
	return n;
    }

    /** Creates a curve object displaying a specified metric for an individual player, with an appropriate line style.

       <p>Line style:
	<ul>
	<li>dotted lines, all the way along, for people who don't meet the criterion;
	<li>solid before, and dotted after the (m*,w*) point for people who do meet the criterion. 
	</ul>

	<p>Paul's method AAID:
	<pre>
	Part 1. Can there be an "normalized to start of good runs" curve for each different player that is on the C vs. W plot?  

sumW=0;
sumE=0;
sumC=0;
C=0; 
plot(0,0);
at each move that is either  correct or wrong (not ignored)
  if wrong: sumW+=1
      sumE+=(1-p)  
      no point on plot
  if correct: sumC +=1
     if preceding move was correct
                  plot  (sumC,y)
     if preceding move was wrong
                 y=(sumW/sumE)*sumC
                 plot(sumC,y)
     if this is the first move, plot (1,0). 
</pre>

@param ser The player's prepackaged data (containing all his moves on a particular rule set)

@param needAnn Annotate the beginning of the longest flat section
    */
    private static Curve mkCurve(MwSeries ser, CurveMode mode, CurveArgMode argMode, int maxX, boolean needAnn) {
	boolean nonTrivial =  mode==CurveMode.AAIH;
	int n = (argMode == CurveArgMode.C)? findMaxC(ser, nonTrivial):  ser.moveInfo.length;
	
	double [] yy = new double[n+1];
	yy[0] = 0;
	double sumW=0, sumZP=0, sumZP1=0, recentSumW=0, recentSumZP=0,recentSumP=0,
	    omega=0, aaid=0, aaie = 0;
	double sumMu1 = 0; // sum over C, not over M! Only sum mu from the first move after the board changes
	int q=0, lastQ=0; // count of correct moves (C, in Paul's notation)
	double keepMu = 0, aaih = 0;// qNonTrivial;
	
	for(int m=0; m<ser.moveInfo.length; m++) {
	    MoveInfo mi = ser.moveInfo[m];

	    sumZP += 1-mi.p0;
	    recentSumZP += 1-mi.p0;
	    recentSumP += mi.p0;

	    if (m==0 || ser.moveInfo[m-1].success) {
		// new board, new mu
		keepMu = mi.mu;
		sumMu1 += mi.mu-1;		
	    }
	    
	    if (mi.success)	{
		if (nonTrivial) {
		    if (keepMu>1) q++;
		} else {
		    q++;
		}
	    } else {
		sumW++;
		recentSumW++;
		sumZP1 += 1-mi.p0;
		aaid = (sumW/sumZP1) * q;
		aaie = (sumW/sumZP) * q;
		aaih += 1.0/(keepMu - 1);
	    } 
	    
	    boolean omegaPoint = (argMode==CurveArgMode.M) || (q>lastQ);
	    if (omegaPoint) {
		if (recentSumZP==0) {
		    System.out.println("Found a user who was worse than random");
		    recentSumZP=0.5;
		    //throw new IllegalArgumentException("sumZP=0, mi="+mi);
		}
		omega += recentSumW * recentSumP/recentSumZP;
		recentSumZP=0;
		recentSumP=0;
		recentSumW=0;				
	    }

	    double aai = (sumZP==0)? 0: sumW/sumZP;
	    double aaib = aai * (m+1);
	    double aaic = aai * q;
	    double aaig = (sumMu1==0)? 0: (sumW/sumMu1) * q;
	    
	    double y;
	    if (mode==CurveMode.W) 	    y = sumW;
	    else if (mode==CurveMode.OMEGA) 	    y = omega;
	    else if (mode==CurveMode.AAI) {
		//System.out.println("DEBUG: m=" +m+", sumW/sumZP=" + sumW+"/"+sumZP+"=" + aai);
		y = aai;
	    }
	    else if (mode==CurveMode.AAIB) 	    y = aaib;
	    else if (mode==CurveMode.AAIC) 	    y = aaic;
	    else if (mode==CurveMode.AAID) 	    y = aaid;
	    else if (mode==CurveMode.AAIE) 	    y = aaie;
	    else if (mode==CurveMode.AAIG) 	    y = aaig;
	    else if (mode==CurveMode.AAIH) 	    y = aaih;
	    else throw new IllegalArgumentException("curveMode=" + mode);

	    if (argMode ==  CurveArgMode.M) {	    
		yy[m+1] = y;
	    } else if (argMode ==  CurveArgMode.C) {
		if (q>lastQ) yy[q] = y;
		lastQ = q;
	    } else throw new IllegalArgumentException("curveArgMode="+argMode);
	}

	if (argMode ==  CurveArgMode.C) {
	    if (q!= n)  {
		if (mode==CurveMode.AAIH) {
		    // the yy[] is shorter than 27 because some moves
		    // were trivial
		    yy = Arrays.copyOf(yy, q+1);
		} else {		
		    System.out.println("ERROR here: " + ser.toCsv());
		    System.out.println(Util.joinNonBlank(" ", ser.moveInfo));
		    String msg = "yy[] size mismatch for mode=" + mode + ":" + argMode+": expect n=" + (n) +", found q=" + q;
		    System.out.println(msg);
		    //System.exit(1);
		    throw new IllegalArgumentException(msg);
		}
	    }
	}

	//-- if requested, identify the longest flat section (the number of consecutive good moves, for W_C)
	int startLongest = -1, lenLongest = 0;
	if (needAnn) {
	    int i0 = 0;
	    for(int i=0; i<yy.length; i++) {
		if (yy[i] == yy[i0]) {
		    int len = (i-i0);
		    if (i0>0) len++;
		    if (len > lenLongest) {
			startLongest = i0;
			lenLongest = len;
		    }
		} else {
		    i0 = i;
		}
	    }
	}

	//-- extrapolation of a learner's curve, if feasible for the metric being used
	double[] ye = yy;
	int startExtra = yy.length-1;
	if (ser.getLearned()) {
	    double yLast = yy[startExtra];
	    if  (mode==CurveMode.W || mode==CurveMode.AAIB|| mode==CurveMode.AAID|| mode==CurveMode.AAIE||mode==CurveMode.AAIG||mode==CurveMode.AAIH||
		 mode==CurveMode.OMEGA) {
		//-- extrapolation by a horizontal line
		ye = new double[maxX+1];
		int j=0; 
		for(; j<= startExtra; j++) {
		    ye[j] = yy[j];
		}
		for(; j< ye.length; j++) {
		    ye[j] = yLast;
		}
	    } else if (mode==CurveMode.AAIC && argMode ==  CurveArgMode.C) {
		//-- extrapolation by a hyperbola,
		//-- y=gamma*C*W/(W+C)
		ye = new double[maxX+1];
		int j=0; 
		for(; j<= startExtra; j++) {
		    ye[j] = yy[j];
		}

		double gamma = (sumW==0) ? 0: yLast*(sumW+startExtra)/(double)(startExtra*sumW);
		
		for(; j< ye.length; j++) {
		    ye[j] = gamma * j * sumW / (double)(j+sumW);
		}
	    }
	    //System.out.println("call Curve(ye[0:"+startExtra+":" +(ye.length-1)+"])");
	}
	
	Curve c =  new Curve(ye, startExtra);
	String [] dash = ser.learned? new String[] {"", "4"}:	new String[] {"1", null};
	c.setDash(dash);

	if (needAnn && lenLongest>1) {
	    c.addAnnotation( startLongest, ""+ lenLongest);
	}

	return c;
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
