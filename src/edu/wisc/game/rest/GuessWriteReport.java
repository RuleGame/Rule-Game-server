package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.util.regex.*;

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
    GuessWriteReport(File f, long _byteCnt) {
	super(f,  _byteCnt);
    }

    static FileWriteReport writeGuess( String episodeId,  String text, int guessConfidence) {
	
	try {
	    if (text==null) {
		return new FileWriteReport(true,"No guess text supplied");
	    }

	    if (guessConfidence<0) {
		// compatibility with "level: text"
		Pattern p = Pattern.compile("^([0-9]+):\\s*");
		Matcher m = p.matcher(text);
		if (m.find()) {
		    guessConfidence = Integer.parseInt( m.group(1));
		    text = text.substring( m.end());
		}
	    }

	    
	    EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	    if (epi==null)  {
		return new FileWriteReport(true,"No such episode: " + episodeId);
	    }
	    PlayerInfo x = epi.getPlayer();
	    String pid = x.getPlayerId();
	    
	    File f= Files.guessesFile(pid);
	    epi.saveGuessToFile(f, text, guessConfidence);
	    epi.setGuessSaved(true);
	    epi.setGuess(text);
	    if (guessConfidence>=0) {
		epi.setGuessConfidence(guessConfidence);
	    }
	    Main.persistObjects(epi);
	    GuessWriteReport g = new GuessWriteReport(f, f.length());
	    g.transitionMap = x.new TransitionMap();
	    return g;
	} catch(IOException ex) {
	    return  new	    FileWriteReport(true,ex.getMessage());
	}

    }
    
}
