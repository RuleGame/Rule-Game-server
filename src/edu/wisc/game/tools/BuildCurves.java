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



/** As
 * requested by PK, 2025-10
 */
public class BuildCurves extends MwByHuman {

    protected final CurveMode curveMode;
    protected final CurveArgMode curveArgMode;
    
    /** Info about each episode gets added here */
    //    public Vector<MwSeries> savedMws = new Vector<>();

    /** @param  _targetStreak this is how many consecutive error-free moves the player must make (e.g. 10) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _targetR the product of R values of a series of consecutive moves should be at least this high) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
     */
    public BuildCurves(PrecMode _precMode,
		       CurveMode _curveMode,
		       CurveArgMode _curveArgMode,
		       int _targetStreak, double _targetR, double _defaultMStar,Fmter _fm    ) {
	super( _precMode, _targetStreak, _targetR, _defaultMStar, _fm);
	curveMode =  _curveMode;
	curveArgMode =  _curveArgMode;
    }

    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("For usage, see tools/build-curves.html\n\n");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    /** Now, the MW Test, using this.savedMws computed in
	stage1. Generates a report that's attached to this.results.
	@param precMode Controls how the series are assigned to "distinct experiences".
	@param fromFile Indicates that the m* data have come from an extrernal
	file, and are not internally computed.
     */
    public void processStage2(boolean fromFile, boolean useMDagger, File csvOutDir )    {
	if (error) return;

	File[] csvOut = MannWhitneyComparison.expandCsvOutDir(csvOutDir);
	
	MannWhitneyComparison.Mode mode = MannWhitneyComparison.Mode.CMP_RULES_HUMAN;
	MannWhitneyComparison mwc = new MannWhitneyComparison(mode);

	MwSeries[] a = savedMws.toArray(new MwSeries[0]);
	Comparandum[][] allComp = Comparandum.mkHumanComparanda(a,  precMode, useMDagger);	
	//System.out.println("DEBUG: Human comparanda: "+allComp[0].length+" learned, "+allComp[1].length+" unlearned, ");
	
	if (fromFile) {
	    result.append( fm.para("Processing data from an imported CSV file"));
	} else {
	    Vector<String> v = new Vector<>();
	    if (targetStreak>0) v.add("to make "+fm.tt(""+targetStreak)+" consecutive moves with no errors");
	    if (targetR>0) v.add("to make a series of successful consecutive moves with the product of R values at or above "+fm.tt(""+targetR));
	    
	    result.append( fm.para("In the tables below, 'learning' means demonstrating the ability " + Util.joinNonBlank(" or ", v)));

	    result.append( fm.para("mStar is the number of move/pick attempts the player makes until he 'learns' by the above definition. Those who have not learned, or take more than "+fm.tt(""+defaultMStar)+" move/pick attempts to learn, are assigned mStar="+defaultMStar));
	    result.append( fm.para("M-W matrix is computed based on " + (useMDagger? "mDagger" : "mStar")));
	}
	    
	result.append( mwc.doCompare("humans", null, allComp, fm, csvOut));
    }

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
	    MwSeries[] ser={
		fillMwSeries(section, includedEpisodes, p0andR, 0, true),
		fillMwSeries(section, includedEpisodes, p0andR, 1, true)};
	} else {
	    System.out.println("For " +eh.playerId + ", no partner needed");
	    MwSeries ser = fillMwSeries(section, includedEpisodes, p0andR, -1, true);
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
	BuildCurves processor = new BuildCurves(p.precMode, p.curveMode, p.curveArgMode,
						p.targetStreak, p.targetR, p.defaultMStar, plainFm);
	
	try {
	    if (p.importFrom.size()==0) {
	    // Extract the data from the transcript, and put them into savedMws
		processor.processStage1(p.plans, p.pids, p.nicknames, p.uids);
		
		if (p.exportTo!=null) {
		    File gsum=new File(p.exportTo);
		    processor.exportSavedMws(gsum);
		}
	    } else {
		processor.savedMws.clear();		
		for(String from: p.importFrom) {
		    File g = new File(from);
		    MwSeries.readFromFile(g, processor.savedMws);
		    //System.out.println("DEBUG: Has read " + processor.savedMws.size() + " data lines");
		}		
	    }
	    // M-W test on the data from savedMws
	    processor.processStage2( p.importFrom.size()>0, p.useMDagger, p.csvOutDir);


	    processor.printCurveData();

	    processor.doCurves();
	    
	    
	    
	} finally {
	    String text = processor.getReport();
	    System.out.println(text);
	}
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
	if (curveMode==null) {
	    modes=CurveMode.class.getEnumConstants();
	    argModes=CurveArgMode.class.getEnumConstants();
	}
	
	for(CurveArgMode argMode: argModes) {
	    for(CurveMode mode: modes) {
		HashMap<String, OneKey>  h = doPlots(mode, argMode);
		File dm = new File(d, mode.toString() + "_" + argMode);
		for(String key: h.keySet()) {
		    OneKey z = h.get(key);
		    File f = new File(dm, key + ".svg");
		    f.getParentFile().mkdirs();
		    Util.writeTextFile(f, z.plot);
		}
	    }
	}
	FileUtil.mkIndexes(d);
    }

    
    /** The data for one key */
    static class OneKey extends Vector<MwSeries> {
	String plot;
    }
    
    private HashMap<String, OneKey>  doPlots(CurveMode mode, CurveArgMode argMode) {

	int H=600;
	int W=600;
	int maxX = (argMode==CurveArgMode.Q)? roundUp(findMaxQ()):  (int)defaultMStar;
	
	HashMap<String, OneKey> h = new HashMap<>();
	for(MwSeries ser: savedMws) {
	    String key = ser.getKey(precMode);
	    OneKey z = h.get(key);
	    if (z==null) h.put(key, z=new OneKey());
	    z.add(ser);
	}
	String[] results = new String[h.size()];
	int j=0;
	for(String key: h.keySet()) {
	    OneKey z = h.get(key);
	    //MwSeries[] ss = new MwSeries[z.size()];
	    Curve[] curves = new Curve[z.size()];
	    int k = 0;
	    for(MwSeries ser: z) {
		curves[k++] = mkCurve(ser, mode, argMode);
	    }
	    z.plot = mkPlotSvg(W,H, maxX, curves);
	}

	return h;
    }


    private int findMaxQ() {
	int n = 0;
	for(MwSeries ser: savedMws) {
	    int q = findMaxQ(ser);
	    if (q>n) n = q;
	}
	return n;
    }

    /** How many pieces were removed in this series? */
    private int findMaxQ(MwSeries ser) {
	int n=0;
	for(MoveInfo mi: ser.moveInfo) {
	    if (mi.success)	n++;
	}
	return n;
    }

    private Curve mkCurve(MwSeries ser, CurveMode mode, CurveArgMode argMode) {

	int n = (argMode ==  CurveArgMode.Q)? findMaxQ(ser):  ser.moveInfo.length;
	
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
	    if (mode==CurveMode.E) 	    y = sumE;
	    else if (mode==CurveMode.AAI) 	    y = aai;
	    else if (mode==CurveMode.AAIB) 	    y = aai * (m+1);
	    else throw new IllegalArgumentException("curveMode=" + mode);

	    if (argMode ==  CurveArgMode.M) {	    
		yy[j++] = y;
	    } else if (argMode ==  CurveArgMode.Q) {
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
	return new Curve(yy);
    }
    
    //    double maxY;
    //    public double getMaxY() { return maxY;}


   /** Produces a SVG element for one of the curves */
    private String mkPlotSvg(int W, int H, double maxX, Curve[] curves) {
	if (curves.length==0) return "No data have been collected";

	double maxY=0;
	for(Curve curve: curves) {
	    double m = curve.getMaxY();
	    maxY = (m>maxY)? m: maxY;
	}

	double yFactor = -H/maxY; // getMaxY();
	double xFactor = W/maxX;
	
	/*
	
	GroupAggregateWrapper.AvgCurve cu=getCurves().get(i);
	
	Curve medianCu =  getMedianCurves().get(i);
	Curve medianCumulCu =  getMedianCumulCurves().get(i);
	Curve avgCumulCu =  getAvgCumulCurves().get(i);
	*/
	
	String s="";
	int width = W+50;
	int height = H+40;
	s += "<svg   xmlns=\"http://www.w3.org/2000/svg\" width=\"" +
	    width + "\" height=\"" + height +
	    "\" viewBox=\"-20 -20 " + (W+50) + " " + (H+40)+"\"> \n";

	s += "<rect width=\""+ W+ "\" height=\"" + H + "\" " +
	    "style=\"fill:rgb(240,240,240);stroke-width:1;stroke:rgb(0,0,0)\"/>\n"; 
	for(int m: ticPoints(W)) {
	    double x = m*xFactor, y=H+15;
	    s += "<text x=\"" +(x-10) + "\" y=\"" +y + "\" fill=\"black\">" +
		m + "</text>\n";
	    s += "<line x1=\""+x+"\" y1=\""+H+"\" x2=\""+x+"\" y2=\"0\" stroke=\"black\" stroke-dasharray=\"4\"/> \n";
	}


	for(int m: ticPoints((int)maxY)) {
	    double x = 0, y= (int)( (maxY-m)*H/maxY);
	    s += "<text x=\"" +(x-20) + "\" y=\"" +y + "\" fill=\"black\">" +
		m + "</text>\n";
	    s += "<line x1=\"0\" y1=\""+y+"\" x2=\""+W+"\" y2=\""+y+"\" stroke=\"black\" stroke-dasharray=\"4\"/> \n";
	}

	

	for(Curve cu: curves) {
	
	    s += cu.mkSvgPathElement(0,H,xFactor, yFactor, "green",1);
	    s += "\n";
	}

	/*
	if (haveCumulative()) { 
	    s += medianCu.mkSvgPathElement(0,H,yFactor, "darkorange",5);
	}
	s += "\n";
	*/

	/*
	for(int j=0; j<nex; j++) {
	    Expert ex = getWhoGaveEstimates().get(j);
	    double[] c = getCenters().get(j);
	    double cx=W*c[0], cy=H+yFactor*c[1];
	    s += "<circle cx=\""+cx+"\" cy=\""+cy+"\" r=\"5\" stroke=\"black\" stroke-width=\"1\" fill=\"red\" /> \n";
	    s += "<text x=\"" + (cx+7) + "\" y=\"" + cy+ "\" fill=\"black\">" +
		getInitials().get2(ex) + "</text>\n";
	}
	*/
	
	s += "</svg>\n";
	return s;
    }


    /** Compute a round number (with just one non-zero digit) that's
	greater or equal to x
    */
    static int roundUp(int x) {
	int m=1;
	while(m*10 < x)  m*=10;	    
	int j=1;
	while(j<10 && j*m<x) j++;
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
