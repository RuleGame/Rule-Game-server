package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.rest.TrialList;
import edu.wisc.game.rest.Files;
import edu.wisc.game.rest.PlayerResponse;
import edu.wisc.game.engine.RuleSet;
import edu.wisc.game.engine.AllRuleSets;
import edu.wisc.game.saved.*;

import edu.wisc.game.websocket.WatchPlayer;

/** A PlayerInfo object represent information about a player (what trial list he's in, what episodes he's done etc) stored in the SQL database. It is identiied by a playerId (a string). A humans playing the Rule Game may create just one PlayerInfo object (a single playerId), if he comes from the Mechanical Turk, or goes directly to the GUI Client URL; or he can create many such objects (each one with a particular experiment plan), if he starts many games from the Repeat User Launch page, or from the Android app. In the latter case, all such PlayerInfo objects are linked to a single User object.
 */

@Entity  
public class PlayerInfo {
    @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;
    public long getId() { return id;}
    
    /** The date of first activity */
    @Basic
    private Date date; 

    /** The most recent activity. This is mostly used to detect "walk-aways" */
    // @Transient
    Date lastActivityTime=null; 
    
    /** Back link to the user, for JPA's use. It is non-null only for
	player IDs created via the repeat-user launch pages, or via the
	Android app.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;
    public User getUser() { return user; }
    public void setUser(User _user) { user = _user; }


    @Basic 
    private String playerId;
    /** A human-readable string ID for this player. A mandatory
	non-null value, which we strive to keep unique (by the
	PlayerResponse code).  */
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String _playerId) { playerId = _playerId; }

    @Basic 
    private String experimentPlan;
    /** The experiment plan historically was just a directory name, 
	e.g. "pilot06". Starting from ver. 3.004, dynamic experiment 
	plans are also supported, in the form P:plan:modifer or
	R:ruleSet:modifier.
     */
    public String getExperimentPlan() { return experimentPlan; }
    public void setExperimentPlan(String _experimentPlan) {
	experimentPlan = _experimentPlan;
    }

    /** Sets some (transient) properties of this object which depend
	on the name of the experiment plan. For new objects, this method is called (via postLoad()) from PlayerResponse, after an object has been
	created and some basic initialization (setExperimentPlan(),
	initSeries()) took place. For those restored from the
	database, it is called automatically due to the @PostLoad
	directive.
    */
    @PostLoad() 
    public void postLoadPart1() {
	String[] z = experimentPlan.split("/");
	coopGame = z[z.length-1].startsWith("coop.");
	adveGame = z[z.length-1].startsWith("adve.");
    }

    /** Sets some (transient) properties of this object which depend
	on the name of the experiment plan. For new objects, this
	method is called from PlayerResponse, after an object has been
	created and some basic initialization (setExperimentPlan(),
	initSeries()) took place. For those restored from the
	database, it is called via restoreTransientFields().

       	<P>Note: this method can only be called after initSeries(), because
	otherwise getFirstPara() won't work.

	<p>FIXME: What if an object restored from the database,
	e.g. in a MW tools context, needs the needChat flag?
    */	
    public void postLoad() {
	postLoadPart1();
	if (is2PG()) {
	    ParaSet para = getFirstPara(); //-- this can only be done after initSeries() or restoreTransientFields()
	    if (para==null) {
		throw new IllegalArgumentException("Cannot access the player's parameter sets: " + playerId);
	    }
	    needChat = para.getBoolean("chat", false);
	} else {
	    needChat = false;
	}
	
    }

    /** These are set in setExperimentPlan */
    @Transient
    private boolean coopGame, adveGame, needChat;

    
        /** @return true if the name of the experiment plan indicates that this
	is a cooperative two-player game */
    public boolean isCoopGame() {
	return coopGame;
    }
    /** @return true if the name of the experiment plan indicates that this
	is an adversarial two-player game */
    public boolean isAdveGame() {
	return adveGame;
    }
    /** @return true if the name of the experiment plan indicates that this
	is a two-player game (cooperative or adversary) */
    public boolean is2PG() {
	return coopGame || adveGame;
    }
    /** Do we need a between-player chat element in the GUI? (In 2PG only,
	based on para.chat of the first para set of this player.  */
    public boolean getNeedChat() { return needChat; }
  
    
    @Basic 
    private String trialListId;
    /** For traditional (static) and "P:"-type dynamic experiment
	plans, this is the name of the actual trial list file in the
	appropriate experiment plan directory (without the ".csv"
	extension). For "R:"-type dynamic experiment plans (which do
	not involve any trial list files) this field, rather tautologically,
	contains the rule set name.
     */
    public String getTrialListId() { return trialListId; }
    public void setTrialListId(String _trialListId) { trialListId = _trialListId; }
    public Date getDate() { return date; }
    /** Sets the creation date (and the last activity date). This is 
	used on initialization */
    public void setDate(Date _date) {
	lastActivityTime = date = _date;
    }
    public Date getLastActivityTime() { return lastActivityTime; }
    public void setLastActivityTime(Date _lastActivityTime) { lastActivityTime = _lastActivityTime; }


    
    @Basic 
    private String partnerPlayerId;
    /** The playerId value of the partner, if this is a two-player game, and
	this player has already been paired with a partner. Null otherwise.
	 */
    public String getPartnerPlayerId() { return partnerPlayerId; }
    public void setPartnerPlayerId(String _partnerPlayerId) { partnerPlayerId = _partnerPlayerId; }

    /** The playerId of player 0 or player 1
	@param mover Whose playerId do you want? 
     */
    String getPlayerIdForRole(int mover) {
	return mover==Pairing.State.ZERO? getPlayerId(): getPartnerPlayerId();
    }

    /** Is this playerId of player 0 or player 1 in a 2PG?
	@param pid The playerId of this player, or of its partner
	@return 0 or 1
     */
    int getRoleForPlayerId(String pid) {
	if (pid==null) {
	    return Pairing.State.ERROR;
	} else if (pid.equals(getPlayerId())) {
	    return  Pairing.State.ZERO;
	} else if (pid.equals(getPartnerPlayerId())) {
	    return  Pairing.State.ONE;
	} else {
	    return Pairing.State.ERROR;
	}
    }

    /** This method recognizes that an older 1PG client may not pass a playerId;
	thus, it interprets null as Player 0 (the default player)
     */
    public int getRoleForPlayerIdPermissive(String pid) {
	return pid==null?  Pairing.State.ZERO : getRoleForPlayerId(pid);
    }
    
    @Transient
    private PlayerInfo partner = null;    
    public PlayerInfo xgetPartner() {
	if (partnerPlayerId==null || partner!=null) return partner;
	// If the entry has been restored from the databse, the partner PlayerInfo may not have been loaded yet.
	// Reload it from the database then.
	EntityManager em  = Main.getEM();
	synchronized(em) {
	    try {
		partner = PlayerResponse.findPlayerInfo(em, partnerPlayerId);
	    } catch (Exception ex) {}
	}
	return partner;
    }
    /** Sets links in both directions */
    public void linkToPartner( PlayerInfo _partner, int myRole) {
	partner = _partner;
	setPartnerPlayerId(partner.getPlayerId());
	setPairState(myRole);
	partner.partner = this;
	partner.setPartnerPlayerId(getPlayerId());
	partner.setPairState(1-myRole);
	// make sure the info goes into the database
	saveMe();
	partner.saveMe();
    }

    
    @Basic 
    private int pairState;
    /** For a player in a two-player game, this is his current status with respect
	to pairing. The value is from Pairing.Mode.
     */
    public int getPairState() { return pairState; }
    @XmlElement
    public void setPairState(int _pairState) { pairState = _pairState; }


    /** Sets certain pairing-related fields in the object for a newly created player. The settings are to the initial "not paired yet" state. */
    public void initPairing() {
	pairState = is2PG()? Pairing.State.NONE: 0;
	partnerPlayerId = null;
    }
	

    /** FIXME: this may result in an Episode being persisted before its completed, 
	which we, generally, don't like. Usually not a big deal though.
     */
    @OneToMany(
        mappedBy = "player",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
	fetch = FetchType.EAGER)
    private Vector<EpisodeInfo> allEpisodes = new  Vector<>();

    public void addEpisode(EpisodeInfo c) {
        allEpisodes.add(c);
        c.setPlayer(this);
    }
 
    public void removeEpisode(EpisodeInfo c) {
        allEpisodes.remove(c);
        c.setPlayer(null);
    }
    
    public Vector<EpisodeInfo> getAllEpisodes() { return allEpisodes; }
    public void setAllEpisodes(Vector<EpisodeInfo> _allEpisodes) {
	for(EpisodeInfo p: allEpisodes) p.setPlayer(null);
	allEpisodes.setSize(0);
	for(EpisodeInfo p: _allEpisodes) addEpisode(p);    	
    }
   
    //public static String assignTrialList(String player) {
    //	return null;
    //    }

    public String toString() {
	String s = "(PlayerInfo: id=" + id +",  playerId="+ playerId+", pair="+pairState+
	    (partnerPlayerId==null? "": ":" + partnerPlayerId) +
	    ", trialListId=" + trialListId +", date=" + date;
	if (completionMode != 0) s += ", completionMode="+ completionMode;
	s += ")";
	return s;
    }


    /** A Series is a list of all episodes played under a specific param set. A player
	has as many Series objects as there are lines in that player's trial list.
     */
    public class Series {
	final ParaSet para;
	/** This is true when this series "continues" into the next
	    series, forming a super-series. (It may continue further 
	    beyond, if the next series also has cont==true, and so on).
	*/
	final boolean cont;
	final GameGenerator gg;
	public Vector<EpisodeInfo> episodes = new Vector<>();
	int size() { return episodes.size(); }
	
	Series(ParaSet _para) throws IOException, IllegalInputException, ReflectiveOperationException, RuleParseException {
	    para = _para;
	    cont = para.getBoolean("continue", Boolean.FALSE);
	    gg = GameGenerator.mkGameGenerator(Episode.random, para);
	}

	boolean canGiveUp() {
	    int canGiveUpAt = para.getInt("give_up_at", true, 999);
	    return (canGiveUpAt>=0) && (size()<=canGiveUpAt);
	}

		
	
	public String toString() {
	    return "(Series: para.rules=" +	para.getRuleSetName() +
		", episode cnt= "+ episodes.size()+")";
	}

	/** How many bonus episodes (completed or not) this user has
	    completed or is still playing? */
	int countBonusEpisodes() {
	    int cnt=0;
	    for(EpisodeInfo x: episodes) {
		if (x.bonus) cnt++;
	    }
	    return cnt;
	}

	/** Checks if this player has earned a bonus in this series, and if so, 
	    attaches it to an appropriate episode, and ends this series */
	private void assignBonus() {
	    int cnt=0, deserveCnt=0;
	    for(EpisodeInfo x: episodes) {
		if (x.bonus) {
		    cnt++;
		    if (x.bonusSuccessful)  deserveCnt++;
		}
	    }
	    if (cnt==deserveCnt && deserveCnt>=para.getInt("clear_how_many")) {
		// bonus earned; attach it to the last episode in the bonus series,
		// and end the series
		int r = 0;
		for(EpisodeInfo x: episodes) {
		    if (x.bonusSuccessful) {		       
			r++;
			x.earnedBonus = (r==cnt);
			x.rewardBonus= (x.earnedBonus)? para.getInt("bonus_extra_pts"):0;
		    }
		}
	    }
	}

	boolean bonusHasBeenEarned() {
	    for(EpisodeInfo x: episodes) {
		if (x.earnedBonus) return true;
	    }
	    return false;
	}

	/** Scans the episodes of the series to see if the xFactor has
	    been set for this series. Only appliable to series with 
	    DOUBLING (or LIKELIHOOD) incentive scheme.
	    @param mj Which player's record do we look at? (1PG or coop 2PG only have 0; adve 2PG has 0 and 1)
	    @return 1,2, or 4.
	 */
	int findXFactor(int mj) {
	    int f = 1;
	    for(EpisodeInfo x: episodes) {
		if (x.xFactor[mj]>f) f=x.xFactor[mj];
	    }
	    return f;
	}

	/** True if we have the DOUBLING (or LIKELIHOOD) incentive scheme, and this 
	    series has been ended by the x4 achievement */
	public boolean seriesHasX4(int mj) {
	    return para.getIncentive().mastery() &&
		findXFactor(mj)==4;
	}
 	
    }


    public Series getSeries(int k) {
	return allSeries.get(k);
    }
    
    /** Retrieves a link to the currently played series, or null if this player
	has finished all his series. (Since 5.008, this refers to the 
	internal series, not super-series) */
    Series getCurrentSeries() {
	return alreadyFinished()? null: allSeries.get(currentSeriesNo);
    }

    /** Returns true if the current series number is set beyond the possible
	range, which indicates that it has gone through the last possible
	increment (and, therefore, the completion code has been set as well).
	Since ver 8.012, also returns true if the game has been abandoned.
    */
    public boolean alreadyFinished() {
	return gameAbandoned() || currentSeriesNo>=allSeries.size();
    }
    
    /** Based on the current situation, what is the maximum number
	of episodes that can be run within the current series? 
	(Until max_boards is reached, if in the main subseries, 
	or until the bonus is earned, if in the bonus subseries).

	Since ver 5.008, we count allowed episodes over the entire superseries.
    */
    int totalBoardsPredicted() {
	Series ser = getCurrentSeries();	
	if (ser==null) return 0;
	if (inBonus) return ser.size() +    ser.para.getInt("clear_how_many")-
			 countBonusEpisodes(currentSeriesNo);
	int n = 0;

	for(int j = currentSeriesNo; j<allSeries.size(); j++) {
	    n += allSeries.get(j).para.getMaxBoards();
	    if (!allSeries.get(j).cont) break;
	}

	for(int j = currentSeriesNo-1;   j>=0 && allSeries.get(j).cont; j--) {
	    n += allSeries.get(j).size();
	}
	

	return n;
    }

    /** How many episodes are in the current super-series? 
	@param k (internal) series number. It is assumed that this
	is the last (internal) series in its super-series.
     */
    public int getSuperseriesSize(int k) {
	Series ser= allSeries.get(k);
	int n = ser.size();
	for(int j = k-1;   j>=0 && allSeries.get(j).cont; j--) {
	    n += allSeries.get(j).size();
	}
	
	return n;
    }

    
    
    
    /** @return true if an "Activate Bonus" button can be displayed, i.e. 
	the player is eligible to start bonus episodes, but has not done that yet */
    public boolean canActivateBonus() {
	Series ser = getCurrentSeries();	
	if (ser==null) return false;
	if (ser.para.getIncentive()!=ParaSet.Incentive.BONUS) return false;
	if (inBonus) return false;  // already doing a bonus subseries!
	int at = ser.para.getInt("activate_bonus_at");
	// 0-based index of the episode on which activation will be in
	// effect, if it happens
	int nowAt = ser.size();
	if (ser.size()>0) {
	    if (!ser.episodes.lastElement().isCompleted()) nowAt--;
	}
	// 1-based
	nowAt++;

	boolean answer = (nowAt >= at);
	//System.err.println("canActivateBonus("+playerId+")="+answer+", for series No. "+currentSeriesNo+", size="+ser.size()+", at="+at);
	return answer;
    } 

    /** Switches this player from the main subseries to the bonus subseries, and
	saves the information about this fact in the SQL server.
	@param em The active EM to use. (We have this because this
	method is called from a method that has an EM anyway, and this
	object is NOT detached.)
    */
    public void activateBonus(EntityManager em) {
	if (inBonus) throw new IllegalArgumentException("Bonus already activated in the current series");
	
	if (!canActivateBonus()) throw new IllegalArgumentException("Cannot activate bonus in the current series");
	
	inBonus = true;

	Series ser=getCurrentSeries();
	if (ser!=null && ser.size()>0) {
	    EpisodeInfo epi = ser.episodes.lastElement();
	    // if the current episode is still running, make it a bonus episode too
	    if (!epi.isCompleted()) {
		epi.bonus=true;
		System.err.println("Bonus activated for current episode " + epi.episodeId);
	    }
	}
	System.err.println("Bonus activated: player="+playerId+", series No. "+currentSeriesNo+", size="+ser.size());
	// this saves the new value of inBonus
	em.getTransaction().begin();	    
	em.getTransaction().commit();	        
    }

    /** "Gives up" the current series, i.e. immediately switches the
	player to the next series (if there is one). */
    public void giveUp(int seriesNo) throws IOException {
	Logging.info("giveUp(pid="+playerId+", seriesNo=" + seriesNo +"), currentSeriesNo=" +currentSeriesNo);
	if (seriesNo+1==currentSeriesNo) {	    
	    // that series has just ended anyway...
	    Logging.info("giveUp: ignorable call on the previous series");
	    return;
	}
	if (seriesNo!=currentSeriesNo) throw new IllegalArgumentException("Cannot give up on series " + seriesNo +", because we presently are on series " + currentSeriesNo);
	if (seriesNo>=allSeries.size())  throw new IllegalArgumentException("Already finished all "+allSeries.size()+" series");
	Series ser=getCurrentSeries();
	if (ser!=null && ser.size()>0) {
	    EpisodeInfo epi = ser.episodes.lastElement();
	    // give up on the currently active episode, if any
	    if (!epi.isCompleted()) {
		epi.giveUp();
		Logging.info("giveUp: episodeId=" + epi.getEpisodeId()+", set givenUp=" + epi.givenUp);
		//Main.persistObjects(epi);
		// Persists SQL, and write CSV
		ended(epi);
	    }
	}
		   	
	goToNextSeries();
	Logging.info("giveUp completed, now currentSeriesNo=" +currentSeriesNo);

    }


    /** This may be invoked by a maintenance thread in 2PG, when it detects
	that this player has been inactive for a while. (The method should be
        usable in 1PG too, but it's not a major concern).

	<p>If this is 2PG (i.e. players are listening to web sockets),
	we send a Ready.DIS message to both players, so that their
	clients will update their screens

	<p>
	Note that, inside this method, we "abandon" the episode before 
	marking the player as "abandoner", because otherwise
	getCurrenSeries() won't retrieve the episode.
     */
    public void abandon() throws IOException {

	PlayerInfo y = (pairState==Pairing.State.ONE) ? partner: this;
	    
	Logging.info("abandoning by(pid="+playerId+"), currentSeriesNo=" + y.currentSeriesNo);

	// mark the current episode as abandoned, if needed
	boolean saved = false;

	if (y.currentSeriesNo>=y.allSeries.size())  return; // finished all series already anyway
	Series ser=y.getCurrentSeries();
	
	if (ser!=null && ser.size()>0) {
	    EpisodeInfo epi = ser.episodes.lastElement();
	    Logging.info("abandon(playerId): may need to abandon episode=" + epi.getEpisodeId());
	    // mark the currently active episode, if any, as abandoned
	    if (!epi.isCompleted()) {
		epi.abandoned = true;
		Logging.info("abandon: episodeId=" + epi.getEpisodeId()+", set abandoned=" + epi.abandoned);
		//Main.persistObjects(epi);
		// Persists SQL, and write CSV
		y.ended(epi);
		saved = true;
	    }
	} else if (ser==null) {
	    Logging.info("abandon: ser=null");
	} else {
	    Logging.info("abandon: ser.size=" + ser.size());
	}
	
	setCompletionMode(COMPLETION.WALKED_AWAY); 
	if (partner!=null) {
	    Logging.info("abandon("+playerId+"): marking the partner, " + partnerPlayerId + ", as abandoned");
	    partner.setCompletionMode(COMPLETION.ABANDONED);
	    partner.setCompletionCode( buildCompletionCode() + "-ab");
	    partner.saveMe();

	}
	saveMe();

	if (is2PG()) {
	    try {
		WatchPlayer.tellHim(playerId, WatchPlayer.Ready.DIS);
	    } catch(Exception ex) {
		Logging.error("Very unfortunately, caught exception when sending a Ready.DIS ws message to the walk-away player ("+playerId+"): " + ex);
		ex.printStackTrace(System.err);
	    }
	}
	
	if (partner!=null) {
	    try {
		WatchPlayer.tellHim(partnerPlayerId, WatchPlayer.Ready.DIS);
	    } catch(Exception ex) {
		Logging.error("Somewhat unfortunately, caught exception when sending a Ready.DIS  ws message to the abandoned partner ("+partnerPlayerId+"): " + ex);
		ex.printStackTrace(System.err);
	    }
	}
	
	//Logging.info("abandoning completed, now currentSeriesNo=" +currentSeriesNo);

	
    }
    
    /** Can a new "regular" (non-bonus) episode be started in the current series? */
    private boolean canHaveAnotherRegularEpisode() {
	Series ser=getCurrentSeries();
	if (ser==null) return false;
	if (ser.seriesHasX4(0) || ser.seriesHasX4(1)) return false;
	if (inBonus) return false;
	if (gameAbandoned()) return false;
	return  ser.size()<ser.para.getMaxBoards();
    } 

    /** Can a new bonus episode be started in the current series? */
    private boolean canHaveAnotherBonusEpisode() {
	System.err.println("canHaveAnotherBonusEpisode("+playerId+",ser="+currentSeriesNo+")? inBonus="+inBonus);
	Series ser=getCurrentSeries();
	if (ser==null) return false;
	System.err.println("ser=" + ser+", earned=" +  ser.bonusHasBeenEarned());
	if (!inBonus || ser.bonusHasBeenEarned()) return false;
	if (gameAbandoned()) return false;
	int cnt=0;
	System.err.println("Have " +  ser.size() + " episodes to look at");
	for(EpisodeInfo x: ser.episodes) {
	    System.err.println("looking at "+(x.isBonus()? "bonus":"main")+
			       " episode " + x.episodeId + ", completed=" + x.isCompleted());
	    if (x.isBonus()) {
		cnt++;
		if (!x.isCompleted()) return false;
		if (!x.bonusSuccessful) return false;
		System.err.println("ok bonus episode " + x.episodeId);
	    } 
	}
	boolean result = cnt<ser.para.getInt("clear_how_many");
	System.err.println("cnt=" + cnt+", allowed up to " + ser.para.getInt("clear_how_many") +", result=" + result);
	return result;
    }

    /** The main table for all episodes of this player, arranged in
	series.  This table normally contains entries for all series,
	both those already played and the future ones, one series per
	para set. The tables is initialized by initSeries(), and is filled
	with a number of "empty" series, each one having a ParaSet but
	no episodes yet. As an episode is started, its object is added
	to the appropriate Series object in this vector, and thus
	becomes stored in the server's memory for as long as this PlayerInfo
	object lives in PlayerResponse.allPlayers.
     */
    @Transient
    private Vector<Series> allSeries = new Vector<>();

    /** How many episodes are currently in series No. k? */
    public int seriesSize(int k) {
	return allSeries.get(k).size();
    }

    /** How many bonus episodes (complete or not) are currently in series No. k? */
    public int countBonusEpisodes(int k) {
	Series ser= allSeries.get(k);
	int cnt=0;
	for(EpisodeInfo x: ser.episodes) {
	    if (x.isBonus())  cnt++;
	}
	return cnt;
    }

    
    /** What series will the next episode be a part of? (Or, if the current episode
	is not completed, what series is it a part of?) */
    private int currentSeriesNo=0;
    public int getCurrentSeriesNo() { return currentSeriesNo; }

    /** Introduced in ver. 5.008 This gives the series number that would be displayed in the GUI client. This is based on the super-series.
     */
    private int currentDisplaySeriesNo=0;
    //public int getCurrentSeriesNo() { return currentSeriesNo; }

    /** Will the next episode be a part of a bonus subseries? (Or, if the current 
	episode is not completed, is it a part of  a bonus subseries?)
     */
    private boolean inBonus;

    
    /** This is usesd when a player is first registered and a PlayerInfo object is first created.  */
    public void initSeries(TrialList trialList) throws IOException, IllegalInputException, ReflectiveOperationException, RuleParseException {

	if (allSeries.size()>0) throw new IllegalArgumentException("Attempt to initialize PlayerInfo.allSeries again for playerId="+playerId);
	allSeries.clear();
	for( ParaSet para: trialList) {
	    allSeries.add(new Series(para));
	}
    }

    

    /** This method should be called after restoring the object from
      the SQL database, in order to re-create some of the necessary
      non-persistent structures. Typically, this may be needed if
      player resumes his activity after the Game Server has been
      restarted.  In particular, we restore the "series" structure,
      reloading paramter sets from the disk files and and putting
      episodes in their series arrays.

      <p>
      We also review the episodes, and "give up" all incomplete ones, because
      they don't have their transcripts and rules loaded, and cannot
      be continued. This may happen only rarely, when an episode
      had been persisted before beeing completed (thru cascading from
      the player being persisted), and then the server
      was restarted.     
    */
    public void restoreTransientFields() throws IOException, IllegalInputException, ReflectiveOperationException, RuleParseException {
	String exp = experimentPlan;
	// grandfathering older (pre 1.016) entries
	if (exp==null || exp.equals("")) {
	    exp = TrialList.extractExperimentPlanFromPlayerId(playerId);
	}
	    
	TrialList trialList  = new TrialList(exp, trialListId);
	allSeries.clear();

	int k = 0;

	System.err.println("Restore: will check " + allEpisodes.size() + " episodes");
	for(int j=0; j< trialList.size(); j++) {
	    ParaSet para =trialList.get(j);
	    String ruleSetName = para.getRuleSetName();
	    RuleSet rules = AllRuleSets.obtain(ruleSetName);

	    
	    Series ser = new Series(para);
	    allSeries.add(ser);
	    boolean needSave=false;
	    while(k<allEpisodes.size() && allEpisodes.get(k).seriesNo==j) {
		System.err.print("Restore: check series=" + j +", ae["+k+"]=");
		EpisodeInfo epi = allEpisodes.get(k++);
		epi.restorePara(para);
		System.err.println(epi.report()+", completed=" + epi.isCompleted());

		epi.setRules(rules); // just in case the GUI client needs them
		// for some display
		    
		
		if (!epi.isCompleted()) {
		    epi.giveUp();
		    // save the "givenUp" flag in the SQL database. No CSV files to write, though.
		    //Main.persistObjects(epi);
		    needSave=true;
		}
		ser.episodes.add(epi);
	    }
	    ser.gg.advance(ser.size());
	    if (needSave) saveMe();
	}

	// Just in case we need to restore the needChat field as well
	postLoad();
    }

    /** Retrieves the most recent episode, which may be completed or incomplete.
     */
    public EpisodeInfo mostRecentEpisode() {
	for(int k= Math.min(currentSeriesNo, allSeries.size()-1); k>=0; k--) {
	    Series ser=allSeries.get(k);
	    if (ser.size()>0) return ser.episodes.lastElement();
	}
	return null;
    }
    
    
    /** Returns the currently unfinished last episode to be resumed,
	or a new episode (in the current series or the next series, as
	the case may be), or null if this player has finished with all
	series. This is used by the /GameService2/newEpisode web API call. */
    public synchronized EpisodeInfo episodeToDo() throws IOException, RuleParseException {

	Logging.info("episodeToDo(pid="+playerId+"); cs=" + currentSeriesNo +", finished=" + alreadyFinished());

	if (alreadyFinished()) return null;
	boolean needSave=false;
	try {
	while(currentSeriesNo < allSeries.size()) {	    
	    Series ser=getCurrentSeries();
	    if (ser!=null && ser.size()>0) {

		if (inBonus && ser.bonusHasBeenEarned()) {
		    goToNextSeries();
		    continue;
		}
		
		EpisodeInfo epi = ser.episodes.lastElement();
		// should we resume the last episode?
		if (!epi.isCompleted()) {
		    if (epi.isNotPlayable()) {
			epi.giveUp();			
			// we just do SQL persist but don't bother saving CSV, since the data
			// probably just aren't there anyway
			needSave=true;
			//Main.persistObjects(x);
		    } else {
			Logging.info("episodeToDo(pid="+playerId+"): returning existing episode " + epi.episodeId);
			return epi;
		    }
		}
	    }
	    
	    EpisodeInfo epi = null;
	    if (canHaveAnotherRegularEpisode()) {
		epi = EpisodeInfo.mkEpisodeInfo(this, currentSeriesNo, currentDisplaySeriesNo,

						ser.gg, ser.para, false);
	    } else if (canHaveAnotherBonusEpisode()) {
		epi = EpisodeInfo.mkEpisodeInfo(this, currentSeriesNo,  currentDisplaySeriesNo,
						ser.gg, ser.para, true);
	    }

	    if (epi!=null) {
		// The error-free stretch continues across episode border
		if (ser.size()>0) {
		    epi.setLastStretch(ser.episodes.lastElement().getLastStretch());
		    epi.setLastR(ser.episodes.lastElement().getLastR());
		}
		
		ser.episodes.add(epi);
		addEpisode(epi);	
		Logging.info("episodeToDo(pid="+playerId+"): returning new episode " + epi.episodeId);
		return epi;
	    }
	    Logging.info("episodeToDo(pid="+playerId+"): nextSeries");
	    goToNextSeries();
	}
	} finally {
	    if (needSave) saveMe();
	}
	
	Logging.info("episodeToDo(pid="+playerId+"): cannot return anything");

	return null;
    }

    /** To which series does the specified episode belong? */
    private Series whoseEpisode(EpisodeInfo epi) {
	Series s = allSeries.get( epi.seriesNo);
	
	for(EpisodeInfo e: s.episodes) {
	    if (e==epi) return s;
	}
	// This could indicate some problem with the way we use JPA
	System.err.println("whoseEpisode: detected an episode not stored in the current series structure : " + epi.episodeId +", series " + epi.seriesNo);
	return null;
    }

    /** Gives a link to the ParaSet associated with a given episode */
    public ParaSet getPara(EpisodeInfo epi) {
	return  whoseEpisode(epi).para;
    }

    /** Gives a link to the first ParaSet. This is used for things
	that are determined by the first ParaSet, e.g. the pregame 
	experience.
	@return the ParaSet for the first series, or null if none exists.
    */
    public ParaSet getFirstPara() {
	if (allSeries.size()==0) return null;
	Series s = getSeries(0);
	if (s==null) return null;
	return s.para;
    }


    private String completionCode;
    /** The completion code, a string that the player can report as a proof of 
	his completion of the experiment plan. It is set when the current series
	number is incremented beyond the last parameter set number.
     */
    public String getCompletionCode() { return completionCode; }
    public void setCompletionCode(String _completionCode) { completionCode = _completionCode; }


    /** Completion modes */
    static public class COMPLETION {
	static public final int
	/** Abandoned by partner */
	    ABANDONED =1,
	/** Walked away (thus abandoning the partner in the process) */
	    WALKED_AWAY = 2;
    }

    /** This value, if not the default 0, may store additional
	info about how the player's participation ended.
	The value is a constant from COMPLETION */
    private int completionMode;
    public int getCompletionMode() { return completionMode; }
    @XmlElement
    public void setCompletionMode(int _completionMode) { completionMode = _completionMode; }

    public boolean gameAbandoned() {
	return (completionMode == COMPLETION.ABANDONED ||
		completionMode == COMPLETION.WALKED_AWAY);
    }


    
    /** Creates a more or less unique string ID for this Episode object */
    private String buildCompletionCode() {
	String s = playerId + "-" + Episode.sdf.format(new Date()) + "-";
	for(int i=0; i<4; i++) {
	    int k =  Episode.random.nextInt(10 + 'F'-'A'+1);
	    char c = (k<10) ? (char)('0' + k) : (char)('A' + k-10);
	    s += c;
	}
	return s;
    } 
    
    /** Adjusts the counters/flags indicating what series and
	subseries we are on, and persists this object. This is the
	only place in the code where the current series number can be
	incremented. If the series number reaches the last possible
	value (the one beyond the range of parameter set numbers), the
	completion code for this player is set.

	<p>In a 2PG, the partner (Player 1) gets the same completion code as well,
	so that he will be shown one after his demographics page.
     */
    synchronized private void goToNextSeries() {
	if (alreadyFinished()) return;
	if (!getCurrentSeries().cont) currentDisplaySeriesNo++;
	currentSeriesNo++;
	inBonus=false;

	if (alreadyFinished() && completionCode ==null) {
	    completionCode = buildCompletionCode();

	    if (partner!=null) { 
		partner.completionCode = completionCode;
		partner.saveMe();
	    }

	    
	} 
	//Main.persistObjects(this);
	saveMe();
    }

    private int totalRewardEarned;
    /** The total reward shown to this player. In 2PG, each PlayerInfo has a value stored; the two
	partners' value are the same in a coop game, but different in an adversarial game */
    public int getTotalRewardEarned() {
	//System.err.println("getTotalReward("+playerId+")=" + totalRewardEarned);
	return totalRewardEarned;
    }
    public void setTotalRewardEarned(int _totalRewardEarned) { totalRewardEarned = _totalRewardEarned; }

    /** Recomputes this player's (and his partner's, in 2PG) totalRewardEarned, based on all episodes in his record.
	In 2PG, this method is only called on the PlayerInfo who "owns" the episode, i.e. Player 0; the call also
	updates the numbers for Player 1.
     */
    private void updateTotalReward() {
	/*
	int sum=0;
	int cnt=0;
	for(Series ser: allSeries) {
	    int s=0, f=1;
	    for(EpisodeInfo epi: ser.episodes) {
		cnt++;
		s +=  epi.getTotalRewardEarned();  
		f = Math.max(f, epi.getXFactor());
	    }
	    sum += s;
	}
	totalRewardEarned=sum; */

	int z[] = {0,0};
	for(int mj=0; mj<2; mj++) {
	    RewardsAndFactorsPerSeries rx = getRewardsAndFactorsPerSeries(mj);
	    z[mj] = rx.getSum();
	}
       
	totalRewardEarned = z[0];
	if (partnerPlayerId!=null) {
	    partner.setTotalRewardEarned( z[ isAdveGame()? 1:0]);
	}
	    
	
	Logging.info("updateTotalReward(): Total reward("+playerId+"):=" + z[0]);
    }

    /** This structure contains all information needed to calculate the player's
	total reward, and to show how it is computed based on the reward for
	each series. */
    class RewardsAndFactorsPerSeries {
	/** For series No. j, raw[j] = {raw_reward_for_the_series, x_factor_by_which_the_raw_reward_is_to_be_multiplied} */
	final int[][] raw;
	int epiCnt = 0;

	/** @param mj The partner based on whose record we compute
	    rewards. For 1PG and coop 2PG, it's always 0; for adve 2PG,
	    the actual player to whom we show the reward.

	    @return { {s0,f0}, {s1,f1}, {s2,f2}....}, which are the
	    per-series components that sum to reward=s0*f0+ s1*f1+ s2*f2 +....
	*/
	RewardsAndFactorsPerSeries(int mj) {
	    int n = Math.min( currentSeriesNo+1, allSeries.size());
	    raw = new int[n][];

	    for(int j=0; j< n; j++) {
		Series ser = allSeries.get(j);
		int s=0, f=1;
		for(EpisodeInfo epi: ser.episodes) {
		    epiCnt ++;
		    s += epi.getTotalRewardEarned(mj);
		    f = Math.max(f, epi.xFactor[mj]);
		}
		raw[j] = new int[]{s, f};
	    }
	    Logging.info("Created RewardsAndFactorsPerSeries=" + this);
	}

	public String toString() {
	    Vector<String> v = new Vector<>();
	    for(int[] rx: raw) {
		if (rx!=null) {
		    v.add("" + rx[0] +"*"+rx[1]);
		}
	    }
	    return "Reward: " + getSum() + " = " +
		String.join(" + ", v) + ", based on " + epiCnt+ " episodes";
	}


	/** An add-on summing the numbers given by getRewardsAndFactorsPerSeries() */
	int getSum() {
	    int sum  = 0;
	    for(int[] ax: raw) {
		if (ax==null) {
		    IllegalArgumentException ex =  new IllegalArgumentException("ax==null in RAF.getSum");
		    StringWriter sw = new StringWriter();
		    ex.printStackTrace(new PrintWriter(sw));
		    String s = sw.toString();
		    Logging.warning(""+ex);
		    Logging.warning(s);
		    //ex.printStackTrace(System.err);
		    throw ex;
		}
		sum += ax[0];
	    }
	    return sum;
	}

	int getFactorAchieved() {
	    return raw[ raw.length-1][1];
	}
    }

    RewardsAndFactorsPerSeries  getRewardsAndFactorsPerSeries(int mj) {
	return new RewardsAndFactorsPerSeries(mj);
    }
    
    /** This method is called after an episode completes. It computes
	the applicable rewards (if the board has been cleared or
	(since 4.007) stalemated, calls the SQL persist operations,
	writes CSV files, and, if needed, switches the series and
	subseries.

	In a 2PG this method is only called on the player who "owns" the episode
	(Player 0).

	@param epi An episode that's just completed; so all data are in memory 
	now.
    */
    void ended(EpisodeInfo epi) throws IOException {
	Series ser = whoseEpisode(epi);
	if (ser==null) throw new IllegalArgumentException("Could not figure to which series this episode belongs");
	epi.endTime=new Date();
	
	if (epi.stalemate && !epi.stalematesAsClears) {	    
	    // The experimenters should try to design rule sets so that stalemates
	    // do not happen; but if one does, let just finish this series
	    // to avoid extra annoyance for the player
	    goToNextSeries();
	} else if (epi.cleared || epi.earlyWin || epi.abandoned ||
		   epi.stalemate && epi.stalematesAsClears) {
	    // For completions, nPiecesStart==doneMoveCnt, but for
	    // stalemates, we must use the latter
	    double q = epi.attemptSpent - epi.doneMoveCnt;
	    if (isAdveGame()) { // Reward for each player is based on his error count and number of pieces removed
		double d1 = epi.attemptSpent1 - epi.doneMoveCnt1;
		double d[] = {q-d1,d1};
		int done[] = {epi.doneMoveCnt - epi.doneMoveCnt1, epi.doneMoveCnt1};
		for(int j=0; j<2; j++) {
		    double score = ser.para.kantorLupyanReward0(d[j]) * done[j]/(double)epi.doneMoveCnt;
		    epi.setRewardMain(j,  (int)Math.round(score));
		}
	    } else {
		int score =  ser.para.kantorLupyanReward(q);
		epi.setRewardMain(0,  score);
		epi.setRewardMain(1,  score);
	    }
		       
	    
	    
	    if (epi.bonus) {
		ser.assignBonus();
	    }
	    updateTotalReward();
	}

	epi.updateFinishCode();
	Logging.info("PlayerInfo.ended(epi=" + epi.getEpisodeId()+"); finishCode =" + epi.finishCode);
	// save the data in the SQL server
	saveMe();
	// save the data in the CSV files
	File f =  Files.boardsFile(playerId);
	Board b = epi.getCurrentBoard(true);
	BoardManager.saveToFile(b, playerId, epi.episodeId, f);
	f =  Files.transcriptsFile(playerId);
	TranscriptManager.saveTranscriptToFile(playerId, epi.episodeId, f, epi.transcript);
	f =  Files.detailedTranscriptsFile(playerId);
	epi.saveDetailedTranscriptToFile(f);
	Logging.info("PlayerInfo.ended: saved transcripts for (epi=" + epi.getEpisodeId()+"); finishCode =" + epi.finishCode);
	try {
	    WatchPlayer.tellAbout(playerId, "Ended episode " +epi.getEpisodeId()+
			     " with finishCode =" + epi.finishCode);
	} catch(Exception ex) {
	    Logging.error("caught exception when sending an info ws message about "+playerId+": " + ex);
	    ex.printStackTrace(System.err);
	}
	
    }

    /** Generates a concise report on this player's history, handy for
	debugging. It gives summaries of all episodes done (or in
	progress) by this player, broken down by series. */
    public String report() {
	Vector<String> v = new Vector<>();
	v.add(toString());
	int j=0;
	for(Series ser: allSeries) {
	    String s="";
	    if (j==currentSeriesNo) s+= (inBonus? "*B*" : "*M*");
	    s += "[S"+j+"]";
	    for(EpisodeInfo epi: ser.episodes) s += epi.report();
	    v.add(s);
	    j++;
	}
	v.add("id="+id+", curSer="+currentSeriesNo+
	      (currentDisplaySeriesNo!=currentSeriesNo? " (display "+currentDisplaySeriesNo+")":"") +
	      " b="+inBonus+", R=$"+getTotalRewardEarned());
	return String.join("\n", v);
    }


    /** Where can we go from here? */
    public static enum Transition {
	/** A main-subseries episode in the same series*/
	MAIN,
	/** A bonus episode in the same series */
	BONUS,
	/** Start next series (that is, a new param set, with new rules) */
	NEXT,
	/** End the interaction with the system, as the player has at
	    least sampled all param sets, and has completed (or given up
	    on) all of them */	   
	END};
    /** What type of action takes the player to a particular destination? */
    public static enum Action {
	/** Default transition -- no special choice */
	DEFAULT,
	/** Activate bonus */
	ACTIVATE,
	/** Give up */
	GIVE_UP};

    public class TransitionMap extends HashMap<Transition,Action> {
	/** After an episode has been completed, what other episode(s) can follow?
	    This object is transmitted to the client as JSON, and can be used 
	    to draw all appropriate transition buttons.
	    <p>
	    Note that the map may be empty if no more episodes can be played. 
	*/
	public TransitionMap() {
	    
	    Series ser = getCurrentSeries();
	    if (ser==null) return;
	    boolean isLastSeries = (currentSeriesNo + 1 == allSeries.size());

	    // Where do you really get if you are done with this series?
	    Transition whitherNext = isLastSeries?Transition.END: Transition.NEXT;

	    boolean mayGiveUp = ser.canGiveUp();

	    if (inBonus) {
		if (canHaveAnotherBonusEpisode()) {
		    put(Transition.BONUS, Action.DEFAULT);
		    if (mayGiveUp) put(whitherNext, Action.GIVE_UP);
		} else {
		    put(whitherNext, Action.DEFAULT);
		}
	    } else {

		if (canHaveAnotherRegularEpisode()) {
		    // space left to continue or give up
		    put(Transition.MAIN, Action.DEFAULT);
		    if (mayGiveUp) put(whitherNext, Action.GIVE_UP);
		} else {
		    // end of series
		    put(whitherNext, Action.DEFAULT);
		}

		if (canActivateBonus())  put(Transition.BONUS, Action.ACTIVATE);
	    }
	}
    }

    /** Saves this object (and the associated Episode objects, via
	cascading) data in the SQL database. The assumption is that 
	this object is detached, so we call a method which will
	create a new EM and merge this object to the new persistence context.
     */
    public void saveMe() {
	Logging.info("Saving player " + playerId);
	Main.saveObject(this);
    }

    /** Computes the "faces" vector for the series to which the 
	specified episode belongs. This is used by Kevin's GUI tool
	in the DOUBLING  incentive scheme display (ver 4.006)


	@param If it's a 2PG, who am I in the pair?

	@param epi The current (possibly, just finished) episode. We
	pass it in so that everything will work correctly even if this
	is part of a /move call that ended the last episode of the
	series, and currentSeriesNo may already be referring to the
	next series.

	@return An array of booleans, with a T value for each
	successful move in the episode's transcript, and a F value for
	each unsuccessful move/pick attempt, respectively. (We're
	ignoring successful picks to keep players from gaming the
	system.)

    */
    Vector[] computeFaces(int mover, EpisodeInfo epi)// throws IOException
    {
	Series ser = whoseEpisode(epi);
	Vector<Boolean> v= new Vector<Boolean>(), mine=new Vector<Boolean>();
	Vector[] w = {v, mine};
	if (ser==null) return w;
	for(Episode e: ser.episodes) {
	    Vector<Episode.Pick> t=e.transcript;
	    if (t==null) continue;
	    for(Episode.Pick pick: t) {
		if (pick instanceof Episode.Move ||
		    pick.code!=Episode.CODE.ACCEPT) {
		    v.add( pick.code==Episode.CODE.ACCEPT);
		    mine.add( pick.mover == mover);
		}
	    }
	}
	return w;
    }

    /** Compute the "goodness score" of this player, intended to measure
	how good this player has been, to be used during
	the postgame experience to decide if the demographics page
	should contain an invitation to participate in additional
	research (only offered to good players). At present (ver
	6.029), this score is only computed in a non-trivial way in
	games with the incentive plan Incentive.DOUBLING.
	
	<p>In 2PG, the record is stored in Player 0' PlayerInfo object,
	so we refer to it. In oop games, goodness is "joint" for the pair of players,
	since that's how xfactor etc are computed and stored.

	@return For players in games with the incentive plan
	Incentive.DOUBLING (or LIKELIHOOD), the value of the score, in the range
	[0..1], is simply the fraction of all rule sets so far that
	the player has fully mastered (received X4), plus 1/2 of those that were
	partially mastered (received X2). For players in other
	incentive plans, 0 is returned.
    */
    public double goodnessScore() {
	ParaSet para = getFirstPara();
	if (para==null) return 0;
	//hasError("Don't know the players parameter set");

	if ( para.getIncentive()==ParaSet.Incentive.DOUBLING ||
	     para.getIncentive()==ParaSet.Incentive.LIKELIHOOD) {
	    // Whose record do we look at? Only 2PG adve games have separate
	    // xfactor records
	    int mj = (isAdveGame() && pairState==Pairing.State.ONE) ? 1: 0;
	    double g = (pairState==Pairing.State.ONE) ? partner.goodnessScore(mj):
		goodnessScore(mj);
	    Logging.info("Goodness(pid="+playerId+")="+g);
	    return g;

	} else return 0;
    }

    /** Computes the goodness score literally in this PlayerInfo record,
	so if it's 2PG, this player must be Player 0 of the pair (because
	that's where both player's records and XFactors are stored).

	This method must be only called from goodnessScore(), so that the 
	incentive scheme is already checked.

	@param mj Normally, 0. In adve 2PG, this may be 0 or 1, to refer
	to a particular player's section of the stored record.
     */
    private double goodnessScore(int mj) {	
	
	int sumFactor = 0;
	for(Series ser: allSeries) {
	    if (ser!=null) {
		//int f = ser.findXFactor(pairState);
		int f = ser.findXFactor(mj);
		if (f>1) sumFactor+=f;
	    }
	}
	double g=(double)sumFactor / (4 * (double) allSeries.size());
	Logging.info("Goodness(pid="+playerId+", mj="+mj+")="+g);
	return g;
    } 

    /** This is used to keep track of when a player joins the pairing queue */
    @Transient
    private Date pairingRegistrationTime = null;
    /** Mark this player as someone who is interested in pairing right now */
    void  setPairingRegistrationTimeNow() {
	pairingRegistrationTime = new Date();
    }
    Date getPairingRegistrationTime() {
	return pairingRegistrationTime;
    }

    /** Does this player's playerId look like one generated in one of
	our Prolific studies? */
    boolean isProlific() {
	return getPlayerId().startsWith("prolific-");
    }
    
}
 
