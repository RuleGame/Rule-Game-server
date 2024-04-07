package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

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
	mergePlayerInfo( em, aem, uidMap );

	System.out.println("--- Data stats after the merge: ----");
	System.out.println(reportTableSizes(em));
	System.out.println(reportTableSizes(aem));

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

    static void //HashMap<Long,User>
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
	System.out.println("Found " + nap0 + " User entries in added database, " + nmp0 + " entries in the merge db");
	for(PlayerInfo ap: aPlayers) {
	    String pid = ap.getPlayerId();
	    Query q = em.createQuery("select p from PlayerInfo p where p.playerId=:p");

	    q.setParameter("p", pid);
	    PlayerInfo m = null;
	    try {
		m = (PlayerInfo)q.getSingleResult();
	    } catch(NoResultException ex) { 
		// no such player
		m = null;
	    }  catch(NonUniqueResultException ex) {
		// this should not happen, as we have an (informal) uniqueness constraint
		System.out.println("Non-unique playerInfo entry in the merge database for playerId='"+pid+"'! Skipping addition");
		pCntSkip ++;
		continue;
	    }


	    if (m==null) {
		// copy the entry to the merge database
		Logging.info("Copying player to the merge db: " + ap);       
		PlayerInfo p = new PlayerInfo();		
		copyFields(playerReflect, ap, p);
		User u = ap.getUser();
		if (u!=null) p.setUser( uidMap.get( u.getId()));

		for(EpisodeInfo ae: ap.getAllEpisodes()) {
		    EpisodeInfo e = new EpisodeInfo();
		    copyFields(episodeReflect, ae, e);
		    p.addEpisode(e);
		}
 
		
		em.getTransaction().begin();
		PlayerInfo om = em.merge(p);
		em.getTransaction().commit();	    
		//uidMap.put( id, om);
		pCntNew ++;
	    } else {
		//Logging.info("Found an existing copy: " + au + " --> " + m);
		pCntExisting ++;
	    }

	    
	}
	System.out.println("Merging in " + nap0 + " User entries: " + pCntExisting + " entries already were in the merge database, " + pCntNew + " entries created, " + pCntSkip + " skipped due to problems");

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
    
}
