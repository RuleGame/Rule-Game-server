package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

import javax.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.rest.TrialList;
import edu.wisc.game.rest.Files;

/** Information about a player (what trial list he's in, what episodes he's done etc) stored in the SQL database.
 */

@Entity  
public class PlayerInfo {
    @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;

    @Basic 
    private String playerId;
    @Basic 
    private String experimentPlan;
    @Basic 
    private String trialListId;

    /** The date of first activity */
    @Basic
    private Date date; 

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String _playerId) { playerId = _playerId; }

    public String getExperimentPlan() { return experimentPlan; }
    public void setExperimentPlan(String _experimentPlan) { experimentPlan = _experimentPlan; }

    
    public String getTrialListId() { return trialListId; }
    public void setTrialListId(String _trialListId) { trialListId = _trialListId; }
    public Date getDate() { return date; }
    public void setDate(Date _date) { date = _date; }

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
   
    public static String assignTrialList(String player) {
	return null;
    }

    public String toString() {
	return "(PlayerInfo: id=" + id +",  playerId="+ playerId+", trialListId=" + trialListId +", date=" + date+")";
    }


    /** A Series is a list of all episodes played under a specific param set. A player
	has as many Series objects as there are lines in that player's trial list.
     */
    class Series {
	ParaSet para;
	Vector<EpisodeInfo> episodes = new Vector<>();

	Series(ParaSet _para) {
	    para = _para;
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
	void assignBonus() {
	    double clearingThreshold = para.getDouble("clearing_threshold");
	    int cnt=0, deserveCnt=0;
	    for(EpisodeInfo x: episodes) {
		if (x.bonus) {
		    cnt++;
		    if (x.deservesBonus(clearingThreshold ))  deserveCnt++;
		}
	    }
	    if (cnt==deserveCnt && deserveCnt >=  para.getInt("clear_how_many")) {
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

    }


    /** Retrieves a link to the currently played series, or null if this player
	has finihed all his series */
    private Series getCurrentSeries() {
	return currentSeriesNo<allSeries.size()? allSeries.get(currentSeriesNo): null;
    }

    public boolean alreadyFinished() {
	return currentSeriesNo>=allSeries.size();
    }
    
    /** Based on the current situation, what is the maximum number
	of episodes that can be run within the current series? 
	(Until max_boards is reached, if in the main subseries, 
	or until the bonus is earned, if in the bonus subseries).
    */
    int totalBoardsPredicted() {
	Series ser = getCurrentSeries();	
	return ser==null? 0:
	    inBonus? ser.episodes.size() +    ser.para.getInt("clear_how_many")-
	    countBonusEpisodes(currentSeriesNo):	    
	    getCurrentSeries().para.getMaxBoards();
    }

    
    /** @return true if an "Activate Bonus" button can be displayed, i.e. 
	the player is eligible to start bonus episodes, but has not done that yet */
    public boolean canActivateBonus() {
	Series ser=getCurrentSeries();
	if (ser==null) return false;
	int at = ser.para.getInt("activate_bonus_at");
	// 0-based index of the episode on which activation will be in effect, if
	// it happens
	int nowAt = ser.episodes.size();
	if (ser.episodes.size()>0 && !ser.episodes.lastElement().isCompleted()) nowAt--;
	// 1-based
	nowAt++;

	boolean answer = (nowAt >= at);
	System.err.println("canActivateBonus("+playerId+")="+answer+", for series No. "+currentSeriesNo+", size="+ser.episodes.size()+", at="+at);
	return answer;
    } 

    /** Switches this player from the main subseries to the bonus subseries, and
	perisist the information about this fact in the SQL server. */
    public void activateBonus() {
	if (inBonus) throw new IllegalArgumentException("Bonus already activated in the current series");
	
	if (!canActivateBonus()) throw new IllegalArgumentException("Cannot activate bonus in the current series");
	
	inBonus = true;

	Series ser=getCurrentSeries();
	if (ser!=null && ser.episodes.size()>0) {
	    EpisodeInfo epi = ser.episodes.lastElement();
	    // if the current episode is still running, make it a bonus episode too
	    if (!epi.isCompleted()) {
		epi.bonus=true;
		System.err.println("Bonus activated for current episode " + epi.episodeId);
	    }
	}
	System.err.println("Bonus activated: player="+playerId+", series No. "+currentSeriesNo+", size="+ser.episodes.size());
	Main.persistObjects(this); // this saves the new value of inBonus
    }

    /** "Gives up" the current series, i.e. immediately switches the player to the next
	series (if there is one) */
    public void giveUp(int seriesNo) {
	if (seriesNo+1==currentSeriesNo) {
	    // that series has just ended anyway...
	    return;
	}
	if (seriesNo!=currentSeriesNo) throw new IllegalArgumentException("Cannot give up on series " + seriesNo +", because we presently are on series " + currentSeriesNo);
	if (seriesNo>=allSeries.size())  throw new IllegalArgumentException("Already finished all "+allSeries.size()+" series");
	Series ser=getCurrentSeries();
	if (ser!=null || ser.episodes.size()>0) {
	    EpisodeInfo epi = ser.episodes.lastElement();
	    // give up on the currently active episode, if any
	    if (!epi.isCompleted()) {
		epi.giveUp();
		Main.persistObjects(epi);
	    }
	}
	goToNextSeries();
    }


    /** Can a new "regular" (non-bonus) episode be started in the current series? */
    private boolean canHaveAnotherRegularEpisode() {
	Series ser=getCurrentSeries();
	return ser!=null && !inBonus && ser.episodes.size()<ser.para.getMaxBoards();
    } 

    /** Can a new bonus episode be started in the current series? */
    private boolean canHaveAnotherBonusEpisode() {
	System.err.println("canHaveAnotherBonusEpisode("+playerId+",ser="+currentSeriesNo+")? inBonus="+inBonus);
	Series ser=getCurrentSeries();
	if (ser==null) return false;
	System.err.println("ser=" + ser+", earned=" +  ser.bonusHasBeenEarned());
	if (!inBonus || ser.bonusHasBeenEarned()) return false;
	double clearingThreshold = ser.para.getDouble("clearing_threshold");
	int cnt=0;
	System.err.println("Have " +  ser.episodes.size() + " episodes to look at");
	for(EpisodeInfo x: ser.episodes) {
	    System.err.println("looking at "+(x.isBonus()? "main" : " bonus")+
			       " episode " + x.episodeId + ", completed=" + x.isCompleted());
	    if (x.isBonus()) {
		cnt++;
		if (!x.isCompleted()) return false;
		if (x.failedBonus(clearingThreshold)) return false;
		System.err.println("ok bonus episode " + x.episodeId);
	    } 
	}
	boolean result = cnt<ser.para.getInt("clear_how_many");
	System.err.println("cnt=" + cnt+", allowed up to " + ser.para.getInt("clear_how_many") +", result=" + result);
	return result;
    }

    /** The main table for all episodes of this player, arranged in series */
    @Transient
    private Vector<Series> allSeries = new Vector<>();

    /** How many episodes are currently in series No. k? */
    public int seriesSize(int k) {
	return allSeries.get(k).episodes.size();
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

    /** Will the next episode be a part of a bonus subseries? (Or, if the current 
	episode is not completed, is it a part of  a bonus subseries?)
     */
    private boolean inBonus;

    
    /** This is usesd when a player is first registered and a PlayerInfo object is firesst created */
    public void initSeries(TrialList trialList) {
	if (allSeries.size()>0) throw new IllegalArgumentException("Attempt to initialize PlayerInfor.allSeries again");
	allSeries.clear();
	for( ParaSet para: trialList) {
	    allSeries.add(new Series(para));
	}
    }

    /** This method should be called after restoring the object from the SQL database, in order to re-create some of the necessary non-persistent structures. */
    public void restoreTransientFields() {
	String exp = experimentPlan;
	// grandfathering older (pre 1.016) entries
	if (exp==null || exp.equals("")) {
	    exp = TrialList.extractExperimentPlanFromPlayerId(playerId);
	}
	    
	TrialList trialList  = new TrialList(exp, trialListId);
	allSeries.clear();

	int k = 0;
	for(int j=0; j< trialList.size(); j++) {
	    ParaSet para =trialList.get(j);
	    Series ser = new Series(para);
	    allSeries.add(ser);
	    while(k<allEpisodes.size() && allEpisodes.get(k).seriesNo==j) {
		ser.episodes.add(  allEpisodes.get(k++));
	    }
	}
    }

    /** Retrieves the most recent episode, which may be completed or incomplete.
     */
    public EpisodeInfo mostRecentEpisode() {
	for(int k= Math.min(currentSeriesNo, allSeries.size()-1); k>=0; k--) {
	    Series ser=allSeries.get(k);
	    if (ser.episodes.size()>0) return ser.episodes.lastElement();
	}
	return null;
    }
    
    
    /** Returns the currently unfinished last episode to be resumed,
	or a new episode (in the current series or the next series, as
	the case may be), or null if this player has finished with all series. */
    public EpisodeInfo episodeToDo() throws IOException, RuleParseException {

	while(currentSeriesNo < allSeries.size()) {	    
	    Series ser=getCurrentSeries();
	    if (ser!=null && ser.episodes.size()>0) {

		if (inBonus && ser.bonusHasBeenEarned()) {
		    goToNextSeries();
		    continue;
		}
		
		EpisodeInfo x = ser.episodes.lastElement();
		// should we resume the last episode?
		if (!x.isCompleted()) {
		    if (x.isNotPlayable()) {
			x.giveUp();
			Main.persistObjects(x);
		    } else {
			return x;
		    }
		}
	    }
	    
	    EpisodeInfo x = null;
	    if (canHaveAnotherRegularEpisode()) {
		x = EpisodeInfo.mkEpisodeInfo(currentSeriesNo, ser.para, false);
	    } else if (canHaveAnotherBonusEpisode()) {
		x = EpisodeInfo.mkEpisodeInfo(currentSeriesNo, ser.para, true);
	    }

	    if (x!=null) {
		ser.episodes.add(x);
		addEpisode(x);
		return x;
	    }	    	    	    
	    goToNextSeries();
	}
	
	return null;
    }

    private Series whoseEpisode(EpisodeInfo epi) {
	Series s = allSeries.get( epi.seriesNo);
	
	for(EpisodeInfo e: s.episodes) {
	    if (e==epi) return s;
	}
	// This could indicate some problem with the way we use JPA
	System.err.println("whoseEpisode: detected an episod not stored in the current series structure : " + epi);
	return null;
    }

    /** Gives a link to the ParaSet associated with a given episode */
    public ParaSet getPara(EpisodeInfo epi) {
	return  whoseEpisode(epi).para;
    }

    /** Adjusts the counters/flags indicating what series and subseries we are on,
	and persists this object.
     */
    private void goToNextSeries() {
	currentSeriesNo++;
	inBonus=false;
	Main.persistObjects(this);
    }

    private int totalRewardEarned;
    public int getTotalRewardEarned() {
	System.err.println("getTotalReward("+playerId+")=" + totalRewardEarned);
	return totalRewardEarned;
    }
    public void setTotalRewardEarned(int _totalRewardEarned) { totalRewardEarned = _totalRewardEarned; }

    /** Recomputes this player's totalRewardEarned, based on all episodes in his record*/
    private void updateTotalReward() {
	int sum=0;
	int cnt=0;
	for(Series ser: allSeries) {
	    for(EpisodeInfo epi: ser.episodes) {
		cnt++;
		sum +=  epi.getTotalRewardEarned();  
	    }
	}
	totalRewardEarned=sum;
	System.err.println("Total reward("+playerId+"):=" + totalRewardEarned +", based on " + cnt + " episodes");
    }
    
    
    /** This method is called after an episode completes. It computes
	rewards (if the board has been cleared), and, if needed,
	switches the series and subseries. */
    void ended(EpisodeInfo epi) throws IOException {
	Series ser = whoseEpisode(epi);
	if (ser==null) throw new IllegalArgumentException("Could not figure to which series this episode belongs");
	epi.endTime=new Date();
	
	if (epi.stalemate) {
	    // The experimenters should try to design rule sets so that stalemates
	    // do not happen; but if one does, let just finish this series
	    // to avoid extra annoyance for the player
	    goToNextSeries();
	} else if (epi.cleared) {
	    double smax = ser.para.getDouble("max_points");
	    double smin = ser.para.getDouble("min_points");
	    double b = ser.para.getDouble("b");
	    int d = epi.attemptCnt - epi.getNPiecesStart();
	    epi.rewardMain = (int)Math.round( smin + (smax-smin)/(1.0 + Math.exp(b*(d-2))));
	    if (epi.bonus) {
		ser.assignBonus();
	    }
	    updateTotalReward();
	}

	Main.persistObjects(this, epi);
	//try {
	    File f =  Files.boardsFile(playerId);
	    epi.getCurrentBoard(true).saveToFile(playerId, epi.episodeId, f);
	    f =  Files.transcriptsFile(playerId);
	    epi.saveTranscriptToFile(playerId, epi.episodeId, f);
	
	    //} catch(IOException ex) {	}
    }

    /** Generates a concise report on this player's history, handy for
	debugging. It gives summaries of all episodes done (or in
	progress) by this player, broken down by series. */
    public String report() {
	Vector<String> v = new Vector<>();
	int j=0;
	for(Series ser: allSeries) {
	    String s="";
	    if (j==currentSeriesNo) s+= (inBonus? "*B*" : "*M*");
	    s += "["+j+"]";
	    for(EpisodeInfo epi: ser.episodes) s += epi.report();
	    v.add(s);
	    j++;
	}
	v.add("R=$"+getTotalRewardEarned());
	return String.join("\n", v);
    }


    public static enum Transition { MAIN, BONUS, NEXT, END};
    public static enum Action { DEFAULT, ACTIVATE, GIVE_UP};

    /** After an episode has been completed, what other episode(s) can follow?
     */
    public class TransitionMap extends HashMap<Transition,Action> {
	public TransitionMap() {
	    
	    Series ser = getCurrentSeries();
	    boolean isLastSeries = (currentSeriesNo + 1 == allSeries.size());
	    if (ser==null) return;
	    if (inBonus) {
		if (canHaveAnotherBonusEpisode()) {
		    put(Transition.BONUS, Action.DEFAULT);
		    put(isLastSeries?Transition.END: Transition.NEXT, Action.GIVE_UP);
		} else {
		    put(isLastSeries?Transition.END: Transition.NEXT, Action.DEFAULT);
		}
	    } else {
		if (ser.episodes.size()<ser.para.getMaxBoards()) {
		    put(Transition.MAIN, Action.DEFAULT);
		    put(Transition.NEXT, Action.GIVE_UP);
		} else {
		    put(isLastSeries?Transition.END: Transition.NEXT, Action.DEFAULT);
		}

		if (canActivateBonus())  put(Transition.BONUS, Action.ACTIVATE);
	    }
	}
    }

}
 
