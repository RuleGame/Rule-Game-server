package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.rest.ParaSet.Incentive;

import jakarta.xml.bind.annotation.XmlElement; 

/** An EpisodeInfo instance extends an Episode, containing additional
    information related to it being played as part of an
    experiment. That includes support for creating an Episode based on
    a parameter set, and for managing earned reward amount.
 */
@Entity  
public class EpisodeInfo extends Episode {

    /** Since 4.007, this will fully activate the mode proposed by
	Erick Pulick, "to treat stalemates identical with board
	clearings" */
    static final boolean stalematesAsClears = true;

    /** Back link to the player, for JPA's use */
    @ManyToOne(fetch = FetchType.EAGER)
    private PlayerInfo player;
    public PlayerInfo getPlayer() { return player; }
    public void setPlayer(PlayerInfo _player) { player = _player; }

    public static HashMap<String, EpisodeInfo> globalAllEpisodes = new HashMap<>();
    public static EpisodeInfo locateEpisode(String eid) {
	return globalAllEpisodes.get(eid);
    }
    public void cache() {
    	globalAllEpisodes.put(episodeId, this);
    }

    
    Date endTime;
    /** This is an afterthough, just for saving in SQL server. */	
    int finishCode;
    void updateFinishCode() {
	finishCode = getFinishCode();
    }

    /** Is this episode part of the bonus series? (For Incentive.BONUS scheme) */
    boolean bonus;
    public boolean isBonus() { return bonus; }
    public void setBonus(boolean _bonus) { bonus = _bonus; }

    /** Set to true if this was one of the "successful bonus
	episodes", i.e.  bonus-series episode, and the board was
	cleared (or, since 4.007, stalemated) quickly enough for the
	bonus series to continue. */
    boolean bonusSuccessful;
   /** True if the bonus rewarded has been given for this
    episode. This typically is the last episode of a successful
    bonus subseries. */     
    boolean earnedBonus;
    /** The standard reward that has been given for this episode. It is assigned for every episode that has been completed (board cleared), and perhaps
(depending on the stalematesAsClears flag) also for stalemated episodes. */
    int rewardMain;
    /** The bonus reward that has been given for this episode. This is only applicable to the BONUS reward scheme. At most one episode in a series (namely, the last episode of a successful bonus subseries) may have this reward; this episode is marked with earnedBonus=true */
    int rewardBonus;

    /** The total reward earned in this episode */
    int getTotalRewardEarned() { return rewardMain +  rewardBonus; }
    
    /** Indicates the number of the series (within a player's set of
	episodes) to which this episode belongs. This is used during
	deserialization. */
    int seriesNo;
    public int getSeriesNo() { return seriesNo; }
    public void setSeriesNo(int _seriesNo) { seriesNo = _seriesNo; }

    private int displaySeriesNo;
 
    
    private PlayerInfo.Series mySeries() {
	return  getPlayer().getSeries(getSeriesNo());
    }

    
    @Basic
    boolean guessSaved;
    public boolean getGuessSaved() { return guessSaved; }
    public void setGuessSaved(boolean _guessSaved) { guessSaved = _guessSaved; }

    /** The default length is varchar(255) */
    @Basic
    String guess = null;	
    public String getGuess() { return guess; }
    /** Sets the guess value, truncating it if necessary */
    public void setGuess(String _guess) {
	final int L =255;
	if (_guess.length()>L)  _guess = _guess.substring(0,L);
	guess = _guess;
    }    

    @Basic
    int guessConfidence;
    public int getGuessConfidence() { return guessConfidence; }
    public void setGuessConfidence(int _guessConfidence) { guessConfidence = _guessConfidence; }

    /** If we look at the sequence of all move/pick attempts in this
	series up to and including this episode (including the last [so
	far] attempt of this episode), does it end in a sequence of
	successful ones? How long is this sequence? Once the episode is
	completed, this episode's number is preserved in this episode's
	SQL server record.
    */
    int lastStretch;
    public int getLastStretch() { return lastStretch; }
    @XmlElement
    public void setLastStretch(int _lastStretch) { lastStretch = _lastStretch; }

    /** Bayesian-based intervention */
    double lastR;
    public double getLastR() { return lastR; }
    @XmlElement
    public void setLastR(double _lastR) { lastR = _lastR; }

    
    /** In a series with the DOUBLING (or LIKELIHOOD) incentive scheme, the episode
	that triggered x2 for the series has the value of 2 stored
	here, and the episode that triggered x4 has 4 stored here. 
	The default value for this field (stored in all other episodes)
	is 0.
     */
    int xFactor;
    public int getXFactor() { return xFactor; }
    @XmlElement
    public void setXFactor(int _xFactor) { xFactor = _xFactor; }

    /** If the default no-arg constructor is used when restoring a series
	from the SQL database, the ParaSet needs to be set again */
    @Transient
    //final
	private ParaSet para;
    public ParaSet xgetPara() { return para;}

    /** This is used after the episode has been restored from the database */
    void restorePara(ParaSet _para) {
	if (para==null) para=_para;
    }

	
    double xgetPickCost() {
	return para.getPickCost();
    }

    Incentive xgetIncentive() {
	return para.getIncentive();
    }
    
    @Transient
    final private double clearingThreshold;

    /** Dummy episode, used for sending error mesages only */
    public EpisodeInfo() {
	super();
	clearingThreshold = 1.0; // does not matter
    }

    public EpisodeInfo(Game game, ParaSet _para) {
	super(game, Episode.OutputMode.BRIEF, null, null);
	para = _para;
	clearingThreshold = (para!=null && xgetIncentive()==Incentive.BONUS)?
	    para.getClearingThreshold(): 1.0;
    }


    /** Creates a new episode, whose rules and initial board are based (with 
	appropriate randomization) on a specified parameter set */
    static EpisodeInfo mkEpisodeInfo(int seriesNo, int displaySeriesNo, GameGenerator gg, ParaSet para, boolean bonus)
	throws IOException, RuleParseException {
	   
	Game game = gg.nextGame();
	EpisodeInfo epi = new EpisodeInfo(game, para);
	epi.bonus = bonus;
	epi.seriesNo = seriesNo;
	epi.displaySeriesNo = displaySeriesNo;
	
	epi.cache();
	return epi;	    	      
    }


    /** An episode deserves a bonus if it was part of the bonus series,
	has been completed, and was completed sufficiently quickly */
    //    boolean deservesBonus() {
    //	bonusSuccessful = bonusSuccessful ||
    //	    (bonus && cleared && movesLeftToStayInBonus()>=0);
    //	return bonusSuccessful;
    //    }

    /** Our GUI tells the player where all movable pieces are, unless the 
	para set mandates "free" mode.
     */
    boolean weShowAllMovables() {
	return !para.isFeedbackSwitchesFree();
    }
    
    /** The player must clear the board within this many move attempts in
	order to stay in the bonus series. This is only defined during
	bonus episodes. */
    private Double  movesLeftToStayInBonus() {
	if (!bonus) return null;
	double x = (getNPiecesStart()*clearingThreshold) - attemptSpent;
	if (!para.isFeedbackSwitchesFree() || para.pickCostIsInt()) { // all int
	    x = (int)x;
	}
	return x;
	
	//	return bonus?
	//	    (int)(getNPiecesStart()*clearingThreshold) -  attemptCnt :
	//	    null;
    }

    /** An episode was part of a bonus series, but has permanently failed to earn the
	bonus */
    //    boolean failedBonus() {
    //	return bonus && (givenUp || stalemate || lost || cleared && !deservesBonus());
    //    }

    /** An allowance for rounding */
    private static final double eps = 1e-6;

    /** Calls Episode.doMove, and then does various adjustments related to 
	this episode's role in the experiment plan.  If the player has
	failed to complete a bonus episode on time, this is the place
	that sets the "lost" flag.
     */
    public ExtendedDisplay doMove(int y, int x, int by, int bx, int _attemptCnt) throws IOException {
	Display _q = super.doMove(y, x, by, bx, _attemptCnt);

	Pick move = _q.pick;
	
	return processMove(_q, move);
    }

    public ExtendedDisplay doPick(int y, int x, int _attemptCnt) throws IOException {
	Display _q = super.doPick(y, x, _attemptCnt);
	Pick pick = _q.pick;
	return processMove(_q, pick);
    }

    /** This is set when the player first reach a new threshold. The info
     should be kept until the end of the episode. */
    private int factorPromised = 0;
    private boolean justReachedX2=false, justReachedX4=false;
    
    /** The common part of doMove and doPick: after a move or pick has
	been completed, see how it affects the current state of the
	episode.  

	<p>
	This method takes care of the issues related to the incentive scheme.

	<p>
	The player fails a bonus episode if there are still
	pieces on the board, but less than 1 move left in the budget.

	<p>
        If the "DOUBLING" (or "LIKELIHOOD") incentive scheme is in
	effect, the xFactor (2 or 4) is "promised" when the successful
	stretch reaches the required length (or R passes the required
	threshold), but is "crystallized" (actually become applicable)
	only when the episode is successfully completed (cleared or
	stalemated).
	
	<p>
	Doubling (and LIKELIHOOD): successful picks are ignored in
	stretch (and R) calculations, to prevent the player from gaming the
	system.

 */
    private ExtendedDisplay processMove(Display _q, Pick move) throws IOException  {
	boolean isMove = (move instanceof Move);
	justReachedX2=justReachedX4=false;

	double prevR = lastR;
	
	if (_q.code==CODE.ACCEPT) {
	    // count succcessful moves only, but ignore successful picks
	    if (isMove) {
		lastStretch++;
		if (lastR==0) lastR=1;
		lastR *= move.getRValue();
	    }
	} else { // failed move or pick
	    lastStretch=0;
	    lastR = 0;
	}

	
	if (xgetIncentive()==Incentive.DOUBLING) {
	    final int x2=para.getInt("x2_after"), x4=para.getInt("x4_after");

	    PlayerInfo.Series ser =  mySeries();
	    int f = ser.findXFactor();

	    if (f < 4 && factorPromised < 4 && lastStretch>=x4) {
		factorPromised = 4;
		//setXFactor(4);
		justReachedX4=true;
		if (!cleared) earlyWin = true;
	    } else if (f<2 && factorPromised < 2 && lastStretch>=x2) {
		factorPromised = 2;
		//setXFactor(2);
		justReachedX2=true;
	    }
	   
	    if (cleared || earlyWin ||
		stalematesAsClears && stalemate) {
		if (getXFactor()<factorPromised) setXFactor(factorPromised);
	    }

	}

	if (xgetIncentive()==Incentive.LIKELIHOOD) {
	    final double x2=para.getInt("x2_likelihood"), x4=para.getInt("x4_likelihood");

	    PlayerInfo.Series ser =  mySeries();
	    int f = ser.findXFactor();

	    if (f < 4 && factorPromised < 4 && lastR >=x4) {
		factorPromised = 4;
		//setXFactor(4);
		justReachedX4=true;
		if (!cleared) earlyWin = true;
		Logging.info("Setting factorPromised=" + factorPromised);
	    } else if (f <2 && factorPromised < 2 && lastR>=x2) {
		factorPromised = 2;
		//setXFactor(2);
		justReachedX2=true;
		Logging.info("Setting factorPromised=" + factorPromised);
	    } else {
		Logging.info("Keeping factorPromised=" + factorPromised +", as lastR=" + lastR + " for x2="+x2+", x4="+x4);
	    }
	   
	    if (cleared || earlyWin ||
		stalematesAsClears && stalemate) {
		if (getXFactor()<factorPromised) setXFactor(factorPromised);
	    }

	}

	
	if (bonus) {
	    if (isCompleted()) {
		if (movesLeftToStayInBonus()< -eps) { // has run out of moves
		    lost=true;
		    Logging.info("PM: Lost (cleared, but with negative balance), episodeId=" + episodeId);
		    bonusSuccessful = false;
		} else { 
		    lost=false;		    
		    bonusSuccessful = cleared ||
		       stalemate && stalematesAsClears;
		        // can stay in bonus if cleared (or stalemated)
		    Logging.info("PM: Completed (bonusSuccesful="+bonusSuccessful+"), episodeId=" + episodeId);
		}
	    } else {  // there are still pieces of the board...
		// so the player has lost if less than 1 move left
		lost = movesLeftToStayInBonus()< 1.0-eps;
		Logging.info("PM: not cleared yet, lost="+lost+", episodeId=" + episodeId);
	    }
	}
	    	
	if (isCompleted() && getPlayer()!=null) {
	    getPlayer().ended(this);
	}
	// just so that it would get persisted correctly
	updateFinishCode();
	// must do ExtendedDisplay after the "ended()" call, to have correct reward!
	// must convert to ExtendedDisplay to get params such as "moves left"
	ExtendedDisplay q = new ExtendedDisplay(_q);
	return q;
    }

    /** Concise report, handy for debugging */
    public String report() {
	return "["+episodeId+"; FC="+getFinishCode()+
	    (getGuessSaved()? "g" : "") +   	    "; "+
	    (earnedBonus? "BB" :
	     bonusSuccessful? "B" :
	     bonus&lost? "L" :
	     bonus?"b":"m")+" " +
	    attemptCnt + "/"+getNPiecesStart()  +
	    " $"+getTotalRewardEarned()+"]";
    }
    
    /** Shows tHe current board (including dropped pieces, which are labeled as such) */
    public Board getCurrentBoard() {
	return getCurrentBoard(true);
    }

 

    /** Provides some extra information related to the episode's
	context within the experiment. The assumption is that this
	episode is the most recent ones. This structure is converted
	to JSON and sent to the GUI client as the response of the
	/display and /move calls, as well as as one of the components
	of the responses to the /newEpisode and /mostRecentEpisosde calls.
	The list of the fields here is based on what Kevin said the 
	GUI tool needs to render the board and messages around it.
     */
    public class ExtendedDisplay extends Display {

	ExtendedDisplay(int _code,  String _errmsg) {
	    this(_code, _errmsg, false);
	}
 
	/** @param dummy If true, the returned structure will contain
	    just the error message and no real data.
	*/
	private ExtendedDisplay(int _code, 	String _errmsg, boolean dummy) {
	    super(_code, _errmsg);
	    if (dummy) return;
	    bonus = EpisodeInfo.this.isBonus();
	    seriesNo = EpisodeInfo.this.getSeriesNo();
	    displaySeriesNo = EpisodeInfo.this.displaySeriesNo;

	    if (getPlayer()!=null) {
		PlayerInfo p = getPlayer();

		trialListId = p.getTrialListId();
		
		episodeNo = p.seriesSize(seriesNo)-1;	    
		displayEpisodeNo = p.getSuperseriesSize(seriesNo)-1;	    

		bonusEpisodeNo = bonus? p.countBonusEpisodes(seriesNo)-1 : 0;

		canActivateBonus = p.canActivateBonus();
		totalRewardEarned = p.getTotalRewardEarned();

		totalBoardsPredicted = p.totalBoardsPredicted();

		ParaSet para=p.getPara(EpisodeInfo.this);
		ruleSetName = para.getRuleSetName();
		movesLeftToStayInBonus = EpisodeInfo.this.movesLeftToStayInBonus();

		double d = attemptSpent - doneMoveCnt;
		rewardRange = para.kantorLupyanRewardRange(d);

		
		if (getFinishCode()!=FINISH_CODE.NO) {
		    transitionMap = p.new TransitionMap();
		}

		incentive2();
		
		errmsg += "\nDEBUG\n" + getPlayer().report();


		if (EpisodeInfo.this.isNotPlayable()) {
		    String msg = "Sadly, you cannot continue playing, because the server has been restarted since the last episode, and the board has been purged out of the server memory. This problem could perhaps have been prevented if the client had made a /newEpisode call, rather than /display, after the last /mostRecentEpisode.";
		    setErrmsg(msg);
		    setError( true);		
		}

		faces =p.computeFaces(EpisodeInfo.this);

		
	    }
	    //	    Logging.info("Prepared EpisodeInfo.ExtendedDisplay=" +
	    //		 JsonReflect.reflectToJSONObject(this, true));

	}
	ExtendedDisplay(Display d) {
	    this(d.code, d.errmsg);
	}

	boolean bonus;
	/** True if this episode is part of a bonus subseries. */
	public boolean isBonus() { return bonus; }
	//	@XmlElement
	//	public void setBonus(boolean _bonus) { bonus = _bonus; }

	int totalRewardEarned=0;
	/** The total reward earned by this player so far, including the regular rewards and any bonuses, for all episodes. */
	public int getTotalRewardEarned() { return totalRewardEarned; }
	//@XmlElement
	//public void setTotalRewardEarned(int _totalRewardEarned) { totalRewardEarned = _totalRewardEarned; }
	
	int seriesNo;
	/** The number of the current series (zero-based) among all series in the trial list.
	    This can also be interpreted as the number of the preceding series that have been completed or given up by this player.
	*/
	public int getSeriesNo() { return seriesNo; }

	int displaySeriesNo;
	/** The number of the current super-series (zero-based) among all super-series in the trial list. This is used in the "Rule XXX" display element in the GUI. (Introduced in GS ver. 5.008)
	*/
	public int getDisplaySeriesNo() { return displaySeriesNo; }

	
	int episodeNo;
	/** The number of this episode within the current (internal) series (zero-based).
	    This can also be interpreted as the number of the preceding episodes (completed or given up) in this series.
	*/
	public int getEpisodeNo() { return episodeNo; }

	int displayEpisodeNo;
	/** Introduced in ver. 5.008, this represent the number of
	    this episode within the current super-series (zero-based).
	*/
	public int getDisplayEpisodeNo() { return displayEpisodeNo; }

	
	
	int bonusEpisodeNo;
	/** The number of bonus episodes that have been completed (or given up) prior to
	    the beginning of this episode. */
	public int getBonusEpisodeNo() { return bonusEpisodeNo; }

	boolean canActivateBonus;
	/** This is set to true if an "Activate Bonus" button can be
	displayed now, i.e. the player is eligible to start bonus
	episodes, but has not done that yet */
	public boolean getCanActivateBonus() { return canActivateBonus; }	

	int totalBoardsPredicted;
	/** Based on the current situation, what is the maximum number
	    of episodes that can be run within the current series? 
	    (Until max_boards is reached, if in the main subseries, 
	    or until the bonus is earned, if in the bonus subseries).
	*/
	public int getTotalBoardsPredicted() { return totalBoardsPredicted; }

	boolean guessSaved =  EpisodeInfo.this.guessSaved;
	/** True if the player's guess has been recorded at the end of this episode */
	public boolean getGuessSaved() { return guessSaved; }

	Double movesLeftToStayInBonus = null;
	/**
<ul>
<li>If it's not a bonus episode, null is returned.
<li>If it's a bonus episode, we return the number X such that if, starting from this point, the player must clear the board in no more than X additional moves in order not to be ejected from the bonus series. The number 0, or a negative number, means  that, unless the board has just been cleared, the player will be ejected from the bonus series at the end of the current episode (i.e. once he eventually clears it). A negative number means that the player has already made more move attempts than he's allowed to make in order to stay in the bonus series.
</ul>
	 */
	public Double getMovesLeftToStayInBonus() { return movesLeftToStayInBonus; }


	/** (min,max) */
	int[] rewardRange;
	public int[] getRewardRange() { return rewardRange; }
	
	
	PlayerInfo.TransitionMap transitionMap=null;
	/** Describes the possible transitions (another episode in the
	    same series, new series, etc) which can be effected after this
	    episode. This can be used to generate transition buttons.	
	    This field appears in JSON (i.e. is not null) only if the episode
	    is finished, i.e. finishCode==0.
	*/
	public PlayerInfo.TransitionMap getTransitionMap() { return transitionMap; }

	String trialListId;
	String ruleSetName;
	public String getTrialListId() { return trialListId; }
        public String getRuleSetName() { return ruleSetName; }

	/** Indicators for the incentive scheme (BONUS, DOUBLING, LIKELIHOOD)*/
	Incentive incentive;
        public Incentive getIncentive() { return incentive; }

	int lastStretch;
	public int getLastStretch() { return lastStretch; }

	/** The R-value for the current continuous sequence of successful moves (for the Bayesian-based intervention) */
	double lastR;
	public double getLastR() { return lastR; }


	/** For GS 4.006, this represents all moves (and picks) 
	    in all episodes in this series, with a true for 
	    each success, and a false for each failure */	   
	private Vector<Boolean> faces =  null;
	public Vector<Boolean> getFaces() { return faces; }

	
	/** The components of the total reward: an array of
	    (reward,factor) pairs for all series so far.
	 */
	PlayerInfo.RewardsAndFactorsPerSeries rewardsAndFactorsPerSeries;
	/** Have we just reached this threshold on the most recent move? */
	boolean justReachedX2=false, justReachedX4=false;
	/** This factor (1,2,4) will be given out for certain, because
	    it has been achieved at one of the completed episodes in
	    this series.
	 */
	int factorAchieved;
	/** This factor (1,2,4) will be in effect once the current
	    episode has been completed. This may be higher than
	    factorAchieved between the point when a new threshold has
	    been achieved in this episode and the completion of the episode */
	int factorPromised;
	public int[][] getRewardsAndFactorsPerSeries() {
	    if (rewardsAndFactorsPerSeries==null) {
		// Why does this happen anyway?
		Logging.warning("rewardsAndFactorsPerSeries==null");
		return new int[0][];
	    }
	    return rewardsAndFactorsPerSeries.raw; }

	public String xgetRewardsAndFactorsPerSeriesString() {
	    return rewardsAndFactorsPerSeries==null? "":
		rewardsAndFactorsPerSeries.toString(); }
   	
	public boolean getJustReachedX2() { return justReachedX2; }
	public boolean getJustReachedX4() { return justReachedX4; }
        public int getFactorAchieved() { return factorAchieved; }
        public int getFactorPromised() { return factorPromised; }

	/** Sets a few fields related to the DOUBLING or LIKELIHOOD incentive scheme */
	private void incentive2() {	    
	    incentive = xgetIncentive();
	    lastStretch = EpisodeInfo.this.lastStretch;
	    lastR = EpisodeInfo.this.lastR;
	    rewardsAndFactorsPerSeries = getPlayer().getRewardsAndFactorsPerSeries();
	    Logging.info("EpisodeInfo.ED.incentive2(): obtained rewardsAndFactorsPerSeries = " + rewardsAndFactorsPerSeries);
	    justReachedX2 = EpisodeInfo.this.justReachedX2;
 	    justReachedX4 = EpisodeInfo.this.justReachedX4;

	    factorAchieved=1;
	    try {
		factorAchieved = rewardsAndFactorsPerSeries.getFactorAchieved();
	    } catch(Exception ex) {
		Logging.warning("EpisodeInfo.ED.incentive2(): exception: " + ex);
	    } // fixme - silly fix for Kevin's report ,2022-01-28
	    factorPromised =  EpisodeInfo.this.factorPromised;
	}
	
    }
    
    /** Builds a display to be sent out over the web UI */
    public ExtendedDisplay mkDisplay() {
    	return new ExtendedDisplay(Episode.CODE.JUST_A_DISPLAY, "Display requested");
    }

    public ExtendedDisplay dummyDisplay(int _code, 	String _errmsg) {
	return new ExtendedDisplay(_code, _errmsg, true);
    }



   void saveDetailedTranscriptToFile(File f) {

       final String[] keys = 
	   { "playerId",
	     "trialListId",  // string "trial_1"
	     "seriesNo",  // 0-based
	     "ruleId", // "TD-5"
	     "episodeNo", // position of the episode in the series, 0-based
	     "episodeId",
	     "moveNo", // 0-based number of the move in the transcript
	     "timestamp", // YYYYMMDD-hhmmss.sss
	     "reactionTime", // (* diff ; also use e.startTime)
	     "objectType", // "yellow_circle" in GS 1&2; image.png for image-based objects in GS 3
	     "objectId", // Typically 0-based index within the episode's object list
	     "y", "x",
	     "bucketId", // 0 thru 3
	     "by", "bx",
	     "code", 
	     "objectCnt", // how many pieces are left on the board after this move
	   };

       HashMap<String, Object> h = new HashMap<>();
       PlayerInfo x = getPlayer();
       int moveNo=0;
       Date prevTime = getStartTime();
       int objectCnt = getNPiecesStart();
       Vector<String> lines=new  Vector<String>();
       for(Pick move: transcript) {
	   h.clear();
	   h.put( "playerId", x.getPlayerId());
	   h.put( "trialListId", x.getTrialListId());
	   h.put( "seriesNo", getSeriesNo());
	   PlayerInfo.Series ser =  mySeries();
	   h.put( "ruleId",  ser.para.getRuleSetName());
	   h.put( "episodeNo", ser.episodes.indexOf(this));
	   h.put( "episodeId", getEpisodeId());	   
	   h.put( "moveNo", moveNo++);
	   h.put( "timestamp", 	sdf2.format(move.time));
	   long msec = move.time.getTime() - prevTime.getTime();
	   h.put(  "reactionTime", "" + (double)msec/1000.0);
	   prevTime = move.time;
	   // can be null if the player tried to move a non-existent piece,
	   // which the GUI normally prohibits
	   Piece piece = move.piece; 
	   h.put("objectType", (piece==null? "": move.piece.objectType()));
	   h.put("objectId",  (piece==null? "": move.piece.getId()));
	   Board.Pos q = new Board.Pos(move.pos);
	   h.put("y", q.y);
	   h.put("x", q.x);

	   if (move instanceof Move) { // a real move with a destination
	       Move m = (Move)move;
	       h.put("bucketId", m.bucketNo);	   
	       Board.Pos b = Board.buckets[m.bucketNo];
	       h.put("by", b.y);
	       h.put("bx", b.x);	       
	   } else { // just a pick -- no destination
	       h.put("bucketId", "");
	       h.put("by", "");
	       h.put("bx", "");	       
	   }

	   
	   h.put("code",move.code);
	   if (move instanceof Move && move.code==CODE.ACCEPT) 	   objectCnt--;
	   h.put("objectCnt",objectCnt);
	   Vector<String> v = new Vector<>();
	   for(String key: keys) v.add("" + h.get(key));
	   lines.add(String.join(",", v));
       }
          
       
       synchronized(file_writing_lock) {
	   try {	    
	       PrintWriter w = new PrintWriter(new	FileWriter(f, true));
	       if (f.length()==0) w.println("#" + String.join(",", keys));
	       for(String line: lines) {
		   w.println(line);
	       }
	       w.close();
	   } catch(IOException ex) {
	       System.err.println("Error writing the transcript: " + ex);
	       ex.printStackTrace(System.err);
	   }	    
       }
   }
	
    /** Records the player-provided "guess" to a CSV file */
    public void saveGuessToFile(File f, String guessText, int confidence) {
	     final String[] keys = 
	   { "playerId",
	     "trialListId",  // string "trial_1"
	     "seriesNo",
	     "ruleId", // "TD-5"
	     "episodeNo", // position of the episode in the series, 0-based
	     "episodeId",
	     "guess",
	     "guessConfidence"
	   };

       HashMap<String, Object> h = new HashMap<>();
       PlayerInfo x = getPlayer();
       int moveNo=0;
       Date prevTime = getStartTime();
       int objectCnt = getNPiecesStart();
       h.clear();
       h.put( "playerId", x.getPlayerId());
       h.put( "trialListId", x.getTrialListId());
       h.put( "seriesNo", getSeriesNo());
       PlayerInfo.Series ser =  mySeries();
       h.put( "ruleId",  ser.para.getRuleSetName());
       h.put( "episodeNo", ser.episodes.indexOf(this));
       h.put( "episodeId", getEpisodeId());	   
       h.put( "guess",   ImportCSV.escape(guessText));
       h.put( "guessConfidence",   confidence);
       Vector<String> v = new Vector<>();
       for(String key: keys) v.add("" + h.get(key));
       String line = String.join(",", v);

       synchronized(file_writing_lock) {
	   try {	    
	       PrintWriter w = new PrintWriter(new	FileWriter(f, true));
	       if (f.length()==0) w.println("#" + String.join(",", keys));
	       w.println(line);
	       w.close();
	   } catch(IOException ex) {
	       System.err.println("Error writing the guess: " + ex);
	       ex.printStackTrace(System.err);
	   }	    
       }
         
    }

    
    
}
