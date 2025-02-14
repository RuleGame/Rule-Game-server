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

    /** @param _mode What are we going to compare? Rules (by the algos'
	performance on them), rules (by the humans' performance on them)
	or ML algos (by their performance on the 	
     */
    public MannWhitneyComparison(Mode _mode) {
	mode = _mode;
    }

    /** Creates a SQL query on the MLC data, which, depending on the
	mode, will find either the entries for all algos playing the
	specified rule set ("rule"), or the entires for all rules that
	the specified algo ("nickname") has played.
	@param rule In  the CMP_ALGOS mode, we will be comparing algos with respect to their performance on this rule. In other modes, ignored
	@param nickname In  the CMP_RULES mode, we will be comparing rules with respect to this algo's performance on them. In other modes, ignored
     */
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

    /** From an MlcEntry, get the comparison key
	@return Depending on the mode, either the algo's nickname or the rule name
     */
    private String getKey(MlcEntry e) {	
	return (mode==Mode.CMP_ALGOS) ?
	    e.getNickname():
	    e.getRuleSetName();
    }

    /** Creates a list of comparanda based on MLC data, either to compare
	ML algos or to compare rule sets.

	@param rule In  the CMP_ALGOS mode, we will be comparing algos with respect to their performance on this rule. In other modes, ignored
	@param nickname In  the CMP_RULES mode, we will be comparing rules with respect to this algo's performance on them. In other modes, ignored
	
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

    
    /** Carries out the comparison of the performance for different "keys"
	(algo nicknames or rule sets). In the CMP_ALGOS mode, a particular
	rule set is chosen, and ML algorithms are ranked by their performance
	on that rule set; thus the "key" is the algo nickname. In the CMP_RULES
	mode, a particular algorithm is chosen (the nickname) is specified,
	and the rule sets are ranked by their ease for this algorithm; thus
	the rule sets names are keys.
	@param allComp The things to compare. allComp[0] is the list of "learned" comparanda, and allComp[1] is the list of unlearned ones. The comparison is done under different criteria in each group.
	@param csvOut If non-null, designates 3 files into which the
	raw M-W matrix, the M-W ratio matrix, and the final table will be written into
     */
    public String doCompare( String nickname,  String rule,
			     Comparandum[][] allComp,
			     Fmter fm, File[] csvOut) {

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

	    int nm =order.size()+1;
	    String[][][] mat = {new String[nm][], new String[nm][]};
	    for(int h=0; h< nm; h++) {
		mat[0][h] = new String[nm];
		mat[1][h] = new String[nm];
	    }
	    
	    mat[0][0][0] = mat[1][0][0] = "#key";
	    
	    for(int h=0; h< order.size(); h++) {
		int k = order.get(h);
		Comparandum q = learnedOnes[k];
		String key = q.key;
		boolean isMe = key.equals(myKey);
		Vector<String> c = new Vector<>(), cc = new Vector<>();

		mat[0][0][h+1]=	mat[1][0][h+1]=key;
		mat[0][h+1][0]=	mat[1][h+1][0]=key;

		
		for(int i=0; i< order.size(); i++)  {
		    double a[] = { z[k][order.get(i)],zr[k][order.get(i)] };
		    mat[0][h+1][i+1]=""+a[0];
		    mat[1][h+1][i+1]=""+a[1];

		    c.add( ""+a[0]);
		    cc.add( fm.sprintf("%8.4f", a[1]));
		}
		
		if (isMe) eachStrong(fm,c);
		if (isMe) eachStrong(fm,cc);
		v.add( fm.rowTh(key, "align='right'", c));
		vv.add( fm.rowTh(key, "align='right'", cc));
	    }
	    body += fm.h3("Raw M-W matrix");	    	    
	    //	    body += fm.pre(String.join("\n", v));
	    body += fm.table("",v);

	    body += fm.h3("M-W ratio matrix");
	    //body += fm.pre(String.join("\n", vv));
	    body += fm.table("",vv);

	    if (csvOut!=null) {
		for(int k=0; k<2; k++) {
		    ImportCSV.escapeAndwriteToFile(mat[k],csvOut[k]);
		}
	    }

	    String h3 =	(mode==Mode.CMP_ALGOS)? "Comparison of algorithms":
		"Comparison of rule sets";
	    body += fm.h3(h3);

	    String keyCell =	(mode==Mode.CMP_ALGOS)? "#Algo nickname":
		"#Rule set name";
	    
	    Vector<String> rows = new Vector<>();

	    String[] headers;
	    String[][] matt=new String[nm][];
	    
	    if (mode==Mode.CMP_ALGOS || mode==Mode.CMP_RULES) {
		headers=new String[] {keyCell,
			      "Learned? (learned/not learned)",
			      "EV score",
			      "Runs",
			      "Avg episodes till learned",
			      "Avg errors till learned",
			      "Avg moves till learned",
			      "Avg error rate"};

		matt[0] = new String[] {keyCell,
					"Learned", "Not learned",
					"EV score",
					"Runs",
					"Avg episodes till learned",
					"Avg errors till learned",
					"Avg moves till learned",
					"Avg error rate"};

	    

	    } else {
		boolean useMDagger = (order.size()>0 && 
		    learnedOnes[order.get(0)].useMDagger);
		String ms = useMDagger? "m<sub>&dagger;</sub>":  "m<sub>*</sub>";

		
		headers=new String[] {keyCell,
				      "Learned/not learned",
				      "EV score",
				      "Avg "+ms + " (learners/all)",
				      "min-median-max "+ms+fm.brHtml()+"(learners)",
				      "Harmonic mean "+ms + fm.brHtml() + "(learners/all)",
				      "Avg error rate"};
		
		ms = useMDagger? "m!":  "m*";

		matt[0]=new String[] {keyCell,
				      "Learned", "Not learned",
				      "EV score",
				      "Avg "+ms+" on learners",  "Avg "+ms+" on all",
				      "min "+ms, "med "+ms, "max "+ms,
				      "Harmonic mean "+ms+" on learners",
				      "Harmonic mean "+ms+" on all",
				      "Avg error rate"
		};
	    }
	    

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
			// Using errors until learned (and not total errors)
			// since Jan 2023, as per Eric and Paul
			// avgE += e.getTotalErrors();
			avgE += e.getErrorsUntilLearned();
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

		    matt[k+1] =  new String[]{
			key,
			""+runs,"0",
			""+ evScore,
			"" + runs,
			""+avgEp,
			""+avgE,
			""+avgM,
			""
		    };

		    if (isMe) eachStrong(fm,w2);
		    rows.add( fm.rowTh(key, "align='right'", w2));

		} else if (q.humanSer!=null) { // human
		    //"Learned/not learned",
		    //"EV score",
		    //"m* (errors till learned)",
		    int learnedCnt=0;
		    double sumM=0, sumMLearned=0;
		    int sumTotalMoves=0, sumTotalErrors=0;
		    double harmonicM = 0, harmonicMLearned = 0 ;
		    for(MwSeries ser: q.humanSer) {
			double m = q.getM(ser);
			if (ser.getLearned())  {
			    learnedCnt++;
			    sumMLearned += m;
			    harmonicMLearned += 1.0/m;
			}
			sumM += m;
			harmonicM += 1.0/m;
			sumTotalMoves += ser.getTotalMoves();
			sumTotalErrors += ser.getTotalErrors();
		    }

		    double ma[] = new double[learnedCnt];
		    int pj = 0;
		    for(MwSeries ser: q.humanSer) {
			if (ser.getLearned())  {
			    ma[pj++] = q.getM(ser);
			}
		    }
		    double[] mmm = minMedMax(ma);

		    double n = (double)q.humanSer.length;
		    double avgM = sumM/n;
		    double avgMLearned = sumMLearned/learnedCnt;
		    harmonicM = n/harmonicM;
		    harmonicMLearned = learnedCnt/harmonicMLearned;

		    double avgE = sumTotalErrors/(double)sumTotalMoves;
		    String fkey = formatHumanKey(fm, key);
		    w2 = new String[]{
			fkey,
			""+learnedCnt+"/" + (q.humanSer.length-learnedCnt),
			fm.sprintf("%6.4g", evScore),
			fm.sprintf("%6.2f",avgMLearned) + "/" +
			fm.sprintf("%6.2f",avgM),
			(ma.length>0) ? (int)mmm[0] + " - " +
			fm.sprintf("%6.1f",mmm[1]) +" - " + (int)mmm[2] :
			"",
			fm.sprintf("%6.2f",harmonicMLearned) + "/" +
			fm.sprintf("%6.2f",harmonicM),
			fm.sprintf("%4.2f",avgE)
		    };

		    matt[k+1] =  new String[]{
			fkey,
			""+learnedCnt, "" + (q.humanSer.length-learnedCnt),
			""+ evScore,
			""+avgMLearned, ""+avgM,
			(ma.length>0) ? ""+mmm[0]: "",
			(ma.length>0) ? ""+mmm[1]: "",
			(ma.length>0) ? ""+mmm[2]: "",
			""+harmonicMLearned, ""+harmonicM,
			""+avgE
		    };
		    
		    if (isMe) eachStrong(fm,w2);
		    rows.add( fm.rowExtra( "align='right'", w2));


		    
		} else throw new IllegalArgumentException();

		//for(int i=0; i<w2.length; i++) w2[i] = w2[i].trim();
		
	    }

	    if (csvOut!=null) {
		ImportCSV.escapeAndwriteToFile(matt,csvOut[2]);
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
		double avgErrorRate = totalE/(double)totalM;
	
		String learnedWord = (learnedRuns==0)? "Not learned" : "Sometimes learned";

		order.add(rows2.size());
		String []w2 = { learnedWord + " ("+learnedRuns+"/"+(runs-learnedRuns)+")",
				"",
				"",
				"",
				"",
				"",
				fm.sprintf("%4.3f", avgErrorRate) };

		if (isMe) eachStrong(fm,w2);
		rows2.add(fm.rowTh(key, "align='right'", w2));

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

    static double[] minMedMax(double a[]) {
	if (a.length==0) return new double[] {Double.NaN, Double.NaN, Double.NaN};
	Arrays.sort(a);
	int n = a.length/2;

	double med = (a.length % 2 == 1) ?    a[n] :  (a[n-1] + a[n]) / 2;
	return new double[] {a[0], med, a[a.length-1]};
    }

      static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	//	System.err.println("For usage, see tools/analyze-transcripts-mwh.html\n\n");
	System.err.println("Usage:");
	System.err.println(" MannWhitneyComparison -mode CMP_ALGOS -rule ruleName [-csvOut dir]");
	System.err.println(" MannWhitneyComparison -mode CMP_RULES -algo ruleName [-csvOut dir]");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    
    public static void main(String[] argv) {
	Mode mode = Mode.CMP_RULES;
	String nickname = null;
	String rule=null;
	File csvOutDir = null;

	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];
	    if (j+1< argv.length && a.equals("-mode")) {
		String s = argv[++j];
		mode = Enum.valueOf( Mode.class, s.toUpperCase());
	    } else if (j+1< argv.length && a.equals("-algo")) {
		nickname =  argv[++j];
	    } else if (j+1< argv.length && a.equals("-rule")) {
		rule =  argv[++j];
	    } else if (j+1< argv.length && a.equals("-csvOut")) {
		csvOutDir = new File(argv[++j]);
	    } else if (a.startsWith("-")) {
		usage("Unknown option: " + a);
	    } else {
		usage("Don't know what to do with the argument: " + a);
	    }
	}


	if (mode==Mode.CMP_RULES) {
	    if (nickname==null) usage("In the mode " + mode + ", must supply -algo");
	} else	if (mode==Mode.CMP_ALGOS) {
	    if (rule==null) usage("In the mode " + mode + ", must supply -rule");
	} else usage("Mode not supported: " + mode);
	
	MannWhitneyComparison mwc = new MannWhitneyComparison(mode);
	Comparandum[][] allComp = mwc.mkMlcComparanda(nickname,  rule);

	String text =  mwc.doCompare(nickname, rule, allComp, plainFm, expandCsvOutDir(csvOutDir));
	System.out.println(text);
	
    }

    static private void eachStrong(Fmter fm, String[] w) {
	for(int j=0; j<w.length; j++)  {
	    w[j] = fm.strong(w[j]);
	}
    }

    static private void eachStrong(Fmter fm, Vector<String> w) {
	for(int j=0; j<w.size(); j++)  {
	    w.set(j, fm.strong(w.get(j)));
	}
    }

    /** Bolds the most important section (R3) of the R1:R2:R3 key only, leaving
	the preceding rule sets un-bolded */
    static private String formatHumanKey(Fmter fm, String key) {
	String z[] = key.split(":");
	z[z.length-1] = fm.strong(z[z.length-1]);
	return String.join(":", z);
    }

    /** Suggests names for CSV output files */
    public static File[] expandCsvOutDir(File csvOutDir) {
	File[] csvOut = null;
	if (csvOutDir!=null) {
	    csvOutDir.mkdirs();
	    csvOut = new File[] { new File(csvOutDir, "raw-wm.csv"),
		new File(csvOutDir, "ratio-wm.csv"),
		new File(csvOutDir, "ranking.csv")};
	}
	return csvOut;
    }

    
}
