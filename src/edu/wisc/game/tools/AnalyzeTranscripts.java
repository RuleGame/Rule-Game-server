package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

import javax.persistence.*;

import java.sql.SQLException;


import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.*;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.sql.ReplayedEpisode.RandomPlayer;
	
/** Methods for the statistical analysis of game transcripts.
    For documentation, including usage, see analyze-transcripts.html
 */
public class AnalyzeTranscripts {

    protected boolean quiet = false;
    static boolean debug=false;
    
    private static void usage() {
	usage(null);
    }
    private static void usage(String msg) {
	System.err.println("For usage info, please see:\n");
	System.err.println("http://rulegame.wisc.edu/w2020/tools/analyze-transcripts.html");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    
    /** Looks up the EpisodeHandle object in vector v for a specified
	episode id.
	
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

    /** Do we need to compute p0? */
    private static boolean needP0=false;
    /** Do we need to print out the board position after p0? */
    private static boolean needBoards=false;
    /** How should be compute p0? (Using which baseline random player model?) */
    protected RandomPlayer randomPlayerModel=RandomPlayer.COMPLETELY_RANDOM;	


    /** Do we want to fit the learning curve for the Y(t) vector? This
	can be set to false, with the "-nofit" option, to skip curve
	fitting */
    private static boolean weWantFitting=true;
    /** Produce a CSV file with episode counts, move numbers, timing
	numbers etc for all players */
    private static boolean weWantTiming=true;

    /** Various ways to interpret argv elements -- as experiment plan
	names, player IDs, etc. */
    enum ArgType { PLAN, PID, UID, UNICK};
    
    static boolean weWantPredecessorEnvironment  = false;

    /** The main() method processes the command line arguments, allowing a large variety of ways to specify
	the set of players whose data are to be analyzed.
     */
    public static void main(String[] argv) throws Exception {

	//protected static
	ReplayedEpisode.RandomPlayer randomPlayerModel=//null;
	ReplayedEpisode.RandomPlayer.COMPLETELY_RANDOM;	


	String outDir = "tmp";

	if (argv.length==0) {
	    int y[]={1, 1, 0, 0, 0, 0, 1, 1};
	    test(y);
	    return;
	}


	
	if (argv.length==2 && argv[0].equals("-y")) {
	    String[] ys = argv[1].split("\\s+");
	    int[] y = new int[ys.length];
	    for(int j=0; j<y.length; j++) y[j] = Integer.parseInt(ys[j]);
	    test(y);
	    return;
	} else {
	    //System.out.println("argv.length=" + argv.length +", argv[0]='" +argv[0] +"'");
	}

	boolean jf = false;

	String config = null;
	ArgType argType = ArgType.PLAN;
	boolean fromFile = false;
	String inputDir = null;
	
	Vector<String> plans = new Vector<>();
	Vector<String> pids = new Vector<>();
	Vector<String> nicknames = new Vector<>();
	Vector<Long> uids = new Vector<>();
	
	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];
	    if (j+1< argv.length && a.equals("-config")) {
		config = argv[++j];
	    } else if (a.startsWith("-p0")) {
		String mode = a.substring(3);
		if (mode.equals("")) {
		    if (++j >= argv.length) usage("The -p0 option must be followed by a model name");
		    mode = argv[j];
		}
		needP0=true;		
		randomPlayerModel = ReplayedEpisode.RandomPlayer.valueOf1(mode);
	    } else if  (a.equals("-jf")) {
		jf = true;
	    } else if  (a.equals("-boards")) {
		needBoards = true;
	    } else if  (a.equals("-pre")) {
		weWantPredecessorEnvironment  = true;
		System.out.println("weWantPredecessorEnvironment=" + weWantPredecessorEnvironment);
	    } else if  (a.equals("-timing")) {
		weWantTiming=true;
	    } else if  (a.equals("-fit")) {
		weWantFitting=true;
	    } else if  (a.equals("-nofit")) {
		weWantFitting=false;
	    } else if (j+1< argv.length && a.equals("-out")) {
		outDir = argv[++j];
	    } else if (j+1< argv.length && a.equals("-in")) {
		inputDir = argv[++j];
	    } else if  (a.equals("-nickname")) {
		argType = ArgType.UNICK;
	    } else if  (a.equals("-plan")) {
		argType = ArgType.PLAN;
	    } else if  (a.equals("-uid")) {
		argType = ArgType.UID;
	    } else if  (a.equals("-pid")) {
		argType = ArgType.PID;
	    } else if  (a.equals("-file")) {
		fromFile=true;
	    } else {

		String[] v=  fromFile? readList(new File(a)):  new String[]{a};
				  
		for(String b: v) {
		    if (argType==ArgType.PLAN) 	plans.add(b);
		    else if (argType==ArgType.UNICK)  nicknames.add(b);
		    else if (argType==ArgType.UID)  uids.add(Long.parseLong(b));
		    else if (argType==ArgType.PID)  pids.add(b);
		}
	    }
	}

	if (config!=null) {
	    // Instead of the master conf file in /opt/w2020, use the customized one
	    MainConfig.setPath(config);

	    // Set the input directory as per the config file, unless
	    // explicitly overridden by the "-in" option.
	    //	    if (inputDir == null) inputDir = MainConfig.getString("FILES_SAVED", null);
	}

	// The -input option may override -config
	if (inputDir != null) {
	    if (!(new File(inputDir)).isDirectory()) usage("Not a directory: " + inputDir);
	    //Files.setSavedDir(inputDir);
	    MainConfig.put("FILES_SAVED", inputDir);
	}

	EntityManager em = Main.getNewEM();

	if (needBoards && !needP0) throw new IllegalArgumentException("Cannot use option -boards without -p0");

	EpisodesByPlayer ph = listEpisodesByPlayer( em, plans, pids, nicknames, uids);

	if (jf) { // The Jacob Feldman's preferred format
	    AdvancedScoring.doJF(ph, em, outDir);
	    // EntityManager em, String outDir, 
	    return;
	}
		
		
	
	File base = new File(outDir);
	if (!base.exists()) {
	    if (!base.mkdirs()) usage("Cannot create output directory: " + base);
	}
	if (!base.isDirectory() || !base.canWrite()) usage("Not a writeable directory: " + base);

	PrintWriter wsum =null;
	if (weWantFitting || weWantTiming) {
	    File gsum=new File(base, needP0? "summary-p0-"+randomPlayerModel+".csv" : "summary-flat.csv");
	    wsum = new PrintWriter(new FileWriter(gsum, false));
	    String sumHeader = "#ruleSetName,playerId,experimentPlan,trialListId,seriesNo";
	    if (weWantFitting) {
		sumHeader += ",yy,B,C,t_I,k,Z,n,L/n,AIC/n";
	    }
	    if (weWantTiming) {
		sumHeader += ",episodes,moves,sec";
	    }

	    wsum.println(sumHeader);
	}

	// Create subdirectories for all relevant rule sets
	for(String ruleSetName: ph.allHandles.keySet()) {
	    System.out.println("For rule set=" +ruleSetName+", found " + ph.allHandles.get(ruleSetName).size()+" good episodes"); //:"+Util.joinNonBlank(" ",ph.allHandles.get(ruleSetName) ));
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
	    try {
		AnalyzeTranscripts atr = new AnalyzeTranscripts(base, wsum, randomPlayerModel);
		atr.analyzePlayerRecord(playerId, v);
	    } catch(Exception ex) {
		System.err.println("ERROR: Cannot process data for player=" +playerId+" due to missing data. The problem is as follows:");
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }
	}
	if (wsum!=null) wsum.close();
    }

    /** Given the command-line arguments, finds all players whose data
	we want, and all episodes by these players.

	<ul>

	<li>
	If only the list of plans is non-empty, the returned list of
	players will include all players who played those plans.

	<li>
	If the list of plans is empty, and the lists of pids and/or
	nicknames and/or uids are non-empty, the returned list of
	players will include all players associated with any of these
	IDs. (In practice, it is likely that the user will typically
	supply only one of these participant-based lists).

	<li> If both the list of plans and at least one of the 3
	participant-based lists (pids, nicknames, uids) are non-empty,
	this will be understood as a conjunction, i.e. that the user
	wants the data only for certain plans, but only for games in
	these plans played by certain players (e.g. only by M-Turkers
	and not by our staff).  Thus this will be interpreted as a
	conjunction, and only players that are simultaneously in the
	requested plans and in the requested participant-based lists
	will be returned. (E.g., "the M-Turkers who played games in
	plan X").  </ul>
    */
    static EpisodesByPlayer listEpisodesByPlayer(EntityManager em,
						 Vector<String> plans,
						 Vector<String> pids,
						 Vector<String> nicknames,
						 Vector<Long> uids) throws Exception {

	boolean needConjunction = (plans.size()>0 && pids.size()+nicknames.size()+uids.size() > 0);

	plans = expandPlans(em, plans);

	PlayerList plist = new 	PlayerList(em,  pids,  nicknames,   uids);
	//System.out.println("DEBUG:: pids=" + Util.joinNonBlank(", " ,pids));
	//System.out.println("DEBUG:: plist=" + plist);

	//-- To be used only if needConjunction
	HashSet<String> eligiblePlayerIDs = new HashSet<>();

	if (needConjunction) {
	    for(PlayerInfo p: plist) {
		eligiblePlayerIDs.add(p.getPlayerId());
	    }

	    System.out.println("Conjunction mode: plan in {" + Util.joinNonBlank(", " ,plans) + "} AND (" +
			       "pid in + {" + Util.joinNonBlank(", " ,pids) + "} OR " +
			       "nickname in {" + Util.joinNonBlank(", " ,nicknames) + "} OR " +
			       "uid in {" + Util.joinNonBlank(", " ,uids) + "})");
	    System.out.println("The right side of this conjunction includes " + plist.size() + " eligible player IDs");
	} else if (plans.size()>0) {
	    System.out.println("Selection by plan: plan in {" + Util.joinNonBlank(", " ,plans) + "}");
	} else {
	    System.out.println("Selection by participant: " +
			       "pid in + {" + Util.joinNonBlank(", " ,pids) + "} OR " +
			       "nickname in {" + Util.joinNonBlank(", " ,nicknames) + "} OR " +
			       "uid in {" + Util.joinNonBlank(", " ,uids) + "}");
	}
	

	EpisodesByPlayer ph =new EpisodesByPlayer();

	// If we're given a list of experiment plan, get the list of players for each one,
	// applying conjunction if needed.
	for(String exp: plans) {		
	    System.out.println("Experiment plan=" +exp);
	    try {
	    
	    // ... List all trial lists 
	    TrialListMap trialListMap=new TrialListMap(exp);
	    System.out.println("Experiment plan=" +exp+" has " + trialListMap.size() +" trial lists: "+ Util.joinNonBlank(", ", trialListMap.keySet()));
	    
	    Vector<EpisodeHandle> handles= new Vector<>();

	    // ... and all players enrolled in the plan
	    Query q = em.createQuery("select m from PlayerInfo m where m.experimentPlan=:e");
	    q.setParameter("e", exp);
	    List<PlayerInfo> res = (List<PlayerInfo>)q.getResultList();
	    for(PlayerInfo p:res) {
		boolean include = !needConjunction || eligiblePlayerIDs.contains(p.getPlayerId());
		if (include) ph.doOnePlayer(p,  trialListMap, handles);
	    }
	    
	    System.out.println("For experiment plan=" +exp+", found " + handles.size()+" good episodes");//: "+Util.joinNonBlank(" ", handles));
	    } catch(Exception ex) {
		String msg = "ERROR: Skipping plan=" +exp+" due to an exception:";
		System.out.println(msg);
		System.err.println(msg);
		System.err.println(ex);
		ex.printStackTrace(System.err);
	    }
	}

	// Or, for each specified player...
	if (!needConjunction) {
	    HashMap<String,TrialListMap> trialListMaps = new HashMap<>();	
	    for(PlayerInfo p: plist) {
		try {
		    String exp=p.getExperimentPlan();
		    TrialListMap trialListMap=trialListMaps.get(exp);
		    if (trialListMap==null) trialListMaps.put(exp, trialListMap=new TrialListMap(p.getExperimentPlan()));
		    Vector<EpisodeHandle> handles= new Vector<>();
		    ph.doOnePlayer(p,  trialListMap, handles);
		    System.out.println("For player=" +p.getPlayerId()+", found " + handles.size()+" good episodes");//: "+Util.joinNonBlank(" ", handles));
		} catch(Exception ex) {
		    System.err.println("ERROR: Skipping player=" +p.getPlayerId()+" due to missing data. The problem is as follows:");
		    System.err.println(ex);
		    ex.printStackTrace(System.err);
		}
	    }
	}
	
	return ph;	
    }

    
    /** Lists all trial lists for an experiment plan. The keys are
	trial list IDs */
    public static class TrialListMap extends HashMap<String,TrialList> {
	public TrialListMap(String exp) throws IOException, IllegalInputException {
	    Vector<String> trialListNames = TrialList.listTrialLists(exp);
	    for(String trialListId: trialListNames) {
		TrialList t = new  TrialList(exp, trialListId);
		put( trialListId,t);	
	    }
	}
    }

    /** Reads a list of something (e.g. player IDs) from the first column
	of a CSV file.

	<p>
	Special treatment is provided for Prolific summary files
	(exportable with "Download summary" on a study's screen in Prolific.com).
	These files are identified by their name (e.g. prolific_export_671b8ae84509b9a624b95e13.csv);
	when such a file is provided, we construct playerId from the study ID (found in the file name)
	and the participant ID (column 2, in one-based count), e.g.
	prolific-671b8ae84509b9a624b95e13-5e9088a8a4b6ed018b45ba7b
	<pre>
	Submission id,Participant id,Status,Custom study tncs accepted at,Started at,Completed at,Reviewed at,Archived at,Time taken,Completion code,Total approvals,Fluent languages,Age,Sex,....
671b8e95b17f88050648883e,5e9088a8a4b6ed018b45ba7b,APPROVED,Not Applicable,2024-10-25T12:27:09.179000Z,2024-10-25T12:46:18.540000Z,2024-10-25T23:35:53.040000Z,2024-10-25T12:46:19.217499Z,1150,prolific-671b8ae84509b9a624b95e13-5e9088a8a4b6ed018b45ba7b-20241025-084454-22D5,389,"Czech, English, Russian, Slovak",25,Female,...
671b8e98fd64b1b65fa1a726,5c655e3c42faef0001283e77,APPROVED,Not Applicable,2024-10-25T12:27:10.328000Z,2024-10-25T12:52:05.286000Z,2024-10-25T23:35:53.354000Z,2024-10-25T12:54:01.498864Z,1495,prolific-671b8ae84509b9a624b95e13-5c655e3c42faef0001283e77-20241025-085043-C77D,365,"German, English",24,Male,....
</pre>
    */
    static String[] readList(File f) throws IOException, IllegalInputException{

	String studyId = getProlificStudyId(f);
	//System.out.println("f=" + f+", study=" + studyId);
	Vector<String> v=new Vector<>();
	CsvData csv = new CsvData(f, true, false, null);
	for(CsvData.LineEntry _e: csv.entries) {
	    CsvData.BasicLineEntry e = (CsvData.BasicLineEntry)_e;
	    String x = (studyId==null) ? e.getKey():
		"prolific-" + studyId + "-"  + e.getCol(1);

	    // strip any suffix from "foo.transcript.csv"
	    int pos = x.indexOf(".");
	    if (pos>0) x = x.substring(0, pos);

	    
	    v.add(x);
	}
	return v.toArray(new String[0]);
    }

    private static Pattern prolificFileNamePat =  Pattern.compile("prolific_export_([a-zA-Z0-9]+)\\.csv");
    
    /** Looks at a file name, and if it looks like a Prolific study summary file (e.g.
	prolific_export_671b8ae84509b9a624b95e13.csv), extracts study ID from it.
	@return The study ID, or null if the file name does not look like a Prolific study
	summary file.
    */
    private static String getProlificStudyId(File f) {
	Matcher m = prolificFileNamePat.matcher(f.getName());
	if (!m.matches()) return null;
	return m.group(1);
    }
    
    /** An ordered list of unique PlayerInfo objects. It can be
	created based on a list of player IDs, repeat user nicknames,
	and/or experiment plans.
     */
    static class PlayerList extends Vector<PlayerInfo> {
	private HashSet<Long> h = new HashSet<>();	
	public boolean addAll(Collection<? extends PlayerInfo > c) {
	    int cnt=0;
	    for(PlayerInfo p: c) {
		if (h.contains(p.getId())) continue;
		String pid = p.getPlayerId();
		// These days, player IDs cannot contain a slash (because it would prevent
		// the creation of a transcript file), but earliest version of GS allowed them.
		// We must filter them out to avoid problems later on, when trying to access
		// (non-existent) transcript files.
		if (pid.indexOf("/")>=0) continue;
		h.add(p.getId());
		add(p);
		cnt++;	       
	    }
	    return(cnt>0);
	}
	
	PlayerList(EntityManager em,
		   Vector<String> pids,
		   Vector<String> nicknames,
		   Vector<Long> uids) {

	    for(String pid: pids) {
		Query q =
		    (pid.indexOf('%')>=0)?
		    em.createQuery("select p from PlayerInfo p where p.playerId like :p"):
		    em.createQuery("select p from PlayerInfo p where p.playerId=:p");
	    
		q.setParameter("p", pid);
		addAll((List<PlayerInfo>)q.getResultList());
	    }	    

	    for(Long uid: uids) {
		Query q = em.createQuery("select p from PlayerInfo p where p.user.id=:u");
	    
		q.setParameter("u", uid);
		addAll((List<PlayerInfo>)q.getResultList());
	    }

	    for(String nickname: nicknames) {
		Query q =
		    (nickname.indexOf('%')>=0)?
		    em.createQuery("select p from PlayerInfo p where p.user.nickname like :n"):
		    em.createQuery("select p from PlayerInfo p where p.user.nickname=:n");
	    
		q.setParameter("n", nickname);
		addAll((List<PlayerInfo>)q.getResultList());
	    }

	}

	public String toString() {
	    return "{" + Util.joinNonBlank("; ", this) + "}";
	}
	
    }

    
    /** Expands '%' in plan names. Only includes plans that have any
	non-empty episodes associated with them. */
    static Vector<String> expandPlans(EntityManager em, Vector<String> v0) throws Exception  {
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
    	@param _base The main output directory. If null, no files will be
	written
	@param _wsum If non-null, the summary file will go there
    */
    AnalyzeTranscripts( File _base, PrintWriter _wsum,
			RandomPlayer _randomPlayerModel) {
	base = _base;
	wsum = _wsum;
	randomPlayerModel = _randomPlayerModel;
    }

    final private File base;
    final private PrintWriter wsum;

    /** Saves the data for a single (player, ruleSet) pair. This method
	can only be called if base!=null. 
	@param section A vector of arrays, each array representing the recorded
	moves for one episode.
	@param includedEpisodes All non-empty episodes played by this player in this rule set
    */
    protected void saveAnyData(Vector<TranscriptManager.ReadTranscriptData.Entry[]> section,
			     Vector<EpisodeHandle> includedEpisodes)
	throws  IOException, IllegalInputException,  RuleParseException {

	if (includedEpisodes.size()==0) return;
	EpisodeHandle eh0 =  includedEpisodes.firstElement();

	HashSet<String> excludableNames = Util.array2set("buckets", "dropped");

	double[] p0=null;
	Vector<Board> boardHistory = (needBoards)? new Vector<>(): null;
	String outHeader="#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,orderInSeries,episodeId," + "moveNo,timestamp,y,x,by,bx,code";
	if (needP0) {
	    outHeader += ",p0";
	    
	    p0 = computeP0andR(section, eh0.para, eh0.ruleSetName, boardHistory).p0;
	    if (needBoards) outHeader += ",board";
	}
	if (weWantPredecessorEnvironment) {
	    outHeader += ",precedingRules";
	}
					      
	String rid = eh0.ruleSetName;
	File d=new File(base, rid);
	File g=new File(d, includedEpisodes.firstElement().playerId + ".split-transcripts.csv");		
	PrintWriter w =  new PrintWriter(new FileWriter(g, false));
	w.println(outHeader);
	//PrintWriter wb = null;
	

	int je =0, jp=0;
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: section) {
	    EpisodeHandle eh = includedEpisodes.get(je ++);
	    for(TranscriptManager.ReadTranscriptData.Entry e: subsection) {
		if (!eh.episodeId.equals(e.eid)) throw new IllegalArgumentException("Array mismatch");
		
		w.print(rid+","+e.pid+","+eh.exp+","+eh.trialListId+","+eh.seriesNo+","+eh.episodeNo+","+e.eid);
		for(int j=2; j<e.csv.nCol(); j++) {
		    w.print(","+ImportCSV.escape(e.csv.getCol(j)));
		}
		if (needP0) {
		    w.print(","+ p0[jp]);
		    if (needBoards) {
			Board b = boardHistory.get(jp);
			String s = JsonReflect.reflectToJSONObject(b, true, excludableNames).toString();
			s =  ImportCSV.escape(s);
			w.print("," + s);
		    }
		    jp++;
					
		}
		if (weWantPredecessorEnvironment) {
		    w.print("," + ImportCSV.escape( String.join(";", eh.precedingRules)));
		}
		
		w.println();	
	    }
	}

	w.close(); w=null;

	if (weWantFitting || weWantTiming) {
	    OptimumExplained oe = analyzeSection( Util.joinSubsections( section), eh0, wsum, needP0? p0: null);
	}
	section.clear();
	includedEpisodes.clear();
    }

    /** Splits a section of transcript pertaining to a single rule set (i.e. a series of episodes) into subsections, each subsection pertaining to one specific
	episode.
     */
    static public Vector<TranscriptManager.ReadTranscriptData.Entry[]> splitTranscriptIntoEpisodes(Vector<TranscriptManager.ReadTranscriptData.Entry> section) {
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

    /** Removes any duplicate entries from each subsection. Such
	entries may have been created due to imperfections in the
	transcript-saving process.
    */
    static public void removeDuplicates(Vector<TranscriptManager.ReadTranscriptData.Entry[]>  subsections) {
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


    public static class P0andR {
	public final double [] p0, rValues;
	
	public P0andR(int n) {
	    p0 = new double[n];
	    rValues = new double[n];
	}
	public P0andR(Vector<Double> _p0, Vector<Double> r) {
	    this( _p0.size());
	    if (r.size() != p0.length) throw new IllegalArgumentException();
	    int k=0;
	    for(double x: _p0) p0[k++] = x;
	    k=0;
	    for(double x: r) rValues[k++] = x;		       
	}
   
    }
    
    
    /** Reconstructs and replays the historical episodes in one
	series, computing p0 for every pick or move attempt.
	
	@param subsections A (preprocessed) transcript by a player, which covers an entire series of  episodes.
	@param para The parameter set that was in effect for this series.
	@param boardHistory An output parameter. If not null, we will save the board before each move to that vector. The number of entries put into this vector will be equal to the number of values put into the return value (p0)
	@return An array containing p0 values for each move or pick attempt  in all episodes of the series.

     */
    protected P0andR computeP0andR(Vector<TranscriptManager.ReadTranscriptData.Entry[]> subsections, ParaSet para, String ruleSetName,
			       Vector<Board> boardHistory
			       )  throws  IOException, IllegalInputException,  RuleParseException{

	
	RuleSet rules = AllRuleSets.obtain( ruleSetName);

	P0andR result = new P0andR( Util.sumLen(subsections));
	int k=0;
	
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: subsections) {
	    String episodeId = subsection[0].eid;
	    
	    Board board = boards.get(episodeId);
	    Game game = new Game(rules, board);
	    ReplayedEpisode rep = new ReplayedEpisode(episodeId, para, game, randomPlayerModel);

	    if (debug) System.out.println("- P&R ---------- eid=" + episodeId);

	    if (debug) System.out.println("All moves:");
	    for(int j=0; j<subsection.length; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];
		if (debug) System.out.println(e.pick.toString());
	    }
	
	    for(int j=0; j<subsection.length; j++) {
		TranscriptManager.ReadTranscriptData.Entry e = subsection[j];

 		//System.out.println("j=" + j);
		//System.out.println(rep.graphicDisplay());

		if (boardHistory!=null) {		
		    Board b = rep.getCurrentBoard();
		    boardHistory.add(b);
		}


		double p =rep.computeP0(e.pick, e.isSuccessfulPick());	    
		result.p0[k] = p;
	    
		//-- replay the move/pick attempt 
		int code = rep.accept(e.pick);

		result.rValues[k] = e.pick.getRValue();

		if (debug) System.out.println("Move["+k+"]=" +e.pick.toString() +", p0=" + p+", replay code=" + code +", r=" + result.rValues[k]);

		k++;

		
		if (!Episode.CODE.areSimilar(code,e.code)) {
		    throw new IllegalArgumentException("Unexpected code in episode "+episodeId+", replay code=" + code +", vs. the recorded code=" + e.code);
		}
	    }
	}
	return result;
    }

    

    /** The initial boards for all episodes of this player */
    private HashMap<String,Board> boards;
    
    /** Reads one player's transcript, and prepares a complete report 
	for that player.
	@param playerId The player whose record we want to analyze
	@param v The list of episodes played by this player
    */
    protected  void    analyzePlayerRecord(String playerId, Vector<EpisodeHandle> v) throws  IOException, IllegalInputException,  RuleParseException{

	HashMap <String,Boolean> useImages = new HashMap<>();
	for(EpisodeHandle eh: v) {
	    useImages.put(eh.episodeId, eh.useImages);
	}

	File boardsFile =  Files.boardsFile(playerId, true);
	boards = BoardManager.readBoardFile(boardsFile, useImages);	
	
	File inFile = Files.transcriptsFile(playerId, true);
	TranscriptManager.ReadTranscriptData transcript = new TranscriptManager.ReadTranscriptData(inFile);

	// split by episode 
	Vector<TranscriptManager.ReadTranscriptData.Entry[]> subsections = splitTranscriptIntoEpisodes(transcript);
	// remove any duplicates that may exist due to imperfections in the transcript saving mechanism
	removeDuplicates(subsections);
	
	
	// One subsection per episode
	if (!quiet) System.out.println("Player "+playerId+": split the transcript ("+transcript.size()+" moves) into "+subsections.size()+ " episode sections");
	    
	String lastRid=null;
	// all episodes' subsections for a given rule sets
	Vector<TranscriptManager.ReadTranscriptData.Entry[]> section=new Vector<>();
	Vector<EpisodeHandle> includedEpisodes=new Vector<>();

	// For each episode...
	for(TranscriptManager.ReadTranscriptData.Entry[] subsection: subsections)  {
	    String eid = subsection[0].eid;
	    EpisodeHandle eh = findEpisodeHandle(v, eid);
	    if (eh==null) {
		String msg = "In file "+inFile+", found unexpected experimentId="+ eid;
		System.err.println(msg);		continue;
		//throw new IllegalArgumentException(msg);
	    }
	    
	    String rid=eh.ruleSetName;

	    if (lastRid == null) lastRid = rid;
	    else if (!lastRid.equals(rid)) {
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
       @param eh A handle for one of the episodes in this  (player, rule set) series. It is only used to access the information common for the entire series, not for this specific episode.
     */
    private static OptimumExplained analyzeSection(Vector<TranscriptManager.ReadTranscriptData.Entry> section,
						   EpisodeHandle eh,   PrintWriter wsum, double[] p0
						   ) {	
	int[] y = TranscriptManager.ReadTranscriptData.asVectorY(section);
	if (y.length<2) return null;
	
	String rid=eh.ruleSetName;
	String line = rid+","+eh.playerId+","+eh.exp+","+eh.trialListId+","+eh.seriesNo;

	
	OptimumExplained oe = null;
	if (weWantFitting) {
	    oe =  analyzeSection(eh.playerId, y, p0, null);
		
	    if (oe!=null) {
		//	 sumHeader = "#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z";
		line += "," +  mkYString(y)+"," + oe.toCsvString();
	    }
	}

	if (weWantTiming) {
	    TimingStats ts = new TimingStats(section);
	    line += "," + ts.toString();
	}
	
	wsum.println(line);

	
	return oe;
    }

    /** Used to count episodes, moves, and seconds in a player's interaction
	with a rule set */
    private static class TimingStats {

	final int episodes, moves;
	final long msec;

	private static NumberFormat df = new DecimalFormat("0.000");
	/**  "episodes,moves,sec" */
	public String toString() {
	    return "" + episodes + "," + moves + "," + df.format(msec * 0.001);
	}
	
	TimingStats(Vector<TranscriptManager.ReadTranscriptData.Entry> section) {
	    long minMsec=section.get(0).timestamp().getTime();
	    long maxMsec=minMsec;
	    moves = section.size();
	    String lastEid = null;
	    int ne = 0;
	    for(TranscriptManager.ReadTranscriptData.Entry e: section) {
		if (!e.eid.equals(lastEid)) {
		    lastEid = e.eid;
		    ne++;
		}
		long t = e.timestamp().getTime();
		if (t<minMsec) minMsec = t;
		if (t>maxMsec) maxMsec = t;
	    }
	    episodes = ne;
	    msec = maxMsec - minMsec;
	}
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
