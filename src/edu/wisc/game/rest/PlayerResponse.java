package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import jakarta.json.*;

import javax.persistence.*;

import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.reflect.JsonReflect;
import edu.wisc.game.parser.RuleParseException;


/** The object returned by the /player call. This is the call that's
    used at the beginning (or resumption) of a series, to create
    a new player entry in the database or find an existing one.
 */
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

    /** Typically, this is the same player ID which was used in the
	/player call. However, there is a mode (with a repeat user ID in use)
	when the /player call will create a player ID, and return it via this field.
     */
    private String playerId;
    public String getPlayerId() { return playerId; }
    @XmlElement
    public void setPlayerId(String _playerId) { playerId = _playerId; }

    
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

    /** This is mostly used so that the caller can check if a re-used
	player is in the right plan */
    private String experimentPlan;
    public String getExperimentPlan() { return experimentPlan; }

    
    private boolean isCoopGame;
    private boolean isAdveGame;
    /** Are we playing a cooperative two-player game (2PG)? */
    public boolean getIsCoopGame() { return isCoopGame; }
    //    @XmlElement   
    //    public void setIsCoopGame(boolean _isCoopGame) { isCoopGame = _isCoopGame; }
    /** Are we playing an adversarial 2PG? */
    public boolean getIsAdveGame() { return isAdveGame; }
    //@XmlElement
    //public void setIsAdveGame(boolean _isAdveGame) { isAdveGame = _isAdveGame; }
    /** Are we playing a 2PG? (A 2PG may be adversarial or cooperative) */
    public boolean getIsTwoPlayerGame() {
	return getIsCoopGame() || getIsAdveGame();
    }

    private boolean needChat;
    /** Do we need a between-player chat element in the GUI? (In 2PG only,
	based on para.chat */
    public boolean getNeedChat() { return needChat; }

    static private final Pattern repeatUserPat = Pattern.compile("^RepeatUser-([0-9]+)-");

    PlayerResponse(String pid, String exp, int uid) {
	this(pid, exp, uid, false);
    }


    /** Registers a new player, or finds an existing player record.
      
	@param pid The player ID. If it is not supplied, the uid
	must be supplied, and this method will create a semi-random 
	player ID.
	@param uid The numeric ID of the repeat user who creates this 
	playerId for himself. If negative, this parameter is ignored,
	as this is an M-Turker etc, and not a repeat user.
    */
    PlayerResponse(String pid, String exp, int uid, boolean debug) {
	exp = regularize(exp);
	pid = regularize(pid);
	Date now = new Date();
	
	Matcher m;
	EntityManager em =null;
	if (pid==null) { 	    
	    if (uid<0) {
		hasError("Neither player Id nor user Id have been provided");
		return;	
	    } else {
		// Creating a PID dynamically 
		pid = "RepeatUser-" + uid + "-" +  Episode.randomWord(6) + "-" + Episode.sdf.format(now);
	    }
	} else if (uid<0 && (m = repeatUserPat.matcher(pid)).find()) {
	    // This is a temporary hack, for when the GUI client does not
	    // support the uid parameter
	    uid = Integer.parseInt(m.group(1));
	}

	if (badPid(pid)) hasError("Invalid player Id = '"+pid+"'. Player IDs may only contain alphanumeric characters, underlines, and hyphens.");

	

	try {

	    Logging.info("PlayerResponse(pid="+ pid+", exp="+exp+")");
	    if (pid==null || pid.trim().equals("") || pid.equals("null")) throw new IOException("Missing or invalid playerId");
	    PlayerInfo x = findPlayerInfoAlreadyCached(pid);
	    if (debug) playerInfo=x;
	    
	    setErrmsg("Debug: pid="+pid+"; Retrieved x="+x);
	    setNewlyRegistered(x==null);
	    if (x!=null) {  // existing and already cached player
		setupResponseForExistingPlayer(x, pid, exp, uid);
	    } else synchronized (lock) {
		    // keep this part synchronized, to avoid creating 2 entries if the player clicks twice
		    em = Main.getNewEM();
	
		    x = findPlayerInfo(em, pid);  // either a new player, or one restored from the database
		    if (debug) playerInfo=x;
		    
		    
		    if (x!=null) {  // existing  player
			setupResponseForExistingPlayer(x, pid, exp, uid);
		    } else {
			// new player
			x = new PlayerInfo();
			x.setDate(now);
			x.setPlayerId(pid);
			
			if (uid>=0) {
			    User user = (User)em.find(User.class, uid);
			    if (user==null) {
				String msg="Invalid user id=" + uid+". No user exists with that ID";
				hasError(msg);
				return;
			    }
			    x.setUser(user);
			}
			if (exp==null) {
			    //exp= TrialList.extractExperimentPlanFromPlayerId(pid);
			    hasError("Attempt to register a new plan without specifying an experiment plan");
			    return;
			}
			x.setExperimentPlan(exp);		
			assignRandomTrialList(x);
			x.postLoad1();
			// Check if it's a pair game
			x.initPairing();
			Pairing.newPlayerRegistration(x);
			em.getTransaction().begin();
			em.persist(x);
			em.flush(); // to get the new ID in
			em.getTransaction().commit();
			Logging.info("Persisted new player=" + x);
		    }
		    allPlayers.put(pid,x);
		}
	    
	    playerId = x.getPlayerId();
	    experimentPlan = x.getExperimentPlan();
	    trialListId = x.getTrialListId();	
	    isCoopGame = x.isCoopGame();
	    isAdveGame = x.isAdveGame();
	    needChat = x.getNeedChat();

		
	    setError(false);
	    setErrmsg("Debug:\n" + x.report());

	} catch(Exception ex) {
	    Logging.info("PlayerResponse.catch: " + ex);
	    StringWriter sw = new StringWriter();
	    ex.printStackTrace(new PrintWriter(sw));
	    String s = sw.toString();
	    Logging.warning(s);

	    //System.err.println(ex);
	    //ex.printStackTrace(System.err);
	    setError(true);
	    setErrmsg(ex.toString());
	} finally {
	    try { if (em!=null)	    em.close();} catch(Exception ex) {}
	    Logging.info("PlayerResponse(pid="+ pid+", exp="+exp+"), returning:\n" +
			 JsonReflect.reflectToJSONObject(this, true));
	}
    }

    private void setupResponseForExistingPlayer(PlayerInfo x, String pid, String exp, int uid) throws IOException, IllegalInputException {
	Logging.info("Found existing player=" + x + ", with plan=" + x.getExperimentPlan());
	trialList  = new TrialList(x.getExperimentPlan(), x.getTrialListId());		
	alreadyFinished = x.alreadyFinished();
	completionCode = x.getCompletionCode();
	
	String msg=null;
	if (exp!=null  && !x.getExperimentPlan().equals(exp)) {
	    msg = "Cannot play experiment plan '" + exp + "' with playerId=" + pid + ", because that playerId is already assigned to experiment plan '" + x.getExperimentPlan() +"'";
	    
	} else 	if (uid>=0 && x.getUser()==null) {
	    msg = "Cannot use playerId=" + pid + " with a user ID=" + uid +", because this playerId is  already created without a user ID";
	} else if (uid<0 && x.getUser()!=null) {
	    msg = "Cannot use playerId=" + pid + " without a user ID, because this playerId is  already created with user ID=" + x.getUser().getId();
	} else if (uid>=0 &&  x.getUser()!=null && uid!=x.getUser().getId()) {
	    msg = "Cannot use playerId=" + pid + " without user ID="+uid+", because this playerId is  already created with user ID=" + x.getUser().getId();    
	}		
	if (msg!=null) {
	    hasError(msg);
	    return;
	}		

    }
    
    /** Server's local cache, used to reduce database calls, and to store
	"transient" info (such as the transripts of epsiodes)
     */
    private static HashMap<String, PlayerInfo> allPlayers = new HashMap<String, PlayerInfo>();
        
    private static String lock = "lock";

    
    /** Checks if the matching record is already cached. This method
	is used so that we won't need to synchronize so much. However, if
	it returns null, one will need to go for the full findPlayer(),
	which will also look into the database, and has a synchronized block.
	@return cached matched record, or null if none is found.
    */
    static PlayerInfo findPlayerInfoAlreadyCached(String pid) { //throws IOException, IllegalInputException, ReflectiveOperationException, RuleParseException {
	return allPlayers.get(pid);
    }
    
    /** Find the matching record for a player, in the cache of the
	database. First looks it up in the local cache; then, if not
	found, in the SQL database. The main block is synchronized, to
	ensure that we don't put duplicate copies of a database entry
	into the cache.
	
	@param em The EntityManager to use, if needed. If null is
	given, the EM will be created when needed, and then closed, so
	that the returned object will be detached.
	
	@return The PlayerInfo object with the matching name, or null if none is found */
    public static PlayerInfo findPlayerInfo(EntityManager em, String pid) throws IOException, IllegalInputException, ReflectiveOperationException, RuleParseException {
	if (badPid(pid)) throw new  IllegalInputException("Player ID contains illegal characters: '"+pid+"'");

	//-- Maybe it's already in cache, and is thus fully ready to use?
	PlayerInfo x =  findPlayerInfoAlreadyCached( pid);
	if (x!=null) return x;

	//-- Not cached; we next check is it is in the SQL database
	//-- and can be restored from there (and cached).
	//-- This part (including a preliminary cache lookup) must be
	//-- static-synchronized, to avoid accidentally creating 2 Java
	//-- objects in cache for the same player ID.
	
	synchronized(lock) {
	    x = allPlayers.get(pid); // double-checking, to avoid race conditions
	    if (x!=null) return x;
	    
	    boolean mustClose=(em==null);
	    if (mustClose) em=Main.getNewEM();
	    try {		
		Query q = em.createQuery("select m from PlayerInfo m where m.playerId=:c");
		q.setParameter("c", pid);
		//-- Note that this will trigger @PostLoad methods on the retrieved PlayerInfo objects 
		List<PlayerInfo> res = (List<PlayerInfo>)q.getResultList();
		if (res.size() != 0) {
		    x = res.iterator().next();
		} else {
		    return null;
		}
	    } finally {
		if (mustClose) { em.close(); em=null; }
	    }
	    allPlayers.put(pid,x); // save in a local cache for faster lookup later
	    x.restoreTransientFields(); // make it ready to use
	    x.postLoad1(); // safe to do it now
	    for(EpisodeInfo epi: x.getAllEpisodes())  {
		epi.cache();
	    }
	}
	return x;
    }    

    /** Uses the database to balance assignments to different lists fairly precisely.
	This is done when a player is first entering the system.
	@param x It already has the experiment plan set, but the specific trial list within that experiment needs to be choosen and set now
     */
    private void assignRandomTrialList(PlayerInfo x) throws IOException, IllegalInputException, ReflectiveOperationException, RuleParseException {
	String exp = x.getExperimentPlan();

	String minName=chooseRandomTrialList(exp, 1.0, false);
	x.setTrialListId(minName);
	trialList  = new TrialList(exp, minName);
	x.initSeries(trialList);
    }
    
    /** Picks a suitable trial list for a new player in a given
	experiment plan.  The intent is to provide "balancing" between
	trial lists, i.e. to pick such a trial list that the
	number of "non-trivial" planners (completers, plus those who
	have started recently, and maybe are still playing) assigned
	to each trial list in the experiment plan is roughly the same.
	
	@param exp The name of the experiment plan
     */
    private static synchronized String chooseRandomTrialList(String exp, double hrs, boolean debug) throws IOException, IllegalInputException, ReflectiveOperationException, RuleParseException {

	
	
	Vector<String> lists = TrialList.listTrialLists(exp);
	if (lists.size()==0)  throw new IOException("Found no CSV files in the trial list directory for experiment plan=" + exp);
	if (lists.size()==1)  return lists.get(0);
	
	HashMap<String,Integer> names = new HashMap<>();
	for(String key: lists) names.put(key,0);

	long msecAgo = (long)(hrs * 3600.0 * 1000.0);
	Date recent = new Date(  (new Date()).getTime() - msecAgo);

	EntityManager em  = Main.getEM();
	synchronized(em) {
	    Query q = em.createQuery("select p.trialListId, count(p) from PlayerInfo p where p.experimentPlan=:e and (p.completionCode is not null or p.date > :recent) group by p.trialListId");
	    q.setParameter("e", exp);
	    q.setParameter("recent", recent);
	    List list = q.getResultList();
	    for(Object o: list) {
		Object[] z = (Object[]) o;
		String name = (String)(z[0]);
		if (names.containsKey(name)) {
		    int cnt = (int)((Long)(z[1])).longValue();
		    names.put(name,cnt);
		    if (debug) System.out.println("C+R for (" +name+")=" + cnt);
		}		
	    }
	}

	HashMap<String,Integer> defects = TrialList.readDefects(exp);
	if (debug) System.out.println("Read "+defects.size()+" entries from the defect file");
	
	// The defect table may tell us to ignore a certain number
	// of "completers" in some trial lists
	for(String name: defects.keySet()) {
	    int d = defects.get(name);
	    if (names.containsKey(name)) {
		int cnt = names.get(name) - d;
		names.put(name, cnt);
		if (debug) System.out.println("C+R-D for (" +name+")=" + cnt);
	    } else {
		System.err.println("Ignoring defect(" +name+")=" + d +"; non-existent trial list name!");		
	    }		      
	}
	
	String minName=null;
	for(String name: lists) {
	    if (minName==null || names.get(name)<names.get(minName)) minName=name;
	}
	return minName;
    }


    /** The player ID must be a non-empty string consisting of allowed
	characters (letters, digits, _ - )
    */
    private static boolean badPid(String s) {
	if (s.length()==0) return true;
	s = s.toLowerCase();
	for(int j=0; j<s.length(); j++) {
	    char c = s.charAt(j);
	    if (!(c>='a' && c<='z' || c>='0' && c<='9' || c=='_' || c=='-')) return true;
	}
	return false;
			       
    }
					     
	
    
    /** Handy testing */
    public static void main(String[] argv) throws Exception {
	double hrs = Double.parseDouble(argv[0]);
	System.out.println("Looking back at hrs=" + hrs);
	for(int j=1; j<argv.length; j++) {
	    String exp = argv[j];
	    System.out.println("Plan=" +exp);
	    String minName=chooseRandomTrialList(exp, hrs, true);
	    System.out.println("If a player were to register now, it would be assigned to trialList=" + minName);
	}
	
    }
    
}

