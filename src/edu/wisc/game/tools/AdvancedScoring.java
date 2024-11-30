package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import java.sql.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;


/** A well-advised alternative to "AvgAttempts"
 */
class AdvancedScoring {

    /** Jacob Fledman's preferred format */
    static void doJF(EpisodesByPlayer ph, EntityManager em, String outDir) throws IOException, SQLException {
	//-- select only players with at least 1 episodes
	//-- ignore players with a slash in their names (this is prohibited
	//-- now, but existed in 2021)
	Vector<String> v = new Vector<>();
	for(String pid:  ph.keySet()) {
	    if (pid.indexOf("/")>=0) continue;
	    if (ph.get(pid).size()>0) v.add(pid);
	}	
	String[] plist = v.toArray(new String[0]);
	Arrays.sort(plist);

	File base = new File(outDir);
	if (!base.exists()) {
	    if (!base.mkdirs()) AnalyzeTranscripts.usage("Cannot create output directory: " + base);
	}
	if (!base.isDirectory() || !base.canWrite()) AnalyzeTranscripts.usage("Not a writeable directory: " + base);

	File pidListFile = new File( base, "pid.csv");
	Util.writeTextFile(pidListFile, Util.joinNonBlank("\n", plist)+"\n");

	File tDir = new File( base, Files.Saved.TRANSCRIPTS);
	if (!tDir.isDirectory() && !tDir.mkdirs()) throw new IOException("Cannot create output directory: " + tDir);
	File dDir = new File( base, Files.Saved.DETAILED_TRANSCRIPTS);
	if (!dDir.isDirectory() && !dDir.mkdirs()) throw new IOException("Cannot create output directory: " + dDir);

	Util.CopyInfo statsT=new Util.CopyInfo();
	Util.CopyInfo statsD=new Util.CopyInfo();
	for(String playerId: plist) {
	    File f = Files.transcriptsFile(playerId, true);
	    File g = new File(tDir, f.getName());
	    //System.out.println("Copying transcript file " + f+ " to " + g);
	    
	    statsT.add( Util.copyFileUniqueLines(f,g));
	    f = Files.detailedTranscriptsFile(playerId, true);
	    g = new File(dDir, f.getName());
	    statsD.add( Util.copyFileUniqueLines(f,g));
	}
	System.out.println("Basic transcript files: copied " + statsT.n + " files, " + statsT.linesIn + " lines in, " + statsT.linesOut + " lines out");
	System.out.println("Detailed transcript files: copied " + statsD.n + " files, " + statsD.linesIn + " lines in, " + statsD.linesOut + " lines out");

	// SQL tables
	File sqlDir = new File( base, "sql");
	if (!sqlDir.isDirectory() && !sqlDir.mkdirs()) throw new IOException("Cannot create output directory: " + dDir);


	String pj = joinPlist(plist);
	String sql = "select * from PlayerInfo where playerId in ("+pj+")";
	File g = new File(sqlDir, "PlayerInfo.csv");
	ExportTable.doQuery(sql,g);
	
	sql = "select e.* from Episode e, PlayerInfo p where e.PLAYER_ID = p.ID and p.playerId in ("+pj+")";
	g = new File(sqlDir, "Episode.csv");
	ExportTable.doQuery(sql,g);
	Connection conn  = ExportTable.getConnection();
	scoring(conn, sqlDir, ph, plist);
	conn.close();
    }

    
    /**
       @param { "foo", "bar", "etc" }
       @return    "'foo','bar','etc'" */
    private static String joinPlist(String[] plist) {
	Vector<String> v = new Vector<>();
	for(String x: plist) v.add("'" + x + "'");
	return Util.joinNonBlank(",", v);
    }

    /*
    class EpisodeHandle {
    final String ruleSetName;
    ...
    final String exp;
    final String trialListId;
    final int seriesNo;
    final int orderInSeries;
    final String episodeId;
    final String playerId;
    final boolean useImages;
    final ParaSet para;
    ...  */


    static class EpisodeTemplate implements Comparable {
	final String exp;
	final String trialListId;
	final int seriesNo;
	String ruleSetName;
	int maxToRemove = 0;
	
	EpisodeTemplate( EpisodeHandle eh) {
	    exp = eh.exp;
	    trialListId = eh.trialListId;
	    seriesNo = eh.seriesNo;
	    ruleSetName = eh.ruleSetName;
	}
	public boolean equals(Object o) {
	    if (!(o instanceof EpisodeTemplate)) return false;
	    EpisodeTemplate z= (EpisodeTemplate)o;
	    return exp.equals(z.exp) && trialListId.equals(z.trialListId) &&
		seriesNo==z.seriesNo &&
		ruleSetName.equals(z.ruleSetName);
	}

	public int compareTo(Object o) {
	    if (!(o instanceof EpisodeTemplate)) throw new IllegalArgumentException("Cannot compare EpisodeTemplate to " + o);
	    EpisodeTemplate z= (EpisodeTemplate)o;
	    int q = exp.compareTo(z.exp);
	    if (q!=0) return q;
	    q = trialListId.compareTo(z.trialListId);
	    if (q!=0) return q;
	    q = (seriesNo - z.seriesNo);
	    if (q!=0) return q;
	    return ruleSetName.compareTo(z.ruleSetName);
	}
    }

    /** Removes the maximum common prefix ending in "/" from all rule
	set names in the set. (E.g. "FDCL/basic/foo" to "foo". This is
	done for brevity in reporting.  */
    private static void removeCommonPrefix(TreeSet <EpisodeTemplate> tt) {
	String p = null;
	for( EpisodeTemplate et: tt) {
	    String r = et.ruleSetName;
	    if (p==null) p = r;
	    else { // max common part
		int j=0;
		while(j<p.length()&& j<r.length() && p.charAt(j)==r.charAt(j)) {
		    j++;		    
		}
		if (j<p.length()) p = p.substring(0,j);
	    }
	}
	if (p==null) return;
	int n = 0;
	for(int j=0; j<p.length(); j++) {
	    if (p.charAt(j) == '/') n = j+1;
	}
	
	for( EpisodeTemplate et: tt) {
	    String r = et.ruleSetName;
	    et.ruleSetName = r.substring(n);
	}
	
    }

    
    /** Creates temporary table "master" with information about rule set names
     */
    //    static class EpisodesByPlayer extends TreeMap<String,Vector<EpisodeHandle>>    
    private static void createMasterTable(Connection conn, EpisodesByPlayer ph)  throws SQLException {
	TreeSet <EpisodeTemplate> tt=new TreeSet<>();
	for(Vector<EpisodeHandle> v: ph.values()) {
	    for(EpisodeHandle eh: v) {
		EpisodeTemplate et =  new EpisodeTemplate(eh);		
		boolean added = tt.add(et);
		if (added) {
		    et.maxToRemove = eh.para.getInt("max_objects") * eh.para.getMaxBoards();

		}

	    }
	}


	removeCommonPrefix(tt);
	//Connection conn  = getConnection();
	Statement stmt = conn.createStatement();

	//	Vector<String> qq = new Vector<>();
	//qq.add(q);
	String q= "CREATE TEMPORARY TABLE IF NOT EXISTS master(experimentPlan varchar(128), trialListId varchar(128), seriesNo int, rule varchar(128), maxToRemove int)";
	stmt.execute(q);

	String s = "insert into master values(?,?,?,?,?)";
	PreparedStatement pstmt = conn.prepareStatement(s);

	for( EpisodeTemplate et: tt) {
	    pstmt.setString(1, et.exp);
	    pstmt.setString(2, et.trialListId);
	    pstmt.setInt(3, et.seriesNo);
	    pstmt.setString(4, et.ruleSetName);
	    pstmt.setInt(5, et.maxToRemove);
	    pstmt.executeUpdate();
	}
    
    }

    /** This methods computes scores similar to Jacob's usual "Avg(attempts),
	but with corrections due to early wins, give-ups, and walk-aways.
	Discussed in email ca. 2024-11-25...26
	@param sqlDir The directory into which data files are to be written
     */
    private static void scoring(Connection conn, File sqlDir, EpisodesByPlayer ph, String[] plist)   throws IOException, SQLException {
	createMasterTable(conn, ph);
	
	Vector<String> qq = new Vector<>();
	String pj = joinPlist(plist);

	// Grouping by m.rule and m.maxToRemove is not actually
	// needed, but is required by the MySQL server, because
	// it does not know that (experimentPlan, seriesNo) could
	// be a unique primary key on master
	String q = "CREATE TEMPORARY TABLE IF NOT EXISTS tmp as " +
	    "(select p.playerId playerId, p.trialListId, e.seriesNo seriesNo, m.rule rule, m.maxToRemove maxToRemove, count(*) episodes, sum(e.attemptCnt) attempts, sum(e.doneMoveCnt) removed, 0 couldAlsoAttempt, 0 couldAlsoRemove, max(e.finishCode) finishCode " +
	    " from PlayerInfo p, Episode e, master m " +
	    " where p.playerId in ("+pj+") " +
	    " and e.PLAYER_ID=p.ID " +
	    " and p.trialListId=m.trialListId "+
	    " and p.experimentPlan=m.experimentPlan " +
	    " and m.seriesNo=e.seriesNo " +
	    " and e.seriesNo=0 " +
	    " group by e.PLAYER_ID, e.seriesNo, m.rule, m.maxToRemove)";

	qq.add(q);

	q = "update tmp set couldAlsoRemove=maxToRemove-removed, couldAlsoAttempt=maxToRemove-removed where finishCode = "+Episode.FINISH_CODE.EARLY_WIN;
	qq.add(q);

	q = "CREATE TEMPORARY TABLE IF NOT EXISTS Scoring as " +
	    "(select rule, count(*) n, avg(attempts) avgAttempts, sum(attempts+couldAlsoAttempt)/sum(removed+couldAlsoRemove) score, sum(attempts+couldAlsoAttempt)/sum(removed+couldAlsoRemove)*max(maxToRemove) scoreTimesObjectCnt from tmp group by rule)";

	qq.add(q);
	qq.add("select * from Scoring");
	File g = new File(sqlDir, "scores.csv");

	//	ExportTable.doQuery2(conn, qq.toArray(new String[0]), f);
	ExportTable.doQuery2(conn, qq.toArray(new String[0]), g);

	g  = new File(sqlDir, "playerStats.csv");
	qq.clear();
	qq.add("select * from tmp");
	ExportTable.doQuery2(conn, qq.toArray(new String[0]), g);
    }

}
