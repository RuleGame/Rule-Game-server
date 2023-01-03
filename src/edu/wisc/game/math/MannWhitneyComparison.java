package edu.wisc.game.math;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.tools.MwByHuman;
import edu.wisc.game.tools.MwByHuman.MwSeries;

/** Comparing players or rules based on the Mann-Whitney test
 */
public class MannWhitneyComparison {

    public enum Mode { CMP_RULES, CMP_RULES_HUMAN, CMP_ALGOS};

    final Mode mode;

    private static Fmter plainFm = new Fmter();

    public MannWhitneyComparison(Mode _mode) {
	mode = _mode;
    }
    
    private Query mkQuery(EntityManager em, String nickname,  String rule) {
	Query q;
	if (mode==Mode.CMP_ALGOS) {
	    q = em.createQuery("select m from MlcEntry m where m.ruleSetName=:x");
	    q.setParameter("x", rule);
	} else if (mode==Mode.CMP_RULES)  {
	    q = em.createQuery("select m from MlcEntry m where m.nickname=:x");
	    q.setParameter("x", nickname);

	} else throw new IllegalArgumentException("Wrong mode for querying MlcEntry: " + mode);
	return q;
    }

    /** From an MlcEntry, get the comparison key */
    private String getKey(MlcEntry e) {	
	return (mode==Mode.CMP_ALGOS) ?
	    e.getNickname():
	    e.getRuleSetName();
    }

    /** Creates a list of comparanda based on MLC data, either to compare
	ML algos or to compare rule sets.
       @return {learnedOnes[], nonLearnedOnes[]}
     */
    public Comparandum[][] mkMlcComparanda(String nickname,  String rule) {

	
	EntityManager em=null;

	try {
	em = Main.getNewEM();

	Query q = mkQuery(em, nickname, rule);
	List<MlcEntry> res = (List<MlcEntry>)q.getResultList();

	// distinct keys
	Vector<String> keys = new Vector<>();
	// maps each key to its position in the "keys" array
	HashMap<String,Integer> keysOrder = new HashMap<>();
	// how many runs have been done for each key
	Vector<Integer> counts = new Vector<>();
	    
	// How many distinct keys (algo nicknames or rule set names)
	int n = 0;
	for(MlcEntry e: res) {
	    String key  = getKey(e);
	    boolean isNew = (keysOrder.get(key)==null);
	    
	    int j = isNew? n++ :  keysOrder.get(key);
	    if (isNew) {
		keysOrder.put( key, j);
		keys.add(key);
		counts.add(1);
	    } else {		
		int m = counts.get(j);
		counts.set(j,m+1);
	    }
	}

	// All entries (runs) separated by key 
	MlcEntry [][]w = new MlcEntry[n][];
	for(int j=0; j<n; j++) w[j] = new MlcEntry[ counts.get(j) ];
	int p[] = new int[n];
	for(MlcEntry e: res) {
	    int j = keysOrder.get( getKey(e));
	    MlcEntry [] row = w[j];
	    int k = p[j]++;
	    row[k] = e;	    
	}

	Vector<Comparandum> learnedOnes=new Vector<>(), unlearnedOnes=new Vector<>();
	    
	for(int j=0; j<n; j++) {
	    boolean failed = false;
	    for(int k=0; k<w[j].length; k++) {
		failed = failed || !w[j][k].getLearned();
	    }

	    String key = getKey(w[j][0]);
	    (failed? unlearnedOnes: learnedOnes).add(new Comparandum(key, !failed, w[j]));
	}

	Comparandum [] dummy = new Comparandum[0];
	Comparandum [][] allComp = {learnedOnes.toArray(dummy), unlearnedOnes.toArray(dummy)};
	return allComp;
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}	
    }

    
    /** Carries out comparison of the performance for different "keys"
	(algo nicknames or rule sets). In the CMP_ALGOS mode, a particular
	rule set is chosen, and ML algorithms are ranked by their performance
	on that rule set; thus the "key" is the algo nickname. In the CMP_RULES
	mode, a particular algorithm is chosen (the nickname) is specified,
	and the rule sets are ranked by their ease for this algorithm; thus
	the rule sets names are keys.
     */
    public String doCompare( String nickname,  String rule,
			     Comparandum[][] allComp,
			     Fmter fm) {

	final String myKey = (mode==Mode.CMP_ALGOS)? nickname:
	    (mode==Mode.CMP_RULES)? rule: "";
	final String pivot = (mode==Mode.CMP_ALGOS)? rule:
	    (mode==Mode.CMP_RULES)?    nickname: "";
	String titlePrefix = (mode==Mode.CMP_ALGOS)?
	    "Results comparison on rule set ":
	    (mode==Mode.CMP_RULES)?
	    "Comparing rule sets with respect to algo ":
	    "Comparing rule sets with respect to human performance";
			
	String h1= titlePrefix + fm.tt(pivot), title=titlePrefix + pivot;
	String body="", errmsg = null;

	try {
	    Comparandum[] learnedOnes = allComp[0];
	    Comparandum[] unlearnedOnes = allComp[1];
	    
	    body += fm.h1(h1);
	    
	    double[][] z = MannWhitney.rawMatrix(Comparandum.asArray(learnedOnes));
	    double[][] zr = MannWhitney.ratioMatrix(z);
	    
	    double[] ev = MannWhitney.topEigenVector(zr);

	    for(int j=0; j<ev.length; j++) learnedOnes[j].setEv(ev[j]);
	    
	    Vector<Integer> order = new Vector<>();
	    for(int j=0; j<learnedOnes.length; j++) order.add(j);
	    order.sort((o1,o2)-> (int)Math.signum(ev[o2]-ev[o1]));

	    Vector<String> v = new Vector<>(), vv = new Vector<>();

	    for(int h=0; h< order.size(); h++) {
		int k = order.get(h);
		Comparandum q = learnedOnes[k];
		String key = q.key;
		boolean isMe = key.equals(myKey);
		String s =key + "\t";

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		pw.print(key);


		double[] c = new double[order.size()];		
		double[] cc = new double[order.size()];		
		for(int i=0; i< c.length; i++) {
		    c[i] = z[k][order.get(i)];
		    cc[i] = zr[k][order.get(i)];
		    pw.format("\t%8.4f", cc[i]);
		}
		
		s += Util.joinNonBlank("\t", c);
		if (isMe) s = fm.strong(s);
		v.add(s);


		s = sw.toString();
		if (isMe) s = fm.strong(s);
		vv.add(s);

	    }
	    body += fm.h3("Raw M-W matrix");	    	    
	    body += fm.pre(String.join("\n", v));
	    body += fm.h3("M-W ratio matrix");
	    body += fm.pre(String.join("\n", vv));

	    //Arrays.sort(learnedOnes);

	    String h3 =	(mode==Mode.CMP_ALGOS)? "Comparison of algorithms":
		"Comparison of rule sets";
	    body += fm.h3(h3);

	    String keyCell =	(mode==Mode.CMP_ALGOS)? "Algo nickname":
		"Rule set name";
	    
	    Vector<String> rows = new Vector<>();

	    String[] headers =
		(mode==Mode.CMP_ALGOS || mode==Mode.CMP_RULES)?
		new String[] {keyCell,
			      "Learned? (learned/not learned)",
			      "EV score",
			      "Runs",
			      "Avg. episodes till learned",
			      "Avg. errors till learned",
			      "Avg. moves till learned",
			      "Avg. error rate"}:
		new String[] {keyCell,
		"Learned/not learned",
		"EV score",
		"Avg. m* (errors till learned)",
	    };
	    

	    String row ="";
	    for(String s: headers) {
		row += fm.th(s);
	    }

	    
	    rows.add( row);

	    // the learned ones
	    for(int k=0; k< order.size(); k++) {
		int j=order.get(k);

		Comparandum q = learnedOnes[j];
		String key = q.key;
		
		double evScore = ev[j];

		boolean isMe = key.equals(myKey);
		String w2[]={};

		if (q.mlc!=null) {
		    MlcEntry [] ee = q.mlc;
		    int runs = ee.length;
		    double avgE=0, avgM=0, avgEp=0;
		    for(MlcEntry e: ee) {
			avgE += e.getTotalErrors();
			avgEp += e.getEpisodesUntilLearned();
			avgM += e.getMovesUntilLearned();
		    }
		    avgE /= runs;
		    avgEp /= runs;
		    avgM /= runs;

		    w2 = new String[]{
		    "Learned ("+runs+"/0)",
		    fm.sprintf("%6.4g", evScore),
		    "" + runs,
		    fm.sprintf("%5.2f",avgEp),
		    fm.sprintf("%6.2f",avgE),
		    fm.sprintf("%6.2f",avgM),
		    ""};
		} else if (q.humanSer!=null) { // human
		    //"Learned/not learned",
		    //"EV score",
		    //"m* (errors till learned)",
		    int learnedCnt=0, sumMStar=0;
		    for(MwSeries ser: q.humanSer) {
			if (ser.getLearned())  learnedCnt++;
			sumMStar += ser.getMStar();
		    }
		    double avgMStar = sumMStar/(double)q.humanSer.length;
		    w2 = new String[]{
			""+learnedCnt+"/" + (q.humanSer.length-learnedCnt),
			fm.sprintf("%6.4g", evScore),
			fm.sprintf("%6.2f",avgMStar)
		    };
		} else throw new IllegalArgumentException();
		
		row = fm.th(key);
		for(String s: w2) {
		    if (isMe) s = fm.strong(s);
		    row += fm.td(s);
		}

		rows.add(fm.tr(row));
	    }

	    // Algos who have never learned this rule
	    order.clear();
	    Vector<String> rows2 = new Vector<>();
	    Vector<Double> avgErrorRates   = new Vector<>();
	    for(int j=0; j< unlearnedOnes.length; j++) {
		Comparandum q = unlearnedOnes[j];
		String key = q.key;
		boolean isMe = key.equals(myKey);
		MlcEntry [] ee = q.mlc;
		int runs = ee.length;
	
		double totalM=0, totalE=0;
		int learnedRuns =0;
		for(MlcEntry e: ee) {
		    totalE += e.getTotalErrors();
		    totalM += e.getTotalMoves();
		    if (e.getLearned())  learnedRuns++;
		}
		double avgErrorRate = totalE/totalM;
	
		String learnedWord = (learnedRuns==0)? "Not learned" : "Sometimes learned";

		order.add(rows2.size());
		String []w2 = { learnedWord + " ("+learnedRuns+"/"+(runs-learnedRuns)+")",
				"",
				"",
				"",
				"",
				"",
				fm.sprintf("%4.3f", avgErrorRate) };

		row = fm.th(key);
		for(String s: w2) {
		    if (isMe) s = fm.strong(s);
		    row += fm.td(s);
		}

		rows2.add(fm.tr(row));

		avgErrorRates.add(avgErrorRate);
	
	    }
	    order.sort((o1,o2)->(int)Math.signum(avgErrorRates.get(o1)-avgErrorRates.get(o2)));
	    for(int j: order) rows.add(rows2.get(j));
	    
	    body += fm.table( "border=\"1\"", rows);
	    
	} catch(Exception ex) {
	    title = "Error";
	    body = fm.para(ex.toString()) +
		fm.para(fm.wrap("small", "Details:"+ Util.stackToString(ex)));

	    System.err.println("" + ex);
	    ex.printStackTrace(System.err);

    }
	return fm.html(title, body);		 
    }

    public static void main(String[] argv) {
	int ja=0;
	String s = argv[ja++];
	Mode mode = Enum.valueOf( Mode.class, s.toUpperCase());
	String nickname = argv[ja++];
	String rule=argv[ja++];

	MannWhitneyComparison mwc = new MannWhitneyComparison(mode);
	Comparandum[][] allComp = mwc.mkMlcComparanda(nickname,  rule);

	String text =  mwc.doCompare(nickname, rule, allComp, plainFm);
	System.out.println(text);
	
    }

}
