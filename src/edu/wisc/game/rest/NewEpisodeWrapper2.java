package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import jakarta.json.*;
import javax.persistence.*;


import jakarta.xml.bind.annotation.XmlElement; 
import jakarta.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.reflect.*;

import edu.wisc.game.websocket.WatchPlayer;


/** This is an object that's converted to a JSON structure and sent to the client as a response in /GameService2/newEpisode calls.
<p>
FIXME: need to add periodic purge on episodes 
 */
public class NewEpisodeWrapper2 extends ResponseBase {

    private boolean mustWait;
    /** This flag is true if the episode is not ready yet. The caller must wait
	for a "READY" signal to arrive via the websocket connection, and
	then repeat the /newEpisode call. */
    public boolean getMustWait() { return mustWait; }
    @XmlElement
    public void setMustWait(boolean _mustWait) { mustWait = _mustWait; }


    String episodeId=null;
    /** The episode ID of the resumed or newly created episode */
    public String getEpisodeId() { return episodeId; }

    ParaSet para;
    /** The parameter set currently in effect. This comes from the
	currently active line of the trial list file associated with the
	player.
    */
    public ParaSet getPara() { return para; }
    
    boolean alreadyFinished;
    /** True if this player has finished all episodes he could play.
	This means that the most recent episode has been completed,
	and no more new episodes can be created.
    */
    public boolean getAlreadyFinished() { return alreadyFinished; }

    private String completionCode;
    /** The completion code, a string that the player can report as a proof of 
	his completion of the experiment plan. It is set when the current series
	number is incremented beyond the last parameter set number.
     */
    public String getCompletionCode() { return completionCode; }

    private int completionMode;
    public int getCompletionMode() { return completionMode; }

    
  
    Episode.Display display;
   /** The structure with a lot of information about the current episode,
       and its place in the experiment's framework.
       (See {@link edu.wisc.game.sql.EpisodeInfo.ExtendedDisplay} for the full structure that's actually found here)
    */
    public Episode.Display getDisplay() { return display; }
    private void setDisplay(Episode.Display _display) { display = _display; }    

    
    /** Depending on the parameters, creates a new episode or looks up
	an already existing one that should be continued. Serves the
	/newEpisode and /mostRecentEpisode API calls.

       @param existing If true, look for the most recent existing episode (completed or incomplete); if false, return the recent incomplete expisode or create a new one */
    NewEpisodeWrapper2(String pid, boolean existing, boolean activateBonus, boolean giveUp) {

	Logging.info("NewEpisodeWrapper2(pid="+ pid+", existing="+existing+
		     ", activate="+activateBonus+", gu=" + giveUp+")");
	
	ResponseBase r=null;
	if (activateBonus) {
	    r = new ActivateBonusWrapper(pid);
	}
	if (giveUp && (r==null || !r.getError()) )  {
	    r = new GiveUpWrapper(pid);
	}
	if (r!=null && r.getError()) {
	    setError(true);
	    setErrmsg(r.getErrmsg());
	    return;
	}

	boolean partnerMissing = false;
	try {

	    PlayerInfo x = PlayerResponse.findPlayerInfo(null, pid);
	    Logging.info("NewEpisodeWrapper2(pid="+ pid+"): player="+
			 (x==null? "null" : "" +x+ "\n" + x.report()));
	    x.setLastActivityTime(new Date());

	    if (x==null) {
		setError(true);
		setErrmsg("Player not found: " + pid);
		return;
	    }

	    // See if this is a 2-player game (2PG)
	    partnerMissing = Pairing.ensurePairingNow(x);

	    if (partnerMissing) { // tell the client to wait until the game can start
		Logging.info("NewEpisodeWrapper2(pid="+ pid+"): no partner assigned yet!");
		setMustWait(true);

		// Ver 7.005: the client apparently sometimes wants to look
		// at the parameter set even if the episode is not ready...
		// so let's just give it the first para set.
		// This is to avoid the client's error message,
		// "Cannot read properties of undefined (reading 'grid_memory_show_order')", coming from trials.ts

		para = x.getFirstPara();
		return;
	    } 
		
	    Logging.info("NewEpisodeWrapper2(pid="+ pid+"): partner=" + x.xgetPartner());
	    
	    PlayerInfo y = x;
	    //-- if it's a 2PG, all episodes are stored by player ZERO
	    if (x.xgetPartner()!=null && x.getPairState()==Pairing.State.ONE) {
		y = x.xgetPartner();
	    }	    
	    Logging.info("NewEpisodeWrapper2(pid="+ pid+"): x="+x+", y=" + y);
	    
	    EpisodeInfo epi = existing? y.mostRecentEpisode(): y.episodeToDo();
    
	    alreadyFinished = y.alreadyFinished();
	    completionCode = y.getCompletionCode();
	    completionMode = y.getCompletionMode();
	    if (epi==null) {
		setError(true);
		String msg = alreadyFinished ?
		    "This player has completed all his parameter sets already":
		    existing ? "Failed to find any episode!":
		    "Failed to find or create episode!";
		setErrmsg(msg);
		return;	
	    }
	    para = y.getPara(epi);
	    episodeId = epi.episodeId;
	    setDisplay(epi.mkDisplay(pid));

	    // Tell the other player that the episode is ready
	    if (x.is2PG() && !x.isBotGame()) {
		WatchPlayer.tellHim(x.getPartnerPlayerId(), WatchPlayer.Ready.EPI);
	    }
	    
	    setError( false);

	} catch(Exception ex) {
	    setError(true);
	    String msg = ex.getMessage();
	    if (msg==null) msg = "Unknown internal error ("+ex+"); see stack trace in the server logs";
	    setErrmsg(msg);
	    System.err.print(ex);
	    ex.printStackTrace(System.err);
	    
	    StringWriter sw = new StringWriter();
	    ex.printStackTrace(new PrintWriter(sw));
	    Logging.info(sw.toString());
	    Logging.info("ERRMSG=`" + errmsg+"'");
	    

	} finally {
	    Logging.info("NewEpisodeWrapper2(pid="+ pid+"): returning:\n" +
			 JsonReflect.reflectToJSONObject(this, true));
	    if (partnerMissing) {
		WatchPlayer.tellAbout(pid, "Waiting for a partner to start an episode");
	    } else {
		WatchPlayer.tellAbout(pid, "Starting on episode "  + episodeId);
	    }

	}
		     
	
    }
    
}
