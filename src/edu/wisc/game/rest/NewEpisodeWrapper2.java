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


/**
FIXME: need to add periodic purge on episodes 
 */
public class NewEpisodeWrapper2 extends ResponseBase {
    String episodeId=null;
    
    public String getEpisodeId() { return episodeId; }
    
    ParaSet para;
    public ParaSet getPara() { return para; }
    
    boolean alreadyFinished;
    public boolean getAlreadyFinished() { return alreadyFinished; }
  
    Episode.Display display;
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
