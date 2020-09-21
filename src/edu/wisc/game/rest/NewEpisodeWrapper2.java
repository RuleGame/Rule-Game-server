package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;
import javax.persistence.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.reflect.*;


/** This is an object that's converted to a JSON structure and sent to the client as a response in /GameService2/newEpisode calls.
<p>
FIXME: need to add periodic purge on episodes 
 */
public class NewEpisodeWrapper2 extends ResponseBase {
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
 
  
    Episode.Display display;
   /** The structure with a lot of information about the current episode,
       and its place in the experiment's framework.
       (See {@link edu.wisc.game.sql.EpisodeInfo.ExtendedDisplay} for the full structure that's actually found here)
    */
    public Episode.Display getDisplay() { return display; }
    private void setDisplay(Episode.Display _display) { display = _display; }

    /** @param existing If true, look for the most recent existing episode (completed or incomplete); if false, return the recent incomplete expisode or create a new one */
    NewEpisodeWrapper2(String pid, boolean existing, boolean activateBonus, boolean giveUp) {

	Logging.info("NewEpisodeWrapper2(pid="+ pid+", existing="+existing+
		     ", activate="+activateBonus+", gu=" + giveUp);
	
	ResponseBase r=null;
	if (activateBonus) {
	    r = new  ActivateBonusWrapper(pid);
	}
	if (giveUp && (r==null || !r.getError()) )  {
	    r = new GiveUpWrapper(pid);
	}
	if (r!=null && r.getError()) {
	    setError(true);
	    setErrmsg(r.getErrmsg());
	    return;
	}
	
	try {
	    // register the player if he has not been registered
	    PlayerResponse q =new PlayerResponse(pid);
	    if (q.error) {
		setError(true);
		setErrmsg(q.getErrmsg());
		return;
	    }

	    PlayerInfo x = PlayerResponse.findPlayerInfo(pid);
	    Logging.info("NewEpisodeWrapper2(pid="+ pid+"): player="+
			 (x==null? "null" : "\n" + x.report()));
	    if (x==null) {
		setError(true);
		setErrmsg("Player not found: " + pid);
		return;
	    } 
	    alreadyFinished = x.alreadyFinished();
	    completionCode = x.getCompletionCode();
	    EpisodeInfo epi = existing? x.mostRecentEpisode(): x.episodeToDo();
	    if (epi==null) {
		setError(true);
		String msg = alreadyFinished ?
		    "This player has completed all his parameter sets already":
		    existing ? "Failed to find any episode!":
		    "Failed to find or create episode!";
		setErrmsg(msg);
		return;	
	    }
	    para = x.getPara(epi);
	    episodeId = epi.episodeId;
	    setDisplay(epi.mkDisplay());
	    
	    setError( false);
	} catch(Exception ex) {
	    setError(true);
	    String msg = ex.getMessage();
	    if (msg==null) msg = "Unknown internal error ("+ex+"); see stack trace in the server logs";
	    setErrmsg(msg);
	    System.err.print(ex);
	    ex.printStackTrace(System.err);
	} finally {
	    Logging.info("NewEpisodeWrapper2(pid="+ pid+"): returning:\n" +
			 JsonReflect.reflectToJSONObject(this, true));
	}
		     
	
    }
    
}
