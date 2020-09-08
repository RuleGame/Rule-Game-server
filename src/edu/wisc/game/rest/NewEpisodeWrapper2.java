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

//@XmlRootElement(name = "NewEpisode") 

/**
FIXME: need to add periodic purge on episodes 
 */
public class NewEpisodeWrapper2 extends ResponseBase {
    String episodeId=null;
    
    public String getEpisodeId() { return episodeId; }
    @XmlElement
    public void setEpisodeId(String _episodeId) { episodeId = _episodeId; }

   
    ParaSet para;
    public ParaSet getPara() { return para; }
    @XmlElement
    public void setPara(ParaSet _para) { para = _para; }

   
    boolean alreadyFinished;
    public boolean getAlreadyFinished() { return alreadyFinished; }
    @XmlElement
    public void setAlreadyFinished(boolean _alreadyFinished) { alreadyFinished = _alreadyFinished; }

    Episode.Display display;
    public Episode.Display getDisplay() { return display; }
    @XmlElement
    public void setDisplay(Episode.Display _display) { display = _display; }

    /** @param existing If true, look for the most recent existing episode (completed or incomplete); if false, return the recent incomplete expisode or create a new one */
    NewEpisodeWrapper2(String pid, boolean existing) {
	try {
	    PlayerInfo x = PlayerResponse.findPlayerInfo(pid);
	    if (x==null) {
		setError(true);
		setErrmsg("Player not found: " + pid);
		return;
	    }
			    
	    EpisodeInfo epi = existing? x.mostRecentEpisode(): x.episodeToDo();
	    if (epi==null) {
		setError(true);
		alreadyFinished = x.alreadyFinished();
		String msg = alreadyFinished ?
		    "This player has completed all his parameter sets already":
		    existing ? "Failed to find any episode!":
		    "Failed to find or create episode!";
		setErrmsg(msg);
		return;	
	    }
	    episodeId = epi.episodeId;

	    para = x.getPara(epi);

	    setDisplay(epi.mkDisplay());
	    
	    setError( false);
	} catch(Exception ex) {
	    setError(true);
	    setErrmsg(ex.getMessage());
	    System.err.print(ex);
	    ex.printStackTrace(System.err);
	}      
	
    }
    
}
