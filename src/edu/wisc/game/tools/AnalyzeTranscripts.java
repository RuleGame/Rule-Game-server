package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;


import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.*;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.parser.RuleParseException;

/** Methods for the statistical analysis of game transcripts */
public class AnalyzeTranscripts {


    /** Looks up the EpisodeHandle object for a specified episode id.
	
	FIXME: Using a HashMap instead of a vector could, theoretically,
	provide a faster retrieval, but in practice it probably would make
	little difference, since a player does not play all that many episodes,
	and v is fairly short.
     */
   private static EpisodeHandle findEpisodeHandle(Vector<EpisodeHandle> v, String eid) {
	for(EpisodeHandle eh: v) {
	    if (eh.episodeId.equals(eid)) return eh;
	}
	return null;
    }
   
    private static boolean needP0=false;
    private static ReplayedEpisode.RandomPlayer randomPlayerModel=//null;
	ReplayedEpisode.RandomPlayer.COMPLETELY_RANDOM;	
    
    public static void main(String[] argv) throws Exception {

	String outDir = "tmp";

	if (argv.length==0) {
	    int y[]={1, 1, 0, 0, 0, 0, 1, 1};
	    test(y);
	    return;
	} else if (argv.length==2 && argv[0].equals("-y")) {
	    String[] ys = argv[1].split("\\s+");
	    int[] y = new int[ys.length];
	    for(int j=0; j<y.length; j++) y[j] = Integer.parseInt(ys[j]);
	    test(y);
	    return;
	} else {
	    //System.out.println("argv.length=" + argv.length +", argv[0]='" +argv[0] +"'");
	}
	
	EntityManager em = Main.getNewEM();

	// for each rule set name, keep the list of all episodes
	TreeMap<String, Vector<EpisodeHandle>> allHandles= new TreeMap<>();
	// for each player, the list of episodes...
	TreeMap<String,Vector<EpisodeHandle>> ph =new TreeMap<>();

	Vector<String> plans = new Vector<>();
	for(int j=0; j<argv.length; j++) {
	    String exp = argv[j];
	    if (j==0 && exp.equals("-p0random")) {
		needP0=true;
		randomPlayerModel = ReplayedEpisode.RandomPlayer.COMPLETELY_RANDOM;
		continue;
	    } else if  (j==0 && exp.equals("-p0mcp1")) {
		needP0=true;
		randomPlayerModel = ReplayedEpisode.RandomPlayer.MCP1;
		continue;
	    } else if (j+1< argv.length && exp.equals("out")) {
		outDir = argv[++j];
	    }
	    plans.add(exp);
	}

	plans = expandPlans(em, plans);
	
	// for each experiment plan...
	for(String exp: plans) {
		
	    System.out.println("Experiment plan=" +exp);
	    Vector<String> trialListNames = TrialList.listTrialLists(exp);

	    // ... List all trial lists 
	    HashMap<String,TrialList> trialListMap=new HashMap<>();	    
	    for(String trialListId: trialListNames) {
		TrialList t = new  TrialList(exp, trialListId);
		trialListMap.put( trialListId,t);	
	    }
	    System.out.println("Experiment plan=" +exp+" has " + trialListMap.size() +" trial lists: "+ Util.joinNonBlank(", ", trialListMap.keySet()));
	    
	    Vector<EpisodeHandle> handles= new Vector<>();

	    // ... and all players enrolled in the plan
	    Query q = em.createQuery("select m from PlayerInfo m where m.experimentPlan=:e");
	    q.setParameter("e", exp);
	    List<PlayerInfo> res = (List<PlayerInfo>)q.getResultList();
	    for(PlayerInfo p:res) {
		String trialListId = p.getTrialListId();
		TrialList t = trialListMap.get( trialListId);
		if (t==null) {
		    System.out.println("ERROR: for player "+p.getPlayerId()+", no trial list is available for id=" +  trialListId +" any more");
		    continue;
		}
		int orderInSeries = 0;
		int lastSeriesNo =0;
		// ... and all episodes of each player
		for(EpisodeInfo e: p.getAllEpisodes()) {
		    int seriesNo = e.getSeriesNo();
		    if (seriesNo != lastSeriesNo) orderInSeries = 0;
		    EpisodeHandle eh = new EpisodeHandle(exp, trialListId, t, p.getPlayerId(), e, orderInSeries);
		    handles.add(eh);

		    Vector<EpisodeHandle> v = allHandles.get( eh.ruleSetName);
		    if (v==null)allHandles.put(eh.ruleSetName,v=new Vector<>());
		    v.add(eh);

		    Vector<EpisodeHandle> w = ph.get(eh.playerId);
		    if (w==null) ph.put(eh.playerId, w=new Vector<>());
		    w.add(eh);		     


		    
		    orderInSeries++;
		    lastSeriesNo=seriesNo;
		    
		}
  
	    }

	    System.out.println("For experiment plan=" +exp+", found " + handles.size()+" good episodes");//: "+Util.joinNonBlank(" ", handles));

	}	    

	File base = new File(outDir);
	if (!base.exists() || !base.isDirectory() || !base.canWrite())  throw new IOException("Not a writeable directory: " + base);

	File gsum=new File(base, needP0? "summary-p0-"+randomPlayerModel+".csv" : "summary-flat.csv");
	PrintWriter wsum =new PrintWriter(new FileWriter(gsum, false));
	String sumHeader = "#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z,n,L/n,AIC/n";
	wsum.println(sumHeader);
	
	for(String ruleSetName: allHandles.keySet()) {
	    System.out.println("For rule set=" +ruleSetName+", found " + allHandles.get(ruleSetName).size()+" good episodes"); //:"+Util.joinNonBlank(" ",allHandles.get(ruleSetName) ));
	    File d=new File(base, ruleSetName);
	    if (d.exists()) {
		if (!d.isDirectory() || !base.canWrite())  throw new IOException("Not a writeable directory: " + d);
	    } else {
		if (!d.mkdirs()) throw new IOException("Cannot create directory: " + d);
	    }
	}

	
	
	//-- Take a look at each player's transcript and separate
	//-- it into sections pertaining to different rule sets
	
	for(String playerId: ph.keySet()) {
	    Vector<EpisodeHandle> v = ph.get(playerId);
	    AnalyzeTranscripts atr = new AnalyzeTranscripts(playerId, base, wsum);
	    atr.analyzePlayerRecord(v);
	}
	wsum.close();
    }

    /** Expands '%' in plan names. Only includes plans that have any
	non-empty episodes associated with them. */
    private static Vector<String> expandPlans(EntityManager em, Vector<String> v0) throws Exception  {
	Vector<String> v = new 	Vector<>();
	Query q = em.createQuery("select distinct e.player.experimentPlan from  EpisodeInfo e where e.player.experimentPlan like :p and e.attemptCnt>0");


	for(String p: v0) {
	    if (p.indexOf('%')>=0) {
		q.setParameter("p", p);
		v.addAll((List<String>)q.getResultList());		
	    } else {
		v.add(p);
	    }
	}
	    
	return v;
    }

    
    /**
    	@param base The main output directory
    */
    private AnalyzeTranscripts(String _playerId, File _base, PrintWriter _wsum) {
	base = _base;
	wsum = _wsum;
	playerId = _playerId;
    }

    final private File base;
    final private PrintWriter wsum;
    final String playerId;

    /** Saves the data for a single (player, ruleSet) pair
	@param section A vector of arrays, each array representing the recorded
	moves for one episode.
	@param includedEpisodes All non-empty episodes played by this player in this rule set
    */
    private void saveAnyData(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
			     Vector<EpisodeHandle> includedEpisodes)
	throws  IOException, IllegalInputException,  RuleParseException {

	if (includedEpisodes.size()==0) return;
	EpisodeHandle eh0 =  includedEpisodes.firstElement();

	double[] p0 = computeP0(section, eh0.para, eh0.ruleSetName);

	String rid = eh0.ruleSetName;
	File d=new File(base, rid);
	File g=new File(d, includedEpisodes.firstElement().playerId + ".split-transcripts.csv");		
	PrintWriter w =  new PrintWriter(new FileWriter(g, false));
	final String outHeader="#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,orderInSeries,episodeId," + "moveNo,timestamp,y,x,by,bx,code,p0";
	w.println(outHeader);

	int je =0, jp=0;
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: section) {
	    EpisodeHandle eh = includedEpisodes.get(je ++);
	    for(TranscriptManager.ReadTranscriptData.Entry e: subsection) {
		if (!eh.episodeId.equals(e.eid)) throw new IllegalArgumentException("Array mismatch");
		
		w.print(rid+","+e.pid+","+eh.exp+","+eh.trialListId+","+eh.seriesNo+","+eh.orderInSeries+","+e.eid);
		for(int j=2; j<e.csv.nCol(); j++) {
		    w.print(","+ImportCSV.escape(e.csv.getCol(j)));
		}
		w.print(","+ p0[jp++]);
		w.println();	
	    }
	}

	w.close(); w=null;

	OptimumExplained oe = analyzeSection( joinSubsections( section), eh0, wsum, needP0? p0: null);
	section.clear();
	includedEpisodes.clear();
    }

    /** Splits a section of transcript pertaining to a single rule set (i.e. a series of episodes) into subsections, each subsection pertaining to one specific
	episode.
     */
    private static Vector<TranscriptManager.ReadTranscriptData.Entry[]> splitTranscriptIntoEpisodes(Vector<TranscriptManager.ReadTranscriptData.Entry> section) {
	Vector<TranscriptManager.ReadTranscriptData.Entry[]> result = new Vector<>();
	Vector<TranscriptManager.ReadTranscriptData.Entry> q = new Vector<>();
	String lastEid = "";
	for( TranscriptManager.ReadTranscriptData.Entry e: section) {
	    String eid = e.eid;
	    if (!eid.equals(lastEid)) {
		if (q.size()>0) {
		    result.add( q.toArray(new TranscriptManager.ReadTranscriptData.Entry[0]));
		}
		q.clear();
		lastEid = eid;
	    }
	    q.add(e);
	}
	
	if (q.size()>0) {
	    result.add( q.toArray(new TranscriptManager.ReadTranscriptData.Entry[0]));
	}
	return result;			
    }

    private static <T> Vector<T> joinSubsections(Vector<T[]> w) {
	Vector<T> v = new Vector<>();
	for(T[] a: w) {
	    for(T t: a) {
		v.add(t);
	    }
	}
	return v;
    }
    
    private static <T> int sumLen(Vector<T[]> w) {
	int len=0;
	for(T[] a: w) {
	    len+= a.length;
	}
	return len;
    }

    /** Removes any duplicate entries from each subsection. Such
	entries may have been created due to imperfections in the
	transcript-saving process.
    */
    void removeDuplicates(Vector<TranscriptManager.ReadTranscriptData.Entry[]>  subsections) {
	for(int j=0; j<subsections.size(); j++) {
	    TranscriptManager.ReadTranscriptData.Entry[] in = subsections.get(j);
	    Vector<TranscriptManager.ReadTranscriptData.Entry> out = new Vector<>();
	    for(int k=0; k<in.length; k++) {
		boolean isDup = false;
		for(TranscriptManager.ReadTranscriptData.Entry q: out) {
		    if (q.equals(in[k])) {
			isDup=true;
			break;
		    }
		}
		if (!isDup) out.add(in[k]);
	    }
	    if (out.size()<in.length) {
		subsections.set(j, out.toArray(new TranscriptManager.ReadTranscriptData.Entry[0]));
	    }
	}
    }

    
    /** Reconstructs and replays the historical episode, computing p0 for
	every pick or move attempt.
     */
    private double[] computeP0(Vector<TranscriptManager.ReadTranscriptData.Entry[]> subsections, ParaSet para, String ruleSetName)  throws  IOException, IllegalInputException,  RuleParseException{
	RuleSet rules = AllRuleSets.obtain( ruleSetName);

	double [] p0 = new double[sumLen(subsections)];
	int k=0;
	
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: subsections) {
	    String episodeId = subsection[0].eid;
	    
	    Board board = boards.get(episodeId);
	    Game game = new Game(rules, board);
	    ReplayedEpisode rep = new ReplayedEpisode(episodeId, para, game, randomPlayerModel);

	    System.out.println("------------- eid=" + episodeId);

	    System.out.println("All moves:");
	    for(int j=0; j<subsection.length; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		System.out.println(e.pick.toString());
	    }
	
	    for(int j=0; j<subsection.length; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];

		System.out.println("j=" + j);
		System.out.println(rep.graphicDisplay());
		
		double p =rep.computeP0(e.pick);	    
		p0[k++] = p;

		int code = rep.accept(e.pick);
		
		System.out.println(e.pick.toString() +", p0=" + p+", code=" + code);

		if (code!=e.code) {
		    throw new IllegalArgumentException("Unexpected code in replay: " + code +", vs. the recorded code=" + e.code);
		}
	    }
	}
	return p0;
    }

    
    HashMap<String,Board> boards;
    
    /** Reads one player's transcript, and prepares a complete report 
	for that player.
	@param playerId The player whose record we want to analyze
	@param v The list of episodes played by this player
    */
    private  void    analyzePlayerRecord(Vector<EpisodeHandle> v) throws  IOException, IllegalInputException,  RuleParseException{


	File boardsFile =  Files.boardsFile(playerId, true);

	HashMap <String,Boolean> useImages = new HashMap<>();
	for(EpisodeHandle eh: v) {
	    useImages.put(eh.episodeId, eh.useImages);
	}
	
	boards = BoardManager.readBoardFile(boardsFile, useImages);

	
	File inFile = Files.transcriptsFile(playerId, true);
	TranscriptManager.ReadTranscriptData transcript = new TranscriptManager.ReadTranscriptData(inFile);

	// split by episode 
	Vector<TranscriptManager.ReadTranscriptData.Entry[]> subsections = splitTranscriptIntoEpisodes(transcript);
	// remove any duplicates that may exist due to imperfections in the transcript saving mechanism
	removeDuplicates(subsections);

	
	
	// One subsection per episode
	System.out.println("Split the player's transcript ("+transcript.size()+" moves) into "+subsections.size()+ " episode sections");
	    
	String lastRid="";
	// all episodes' subsections for a given rule sets
	Vector<TranscriptManager.ReadTranscriptData.Entry[]> section=new Vector<>();
	Vector<EpisodeHandle> includedEpisodes=new Vector<>();
	
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: subsections)  {
	    String eid = subsection[0].eid;
	    EpisodeHandle eh = findEpisodeHandle(v, eid);
	    if (eh==null) {
		throw new IllegalArgumentException("In file "+inFile+", found unexpected experimentId="+ eid);
	    }
	    
	    String rid=eh.ruleSetName;
	    if (!lastRid.equals(rid)) {
		saveAnyData( section, includedEpisodes);
		lastRid=rid;
	    }
	    includedEpisodes.add(eh);
	    section.add(subsection);
	}
	saveAnyData( section, includedEpisodes);
    }
       

    static void test(int[] y) {
	double tt[]=new double[y.length*2];
	for(int k=0; k<tt.length; k++) tt[k] = k*0.5;
	analyzeSection("test", y, null, tt);
	
    }
    
    final static DecimalFormat df = new DecimalFormat("0.000");

    /** Processes the sequence of all moves for a (player, rule set) pair
       @param eh A handle for one of the episodes in this  (player, rule set) series. It is only used to access the information common for the entire series, not for the specific episode.
     */
    private static OptimumExplained analyzeSection(Vector<TranscriptManager.ReadTranscriptData.Entry> section,
						   EpisodeHandle eh,   PrintWriter wsum, double[] p0
						   ) {	
	int[] y = TranscriptManager.ReadTranscriptData.asVectorY(section);
	if (y.length<2) return null;
	
	OptimumExplained oe =  analyzeSection(eh.playerId, y, p0, null);

	if (oe!=null) {
	    String rid=eh.ruleSetName;
	    //	 sumHeader = "#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z";
	    wsum.print(rid+","+eh.playerId+","+eh.exp+","+eh.trialListId+","+eh.seriesNo+",");
	    wsum.println(mkYString(y)+"," + oe.toCsvString() );
	}


	
	return oe;
    }


    /** An auxiliary class used to pinpoint possible inflection points */
    static class Divider {
	final double avg0, avg1, L;

	private static double partL(int n, int sum) {
	    double avg = (double)sum/(double)n;
	    return (sum>0? sum * Math.log(avg) : 0) +
		(n-sum>0?  (n-sum) * Math.log(1-avg) : 0);
	}
	
	Divider(int n0, int n1, int sum0, int sum1) {
	    avg0 = sum0/(double)n0;
	    avg1 = sum1/(double)n1;
	    L = partL(n0,sum0) + partL(n1,sum1);
	}
    
	    
	static Divider goodDivider( int[] y, double t0) {
	    if ((double)(int)t0 == t0) return null;
	    if (t0<=0 || t0>= y.length-1) return null;
	    int n0 = (int)t0 + 1;
	    int n1 = y.length - n0;
	    int sum0=0, sum1=0;
	    for(int t=0; t<y.length; t++) {
		if (y[t]>0) {
		    if (t<t0) sum0++; else sum1++;
		}				
	    }
	    Divider d = new Divider(n0,n1,sum0,sum1);
	    if (d.avg0<d.avg1 && y[n0-1]<y[n0] ||
		d.avg0>d.avg1 && y[n0-1]>y[n0]) return d;
	    else return null;
	}
    }

    private static String mkYString(int[] y) {
	Vector<String> v=new Vector<>();
	for(int q: y) v.add("" + q);
	return Util.joinNonBlank(" ", v);
    }

    private static double mkAvg(int[] y) {
	int sum=0;
	for(int q: y) sum+=q;
	return sum/(double)y.length;
    }

    /** 
	@param tt Suggested inflection points. If null, this method will decide 
	on its own.
     */
    private static OptimumExplained analyzeSection(String playerId, int[] y, double p0[], double tt[]) {	

	LoglikProblem.verbose = false;
	
	System.out.println("Player="+playerId+", optimizing for y=[" +
			 mkYString(y)+			"]" );


	int n =y.length;
	
	if (p0!=null) {
	    if (y.length!=p0.length) throw new IllegalArgumentException("y, p0 length mismatch");
	    // Remove the points with p0(t)=1 and y(t)=0, where no fitting is possible
	    Vector<Integer> yz=new Vector<>();
	    Vector<Double> p0z=new Vector<>();
	    for(int j=0; j<y.length; j++) {
		if (p0[j]<0 || p0[j]>1) throw new IllegalArgumentException("Invalid p0");
		if (p0[j]==1 && y[j]==0) continue;
		yz.add(y[j]);
		p0z.add(p0[j]);
	    }
	    int n0=n;
	    n=yz.size();
	    if (n<n0) {
		y = new int[n];
		p0 = new double[n];
		for(int j=0; j<n; j++) {
		    y[j] = yz.get(j);
		    p0[j] = p0z.get(j);
		}
		System.out.println("Reduced n from "+n0+" to " + n+", due to the removal of 'impossible' points with p0==1");
	    }
	}

	if (n==0) return null; // nothing to optimize


	
	if (tt==null) {
	    //tt=new double[3];
	    //for(int mode=0; mode<=2; mode++) {
	    //		tt[mode]= mode * (y.length-1.0)*0.5;
	    //}
	    tt=new double[n*2-1];
	    for(int k=0; k<tt.length; k++) tt[k] = k*0.5;
	}


	double avgY = mkAvg(y);
	LoglikProblem problem =
	    p0==null? new LoglikProblem(y):
	    new LoglikP0Problem(y, p0);

	ObjectiveFunctionGradient ofg = problem.getObjectiveFunctionGradient();

	final int maxEval = 10000;
  
	PointValuePair bestOptimum=null;
	
	for(int mode=0; mode<tt.length; mode++) {

	double t0 = tt[mode];

	
	
	Divider d= Divider.goodDivider(y, t0);
	if (d!=null) {
	    System.out.println("***L(" + df.format(d.avg0)+","+df.format(d.avg1)+")/n=" + df.format(d.L/n) + "*** ");
	}


	int nAttempts = (d==null)? 2: 3;

	for(int jAttempt = 0; jAttempt<nAttempts; jAttempt++) {
	
	    // B,C,t_I, k
	   
	    double[] startPoint =
		jAttempt==0?	new double[]{avgY, avgY, t0, 0}:
		jAttempt==1?	new double[]{0, 1, t0, 0}:
		new double[]{d.avg0, d.avg1, t0, 1};
	    

	    NonLinearConjugateGradientOptimizer optimizer
		= new NonLinearConjugateGradientOptimizer(NonLinearConjugateGradientOptimizer.Formula.//FLETCHER_REEVES,
							  POLAK_RIBIERE,
							  new SimpleValueChecker(1e-7, 1e-7),
							  1e-5, 1e-5, 1);
 

	    PointValuePair optimum;
	    try {
		optimum =
		    optimizer.optimize(new MaxEval(maxEval),
				       problem.getObjectiveFunction(),
				       ofg,
				       GoalType.MAXIMIZE,
				       new InitialGuess(startPoint));
	    } catch(  org.apache.commons.math3.exception.TooManyEvaluationsException ex) {
		System.out.println(ex);
		continue;
	    }
	    OptimumExplained oe = new OptimumExplained(ofg, optimum, n);
	    oe.report("[t0="+t0+", iter=" + optimizer.getIterations()+"] ");

	    if (bestOptimum==null || optimum.getValue()>bestOptimum.getValue()) {
		bestOptimum =optimum;
	    }

	
	}
	}	      

	if (bestOptimum !=null) {
	    OptimumExplained oe = new OptimumExplained(ofg, bestOptimum, n);
	    oe.report("[GLOBAL?] ");
	    return oe;
	}  else {
	    // No solution found, e.g. because the input was empty
	    return null;
	}
    }


    /** An auxiliary structure to describe an optimum */
    static class OptimumExplained {

	final PointValuePair optimum;
	final double p[];
	final double grad[];
	final double B, C, t_I, k;
	final double e0, re0, Z, Ln, AICn;
	final int n;
	
	OptimumExplained(ObjectiveFunctionGradient ofg, PointValuePair _optimum, int _n) {
	    n = _n;
	    optimum = _optimum;
	    p =optimum.getPoint();
	    grad = ofg.getObjectiveFunctionGradient().value(p);
	    t_I=p[2];
	    double k0=p[3];
	    if (k0<0) {
		k= -k0;
		B= p[1];
		C= p[0];
	    } else {
		k = k0;
		B = p[0];
		C = p[1];
	    }
	    e0 = Math.exp(k*t_I);
	    re0 = Math.exp(-k*t_I);
	    Z = B/(1+re0)+C/(1+e0);
	    Ln = optimum.getValue()/n;
	    final int K = 4;
	    AICn = (n<=K+1)? 0:  -2*Ln + 2*K/(double)(n-K-1);
	}

	String toReadableString() {
	    return "B="+   df.format(B)+
		", C="+    df.format(C)+
		", t_I="+   df.format( t_I) +
		", k="+   df.format( k)+
		". Z="+    df.format(Z)+
		". n="+     df.format(n)+
		". L/n="+     df.format(Ln)+
		". AIC/n="+     df.format(AICn);
	}


	String toCsvString() {
	    Vector<Number> v=new Vector<>();
	    v.add(B);
	    v.add(C);
	    v.add(t_I);
	    v.add(k);
	    v.add(Z);
	    v.add(n );
	    v.add(Ln);
	    v.add(AICn);
	    return Util.joinNonBlank(",",v);
	}

	void report(String prefix) {
	    if (prefix!=null) System.out.print(prefix);

	    System.out.println(toReadableString());
	    System.out.println("grad L=["+  Util.joinNonBlank(", ", df, grad)+"]");
	
	}
    
	
    }

  
}
