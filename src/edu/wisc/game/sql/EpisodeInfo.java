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
import edu.wisc.game.pseudo.Pseudo;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.rest.ParaSet.Incentive;

import edu.wisc.game.websocket.WatchPlayer;

import jakarta.xml.bind.annotation.XmlElement; 

/** An EpisodeInfo instance extends an Episode, containing additional
    information related to it being played as part of an
    experiment. That includes support for creating an Episode based on
    a parameter set, and for managing earned reward amount.
 */
@Entity  
@Access(AccessType.FIELD)
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

   /** (2PG only) The count of all attempts (move and pick) done so far  by Player 1, including successful and unsuccessful ones.
	
     */
    int attemptCnt1=0;
    /** The total cost of all attempts (move and pick) done so far by Player 1 (in 2PG only)
	including successful and unsuccessful ones. If cost_pick!=1,
	this value may be different from attemptCnt. */
    double attemptSpent1=0;
    /** All successful moves (not picks) so far, by Player 1
     */
    int doneMoveCnt1=0;
    /** The count of successful picks (not moves) so far, by Player 1 */
    int successfulPickCnt1=0;


    /** Table with all episodes recently played on this server. It is kept to enable quick lookup
	of episode by id. Episode objects are put into this table upon creation.
	FIXME: should purge the table occasionally, to save memory */
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
    /** The standard reward that has been given for this episode. It
	is assigned for every episode that has been completed (board
	cleared), and perhaps (depending on the stalematesAsClears
	flag) also for stalemated episodes. */
    int rewardMain[] = new int[2];//ZZZZ
    @Access(AccessType.PROPERTY)
    public int getRewardMain() { return rewardMain[0]; }
    public void setRewardMain(int x) { rewardMain[0]=x; }
    @Access(AccessType.PROPERTY)
    public int getRewardMain1() { return rewardMain[1]; }
    public void setRewardMain1(int x) { rewardMain[1]=x; }

    public void setRewardMain(int j, int x) { rewardMain[j]=x; }
    
    /** The bonus reward that has been given for this episode. This is only applicable to the BONUS reward scheme, which is only used in 1PG. At most one episode in a series (namely, the last episode of a successful bonus subseries) may have this reward; this episode is marked with earnedBonus=true */
    int rewardBonus;

    /** The total reward earned in this episode by a particular player */
    int getTotalRewardEarned(int mj) { return rewardMain[mj] +  rewardBonus; }
    
    /** Indicates the number of the series (within a player's set of
	episodes) to which this episode belongs. This is used during
	deserialization. */
    int seriesNo;
    public int getSeriesNo() { return seriesNo; }
    public void setSeriesNo(int _seriesNo) { seriesNo = _seriesNo; }

    private int displaySeriesNo;
 
    /** To which Series does this Episode belong? */
    public PlayerInfo.Series mySeries() {
	return  getPlayer().getSeries(getSeriesNo());
    }

    
    @Basic
    boolean guessSaved;
    /** Has the guess from the player (in 1PG) or from player 0 (in 2PG) been saved? */
    public boolean getGuessSaved() { return guessSaved; }
    public void setGuessSaved(boolean _guessSaved) { guessSaved = _guessSaved; }

    @Basic
    boolean guess1Saved;
    /** Has the guess from player 1 (in 2PG) been saved? */
    public boolean getGuess1Saved() { return guess1Saved; }
    public void setGuess1Saved(boolean _guess1Saved) { guess1Saved = _guess1Saved; }

    /** @return the "guessSaved" fields for a particular player */
    public boolean getGuessSavedBy(int mover) {
	return (mover==Pairing.State.ONE)? guess1Saved : guessSaved;
    }

    
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
    String guess1 = null;	
    public String getGuess1() { return guess1; }
    /** Sets the guess value, truncating it if necessary */
    public void setGuess1(String _guess1) {
	final int L =255;
	if (_guess1.length()>L)  _guess1 = _guess1.substring(0,L);
	guess1 = _guess1;
    }    

    @Basic
    int guessConfidence;
    public int getGuessConfidence() { return guessConfidence; }
    public void setGuessConfidence(int _guessConfidence) { guessConfidence = _guessConfidence; }

    @Basic
    int guess1Confidence;
    public int getGuess1Confidence() { return guess1Confidence; }
    public void setGuess1Confidence(int _guess1Confidence) { guess1Confidence = _guess1Confidence; }

    /** If we look at the sequence of all move/pick attempts in this
	series up to and including this episode (including the last [so
	far] attempt of this episode), does it end in a sequence of
	successful ones? How long is this sequence? Once the episode is
	completed, this episode's number is preserved in this episode's
	SQL server record.
    */
    //int lastStretch;
    private int lastStretch[] = new int[2];
    @Access(AccessType.PROPERTY)
    public int getLastStretch() { return lastStretch[0]; }
    //    @XmlElement
    public void setLastStretch(int x) { lastStretch[0] = x; }

    //int lastStretch1;
    /** For Player 1, in adversarial games only */
    @Access(AccessType.PROPERTY)
    public int getLastStretch1() { return lastStretch[1]; }
    //    @XmlElement
    public void setLastStretch1(int x) { lastStretch[1] = x; }

    
    /** Bayesian-based intervention: the R product for the last stretch of good moves */
    //double lastR;
    private double lastR[] = new double[2];
    @Access(AccessType.PROPERTY)
    public double getLastR() { return lastR[0]; }
    //@XmlElement
    public void setLastR(double x) { lastR[0] = x; }

    //double lastR1;
    /** For Player 1, in adversarial games only */
    @Access(AccessType.PROPERTY)
    public double getLastR1() { return lastR[1]; }
    @XmlElement
    public void setLastR1(double x) { lastR[1] = x; }

    
    /** In a series with the DOUBLING (or LIKELIHOOD) incentive scheme, the episode
	that triggered x2 (but did not trigger x4) for the series has the value of 2 stored
	here, and the episode that triggered x4 has 4 stored here. 
	The default value for this field (stored in all other episodes)
	is 0.
     */
    //int xFactor;
    int xFactor[] = new int[2] ;
    @Access(AccessType.PROPERTY)
    public int getXFactor() { return xFactor[0]; }
    public void setXFactor(int x) { xFactor[0] = x; }
    @Access(AccessType.PROPERTY)
    public int getXFactor1() { return xFactor[1]; }
    public void setXFactor1(int x) { xFactor[1] = x; }


    /** If this is a 2PG, who started (or is to start) the episode? (Value from Pairing.State). This is
        set on episode creation. */
    int firstMover;
    public int getFirstMover() { return firstMover; }
    @XmlElement
    public void setFirstMover(int _firstMover) { firstMover = _firstMover; }
  

    
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
	// FIXME: needLabels = f(para)
	super(game, Episode.OutputMode.BRIEF, null, null, null, true);
	para = _para;
	clearingThreshold = (para!=null && xgetIncentive()==Incentive.BONUS)?
	    para.getClearingThreshold(): 1.0;
    }


    /** Creates a new episode, whose rules and initial board are based (with 
	appropriate randomization) on a specified parameter set
	@param p The player who plays this episode. (Or, in a 2PG, the player-ZERO of this game). 
    */
    static EpisodeInfo mkEpisodeInfo(PlayerInfo p, int seriesNo, int displaySeriesNo, GameGenerator gg, ParaSet para, boolean bonus)
	throws IOException, RuleParseException {
	   
	Game game = gg.nextGame();
	EpisodeInfo epi = new EpisodeInfo(game, para);
	epi.bonus = bonus;
	epi.seriesNo = seriesNo;
	epi.displaySeriesNo = displaySeriesNo;
	epi.setPlayer(p);
	
	int epiNo = p.getSeries(seriesNo).size();
	epi.firstMover = Pairing.whoWillStartEpisode(p, seriesNo, epiNo );

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
    public boolean weShowAllMovables() {
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


    /** Used to handle occasional accidental double-clicks */
    @Transient
    private LastCall lastCall = new LastCall();
    
    /** The main method invoked on a /move web API call.
	Calls Episode.doMove, and then does various adjustments related to 
	this episode's role in the experiment plan.  If the player has
	failed to complete a bonus episode on time, this is the place
	that sets the "lost" flag.

	@param playerId null in single-player games; playerId in 2PG

	@param _attemptCnt According to the client, this is how many /move or /pick
	calls it has made before this call; so this is how long the transcript of
	this episode should be.	
     */
    public ExtendedDisplay doMove(String moverPlayerId, int y, int x, int by, int bx, int _attemptCnt) throws IOException {
	ExtendedDisplay d = lastCall.doMoveCheck(moverPlayerId, y, x, by, bx,  _attemptCnt);	
	if (d!=null) return d;
	d = checkWhoseTurn(moverPlayerId);
	if (d!=null) return d;
	Display _q = super.doMove(y, x, by, bx, _attemptCnt);
	Pick move = _q.pick;
	if (move==null) return new ExtendedDisplay(0,_q); // FIXME: would be nice to have the correct mover value
	move.mover = player.getRoleForPlayerId(moverPlayerId);
	d = processMove(_q, move);
	return d;

    }

    public ExtendedDisplay doMove2(String moverPlayerId, int pieceId, int bucketId, int _attemptCnt) throws IOException {
	ExtendedDisplay d =  lastCall.doMove2Check(moverPlayerId, pieceId, bucketId,  _attemptCnt);
	if (d!=null) return d;
	d = checkWhoseTurn(moverPlayerId);
	if (d!=null) return d;
	Display _q = super.doMove2(pieceId,  bucketId, _attemptCnt);
	if (_q.error) return new ExtendedDisplay(0,_q); // FIXME: would be nice to have the correct mover value
	Pick move = _q.pick;
	if (move==null) return new ExtendedDisplay(0,_q); // FIXME: would be nice to have the correct mover value
	move.mover = player.getRoleForPlayerId(moverPlayerId);
	d = processMove(_q, move);
	return d;

    }

    
    /** Like /doMove, but without a destination (because the game piece was not movable,
	or because the player just dropped in on the board) */
    public ExtendedDisplay doPick(String moverPlayerId, int y, int x, int _attemptCnt) throws IOException {
	ExtendedDisplay d =  lastCall.doPickCheck(moverPlayerId, y, x,  _attemptCnt);
	if (d!=null) return d;
	d = checkWhoseTurn(moverPlayerId);
	if (d!=null) return d;
	Display _q = super.doPick(y, x, _attemptCnt);
	Pick pick = _q.pick;
	if (pick==null) return new ExtendedDisplay(0,_q); // FIXME: would be nice to have the correct mover value
	pick.mover = player.getRoleForPlayerId(moverPlayerId);	
	d = processMove(_q, pick);
	return d;
    }
    
    public ExtendedDisplay doPick2(String moverPlayerId,  int pieceId, int _attemptCnt) throws IOException {
	ExtendedDisplay d =  lastCall.doPick2Check(moverPlayerId, pieceId, _attemptCnt);
	if (d!=null) return d;
	d = checkWhoseTurn(moverPlayerId);
	if (d!=null) return d;
	Display _q = super.doPick2(pieceId, _attemptCnt);
	Pick pick = _q.pick;
	if (pick==null) return new ExtendedDisplay(0,_q); // FIXME: would be nice to have the correct mover value
	pick.mover = player.getRoleForPlayerId(moverPlayerId);
	
	d = processMove(_q, pick);
	return d;
    }

    /** This is set when the player first reach a new threshold. The info
     should be kept until the end of the episode. */
    @Transient
    private int factorPromised[] = new int[2];
    @Transient
    private boolean[] justReachedX2=new boolean[2], justReachedX4=new boolean[2];
    
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

	<P>In 1PG and in co-op 2PG, all good moves contribute to
	lastStretch and lastR. In adversarial 2PG, separate count is
	done for the two players, lastStretch1 and lastR1 being used
	for Player 1.

	<P>Since ver 8.012, this method may also queue a task for the bot partner

	<p>FIXME: a lot more actions probably could be skipped when
	ignored==true. However, this should matter in real life,
	since a good client should not allow such moves in the 
	first place

	@move The just-made pick or move. In 2PG, the move.mover field identifies the player who made the move.
 */
    private ExtendedDisplay processMove(Display _q, Pick move) throws IOException  {
	try {
	    // WatchPlayer.tellAbout( player.getPlayerId(),	    "Made a move: " + move);
	    // WatchPlayer.tellAbout( player.getPlayerId(), move);
	} catch(Exception ex) {
	    Logging.error("Caught exception when sending informational ws message: " + ex);
	    ex.printStackTrace(System.err);
	}
	
	boolean isMove = (move instanceof Move);

	// The move was not recorded in the transcript[] and faces[]
	// (probably because it was an EMPTY_CELL or some other client
	// error). Since we want the mastery stretch ("golden number")
	// and faces[] to be consistent (and we want any replay to be
	// consistent too), we ignore this move here too
	boolean ignored = (transcript.size()==0 || move!=transcript.lastElement());
	
	// Variables attemptCnt etc (incremented in Episode.accept())
	// sum moves of both players; attemptCnt1 etc count those of Player 1
	if (move.mover==Pairing.State.ONE && !ignored) {
	    attemptCnt1++;
	    attemptSpent1 += (move instanceof Move) ? 1.0: xgetPickCost();
	    if (move.code==CODE.ACCEPT) {
		if (move instanceof Move) {
		    doneMoveCnt1++;
		} else {
		    successfulPickCnt++;
		}
	    }
	}
	

	// Should stretch and R be counted as Player 1's separate numbers?
	//boolean forP1 = (player.isAdveGame() && move.mover==Pairing.State.ONE);
	int mj = (player.isAdveGame() && move.mover==Pairing.State.ONE) ? 1: 0;


	justReachedX2[mj]=justReachedX4[mj]=false;

	double prevR = lastR[mj];

	if (_q.code==CODE.ACCEPT) {
	    // For the mastery criteria, we count succcessful moves only,
	    // but ignore successful picks
	    if (isMove) {
		lastStretch[mj]++;
		if (lastR[mj]==0) lastR[mj]=1;
		lastR[mj] *= move.getRValue();		    
	    }
	} else if (ignored) {
	} else {
	    // a failed move or pick breaks the "mastery stretch"
	    lastStretch[mj]=0;
	    lastR[mj] = 0;
	}


	
	if (xgetIncentive()==Incentive.DOUBLING ||
	    xgetIncentive()==Incentive.LIKELIHOOD) {
	    

	    boolean mastery2 = false, mastery4 = false;
	    String s = "";
	    if (xgetIncentive()==Incentive.DOUBLING) {
		final int x2=para.getInt("x2_after"), x4=para.getInt("x4_after");
		mastery4 = (lastStretch[mj]>=x4);
		mastery2 = (lastStretch[mj]>=x2);
	    } else if (xgetIncentive()==Incentive.LIKELIHOOD) {
		final double x2=para.getInt("x2_likelihood"), x4=para.getInt("x4_likelihood");
		mastery4 = (lastR[mj] >=x4);
		mastery2 = (lastR[mj] >=x2);
		s = ", as lastR=" + lastR[mj] + " for x2="+x2+", x4="+x4;
	    }


	    PlayerInfo.Series ser =  mySeries();
	    int f = ser.findXFactor(mj); 

	    if (f < 4 && factorPromised[mj] < 4 && mastery4) {
		factorPromised[mj] = 4;
		justReachedX4[mj]=true;

		// Since ver 7.0, the "EARLY_WIN" finish code is
		// assigned even when "displaying mastery" happens to
		// coincide with the removal of the last game piece.
		// (i.e. cleared==true)
		// In this edge case, the win isn't really "early".

		earlyWin = true;
		Logging.info("Setting factorPromised["+mj+"]=" + factorPromised[mj] + " and earlyWin=" + earlyWin + s);
	    } else if (f<2 && factorPromised[mj] < 2 && mastery2) {
		factorPromised[mj] = 2;
		justReachedX2[mj]=true;
		Logging.info("Setting factorPromised["+mj+"]=" + factorPromised[mj] + s);
	    } else {
		Logging.info("Keeping factorPromised["+mj+"]=" + factorPromised[mj] + s);
	    }
	   
	    if (cleared || earlyWin ||
		stalematesAsClears && stalemate) {  
		if (xFactor[mj]<factorPromised[mj]) xFactor[mj] = factorPromised[mj];
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
	ExtendedDisplay q = new ExtendedDisplay(move.mover, _q);

	// tell the mover's partner to update his screen;
	// or if the partner is a bot, tell him to work, if appropriate.
	// Note that "player" is Player 0, and not necessarily the mover
	if (player.is2PG()) {
	    int other = 1-move.mover;
	    PlayerInfo thisPlayer = player.getPlayerForRole(move.mover);
	    PlayerInfo otherPlayer = player.getPlayerForRole(other);
	    //String otherPid = player.getPlayerIdForRole(other);
	    String thisPid = thisPlayer.getPlayerId();
	    String otherPid = otherPlayer.getPlayerId();


	    Logging.info("Performed move for " + player.getPlayerIdForRole(move.mover)+ "; otherPlayer.amBot=" + otherPlayer.getAmBot() + "; this mover mustWait=" + q.mustWait);

	    
	    if (thisPlayer.getAmBot()) { // I am a bot 
		if (!q.mustWait && !isCompleted()) { // and I am given another move
		    Logging.info("Bot queuing another task for himself=" +thisPid);
		    Pseudo.addTask(thisPlayer, this, attemptCnt);
		}
	    }
	    
	    if (otherPlayer.getAmBot()) { // bot partner
		if (q.mustWait && !isCompleted()) { // and it's bot's turn
		    Logging.info("Human queuing a task for bot=" + otherPid);
		    Pseudo.addTask(otherPlayer, this, attemptCnt);
		}

	    } else {   // human partner; update his screen
		try {
		    Logging.info("Sending READY DIS to human " + otherPid);
		    WatchPlayer.tellHim(otherPid, WatchPlayer.Ready.DIS);
		} catch(Exception ex) {
		    Logging.error("Very unfortunately, caught exception when sending a Ready.DIS ws message to "+otherPid+": " + ex);
		    ex.printStackTrace(System.err);
		}
	    }
	}


	// Bot assist is not used after successful picks, since they may add
	// little new info
	if (mySeries().hasBotAssist() &&
	    (isMove || move.code != CODE.ACCEPT)) { // Assuming it's 1PG. (FIXME: what if 2PG?)
	    if (botAssist==null) botAssist=new BotAssist();
	    botAssist.didHeFollow(move);
	    botAssist.makeSuggestion(this, q);
	}

	lastCall.saveDisplay(q);	
	return q;
    }

    /** The key that can be printed next to reports to help understand them */
    public String reportKey() {
	Incentive incentive = mySeries().para.getIncentive();
	String s = "[EpisodeID; FC=finishCode g-if-guess-saved; "+
	    ((incentive==Incentive.BONUS) ? "MainOrBonus; ":
	     incentive.mastery()? "xFactor; ":"") +
	    "moveCnt/initPieceCnt; $reward]";
	return s;
    }
    
    /** Concise report, handy for debugging */
    public String report() {
	Incentive incentive = mySeries().para.getIncentive();
	
	String s = "["+episodeId+"; FC="+getFinishCode()+
	    (getGuessSaved()? "g" : "") +   (getGuess1Saved()? "G" : "") +  	    "; ";
	s += (incentive==Incentive.BONUS) ?
	     (earnedBonus? "BB" :
	      bonusSuccessful? "B" :
	      bonus&lost? "L" :
	      bonus?"b":"m"):
	     (incentive.mastery()? 
	      ("x" + xFactor[0]+":"+xFactor[1]+")"): "");
	s += " " +
	    attemptCnt + "/"+getNPiecesStart()  +
	    " $"+getTotalRewardEarned(0)+":"+getTotalRewardEarned(1)+
	    "]";
	return s;
    }
    
    /** Shows the current board (including dropped pieces, which are labeled as such) */
    public Board getCurrentBoard() {
	return getCurrentBoard(true);
    }

    /** Computes the "mustWait" flag for the current player */
    private boolean computeMustWait(int mover) {
	if (getPlayer().is2PG() && getFinishCode()==FINISH_CODE.NO)  {
	    int whoMustPlay = whoMustMakeNextMove();
	    return (whoMustPlay != mover);
	}
	return false;
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

	ExtendedDisplay(int mover, int _code, boolean _error, String _errmsg) {
	    this(mover, _code, _error, _errmsg, false);
	}
 
	/** Assembles an ExtendedDisplay structure based on the
	    current state of this EpisodeInfo object. The super(...)
	    call executes the Episode.Display's constructor, which
	    initializes a lot of fields, such as "transcript".
	    
	    @param mover In a 2PG, which of the two players made this call? (Pairing.State). In 1PG, this value is not relevant; it can be 0 or -2

	    @param If this is a /move or /pick call, the success code of the move/pick. 
	    
	    @param dummy If true, the returned structure will contain
	    just the error message and no real data.
	*/
	private ExtendedDisplay(int mover, int _code,  boolean _error,	String _errmsg, boolean dummy) {
	    super(_code, _errmsg);
	    error = _error;
	    this.mover = mover;
	    if (dummy) return;
	    bonus = EpisodeInfo.this.isBonus();
	    seriesNo = EpisodeInfo.this.getSeriesNo();
	    displaySeriesNo = EpisodeInfo.this.displaySeriesNo;
	    incentive =  EpisodeInfo.this.xgetIncentive();
	    
	    if (getPlayer()!=null) {
		PlayerInfo p = getPlayer();
		// In 1PG, we don't send RecentKnowledge to the client,
		// because Paul asked not to show this kind of feedback.
		// (2025-05-24)
		if (!p.is2PG()) {
		    setRecentKnowledge(null);
		    setRecentKnowledge0(null);
		}
	      

		
		// Whose reward numbers we're pulling?
		int mj = (p.isAdveGame() && mover==Pairing.State.ONE) ? 1:0;
		boolean needPartnerReward = p.isAdveGame();
		

		trialListId = p.getTrialListId();
		
		episodeNo = p.seriesSize(seriesNo)-1;	    
		displayEpisodeNo = p.getSuperseriesSize(seriesNo)-1;	    

		bonusEpisodeNo = bonus? p.countBonusEpisodes(seriesNo)-1 : 0;

		canActivateBonus = p.canActivateBonus();
		int[] reward = {p.getTotalRewardEarned(), p.is2PG()?  p.xgetPartner().getTotalRewardEarned():0};
		totalRewardEarned = reward[mj]; 
		if (needPartnerReward) totalRewardEarnedPartner = reward[1-mj];

		totalBoardsPredicted = p.totalBoardsPredicted();

		ParaSet para=p.getPara(EpisodeInfo.this);
		ruleSetName = para.getRuleSetName();
		movesLeftToStayInBonus = EpisodeInfo.this.movesLeftToStayInBonus();

		// Score for 1PG/coop/adve
		double d = attemptSpent - doneMoveCnt;
		double d1 = attemptSpent1 - doneMoveCnt1;
		if (p.isAdveGame()) d = (mover==Pairing.State.ZERO? d-d1: d1);
		    
		rewardRange = para.kantorLupyanRewardRange(d);

		guessSaved =  EpisodeInfo.this.getGuessSavedBy(mover);

		// ZZZ - handle abandoned ones
		
		if (getFinishCode()!=FINISH_CODE.NO) {
		    transitionMap = p.new TransitionMap();
		}

		incentive2(mj);
		
		errmsg += "\nDEBUG\n" + p.report();

		if (EpisodeInfo.this.isNotPlayable()) {
		    String msg = "Sadly, you cannot continue playing, because the server has been restarted since the last episode, and the board has been purged out of the server memory. This problem could perhaps have been prevented if the client had made a /newEpisode call, rather than /display, after the last /mostRecentEpisode.";
		    setErrmsg(msg);
		    setError( true);		
		
		}
		Vector[] w=p.computeFaces(mover, EpisodeInfo.this);
		faces = w[0];
		facesMine = w[1];
		mustWait = computeMustWait(mover);		
	    }
	    //	    Logging.info("Prepared EpisodeInfo.ExtendedDisplay=" +
	    //		 JsonReflect.reflectToJSONObject(this, true));

	}
	ExtendedDisplay(int mover, Display d) {
	    this(mover, d.code, d.error, d.errmsg);
	    //Logging.debug("Extending display from " + d);
	}

	
	private int mover;
	/** What is the role of the player in the episode? In 1PG it's
	    always 0; in 2PG, it's 0 for player 0 of the pair (the one
	    who owns all episodes), and 1 for player 1 */
	public int getMover() { return mover; }
	@XmlElement
	public void setMover(int _mover) { mover = _mover; }

	
	private boolean mustWait;
	/** This flag is true if the episode playable, but it's not this player's turn yet. The caller must wait
	    for a "READY" signal to arrive via the websocket connection, and
	    then repeat the /display call. */
	public boolean getMustWait() { return mustWait; }
	@XmlElement
	public void setMustWait(boolean _mustWait) { mustWait = _mustWait; }

	boolean bonus;
	/** In games with the BONUS incentive scheme, true if this episode is part of a bonus subseries. */
	public boolean isBonus() { return bonus; }
    
	int totalRewardEarned=0;
	/** The total reward earned by this player so far, including the regular rewards and any bonuses, for all episodes. */
	public int getTotalRewardEarned() { return totalRewardEarned; }

	/** Your partner's earnings. (Only in 2PG adversarial) */
	int totalRewardEarnedPartner=0;
	public int getTotalRewardEarnedPartner() { return totalRewardEarnedPartner; }
	
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

	boolean guessSaved; 
	/** True if this player's guess has been recorded at the end
	    of this episode. In a 2PG, this refers to the player identified by the "mover"
	    field.
	 */
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

	/** The name of the incentive scheme in the current game (null, BONUS, DOUBLING, LIKELIHOOD)*/
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

	/** Since GS 7.001, this is an array of the same length as faces. In 2PG, facesMine[j]==true
	    iff faces[j] is attributed to a move by the player for whome we're preparing the ExpandedDisplay
	    structure. In this way it can emphasize the "my own" elements of faces in its display.
	*/
	private Vector<Boolean> facesMine =  null;
	public Vector<Boolean> getFacesMine() { return facesMine; }

	
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
	    factorAchieved, during the interval between the pont when
	    a new threshold has been achieved in this episode and the
	    completion of the episode */
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

	/** Sets a few fields related to the DOUBLING or LIKELIHOOD incentive scheme.
	    @param mj Whose record do we look at? (0 for 1PG or coop 2PG; actual player for adversarial 2PG)
	 */
	private void incentive2(int mj) {	    
	    incentive = xgetIncentive();
	    lastStretch = EpisodeInfo.this.lastStretch[mj]; 
	    lastR = EpisodeInfo.this.lastR[mj];
	    rewardsAndFactorsPerSeries = getPlayer().getRewardsAndFactorsPerSeries(mj);
	    //Logging.info("EpisodeInfo.ED.incentive2(): obtained rewardsAndFactorsPerSeries = " + rewardsAndFactorsPerSeries);
	    justReachedX2 = EpisodeInfo.this.justReachedX2[mj];
 	    justReachedX4 = EpisodeInfo.this.justReachedX4[mj];
	    factorAchieved=1;
	    try {
		factorAchieved = rewardsAndFactorsPerSeries.getFactorAchieved();
	    } catch(Exception ex) {
		Logging.warning("EpisodeInfo.ED.incentive2(): exception: " + ex);
	    } // fixme - silly fix for Kevin's report ,2022-01-28
	    factorPromised =  EpisodeInfo.this.factorPromised[mj];
	}


	/** If there is a bot assist, it can send a message here */
	String botAssistChat = null;
	public String getBotAssistChat() { return botAssistChat; }
        @XmlElement
        public void setBotAssistChat(String _botAssistChat) { botAssistChat = _botAssistChat; }

    }
    
    /** Builds a Dsplay object to be sent out over the web UI on a /display call
	@param playerId This can be null in 1PG, but in 2PG it must
	identify the player to whom we are to show the display
     */
    public ExtendedDisplay mkDisplay(String playerId) {
	int mover = player.getRoleForPlayerId(playerId); // can be -2 in 1PG
	//	if (mover<0) return new ExtendedDisplay(0, CODE.OUT_OF_TURN, "Player " + playerId + " is not a party to this game at all!");

   	ExtendedDisplay q= new ExtendedDisplay(mover, Episode.CODE.JUST_A_DISPLAY, false, "Display requested");
	if (player.isBotGame() && q.mustWait && attemptCnt==0) { // the first move of an episode... and it's not your turn
	    PlayerInfo player= getPlayer();  // owner of the record
	    // the partner of the mover
	    int other = 1-q.mover;
	    PlayerInfo otherPlayer = player.getPlayerForRole(other);
	    String otherPid = otherPlayer.getPlayerId();
	    
	    if (otherPlayer.getAmBot()) { // bot partner; get him to start playing
		Logging.info("Human queuing initial task for bot=" + otherPid);
		Pseudo.addTask(otherPlayer, this, attemptCnt);
	    }
	}

	if (mySeries().hasBotAssist() &&  attemptCnt==0) { // the first move of an episode with bot assist

	    int k = q.getEpisodeNo();
	    String chat = (k==0)?
		"Starting the first episode of a new rule (Rule no. " + (seriesNo+1)+" . Please make your first move!":
		"Starting episode no. " + k + " of Rule no. " + (seriesNo+1)+" . Please make your first move!";

	    q.setBotAssistChat(chat);
	}


	
    	return q;
    }

 


    public ExtendedDisplay dummyDisplay(int _code, 	String _errmsg) {
	return new ExtendedDisplay(0, _code, false, _errmsg, true);
    }



   void old_saveDetailedTranscriptToFile(File f) {

       final String[] keys = 
	   { "playerId",
	     "trialListId",  // string "trial_1"
	     "seriesNo",  // 0-based
	     "ruleId", // "TD-5"
	     "episodeNo", // position of the episode in the series, 0-based
	     "episodeId",
	     "moveNo", // 0-based number of the move in the transcript
	     "timestamp", // YYYYMMDD-hhmmss.sss
	     "mover", // who made the move? 0 or 1. (Since ver 7.001)
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
	   h.put( "mover", ""+move.mover);
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
	
    /** Records the player-provided "guess" to a CSV file
	@param moverPlayerId The playerId of the actual player who
	made the guess. In a 2PG, this is not necessarily the same
	player who owns the EpisodeInfo instance, because each partner's
	guess goes into his own file.
     */
    public void saveGuessToFile(File f, String moverPlayerId, String guessText, int confidence) {
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
       int moveNo=0;
       Date prevTime = getStartTime();
       int objectCnt = getNPiecesStart();
       h.clear();
       h.put( "playerId", moverPlayerId);
       h.put( "trialListId", getPlayer().getTrialListId());
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

        /**
       	<pre>
	#mover,seriesNo,episodeNo,timestamp,text
	</pre>
     */
    public void saveChatToFile(File f, int mover,
				       String text) {
	     final String[] keys = 
	   { "mover",
	     "seriesNo",
	     "episodeNo", // position of the episode in the series, 0-based
	     "moveNo", 
	     "timestamp",
	     "text"
	   };

       HashMap<String, Object> h = new HashMap<>();
       h.clear();
       h.put( "mover", mover);
       h.put( "seriesNo", getSeriesNo());
       PlayerInfo.Series ser =  mySeries();
       h.put( "episodeNo", ser.episodes.indexOf(this));
       h.put( "moveNo", attemptCnt); 
       h.put( "timestamp", 	sdf2.format(new Date()));
       h.put( "text",   ImportCSV.escape(text));
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



    /** Which player made the last recorded move of this episode? (Needed for 2PG only)
	@return normally, 0 or 1; if there were no moves, -1
     */
    public int whoMadeLastMove() {
	Pick move = lastMove();
	return move==null? -1: lastMove().mover;
    }

    /** @return The last move of the episode, or null if the episode was empty (no moves, because the
	para set allowed the player to give up without playing)
    */
    public Pick lastMove() {
	return transcript.isEmpty()? null: transcript.lastElement();
    }

    /** Called at the beginning of /move call, before testing for acceptance. Also can be called at the end of
	the /move or /display call (after acceptance and recording in the transcript), to decide who'll make the
	following move.
     */
    public int whoMustMakeNextMove() {
	if (attemptCnt==0) {
	    return firstMover;
	}
	Pick m = lastMove();
	int z = m.getMover();
	if (player.isCoopGame()) {
	    // Players alternate moves within episode
	    return 1-z;
	} else if (player.isAdveGame()) {
	    // After a correct move, another attempt is given
	    return (m.getCode()==CODE.ACCEPT) ? z : 1-z;
	} else { // single-player
	    return Pairing.State.ZERO;
	}
    }
	
    
    /** Checks if the  /move or /pick call in a 2PG is being made by the player whose turn it is to move.

	<P>Episode.player always refers to the player with
	pairState=State.ZERO, because all episodes of a 2PG are stored
	in that player. Thus if the the playerId sent by the client is
	different from Episode.player.playerId, then the move is
	attempted by the player with pairState=State.ONE.
	
	@param playerId The playerId of the player who wants to make a move now
	
	@return null if there is no problem （i.e. it's a 1PG, or it's
	a 2PG and the move is made in turn); an ExtendedDisplay with a
	proper message if an out-of-turn request */
    private ExtendedDisplay checkWhoseTurn(String playerId) {
	if (!player.is2PG()) return null;

	if (playerId==null) return  new ExtendedDisplay(0, CODE.OUT_OF_TURN, true, "playerId was not sent in a /move or /pick call. This parameter is mandatory in 2PG");
	       
	int mover = player.getRoleForPlayerId(playerId);
	if (mover<0) return new ExtendedDisplay(0, CODE.OUT_OF_TURN, true,  "Player " + playerId + " is not a party to this game (episode "+episodeId+") at all!");

	// Which player is associated with the request?
	PlayerInfo moverPlayer = player.getPlayerForRole( mover );
	
	if (moverPlayer.getCompletionMode() == PlayerInfo.COMPLETION.WALKED_AWAY) {
	    return new ExtendedDisplay(mover, CODE.NO_GAME, true,  "Player " + playerId + " has been timed out");
	} else if (moverPlayer.getCompletionMode() == PlayerInfo.COMPLETION.ABANDONED) {
	    return new ExtendedDisplay(mover, CODE.NO_GAME, true,  "Player " + playerId + " has been abandoned by the partner");
	}

	
	
	int whoMustPlay =whoMustMakeNextMove();
	
	return  (mover==whoMustPlay)? null:
	    new ExtendedDisplay(mover, CODE.OUT_OF_TURN, true,  "Player " + playerId + " tried to make a move out of turn");
    }

    /** @return the role of the player who made this /move, /pick, or
      /display call, or -2 if an impossible player (not a party to
      this game) */
    /*
    private int whoMadeCall(String playerId) {
	if (playerId.equals(player.getPlayerId())) {
	    return  Pairing.State.ZERO;
	} else if (playerId.equals(player.getPartnerPlayerId())) {
	    return  Pairing.State.ONE;
	} else {
	    return  Pairing.State.ERROR;
	}
    }
    */	

    /** Sets all guess-related fields of this episode for one of the partners */
    public void setAllGuessData(int mover, String text, int confidence) {
	if (mover==Pairing.State.ONE) {
	    setGuess1Saved(true);
	    setGuess1(text);
	    if (confidence>=0) {
		setGuess1Confidence(confidence);
	    }
	} else {
	    setGuessSaved(true);
	    setGuess(text);
	    if (confidence>=0) {
		setGuessConfidence(confidence);
	    }
	}
    }

    /** Overrides Episode.FinishCode, taking
	special care of the walk away/abandoned
	situation. (The episode entry is stored
	only once, shared by the two players;
	but the finish code should be returned
	differently to the two players).
    */
    public int getFinishCode() {
	int fc = super.getFinishCode();
	if (fc==FINISH_CODE.ABANDONED || fc==FINISH_CODE.WALKED_AWAY  ) {
	    if (player.getCompletionMode() == PlayerInfo.COMPLETION.WALKED_AWAY) fc = FINISH_CODE.WALKED_AWAY;
	    else if (player.getCompletionMode() == PlayerInfo.COMPLETION.ABANDONED) fc = FINISH_CODE.ABANDONED;
	}
	return fc;
    }
       
    /** In Bot Assist games, the list of all move suggestions made by the bot in this episode */
    @Transient
    BotAssist botAssist = null;
    public Move xgetLastProposed() {
	return botAssist.proposed;
    }
    
}

