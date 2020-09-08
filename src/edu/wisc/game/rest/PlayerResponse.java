package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;

import javax.persistence.*;

//import org.apache.openjpa.persistence.jdbc.*;

import javax.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;


/** The HashMap capability is used for debugging info in debug mode */
public class PlayerResponse extends ResponseBase {
  
    boolean newlyRegistered;
    public boolean getNewlyRegistered() { return newlyRegistered; }
    @XmlElement
    public void setNewlyRegistered(boolean _newlyRegistered) { newlyRegistered = _newlyRegistered; }

    private String trialListId;
    public String getTrialListId() { return trialListId; }
    @XmlElement
    public void setTrialListId(String _trialListId) { trialListId = _trialListId; }

    /** Only used in debug mode */
    private PlayerInfo playerInfo=null; 
    public PlayerInfo getPlayerInfo() { return playerInfo; }
    @XmlElement
    public void setPlayerInfo(PlayerInfo _playerInfo) { playerInfo = _playerInfo; }

    /** Will be closed in the "finally" clause in the constructor */
    //    @PersistenceContext
    //    EntityManager em = Main.getEM();


    PlayerResponse(String pid) {
	this(pid, false);
    }
    
    PlayerResponse(String pid, boolean debug) {
	try {
	    PlayerInfo x = findPlayerInfo(pid);
	    if (debug) playerInfo=x;
	    
	    setErrmsg("Debug: pid="+pid+"; Retrieved x="+x);
	    setNewlyRegistered(x==null);
	    if (x!=null) {  // existing player
		setTrialListId( x.getTrialListId());		
	    } else { // new player
		x = new PlayerInfo();
		x.setPlayerId(pid);
		x.setDate( new Date());
		String ti = chooseRandomTrialList(x, pid);
		setTrialListId( ti);
		x.setTrialListId(ti);
		Main.persistObjects(x);
		allPlayers.put(pid,x);
	    }

	    setError(false);
	    setErrmsg("Debug:\n" + x.report());

	} catch(Exception e) {
	    System.err.println(e);
	    e.printStackTrace(System.err);
	    setError(true);
	    setErrmsg(e.toString());
	} finally {
	    //em.close();
	}
	
    }

    /** Server's local cache, used to reduce database calls */
    private static HashMap<String, PlayerInfo> allPlayers = new HashMap<String, PlayerInfo>();
        

    /** Find the matching record for a player.
	@return The PlayerInfo object with the matching name, or null if none is found */
   static PlayerInfo findPlayerInfo(String pid) {
	return findPlayerInfo(null, pid);	
    }

    /** Looks up a PlayerInfo object for the specified player in the local cache, or
	if not found, in the MySQL database.
	@param em The EntityManager, if already created. If one is not available yet, null can be passed; in this case one will be created locally and subsequently closed.
     */
    static PlayerInfo findPlayerInfo( EntityManager em, String pid) {

	PlayerInfo x = allPlayers.get(pid);
	if (x!=null) return x;

	EntityManager lem  = null;
	if (em==null) em=lem=Main.getEM();

	
	synchronized(em) {
	try {

	Query q = em.createQuery("select m from PlayerInfo m where m.playerId=:c");
	q.setParameter("c", pid);
	List<PlayerInfo> res = (List<PlayerInfo>)q.getResultList();
	if (res.size() != 0) {
	    x = res.iterator().next();
	} else {
	    return null;
	}
	} finally{
	    //	    if (lem!=null) lem.close();
	}
	allPlayers.put(pid,x); // save in a local cache for faster lookup later
	x.restoreTransientFields(); // make it ready to use
	for(EpisodeInfo epi: x.getAllEpisodes())  {
	    epi.cache();
	}
	return x;


	}
    }    

    /** Uses the database to balance assignments to different lists fairly precisely.
	This is done when a player is first entering the system.
     */
    private String chooseRandomTrialList(PlayerInfo x, String playerId) throws IOException {
	File base =  TrialList.dirForTrialLists(playerId);
	//try {
	if (!base.isDirectory()) throw new IOException("No such directory: " + base);
	if (!base.canRead()) throw new IOException("Cannot read directory: " + base);
	//	Vector<File> v = new Vector<>();
	HashMap<String,Integer> names = new HashMap<>();
	final String suff = ".csv";
	for(String s: base.list()) {
	    File f = new File(base, s);		
	    if (!f.isFile()) continue;
	    if (!s.endsWith(suff)) continue;
	    //	v.add(f);
	    String key=s.substring(0, s.length()-suff.length());
	    names.put(key,0);
	}
	if (names.size()==0)  throw new IOException("Found no CSV files in directory: " + base);

	EntityManager em  = Main.getEM();
	synchronized(em) {
	// FIXME: could restrict by the experiment plan, if we had a table for that
	Query q = em.createQuery("select x.trialListId, count(x) from PlayerInfo x group by  x.trialListId");

	List list = q.getResultList();
	for(Object o: list) {
	    Object[] z = (Object[]) o;
	    String name = (String)(z[0]);
	    if (names.containsKey(name)) {
		int cnt = (int)((Long)(z[1])).longValue();
		names.put(name,cnt);
	    }		
	}
	}
	
	String minName=null;
	for(String name: names.keySet()) {
	    if (minName==null || names.get(name)<names.get(minName)) minName=name;
	}

	File g = new File(base, minName + suff);		
	TrialList trialList  = new TrialList(g);
	x.initSeries(trialList);


	
	return minName;
    }
    
}

