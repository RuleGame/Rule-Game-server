package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

//import org.apache.commons.math3.optimization.*;
//import org.apache.commons.math3.optimization.general.*;
//import org.apache.commons.math3.analysis.*;


import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.*;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;

/** Methods for the statistical analysis of game transcripts */
public class AnalyzeTranscripts {

    /** An auxiliary structure used to keep track who and when played 
	episodes
     */
    static class EpisodeHandle {
	String ruleSetName;
	String exp;
	String trialListId;
	int seriesNo;
	int orderInSeries;
	String episodeId;
	String playerId;
	public String toString() {return episodeId;}
	EpisodeHandle(String _exp, String _trialListId, TrialList t, String _playerId, EpisodeInfo e, int _orderInSeries) {
	    episodeId = e.getEpisodeId();

	    playerId = _playerId;
	    exp = _exp;
	    trialListId = _trialListId;
	    seriesNo= e.getSeriesNo();
	    orderInSeries =  _orderInSeries;
	    ParaSet para = t.get(seriesNo);
	    ruleSetName = para.getRuleSetName();	    
	}
    }


    static EpisodeHandle findEpisodeHandle(Vector<EpisodeHandle> v, String eid) {
	for(EpisodeHandle eh: v) {
	    if (eh.episodeId.equals(eid)) return eh;
	}
	return null;
    }

    /*
    private static void writeFile(int eid, Vector<TranscriptManager.ReadTranscriptData.Entry> section) {
	    PrintWriter w=null;
	   
	    
	    for(TranscriptManager.ReadTranscriptData.Entry e: section) {
		EpisodeHandle eh = findEpisodeHandle(v, e.eid);
		if (eh==null) {
		    throw new IllegalArgumentException("In file "+inFile+", found unexpected experimentId="+e.eid);
		}
		    
		String rid=eh.ruleSetName;
		if (!lastRid.equals(rid)) {
		    if (w!=null) { w.close(); w=null;}
		    File d=new File(base, rid);
		    File g=new File(d, e.pid + ".split-transcripts.csv");

		    w =  new PrintWriter(new FileWriter(g, false));
		    w.println(outHeader);
		    lastRid=rid;
		}
		w.print(rid+","+e.pid+","+eh.exp+","+eh.trialListId+","+eh.seriesNo+","+eh.orderInSeries+","+e.eid);
		for(int j=2; j<e.csv.nCol(); j++) {
		    w.print(","+ImportCSV.escape(e.csv.getCol(j)));
		}
		w.println();
	    }
	    if (w!=null) { w.close(); w=null;}



	    }
	    //---- */
    
    public static void main(String[] argv) throws Exception {


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
	    System.out.println("argv.length=" + argv.length +", argv[0]='" +argv[0] +"'");
	}
	
	EntityManager em = Main.getNewEM();

	// for each rule set name, keep the list of all episodes
	TreeMap<String, Vector<EpisodeHandle>> allHandles= new TreeMap<>();
	// for each player, the list of episodes...
	TreeMap<String,Vector<EpisodeHandle>> ph =new TreeMap<>();

	// for h experiment plan
	for(int j=0; j<argv.length; j++) {
	    String exp = argv[j];
	    System.out.println("Experiment plan=" +exp);
	    Vector<String> trialListNames = TrialList.listTrialLists(exp);

	    HashMap<String,TrialList> trialListMap=new HashMap<>();

	    
	    for(String trialListId: trialListNames) {
		TrialList t = new  TrialList(exp, trialListId);
		trialListMap.put( trialListId,t);	
	    }
	    System.out.println("Experiment plan=" +exp+" has " + trialListMap.size() +" trial lists: "+ Util.joinNonBlank(", ", trialListMap.keySet()));
	    
	    Vector<EpisodeHandle> handles= new Vector<>();
	    
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

	File base = new File("tmp");
	if (!base.exists() || !base.isDirectory() || !base.canWrite())  throw new IOException("Not a writeable directory: " + base);

	File gsum=new File(base, "summary.csv");
	PrintWriter wsum =new PrintWriter(new FileWriter(gsum, false));

	String sumHeader = "#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z,L/n";
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
	    
	    File inFile = Files.transcriptsFile(playerId, true);
	    TranscriptManager.ReadTranscriptData transcript = new TranscriptManager.ReadTranscriptData(inFile);

	    //(rid+","+e.pid+","+eh.exp+","+eh.trialListId+","+eh.seriesNo+","+eh.orderInSeries+","+e.eid);
	
	    
	    final String outHeader="#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,orderInSeries,episodeId," + "moveNo,timestamp,y,x,by,bx,code";
	    String lastRid="";	
	    PrintWriter w=null;
	    Vector<TranscriptManager.ReadTranscriptData.Entry> section=new Vector<>();
	    EpisodeHandle  lastEh=null;
	    for(TranscriptManager.ReadTranscriptData.Entry e: transcript) {
		EpisodeHandle eh = findEpisodeHandle(v, e.eid);
		if (eh==null) {
		    throw new IllegalArgumentException("In file "+inFile+", found unexpected experimentId="+e.eid);
		}
		    
		String rid=eh.ruleSetName;
		if (!lastRid.equals(rid)) {
		    if (w!=null) {
			w.close(); w=null;
			OptimumExplained oe = analyzeSection( section, lastEh, wsum);			
			section.clear();
		    }
		    File d=new File(base, rid);
		    File g=new File(d, e.pid + ".split-transcripts.csv");

		    w =  new PrintWriter(new FileWriter(g, false));
		    w.println(outHeader);
		    lastRid=rid;
		}
		w.print(rid+","+e.pid+","+eh.exp+","+eh.trialListId+","+eh.seriesNo+","+eh.orderInSeries+","+e.eid);
		for(int j=2; j<e.csv.nCol(); j++) {
		    w.print(","+ImportCSV.escape(e.csv.getCol(j)));
		}
		w.println();
		section.add(e);
		lastEh = eh;
	    }
	    if (w!=null) {
		w.close(); w=null;
		OptimumExplained oe = analyzeSection( section, lastEh, wsum);
		//section.setSize(0);
		section.clear();
	    }

	    
	}
	
	wsum.close();





	

	/*
	for(String ruleSetName: allHandles.keySet()) {
	    Vector<EpisodeHandle> v = allHandles.get(ruleSetName);
	    
	    // for each player, the list of episodes involving this
	    // rule set
	    TreeMap<String,Vector<EpisodeHandle>> ph =new TreeMap<>();
	    for(EpisodeHandle eh: v) {
		Vector<EpisodeHandle> w = ph.get(eh.playerId);
		if (w==null) ph.put(eh.playerId, w=new Vector<>());
		w.add(eh);		     
	    }

	    File f=new File(base, ruleSetName + ".csv");
	    File d=f.getParentFile();
	    if (d.exists()) {
		if (!d.isDirectory() || !base.canWrite())  throw new IOException("Not a writeable directory: " + d);
	    } else {
		if (!d.mkdirs()) throw new IOException("Cannot create directory: " + d);
	    }
	    System.out.println("For rule set=" + ruleSetName+", will write data to file "+ f +". This will include episodes from " + ph.size() +" players");
	    
	    PrintWriter w = new PrintWriter(new FileWriter(f, false));
	    for(String playerId: ph.keySet()) {
		
	    }	    
	    w.close();		
	}
	*/
    }


    static void test(int[] y) {
	double tt[]=new double[y.length*2];
	for(int k=0; k<tt.length; k++) tt[k] = k*0.5;
	analyzeSection("test", y,tt);
	
    }
    
    final static DecimalFormat df = new DecimalFormat("0.000");
    
    private static OptimumExplained analyzeSection(Vector<TranscriptManager.ReadTranscriptData.Entry> section,
						   EpisodeHandle eh,   PrintWriter wsum
						   ) {	
	int[] y = TranscriptManager.ReadTranscriptData.asVectorY(section);
	if (y.length<2) return null;
	
	OptimumExplained oe =  analyzeSection(eh.playerId, y, null);

	if (oe!=null) {
	    String rid=eh.ruleSetName;
	    //	 sumHeader = "#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z";
	    wsum.print(rid+","+eh.playerId+","+eh.exp+","+eh.trialListId+","+eh.seriesNo+",");
	    wsum.println(mkYString(y)+"," + oe.toCsvString() );
	}


	
	return oe;
    }


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
    
    private static OptimumExplained analyzeSection(String playerId, int[] y, double tt[]) {	

	LoglikProblem.verbose = false;
	
	System.out.println("Player="+playerId+", optimizing for y=[" +
			 mkYString(y)+			"]" );


	if (tt==null) {
	    //tt=new double[3];
	    //for(int mode=0; mode<=2; mode++) {
	    //		tt[mode]= mode * (y.length-1.0)*0.5;
	    //}
	    tt=new double[y.length*2-1];
	    for(int k=0; k<tt.length; k++) tt[k] = k*0.5;
	}


	double avgY = mkAvg(y);
	LoglikProblem problem = new LoglikProblem(y);
	//SimpleProblem problem = new SimpleProblem(y);
	ObjectiveFunctionGradient ofg = problem.getObjectiveFunctionGradient();

	final int maxEval = 10000;
  
	PointValuePair bestOptimum=null;
	
	for(int mode=0; mode<tt.length; mode++) {

	double t0 = tt[mode];

	
	
	Divider d= Divider.goodDivider(y, t0);
	if (d!=null) {
	    System.out.println("***L(" + df.format(d.avg0)+","+df.format(d.avg1)+")/n=" + df.format(d.L/y.length) + "*** ");
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
	    OptimumExplained oe = new OptimumExplained(ofg, optimum, y.length);
	    oe.report("[t0="+t0+", iter=" + optimizer.getIterations()+"] ");

	    if (bestOptimum==null || optimum.getValue()>bestOptimum.getValue()) {
		bestOptimum =optimum;
	    }

	
	}
	}	      

	if (bestOptimum !=null) {
	    OptimumExplained oe = new OptimumExplained(ofg, bestOptimum, y.length);
	    oe.report("[GLOBAL?] ");
	    return oe;
	}  else {
	    return null;
	}
    }


    /** An auxiliary structure to describe an optimum */
    static class OptimumExplained {

	final PointValuePair optimum;
	final double p[];
	final double grad[];
	final double B, C, t_I, k;
	final double e0, re0, Z, Ln;
	
	OptimumExplained(ObjectiveFunctionGradient ofg, PointValuePair _optimum, int n) {
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
	}

	String toReadableString() {
	    return "B="+   df.format(B)+
		", C="+    df.format(C)+
		", t_I="+   df.format( t_I) +
		", k="+   df.format( k)+
		". Z="+    df.format(Z)+
		". L/n="+     df.format(Ln);
	}


	String toCsvString() {
	    Vector<Double> v=new Vector<>();
	    v.add(B);
	    v.add(C);
	    v.add(t_I);
	    v.add(k);
	    v.add(Z);
	    v.add(Ln);
	    return Util.joinNonBlank(",",v);
	}

	void report(String prefix) {
	    if (prefix!=null) System.out.print(prefix);

	    System.out.println(toReadableString());
	    System.out.println("grad L=["+  Util.joinNonBlank(", ", df, grad)+"]");
	
	}
    
	
    }

  
}
