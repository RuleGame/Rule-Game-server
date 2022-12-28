package edu.wisc.game.math;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.formatter.*;

/** Comparing players or rules based on the Mann-Whitney test
 */
public class MannWhitneyComparison {

    public enum Mode { CMP_RULES, CMP_ALGOS};

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
	} else {
	    q = em.createQuery("select m from MlcEntry m where m.nickname=:x");
	    q.setParameter("x", nickname);

	}
	return q;
    }

    /** From an MlcEntry, get the comparison key */
    private String getKey(MlcEntry e) {
	return (mode==Mode.CMP_ALGOS) ?
	    e.getNickname():
	    e.getRuleSetName();
    }

    /** Carries out comparison of the performance for different "keys"
	(algo nicknames or rule sets). In the CMP_ALGOS mode, a particular
	rule set is chosen, and ML algorithms are ranked by their performance
	on that rule set; thus the "key" is the algo nickname. In the CMP_RULES
	mode, a particular algorithm is chosen (the nickname) is specified,
	and the rule sets are ranked by their ease for this algorithm; thus
	the rule sets names are keys.
     */
    public String doCompare( String nickname,  String rule, Fmter fm) {

	final String myKey = (mode==Mode.CMP_ALGOS)? nickname: rule;
	final String pivot = (mode==Mode.CMP_ALGOS)? rule: nickname;
	String titlePrefix = (mode==Mode.CMP_ALGOS)?
	    "Results comparison on rule set ":
	    "Comparing rule sets with respect to algo ";
			
	String h1= titlePrefix + fm.tt(pivot), title=titlePrefix + pivot;
	String body="", errmsg = null;
	EntityManager em=null;

	try {

	    body += fm.h1(h1);
	    
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
		//w[j][ p[j]++] = e;
		
		MlcEntry [] row = w[j];
		int k = p[j]++;
		row[k] = e;

	    }

	    // only those keys where learning was successful
	    boolean[] learned = new boolean[n];
	    int nLearned = 0;
	    for(int j=0; j<n; j++) {
		boolean failed = false;
		for(int k=0; k<w[j].length; k++) {
		    failed = failed || !w[j][k].getLearned();
		}
		learned[j] = !failed;
		if (learned[j]) nLearned ++;
	    }

	    // the keys of successful learners (or successfully learned rules)
	    int goodKeys[] = new int[nLearned];
	    int ptr = 0;
	    for(int j=0; j<n; j++) {
		if (learned[j])  goodKeys[ptr++] = j;
	    }


	    int a[][] = new int[nLearned][];
	    for(int j=0; j<nLearned; j++) {
		int j0 =  goodKeys[j];
		a[j] = new int[ w[j0].length ];
		 for(int k=0; k<w[j0].length; k++) {
		     a[j][k] = w[j0][k].getTotalErrors();
		 }
	    }

	    double[][] z = MannWhitney.rawMatrix(a);
	    double[][] zr = MannWhitney.ratioMatrix(z);
	    
	    double[] ev = MannWhitney.topEigenVector(zr);
	    Vector<Integer> order = new Vector<>();
	    for(int j=0; j<nLearned; j++) order.add(j);
	    order.sort((o1,o2)-> (int)Math.signum(ev[o2]-ev[o1]));


	    body += fm.h3("Raw M-W matrix");
	    	    
	    Vector<String> v = new Vector<>();

	    for(int h=0; h< nLearned; h++) {
		int k=order.get(h);
		String key = keys.get(goodKeys[k]);
		boolean isMe = key.equals(myKey);
		String s =key + "\t";

		double[] c = new double[nLearned];		
		for(int i=0; i< nLearned; i++) c[i] = z[k][order.get(i)];
		
		s += Util.joinNonBlank("\t", c);
		if (isMe) s = fm.strong(s);
		v.add(s);
	    }
	    body += fm.pre(String.join("\n", v));

	    body += fm.h3("M-W ratio matrix");
	    v.clear();
	    for(int h=0; h< nLearned; h++) {
		int k=order.get(h);
		String key = keys.get(goodKeys[k]);
		boolean isMe = key.equals(myKey);
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		pw.print(key);
		double[] c = new double[nLearned];
		for(int i=0; i< nLearned; i++) {
		    c[i] = zr[k][order.get(i)];
		    pw.format("\t%8.4f", c[i]);
		}
		
		String s = sw.toString();
		if (isMe) s = fm.strong(s);
		v.add(s);
	    }
	    body += fm.pre(String.join("\n", v));

	    String h3 =	(mode==Mode.CMP_ALGOS)? "Comparison of algorithms":
		"Comparison of rule sets";
	    body += fm.h3(h3);

	    String keyCell =	(mode==Mode.CMP_ALGOS)? "Algo nickname":
		"Rule set name";
	    
	    Vector<String> rows = new Vector<>();
	    rows.add( fm.tr( fm.th(keyCell) +
			     fm.th("Learned? (learned/not learned)") +
			     fm.th("EV score") +
			     fm.th("Runs") +
			     fm.th("Avg. episodes till learned") +
			     fm.th("Avg. errors till learned") +
			     fm.th("Avg. moves till learned") +
			     fm.th("Avg. error rate")
			     ));

	    // the learned ones
	    for(int k=0; k< nLearned; k++) {
		int j=order.get(k);
		double evScore = ev[j];
		int j0 = goodKeys[j];
		String key = keys.get(j0);
		boolean isMe = key.equals(myKey);
		MlcEntry [] ee = w[j0];
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

		String w2[] = {
		    "Learned ("+runs+"/0)",
		    fm.sprintf("%6.4g", evScore),
		    "" + runs,
		    fm.sprintf("%5.2f",avgEp),
		    fm.sprintf("%6.2f",avgE),
		    fm.sprintf("%6.2f",avgM),
		    ""};

		
		String row = fm.th(key);
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
	    for(int j0=0; j0<n; j0++) {
		if (learned[j0]) continue;
		String key = keys.get(j0);
		boolean isMe = key.equals(myKey);
		MlcEntry [] ee = w[j0];
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

		String row = fm.th(key);
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

	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
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

	String text =  mwc.doCompare(nickname, rule, plainFm);
	System.out.println(text);
	
    }

}
