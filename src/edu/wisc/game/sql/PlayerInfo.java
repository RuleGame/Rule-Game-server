package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;


import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.rest.TrialList;

/** Information about a player (what trial list he's in) stored in the SQL database.
 */

@Entity  
public class PlayerInfo {
    @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;

    @Basic 
    private String playerId;
    @Basic 
    private String trialListId;

    /** The date of first activity */
    @Basic
    private Date date; 

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String _playerId) { playerId = _playerId; }
    public String getTrialListId() { return trialListId; }
    public void setTrialListId(String _trialListId) { trialListId = _trialListId; }
    public Date getDate() { return date; }
    public void setDate(Date _date) { date = _date; }

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
	//allEpisodes = _allEpisodes;
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

  
    class Series {
	ParaSet para;
	Vector<EpisodeInfo> episodes = new Vector<>();
	//SeriesState state = SeriesState.FUTURE;

	Series(ParaSet _para) {
	    para = _para;
	}
	

	public String toString() {
	    return "(Series: para.rules=" +	para.getRuleSetName() +
		", episode cnt= "+ episodes.size()+")";
	}

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
		    if (x.bonus) {		       
			r++;
			x.earnedBonus = (r==cnt);
			x.rewardBonus= (x.earnedBonus)? para.getInt("bonus_extra_pts"):0;
		    }
		}
		goToNextSeries();
	    }
	}
	
    }

    private Series getCurrentSeries() {
	return currentSeriesNo<allSeries.size()? allSeries.get(currentSeriesNo): null;
    }

    
    /** @return true if an "Activate Bonus" button can be displayed,
	i.e. the player is eligible to start bonus episodes, but has not done that 
	yet */
    public boolean canActivateBonus() {
	Series ser=getCurrentSeries();
	int at = ser.para.getInt("activate_bonus_at");
	boolean answer = ser!=null && !inBonus && ser.episodes.size() >=at;
	System.err.println("canActivateBonus("+playerId+")="+answer+", for series No. "+currentSeriesNo+", size="+ser.episodes.size()+", at="+at);
	return answer;
    } 


    public void activateBonus() {
	if (inBonus) throw new IllegalArgumentException("Bonus already activated in the current series");
	
	if (!canActivateBonus()) throw new IllegalArgumentException("Cannot activate bonus in the current series");
	
	inBonus = true;

	Series ser=getCurrentSeries();
	if (ser!=null && ser.episodes.size()>0) {
	    EpisodeInfo epi = ser.episodes.lastElement();
	    // if it's still running, make it a bonus episode too
	    if (!epi.isCompleted()) {
		epi.bonus=true;
		System.err.println("Bonus activated for current episode " + epi.episodeId);
	    }
	}
	System.err.println("Bonus activated: player="+playerId+", series No. "+currentSeriesNo+", size="+ser.episodes.size());
	Main.persistObjects(this); // this saves the new value of inBonus
    }

    public void giveUp(int seriesNo) {
	if (seriesNo!=currentSeriesNo) throw new IllegalArgumentException("Cannot give up on series " + seriesNo +", because we are presently are on series " + currentSeriesNo);
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


    boolean canHaveAnotherRegularEpisode() {
	Series ser=getCurrentSeries();
	return ser!=null && !inBonus && ser.episodes.size()<ser.para.getMaxBoards();
    } 
	
	
    boolean canHaveAnotherBonusEpisode() {
	Series ser=getCurrentSeries();
	if (ser==null || !inBonus) return false;
	double clearingThreshold = ser.para.getDouble("clearing_threshold");
	int cnt=0;
	for(EpisodeInfo x: ser.episodes) {
	    if (x.isBonus()) {
		cnt++;
		if (!x.isCompleted()) return false;
		if (x.failedBonus(clearingThreshold)) return false;
	    }
	}
	return cnt<ser.para.getInt("clear_how_many");
    }

    
    @Transient
    Vector<Series> allSeries = new Vector<>();

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
    int currentSeriesNo=0;
    /** Will the next episode be a part of a bonus subseries? (Or, if
	the current episode is not completed, is it a part of  a bonus subseries?)
     */
    boolean inBonus;

    
    /** This is used when a player is first registered and a PlayerInfo object is firesst created */
    public void initSeries(TrialList trialList) {
	if (allSeries.size()>0) throw new IllegalArgumentException("Attempt to initialize PlayerInfor.allSeries again");
	allSeries.clear();
	for( ParaSet para: trialList) {
	    allSeries.add(new Series(para));
	}
    }

    /** After restoring the object from the SQL database, re-create some of the necessary non-persistent structures */
    public void restoreTransientFields() {
	TrialList trialList  = new TrialList(playerId, trialListId);
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
	//updateTotalReward();
    }


    
    /** Returns the currently unfinished last episode to be resumed,
	or a new episode, or null if this player has finished with all
	series. */
    public EpisodeInfo episodeToDo() throws IOException, RuleParseException {

	while(currentSeriesNo < allSeries.size()) {
	    
	    Series ser=getCurrentSeries();
	    if (ser!=null && ser.episodes.size()>0) {
		EpisodeInfo x = ser.episodes.lastElement();
		// should we resume the last episode?
		if (!x.isCompleted()) {
		    if (x.isNotPlayable()) {
			x.giveUp();
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

    //@Transient
    int totalRewardEarned;
    public int getTotalRewardEarned() {
	System.err.println("getTotalReward("+playerId+")=" + totalRewardEarned);
	return totalRewardEarned;
    }
    //@XmlElement
    public void setTotalRewardEarned(int _totalRewardEarned) { totalRewardEarned = _totalRewardEarned; }

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
    
    
    /** This method is called after an episode completes. It computes rewards,
     and, if needed, switches the series and subseries. */
    void ended(EpisodeInfo epi) {
	Series ser = whoseEpisode(epi);
	if (ser==null) throw new IllegalArgumentException("Could not figure to which series this episode belongs");
	epi.endTime=new Date();
	
	if (epi.stalemate) {
	    // The experimenters should try to ensure that stalemates
	    // do not happen; but if one does, let just finish this series
	    // to avoid extra annoyance for the player
	    goToNextSeries();
	} else if (epi.cleared) {
	    double smax = ser.para.getDouble("max_points");
	    double smin = ser.para.getDouble("min_points");
	    double b = ser.para.getDouble("b");
	    int d = epi.attemptCnt - epi.nPiecesStart;
	    epi.rewardMain = (int)Math.round( smin + (smax-smin)/(1.0 + Math.exp(b*(d-2))));
	    if (epi.bonus) {
		ser.assignBonus();
	    }
	    updateTotalReward();
	}

	Main.persistObjects(this, epi);
	//Main.persistObjects(this);
	//Main.persistObjects(epi);

   	
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



    
}
 
