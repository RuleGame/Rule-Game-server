package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.rest.ParaSet;

import javax.xml.bind.annotation.XmlElement; 

/** An EpisodeInfo instance extends an Episode, containing additional
    information related to it being played as part of an
    experiment. That includes support for creating an Episode based on
    a parameter set, and for managing earned reward amount.
 */
@Entity  
public class EpisodeInfo extends Episode {

    /** Back link to the player, for JPA's use */
    @ManyToOne(fetch = FetchType.EAGER)
    private PlayerInfo player;
    public PlayerInfo getPlayer() { return player; }
    public void setPlayer(PlayerInfo _player) { player = _player; }


    public static HashMap<String, Episode> globalAllEpisodes = new HashMap<>();
    public static Episode locateEpisode(String eid) {
	return globalAllEpisodes.get(eid);
    }
    
    Date endTime;
    int finishCode;
    /** Is this episode part of the bonus series? */
    boolean bonus;
    public boolean isBonus() { return bonus; }
    public void setBonus(boolean _bonus) { bonus = _bonus; }

    /** Set to true if this was a bonus-series episode, and the board was cleared
	quickly enough for the bonus series to continue. */
    boolean earnedBonus;
    /** The standard reward that has been given for this episode */
    int rewardMain;
    /** The bonus reward that has been given for this episode. This value normally
	appears in the last episode of a successful bonus subseries. */
    int rewardBonus;

    /** The total reward earned in this episode */
    int getTotalRewardEarned() { return rewardMain +  rewardBonus; }
    
    /** Indicates the number of the series (within a player's set of
	episodes) to which this episode belongs. This is used during
	deserialization. */
    int seriesNo;
    public int getSeriesNo() { return seriesNo; }
    public void setSeriesNo(int _seriesNo) { seriesNo = _seriesNo; }

    @Basic
    boolean guessSaved;
    public boolean getGuessSaved() { return guessSaved; }
    public void setGuessSaved(boolean _guessSaved) { guessSaved = _guessSaved; }

    
    EpisodeInfo(Game game) {
	super(game, Episode.OutputMode.BRIEF, null, null);
    }
    
    /** Creates a new episode, whose rules and initial board are based (with 
	appropriate randomization) on a specified parameter set */
    static EpisodeInfo mkEpisodeInfo(int seriesNo, ParaSet para, boolean bonus)
	throws IOException, RuleParseException {

	String ruleSetName = para.getRuleSetName();
	int[] nPiecesRange = {para.getInt("min_objects"),
			      para.getInt("max_objects")},
	    nShapesRange = {para.getInt("min_shapes"),
			    para.getInt("max_shapes")},
	    nColorsRange = {para.getInt("min_colors"),
			    para.getInt("max_colors")};

	GameGenerator gg =new 	GameGenerator(ruleSetName, nPiecesRange, nShapesRange,
					      nColorsRange);    
	   
	Game game = gg.nextGame();
	EpisodeInfo epi = new EpisodeInfo(game);
	epi.bonus = bonus;
	epi.seriesNo = seriesNo;
	
	globalAllEpisodes.put(epi.episodeId, epi);
	return epi;	    	      
    }

    /** An episode deserves a bonus if it was part of the bonus series,
	has been completed, and was completed sufficiently quickly */
    boolean deservesBonus(double clearingThreshold ) {
	earnedBonus = earnedBonus ||
	    (bonus && cleared && attemptCnt <= getNPiecesStart() * clearingThreshold);
	return earnedBonus;
    }

    /** An episode was part of a bonus series, but has permanently failed to earn the
	bonus */
    boolean failedBonus(double clearingThreshold ) {
	return bonus && (givenUp || stalemate || cleared && !deservesBonus(clearingThreshold));
    }

    public Display doMove(int y, int x, int by, int bx, int _attemptCnt) {
	Display _q = super.doMove(y, x, by, bx, _attemptCnt);
	if (isCompleted() && getPlayer()!=null) {
	    getPlayer().ended(this);
	}
	// must do ExtendedDisplay after the "ended()" call, to have correct reward!
	ExtendedDisplay q = new ExtendedDisplay(_q);
	return q;
    }
    
    
    /** Concise report, handy for debugging */
    public String report() {
	return "["+episodeId+"; FC="+getFinishCode()+"; "+(bonus?"B":"M")+" " + attemptCnt + "/"+getNPiecesStart()  + " $"+getTotalRewardEarned()+"]";
    }
    
    /** Shows tHe current board (including dropped pieces, which are labeled as such) */
    public Board getCurrentBoard() {
	return getCurrentBoard(true);
    }

 

    /** Provides some extra information related to the episode's context
	within the experiment. The assumption is that this episode 
	is the most recent ones.
     */
    public class ExtendedDisplay extends Display {
	ExtendedDisplay(int _code, 	String _errmsg) {
	    super(_code, _errmsg);
	    bonus = EpisodeInfo.this.isBonus();
	    seriesNo = EpisodeInfo.this.getSeriesNo();


	    if (getPlayer()!=null) {
		PlayerInfo p = getPlayer();
		episodeNo = p.seriesSize(seriesNo)-1;	    
		bonusEpisodeNo = bonus? p.countBonusEpisodes(seriesNo)-1 : 0;

		canActivateBonus = p.canActivateBonus();
		totalRewardEarned = p.getTotalRewardEarned();

		totalBoardsPredicted = p.totalBoardsPredicted();
		guessSaved =  EpisodeInfo.this.guessSaved;
		
		errmsg += "\nDEBUG\n" + getPlayer().report();
	    }	       
	}
	ExtendedDisplay(Display d) {
	    this(d.code, d.errmsg);
	}

	boolean bonus;
	public boolean isBonus() { return bonus; }
	@XmlElement
	public void setBonus(boolean _bonus) { bonus = _bonus; }

	/** Totals for the player; only used in web GUI */
	int totalRewardEarned=0;
	public int getTotalRewardEarned() { return totalRewardEarned; }
	@XmlElement
	public void setTotalRewardEarned(int _totalRewardEarned) { totalRewardEarned = _totalRewardEarned; }
	
	/** The number of the current series (zero-based) among all series in the trial list.
	    This can also be interpreted as the number of the preceding series that have been completed or given up by this player.
	*/
	int seriesNo;
	public int getSeriesNo() { return seriesNo; }
	@XmlElement
	public void setSeriesNo(int _seriesNo) { seriesNo = _seriesNo; }
	
	/** The number of this episode within the current series (zero-based).
	    This can also be interpreted as the number of the preceding episodes (completed or given up) in this series.
	*/
	int episodeNo;
	public int getEpisodeNo() { return episodeNo; }
	@XmlElement
	public void setEpisodeNo(int _episodeNo) { episodeNo = _episodeNo; }
	
	/** The number of bonus episodes that have been completed (or given up) prior to
	    the beginning of this episode. */
	int bonusEpisodeNo;
	public int getBonusEpisodeNo() { return bonusEpisodeNo; }
	@XmlElement
	public void setBonusEpisodeNo(int _bonusEpisodeNo) { bonusEpisodeNo = _bonusEpisodeNo; }

	    /** This is set to true if an "Activate Bonus" button can be displayed,
	i.e. the player is eligible to start bonus episodes, but has not done that 
	yet */
	boolean canActivateBonus;
	public boolean getCanActivateBonus() { return canActivateBonus; }
	@XmlElement
	public void setCanActivateBonus(boolean _canActivateBonus) { canActivateBonus = _canActivateBonus; }
	

	int totalBoardsPredicted;
	/** Based on the current situation, what is the maximum number
	    of episodes that can be run within the current series? 
	    (Until max_boards is reached, if in the main subseries, 
	    or until the bonus is earned, if in the bonus subseries).
	*/
	public int getTotalBoardsPredicted() { return totalBoardsPredicted; }
        @XmlElement
        public void setTotalBoardsPredicted(int _totalBoardsPredicted) { totalBoardsPredicted = _totalBoardsPredicted; }

	boolean guessSaved;
	public boolean getGuessSaved() { return guessSaved; }
	public void setGuessSaved(boolean _guessSaved) { guessSaved = _guessSaved; }
  
	
    }
    
    /** Builds a display to be sent out over the web UI */
    public Display mkDisplay() {
    	return new ExtendedDisplay(Episode.CODE.JUST_A_DISPLAY, "Display requested");
    }
 
}
