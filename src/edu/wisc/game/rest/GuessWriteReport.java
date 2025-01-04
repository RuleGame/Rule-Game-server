package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import jakarta.xml.bind.annotation.XmlElement; 

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

    /** Records a guess.
	@param moverPlayerId The player who actually made the guess. (In a 2PG, it may be different from the
	player who owns the episode)
	@param text The text of the guess, entered by the player, to record
	@param  guessConfidence The integer "confidence level" entered by the player
     */
    static FileWriteReport writeGuess(String moverPlayerId,  String episodeId,  String text, int guessConfidence) {
	
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
	    //String pid = x.getPlayerId();
	    int mover = x.getRoleForPlayerIdPermissive(moverPlayerId);
	    if (mover < 0) throw new IllegalArgumentException("PlayerId " + moverPlayerId + " is not a party to episode " + episodeId);

	    File f= Files.guessesFile(moverPlayerId);
	    epi.saveGuessToFile(f, moverPlayerId, text, guessConfidence);
	    epi.setAllGuessData(mover, text, guessConfidence);
	    //Main.persistObjects(epi);
	    //Main.saveObject(epi); // does this create duplicate Episode rows?
	    x.saveMe(); 
	    GuessWriteReport g = new GuessWriteReport(f, f.length());
	    g.transitionMap = x.new TransitionMap();
	    return g;
	} catch(IOException ex) {
	    return  new	    FileWriteReport(true,ex.getMessage());
	}

    }
    
}
