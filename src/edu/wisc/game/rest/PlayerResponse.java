package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;

import javax.persistence.*;

import javax.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.reflect.JsonReflect;


/** The HashMap capability is used for debugging info in debug mode */
public class PlayerResponse extends ResponseBase {
  
    boolean newlyRegistered;
    public boolean getNewlyRegistered() { return newlyRegistered; }
    @XmlElement
    void setNewlyRegistered(boolean _newlyRegistered) { newlyRegistered = _newlyRegistered; }

    private String trialListId;
    public String getTrialListId() { return trialListId; }
    //    @XmlElement
    //void setTrialListId(String _trialListId) { trialListId = _trialListId; }

    private TrialList trialList = null;
    public TrialList getTrialList() { return trialList; }
 
    
    /** Only used in debug mode */
    private PlayerInfo playerInfo=null; 
    public PlayerInfo getPlayerInfo() { return playerInfo; }
    //    @XmlElement
    //    void setPlayerInfo(PlayerInfo _playerInfo) { playerInfo = _playerInfo; }

    boolean alreadyFinished = false;
    /** True if this player has finished all episodes he could play.
	This means that the most recent episode has been completed,
	and no more new episodes can be created.
    */
    public boolean getAlreadyFinished() { return alreadyFinished; }
  
    
    private String completionCode = null;
    public String getCompletionCode() { return completionCode; }
  
    

    PlayerResponse(String pid, String exp) {
	this(pid, exp, false);
    }
    
    PlayerResponse(String pid, String exp, boolean debug) {
	if (exp!=null && (exp.equals("") || exp.equals("null"))) exp=null;

	EntityManager em = Main.getNewEM();
	
	try {

	    Logging.info("PlayerResponse(pid="+ pid+", exp="+exp+")");
	    PlayerInfo x = findPlayerInfo(em, pid);
	    if (debug) playerInfo=x;
	    
	    setErrmsg("Debug: pid="+pid+"; Retrieved x="+x);
	    setNewlyRegistered(x==null);
	    if (x!=null) {  // existing player
		Logging.info("Found existing player=" + x);
		trialListId = x.getTrialListId();		
		trialList  = new TrialList(x.getExperimentPlan(), trialListId);
		alreadyFinished = x.alreadyFinished();
		completionCode = x.getCompletionCode();
	    } else { // new player
		x = new PlayerInfo();
		x.setDate( new Date());
		x.setPlayerId(pid);
		if (exp==null) exp= TrialList.extractExperimentPlanFromPlayerId(pid);
		x.setExperimentPlan(exp);
		assignRandomTrialList(x);
		trialListId = x.getTrialListId();
		em.getTransaction().begin();
		em.persist(x);
		em.flush(); // to get the new ID in
		em.getTransaction().commit();
		Logging.info("Persisted new player=" + x);
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
	    try {	    em.close();} catch(Exception ex) {}
	    Logging.info("PlayerResponse(pid="+ pid+", exp="+exp+"), returning:\n" +
			 JsonReflect.reflectToJSONObject(this, true));
	}
    }

    /** Server's local cache, used to reduce database calls */
    private static HashMap<String, PlayerInfo> allPlayers = new HashMap<String, PlayerInfo>();
        

    /** Find the matching record for a player. First looks it up in the local cache; then, if not found, in the SQL database.
	@em The EntityManager to use, if needed. If null is given, the EM will
	be created when needed, and then closed, so that the returned object will be detached.
	
	@return The PlayerInfo object with the matching name, or null if none is found */
    static PlayerInfo findPlayerInfo(EntityManager em, String pid) {

	PlayerInfo x = allPlayers.get(pid);
	if (x!=null) return x;

	boolean mustClose=(em==null);
	if (mustClose) em=Main.getNewEM();
	
	synchronized(em) {

	Query q = em.createQuery("select m from PlayerInfo m where m.playerId=:c");
	q.setParameter("c", pid);
	List<PlayerInfo> res = (List<PlayerInfo>)q.getResultList();
	if (res.size() != 0) {
	    x = res.iterator().next();
	} else {
	    return null;
	}
	}

	if (mustClose) { em.close(); em=null; }
	allPlayers.put(pid,x); // save in a local cache for faster lookup later
	x.restoreTransientFields(); // make it ready to use
	for(EpisodeInfo epi: x.getAllEpisodes())  {
	    epi.cache();
	}
	return x;
    }    

    /** Uses the database to balance assignments to different lists fairly precisely.
	This is done when a player is first entering the system.
	@param x It already has the experiment plan set, but the specific trial list within that experiment needs to be choosen and set now
     */
    private void assignRandomTrialList(PlayerInfo x) throws IOException {
	String exp = x.getExperimentPlan();
	Vector<String> lists = TrialList.listTrialLists(x.getExperimentPlan());
	if (lists.size()==0)  throw new IOException("Found no CSV files in the trial list directory for experiment plan=" + exp);
	HashMap<String,Integer> names = new HashMap<>();
	for(String key: lists) names.put(key,0);

	EntityManager em  = Main.getEM();
	synchronized(em) {
	    Query q = em.createQuery("select x.trialListId, count(x) from PlayerInfo x where x.experimentPlan=:e  group by x.trialListId");
	    q.setParameter("e", exp);
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
	for(String name: lists) {
	    if (minName==null || names.get(name)<names.get(minName)) minName=name;
	}

	x.setTrialListId(minName);
	trialList  = new TrialList(exp, minName);
	x.initSeries(trialList);
    }
    
}

