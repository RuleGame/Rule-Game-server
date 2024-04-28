package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;
import java.nio.file.FileSystems;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.parser.RuleParseException;

public class MergeDatasets {


    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	//	System.err.println("For usage info, please see:\n");
	//System.err.println("http://rulegame.wisc.edu/w2020/analyze-transcripts.html");
	System.err.println("Usage: MergeDatasets -config /opt/w2020/merge.conf -addConfig someImportedDataset.conf");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    
    public static void main(String[] argv) throws Exception {
	String config = null, addConfig  = null;
	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];
	    if (j+1< argv.length && a.equals("-config")) {
		config = argv[++j];
	    } else if (j+1< argv.length && a.equals("-addConfig")) {
		addConfig = argv[++j];
	    }
	}

	if (config == null) usage("Must specify the config file for the destination data set (the merged-data data set)");
	if (addConfig == null) usage("Must specify the config file for the data set that is to be added (merged-in)");
	
	// The destination data sets: Instead of the master conf file in /opt/w2020, use the customized one
	MainConfig.setPath(config);

	String s0 = MainConfig.getString("JDBC_DATABASE", null);
	if (!"merge".equals(s0)) {
	    //	    usage("Wrong destination database name ('"+s0+"'). To avoid accidental data corruption, we require that the destination database be named 'merge'");
	}


	MainConfig addConf = new MainConfig(addConfig);

	System.out.println("addConfig=" + addConfig);
	System.out.println("addConf=" + addConf);
	
	String s1 = addConf.doGetString("JDBC_DATABASE", null);
	System.out.println("Merging data from database " + s1 + " into database " + s0);

	/** Maps the numeric uid of a User object in the new database to
	    one in the merged database. */
	//HashMap<Integer,Integer> uidMap = new HashMap<>();

	Main fromMain = new Main(addConf);
	System.out.println("fromMain=" + fromMain);
	
	EntityManager aem = fromMain.doGetNewEM();
	EntityManager em = Main.getNewEM();

	System.out.println("Merging to:\n" + showEM(em));
	System.out.println("Merging from:\n" + showEM(aem));

	System.out.println("--- Data stats before the merge: ----");
	System.out.println(reportTableSizes(em));
	System.out.println(reportTableSizes(aem));

	HashMap<Long,User> uidMap = mergeUser(em, aem);
	HashMap<String,String> pid2mpid = mergePlayerInfo( em, aem, uidMap );

	System.out.println("--- Database stats after the merge: ----");
	System.out.println(reportTableSizes(em));
	System.out.println(reportTableSizes(aem));


	System.out.println("Merging from addConf=" + addConf);
	mergeFiles(addConf, pid2mpid);
	
    }

	/* Merges the user data from aem to the database in em.
	  <pre>
	  mysql> select * from User where id < 20;
+----+---------------------+-------------------+-----------------------------+----------+----------------------------------+
| id | date                | email             | idCode                      | nickname | digest                           |
+----+---------------------+-------------------+-----------------------------+----------+----------------------------------+
|  1 | 2021-10-20 19:03:59 | vmenkov@gmail.com | user-20211020-140359-986RS5 | vmenkov  | 670B14728AD9902AECBA32E22FA4F6BD |
|  2 | 2021-10-21 00:40:01 | NULL              | anon-20211020-194001-51BXDI | NULL     | NULL                             |
|  3 | 2021-10-21 00:40:27 | NULL              | anon-20211020-194027-BVZRIY | NULL     | NULL                             |
|  4 | 2021-10-21 21:23:34 | NULL              | anon-20211021-162333-SZCXAT | NULL     | NULL                             |
|  5 | 2021-10-21 21:41:32 | NULL              | user-20211021-164132-MUKY0Z | pbk      | NULL                             |
|  6 | 2021-10-22 02:34:15 | pbk2              | user-20211021-213415-ICAOND | NULL     | NULL                             |
|  7 | 2021-10-22 04:55:48 | test@example.com  | user-20211021-235547-JS5RSJ | testing  | NULL                             |
|  8 | 2021-10-22 21:30:26 | NULL              | user-20211022-163025-1VHKQW | pbkOCT22 | NULL                             |
</pre>
	*/

    static HashMap<Long,User> mergeUser(EntityManager em, EntityManager aem) {
	HashMap<Long,User> uidMap = new HashMap<>();
	System.out.println("Working on table User");
	
	Query query = em.createQuery("select u from User u");
	List<User> mUsers = (List<User>)query.getResultList();
	int nmu0 = mUsers.size();

	query = aem.createQuery("select u from User u");
	List<User> aUsers = (List<User>)query.getResultList();

	int nau0 = aUsers.size();
	int  uCntExisting=0, uCntNew=0, uCntSkip=0;
	System.out.println("Found " + nau0 + " User entries in added database, " + nmu0 + " entries in the merge db");
	for(User au: aUsers) {
	    String idc = au.getIdCode();
	    if (idc==null) Logging.warning("Ignoring User with no idcode: " + au);

	    Reflect userReflect = Reflect.getReflect(User.class);
	    
	    Query q = em.createQuery("select m from User m where m.idCode=:c");
	    long id = au.getId();
	    q.setParameter("c", idc);
	    User m = null;
	    try {
		m = (User)q.getSingleResult();
	    } catch(NoResultException ex) { 
		// no such user
		m = null;
	    }  catch(NonUniqueResultException ex) {
		// this should not happen, as we have a uniqueness constraint
		System.out.println("Non-unique user entry in the merge database for User.idcode='"+idc+"'! Skipping addition");
		uCntSkip ++;
		continue;
	    }
	    if (m==null) {
		//Logging.info("Copying user to the merge db: " + au);
		// copy the entry to the merge database
		// aem.detach(au);

		User u = new User();
		copyFields(userReflect, au, u);
		em.getTransaction().begin();
		User om = em.merge(u);
		//Logging.info("Now, u=" + u +", om=" + om);
		em.getTransaction().commit();	    
		uidMap.put( id, om);
		uCntNew ++;
	    } else {
		//Logging.info("Found an existing copy: " + au + " --> " + m);
		uidMap.put( id, m);
		uCntExisting ++;
	    }
	}

	System.out.println("Merging in " + nau0 + " User entries: " + uCntExisting + " entries already were in the merge database, " + uCntNew + " entries created, " + uCntSkip + " skipped due to problems");
	return uidMap;
    }

    //private static final DateFormat sdf1 = new SimpleDateFormat("yyyyMMdd.HHmmss.SSS");
    private static final DateFormat sdf1 = new SimpleDateFormat("yyyyMMdd.HHmmss");

    /** @return "originalPid.date.time" */
    private static String mkMergePid(PlayerInfo p) {
	return 	p.getPlayerId() + "." + sdf1.format(p.getDate());
    }
	

    /** Merges player records. Since PlayerInfo entries don't really have
	a cross-server-unique IDs in them, we use "playerId.date" combination
	as a pseudo-unique key, and store it in the PlauerId column of the
	merged database.
     */
    static HashMap<String,String>
    //void //HashMap<Long,User>
	mergePlayerInfo(EntityManager em, EntityManager aem,
			HashMap<Long,User> uidMap ) {
	System.out.println("Working on table PlayerInfo");
	Reflect playerReflect = Reflect.getReflect(PlayerInfo.class);
	Reflect episodeReflect = Reflect.getReflect(EpisodeInfo.class);
	    
	Query query = em.createQuery("select p from PlayerInfo p");
	List<PlayerInfo> mPlayers = (List<PlayerInfo>)query.getResultList();
	int nmp0 = mPlayers.size();

	query = aem.createQuery("select p from PlayerInfo p");
	List<PlayerInfo> aPlayers = (List<PlayerInfo>)query.getResultList();
	int nap0 = aPlayers.size();

	int  pCntExisting=0, pCntNew=0, pCntSkip=0, pCntChanged=0;
	int meCnt=0, neCnt = 0;
	System.out.println("Found " + nap0 + " User entries in added database, " + nmp0 + " entries in the merge db");


	HashMap<String,String> pid2mpid = new HashMap<>();
	for(PlayerInfo ap: aPlayers) {
	    String mpid = mkMergePid( ap );
	    pid2mpid.put( ap.getPlayerId(), mpid);
	    Query q = em.createQuery("select p from PlayerInfo p where p.playerId=:p");

	    q.setParameter("p", mpid);
	    PlayerInfo m = null;
	    try {
		m = (PlayerInfo)q.getSingleResult();
	    } catch(NoResultException ex) { 
		// no such player
		m = null;
	    }  catch(NonUniqueResultException ex) {
		// this should not happen, as we have an (informal) uniqueness constraint
		System.out.println("Non-unique playerInfo entry in the merge database for playerId='"+mpid+"'! Skipping addition");
		pCntSkip ++;
		continue;
	    }

	    //	    final boolean testing=true;
	    
	    if (m==null) {
		// copy the entry to the merge database (with the merge PID)
		//Logging.info("Copying player to the merge db: " + ap);       
		PlayerInfo p = new PlayerInfo();		
		copyFields(playerReflect, ap, p);
		p.setPlayerId( mpid);
		User u = ap.getUser();
		if (u!=null) p.setUser( uidMap.get( u.getId()));

		int cnt=0;
		for(EpisodeInfo ae: ap.getAllEpisodes()) {
		    EpisodeInfo e = new EpisodeInfo();
		    copyFields(episodeReflect, ae, e);
		    p.addEpisode(e);
		    cnt++;
		    meCnt++;
		    //if (testing && cnt>=3) break;
		}
 
		
		em.getTransaction().begin();
		PlayerInfo om = em.merge(p);
		em.getTransaction().commit();	    
		//uidMap.put( id, om);
		pCntNew ++;
	    } else {
		//Logging.info("Found an existing copy: " + au + " --> " + m);
		// See if some more episodes need to be added
		HashSet<String> storedEpisodes = new HashSet<>();
		for(EpisodeInfo e: m.getAllEpisodes()) {
		    storedEpisodes.add( e.getEpisodeId());
		}

		int thisNeCnt = 0;
		for(EpisodeInfo ae: ap.getAllEpisodes()) {
		    if (storedEpisodes.contains(ae.getEpisodeId())) continue;
		    thisNeCnt++;
		    
		    EpisodeInfo e = new EpisodeInfo();
		    copyFields(episodeReflect, ae, e);
		    m.addEpisode(e);
		}
		if (thisNeCnt>0) {
		    // hoping that this saves the new episodes
		    em.getTransaction().begin();	    
		    em.getTransaction().commit();	        
		}

		neCnt += thisNeCnt;
		pCntExisting ++;
	    }

	    
	}
	System.out.println("Merging in " + nap0 + " player entries: " + pCntExisting + " entries already were in the merge database, " + pCntNew + " entries created, " + pCntSkip + " skipped due to problems. " + meCnt + " episodes in newly added players; " + neCnt + " episodes added to already existing players");

	return pid2mpid;
	
    }


    private static String showEM(EntityManager em) {
	Vector<String> v = new Vector<>();

	//String keys[] = em.getProperties().keySet()) {
	String keys[] = {"openjpa.ConnectionURL"};
	for(String key: keys) {
	    v.add(key + " ==> " + em.getProperties().get(key));
	}
	
	return Util.joinNonBlank("\n", v);
    }

    /** Reports on the size of some important tables in the given database */
    static private String reportTableSizes(EntityManager em) {
	Vector<String> v = new Vector<>();
	String k = "openjpa.ConnectionURL";
	String url = em.getProperties().get(k).toString();
	v.add("Size of tables in database "  + url + ":");

	String keys[] = {"User", "PlayerInfo", "Episode"};

	for(String t: keys) {
	    Query query = em.createQuery("select u from "+t+" u");
	    int n = query.getResultList().size();
	    v.add("Table " + t + ": " + n + " entries");
	}
	return Util.joinNonBlank("\n", v);
    }

    
    static void copyFields(Reflect reflect, Object o1, Object o2) {
	for(Reflect.Entry e: reflect.entries) {
	    if (e.name.equals("id")) continue;
	    Object val = null;
	    if (e.s==null) continue;
	    try {
		val = e.g.invoke(o1);
		if (val instanceof Number || val instanceof String
		    || val instanceof Date	    ) {
		    e.s.invoke(o2, val);
		}
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		throw new IllegalArgumentException( "ACCESS_ERROR");
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		throw new IllegalArgumentException( "INVOCATION_TARGET_ERROR");
	    }
	}
						   
    }

    /** Copies CSV files from the dataset-to-add's file store to the merge
	dataset's file store */
    private static void mergeFiles(MainConfig addConf, HashMap<String,String> pid2mpid) throws IOException {

	
	File fromRoot = addConf.doGetFile("FILES_SAVED", null);
	if (fromRoot==null) throw new IllegalArgumentException("The add-set config file does not specify the file store directory");
	System.out.println("Merging from fromRoot="+fromRoot);
	
	for(String sub: Files.Saved.mergeable) {
	    // GUESSES = "guesses",    BOARDS = "boards",	    TRANSCRIPTS = "transcripts",	    DETAILED_TRANSCRIPTS = "detailed-transcripts",
	    File toDir = Files.savedSubDir(sub);
	    File fromDir = new File(fromRoot, sub);
	    System.out.println("Merging from "+fromDir+" to " + toDir);
	    if (toDir.equals(fromDir)) throw new IllegalArgumentException("Cannot merge: source and destination are the same!");
	    if (!toDir.isDirectory())  throw new IllegalArgumentException("Cannot merge: destination directory does not exits: " + toDir);
	    if (!fromDir.isDirectory())  throw new IllegalArgumentException("Cannot merge: source directory does not exits: " + fromDir);
	    
	    File[] files = fromDir.listFiles();
	    Vector<String> v = new Vector<String>();

	    int newFileCnt=0, replacedFileCnt=0, keptFileCnt=0, allFilesCnt=0;
	    
	    for(File cf: files) {
		if (!cf.isFile()) continue;
		allFilesCnt++;
		String fname = cf.getName();

		String[] fcomp = fname.split("\\.");
		if (fcomp.length<2)  throw new IllegalArgumentException("Cannot parse file name=" + cf);
		String s = pid2mpid.get(fcomp[0]);
		if (s==null)  throw new IllegalArgumentException("Player id not known ("+fcomp[0]+"), in file name=" + cf);
		fcomp[0] = s;
		String mfname = String.join(".", fcomp);		
		File mfile = new File(toDir, mfname);
		
		java.nio.file.Path path = FileSystems.getDefault().getPath(cf.getPath()),
		    mpath = FileSystems.getDefault().getPath(mfile.getPath());

		if (!mfile.exists()) {		    // copy
		    java.nio.file.Files.copy(path, mpath);
		    newFileCnt++;
		} else { 		    // check sizes
		    if (mfile.length() < cf.length()) {
			java.nio.file.Files.copy(path, mpath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			replacedFileCnt++;
		    } else {
			keptFileCnt++;
		    }
		}
	    }
	    System.out.println("Out of " + allFilesCnt + " " + sub +" files in the added data set, added " + newFileCnt + " new files to the merge file store, updated " +replacedFileCnt+" files, already were there " + keptFileCnt + " files");
	    return;
	    
	}

    }
}
