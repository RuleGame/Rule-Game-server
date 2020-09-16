package edu.wisc.game.rest;

import java.io.*;
import java.util.*;

import javax.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;

/** This data structure is converted to JSON and send to the client in response to the /guess web API call. */
public class GuessWriteReport extends FileWriteReport {
    PlayerInfo.TransitionMap transitionMap;
    /** Describes the possible transitions (another episode in the
	same series, new series, etc) which can be effected after this
	episode. This can be used to generate transition buttons.	
     */
    public PlayerInfo.TransitionMap getTransitionMap() { return transitionMap; }
    //@XmlElement
    //    void setTransitionMap(PlayerInfo.TransitionMap _destinationMap) { destinationMap = _destinationMap; }
    GuessWriteReport(File f, long _byteCnt) {
	super(f,  _byteCnt);
    }

    static FileWriteReport writeGuess( String episodeId,  String text) {
	
	try {
	    EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	    if (epi==null)  {
		return new FileWriteReport(true,"No such episode: " + episodeId);
	    }
	    PlayerInfo x = epi.getPlayer();
	    String pid = x.getPlayerId();

	    ParaSet para = x.getPara(epi);
	    String ruleSetName = para.getRuleSetName();
	    
	    File f= Files.guessesFile(pid);

	    PrintWriter w = new PrintWriter(new FileWriter(f, true));

	    
	    String ss[] = { pid, episodeId, ruleSetName, text};
	    String data = ImportCSV.escape(ss);
	    w.println(data);
	    w.close();

	    epi.setGuessSaved(true);
	    Main.persistObjects(epi);
	    GuessWriteReport g = new	GuessWriteReport(f, f.length());
	    g.transitionMap = x.new TransitionMap();
	    return g;
	} catch(IOException ex) {
	    return  new	    FileWriteReport(true,ex.getMessage());
	}

    }
    
}
