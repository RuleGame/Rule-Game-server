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
    public void cache() {
    	globalAllEpisodes.put(episodeId, this);
    }

    
    Date endTime;
    /** This is an afterthough, just for saving in SQL server. */	
    int finishCode;
    void updateFinishCode() {
	finishCode = getFinishCode();
    }

    /** Is this episode part of the bonus series? */
    boolean bonus;
    public boolean isBonus() { return bonus; }
    public void setBonus(boolean _bonus) { bonus = _bonus; }

    /** Set to true if this was one of the "successful bonus episodes", i.e. 
	bonus-series episode, and the board
	was cleared quickly enough for the bonus series to
	continue. */
    boolean bonusSuccessful;
   /** True if the bonus rewarded has been given for this
    episode. This typically is the last episode of a successful
    bonus subseries. */     
    boolean earnedBonus;
    /** The standard reward that has been given for this episode. It is assigned for every episode that has been completed (board cleared) */
    int rewardMain;
    /** The bonus reward that has been given for this episode. At most one episode in a series (namely, the last episode of a successful bonus subseries) may have this reward; this episode is marked with earnedBonus=true */
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
    
    @Basic
    String guess = null;	
    public String getGuess() { return guess; }
    public void setGuess(String _guess) { guess = _guess; }

    @Basic
    int guessConfidence;
    public int getGuessConfidence() { return guessConfidence; }
    public void setGuessConfidence(int _guessConfidence) { guessConfidence = _guessConfidence; }

    
    @Transient
    final private ParaSet para;
    public ParaSet xgetPara() { return para;}
	
    double xgetPickCost() {
	return para.getPickCost();
    }

    
    @Transient
    final private double clearingThreshold;
	
    EpisodeInfo(Game game, ParaSet _para) {
	super(game, Episode.OutputMode.BRIEF, null, null);
	para = _para;
	clearingThreshold = (para==null)? 1.0: para.getClearingThreshold();
    }


    /** Creates a new episode, whose rules and initial board are based (with 
	appropriate randomization) on a specified parameter set */
    static EpisodeInfo mkEpisodeInfo(int seriesNo, GameGenerator gg, ParaSet para, boolean bonus)
	throws IOException, RuleParseException {
	   
	Game game = gg.nextGame();
	EpisodeInfo epi = new EpisodeInfo(game, para);
	epi.bonus = bonus;
	epi.seriesNo = seriesNo;

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

    /** We tell the player where all movable pieces are, unless the 
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
	return new Double(x);
	
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
    public Display doMove(int y, int x, int by, int bx, int _attemptCnt) throws IOException {
	Display _q = super.doMove(y, x, by, bx, _attemptCnt);
	return processMove(_q);
    }

    public Display doPick(int y, int x, int _attemptCnt) throws IOException {
	Display _q = super.doPick(y, x, _attemptCnt);
	return processMove(_q);
    }

    /** The common part of doMove and doPick: after a move or pick has
	been completed, see how it affects the current state of the
	episode.  

	The player fails a bonus episode if there are still
	pieces on the board, but less than 1 move left in the budget.
 */
    private Display processMove(Display _q) throws IOException  {

	if (bonus) {
	    if (isCompleted()) {
		if (movesLeftToStayInBonus()< -eps) { // has run out of moves
		    lost=true;		    
		    bonusSuccessful = false;
		} else { 
		    lost=false;		    
		    bonusSuccessful = cleared;  // can stay in bonus if cleared
		}
	    } else {  // there are still pieces of the board...
		// so the player has lost if less than 1 move left
		lost = movesLeftToStayInBonus()< 1.0-eps;
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
	ExtendedDisplay(int _code, 	String _errmsg) {
	    super(_code, _errmsg);
	    bonus = EpisodeInfo.this.isBonus();
	    seriesNo = EpisodeInfo.this.getSeriesNo();

	    if (getPlayer()!=null) {
		PlayerInfo p = getPlayer();


		trialListId = p.getTrialListId();

		
		episodeNo = p.seriesSize(seriesNo)-1;	    
		bonusEpisodeNo = bonus? p.countBonusEpisodes(seriesNo)-1 : 0;

		canActivateBonus = p.canActivateBonus();
		totalRewardEarned = p.getTotalRewardEarned();

		totalBoardsPredicted = p.totalBoardsPredicted();

		ParaSet para=p.getPara(EpisodeInfo.this);
		ruleSetName = para.getRuleSetName();
		movesLeftToStayInBonus = EpisodeInfo.this.movesLeftToStayInBonus();
	
		if (getFinishCode()!=FINISH_CODE.NO) {
		    transitionMap = p.new TransitionMap();
		}
		
		errmsg += "\nDEBUG\n" + getPlayer().report();
	    }	       
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
	
	int episodeNo;
	/** The number of this episode within the current series (zero-based).
	    This can also be interpreted as the number of the preceding episodes (completed or given up) in this series.
	*/
	public int getEpisodeNo() { return episodeNo; }

	
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
    }
    
    /** Builds a display to be sent out over the web UI */
    public Display mkDisplay() {
    	return new ExtendedDisplay(Episode.CODE.JUST_A_DISPLAY, "Display requested");
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
	   PlayerInfo.Series ser =  x.getSeries(getSeriesNo());
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
	   if (move.code==CODE.ACCEPT) 	   objectCnt--;
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
       PlayerInfo.Series ser =  x.getSeries(getSeriesNo());
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
