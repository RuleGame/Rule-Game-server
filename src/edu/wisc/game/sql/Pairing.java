package edu.wisc.game.sql;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;

import edu.wisc.game.websocket.WatchPlayer;

/** Auxiliary methods for two-player games, introduced in GS 7.*.

    In a two-player game, a player may be in one of the following states:

    * Must wait for the first display to appear
    * Can retrieve a ready display
    * Can make a move
    * Must wait for the partner to make a move
    
 */

public class Pairing {

    /** A player's current state in the pairing system. The value is stored in PlayerInfo.pairState. */
    public static class State {
	/** Not yet paired, or not a pair game at all */
	public static final int NONE = -1;
	/** The two players in a pair */
	public static final int ZERO = 0, ONE=1;
	public static final int ERROR = -2;
    }


    
    /** Called on /newPlayer (from PlayerResponse()), to check if the
	new (or restored) player needs to be paired.
	@return true if the player is in the "wait to be paired" state
    */
    static public void newPlayerRegistration(PlayerInfo p) {
	if (!p.is2PG()) return;
	if (p.getPartnerPlayerId()!=null) return;
	//PairingQueue q = getPairingQueue(p.getExperimentPlan());
	//q.register(p);
    }

    /** This is called at the beginning of each episode (on a
	/newEpisode API call), to ensure that if this is a two-player
	game, the player is paired, if at all possible.
	@return true if pairing is needed, but is not possible right
	now, and therefore p has to wait to start playing
    */
    static public boolean ensurePairingNow(PlayerInfo p) {
	if (!p.is2PG()) return false;
	if (p.getPartnerPlayerId()!=null) {
	    p.xgetPartner(); 	    /// ensure that p.partner is loaded
	    return false;
	}

	//PairingQueue q = getPairingQueue(p.getExperimentPlan());
	
	PairingQueue q = getPairingQueue(p.getExperimentPlan());
	//q.register(p);
	PlayerInfo other = q.tryPairing(p);
	return (other==null);
    }

    
    /** A PairingQueue instance stores the list of all not-yet-paired
	players in one two-player experiment plan. Normally, such a
	queue would only contain 0 or 1 players, at least as long as
	the experiment plan only has 1 trial list.

	<p> FIXME: should also add a trial list match check before
	pairing. (The current code simply expects that the experiment
	plan only has 1 list).
    */
    static class PairingQueue extends LinkedList<PlayerInfo> {

	/** Does this list already contain an element with a matching PID? */
	PlayerInfo findByPid(PlayerInfo p) {
	    String pid = p.getPlayerId();
	    for(PlayerInfo z: this) {
		if (z==p || z.getPlayerId().equals(pid)) return z;
	    }
	    return null;
	}

	/** After how many msec a potential pair player is considered
	    "timed out", and won't be matched with any one any more */
	final long TIME_OUT_MSEC = 30 * 60 * 1000L;
	
	/** Looks for someone who can become a good partner for p.
	    This should be called when the queue does not contain p... but
	    we check for that anyway.

	    <p>At present, the "best" is the one who's most recently registered.
	 */
	PlayerInfo findGoodPartner(PlayerInfo p) {
	    String pid = p.getPlayerId();
	    PlayerInfo best = null;
	    long bestAgo = 0;
	    Date now = new Date();
	    Vector<PlayerInfo> timedOutList = new Vector<>();
	    for(PlayerInfo z: this) {
		if (z==p || z.getPlayerId().equals(pid)) continue;
		Date d = z.getPairingRegistrationTime();
		if (d==null) throw new IllegalArgumentException("Player without a registration date found in the pairing queue");
		long msecAgo = now.getTime() - d.getTime();
		if (msecAgo > TIME_OUT_MSEC) {
		    timedOutList.add(z);
		    continue;
		}

		
		//if (best == null || msecAgo < bestAgo)



	    }
	    
	    return null;
	}


	
	/*
	synchronized void register(PlayerInfo p) {
	    PlayerInfo has = findByPid(p);
	    if (has!=null) {
		if (has!=p) throw new IllegalArgumentException("Pairing.register(): Have 2 PlayerInfo objects with the same pid; no good! New object=("+p+"); old one=("+has+")");
	    } else {
		push(p);
	    }
	}
	*/

	// ZZZ
	
	/** Pairs player p with some other player wating for pairing
	    in this queue, if at all possible. This is done on
	    /newEpisode.
	    @param p The player to pair with somebody. It may already be in the list.
	 */
	synchronized PlayerInfo tryPairing(PlayerInfo p) {

	    p.setPairingRegistrationTimeNow();
	    
	    // If this player is already in the queue, remove him
	    PlayerInfo has = findByPid(p);
	    if (has!=null) {
		if (has!=p) throw new IllegalArgumentException("Pairing.tryPairing(): Have 2 PlayerInfo objects with the same pid; no good! New object=("+p+"); old one=("+has+")");
		if (size()==1) return null; // the list only stores this element, and nothing else for it to be paired with
		remove(has);
		Logging.info("Pairing: provisionally removed " + p);
	    }

	    
	    PlayerInfo other =  poll();
	    Logging.info("Pairing: popped " + other + " for pairing");
	    if (other==null) { // push this player back, because there is no one to pair it with
		push(p);
		Logging.info("Pairing: pushed " + p);
		return null;
	    }
	    if (other.getPairState()!=State.NONE || other.getPartnerPlayerId()!=null) throw new IllegalArgumentException("Found an already paired player in the pairing queue: "+other);
	    // FIXME: maybe need proper support for trialListId match
	    if (!other.getTrialListId().equals(p.getTrialListId())) {
		throw new IllegalArgumentException("Unexpectedly, we have players playing two different trial lists in a two-player plan: (" + p + ") and ("+other+")");
	    }

	    // verify that the two players are at the same point in their episode sequence
	    // (normally, 0==0, unless we have restored players from database)
	    if (p.getCurrentSeriesNo()>0 || other.getCurrentSeriesNo()>0) throw new  IllegalArgumentException("Unexpectedly, we are trying to pair a player who already has comlpeted some series: p=(" + p + "), other=("+other+")");

	    
	    p.linkToPartner(other, State.ZERO);

	    Logging.info("Pairing: paired " + other + " AND " + p);
	    return other;
	}
    	
    }


    
    /** For each experiment plan, here's the list of players in that plan who
	have not been paired yet */
    static HashMap<String, PairingQueue> unpairedPlayers = new HashMap<>();

    static private final String lock = "lock";

    /** Retrieves (and, if necessary, creates) the PairingQueue object for a particular
	two-player experiment plan.
    */
    synchronized private static PairingQueue getPairingQueue(String exp) {
	PairingQueue  q = unpairedPlayers.get(exp);
	if (q==null) unpairedPlayers.put(exp, q = new PairingQueue());
	return q;
    }
    
    /** Tries to provide a partner from a new, still unpaired
	player. If the potetnial partner is found, the two are paired.
     */
    static private PlayerInfo providePartner(PlayerInfo p) {
	if (p.getPairState()!=State.NONE || p.getPartnerPlayerId()!=null) throw new IllegalArgumentException("Player " + p + " is already paired");
	String exp = p.getExperimentPlan();
	PairingQueue q = getPairingQueue(exp);
	return q.tryPairing(p);
    }

    /** Which player starts the specified series? */
    public static int whoWillStartSeries(PlayerInfo p, int seriesNo ) {	
	if (seriesNo==0) return State.ZERO;
	
	if (p.isCoopGame()) {
	    // Players alternate starting series
	    return seriesNo % 2;
	} else if (p.isAdveGame()) {
	    // After an early win, the losing player starts the next series; otherwise, alternation
	    PlayerInfo.Series lastSeries = p.getSeries(seriesNo-1);
	    if (lastSeries.seriesHasX4(State.ZERO)) {
		return State.ONE;
	    } else if (lastSeries.seriesHasX4(State.ONE)) {
		return State.ZERO;
	    } else {
		return 1-lastSeries.episodes.firstElement().getFirstMover();
	    }
	    
	} else { // single-player
	    return State.ZERO;
	}
    }

    /** @param epiNo episode number within the series (0-based)
     */
    public static int whoWillStartEpisode(PlayerInfo p, int seriesNo, int epiNo ) {
	if (epiNo==0) return whoWillStartSeries(p, seriesNo);

	PlayerInfo.Series ser = p.getSeries(seriesNo);
	EpisodeInfo lastEpi = ser.episodes.get(epiNo-1);
	Episode.Pick lastMove = lastEpi.lastMove();

	// this may only happen if the last episode was empty (given up
	// without playing)
	if (lastMove==null) return State.ZERO;

	
	if (p.isCoopGame()) {
	    //-- Players alternate starting episodes within the series
	    // return 1-lastEpi.getFirstMover();
	    //-- Ver 7.002: players alternate moves through the border of episodes
	    int z = lastMove.getMover();
	    return  1-z;	    
	} else if (p.isAdveGame()) {
	    // After a correct move, another attempt is given
	    int z = lastMove.getMover();
	    return (lastMove.getCode()==Episode.CODE.ACCEPT) ? z : 1-z;
	} else { // single-player
	    return State.ZERO;
	}
	
    }


}
