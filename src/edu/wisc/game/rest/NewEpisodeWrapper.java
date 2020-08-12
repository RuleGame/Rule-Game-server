package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;

//@XmlRootElement(name = "NewEpisode") 

/**
FIXME: need to add periodic purge on episodes 
 */
public class NewEpisodeWrapper {
    boolean error=false;
    String errmsg=null;
    String episodeId=null;
    
    public boolean getError() { return error; }
    @XmlElement
    public void setError(boolean _error) { error = _error; }
    
    public String getErrmsg() { return errmsg; }
    @XmlElement
    public void setErrmsg(String _errmsg) { errmsg = _errmsg; }
    
    public String getEpisodeId() { return episodeId; }
    @XmlElement
    public void setEpisodeId(String _episodeId) { episodeId = _episodeId; }

    Board board = null;
    public Board getBoard() { return board; }
    @XmlElement
    public void setBoard(Board _b) { board = _b; }

    
    static HashMap<String, Episode> episodes = new HashMap<>();

    private static HashMap<String, RuleSet> ruleSets = new HashMap<>();
    

    NewEpisodeWrapper(String ruleSetName, String nPiecesString) {
	try {
	    if (nPiecesString==null)  throw new IOException("The number of pieces is not specified");
	    int nPieces;
	    try {
		nPieces= Integer.parseInt(nPiecesString);
	    } catch( NumberFormatException ex) {
		throw new IOException("Cannot parse nPieces as a number: " + nPiecesString);
	    }
	    if (ruleSetName==null ||ruleSetName.trim().equals("")) throw new IOException("No rules set specified");
	    

	    RuleSet rules = ruleSets.get(ruleSetName);
	    if (rules==null) {
		File base = new File("/opt/tomcat/game-data");
		base = new File(base, "rules");
		String ext = ".txt";
		String name = ruleSetName;
		if (!name.endsWith(ext)) name += ext;

		File f = new File(base, name);
		if (!f.canRead())  throw new IOException("Cannot read rule file: " + f);
		String text = Util.readTextFile(f);
		rules = new RuleSet(text);
		ruleSets.put(ruleSetName, rules);
	    }
	    if (nPieces <= 0)  throw new IOException("Number of pieces must be positive");
	    Game game = new  Game(rules, nPieces);
	    Episode epi = new Episode(game, Episode.OutputMode.BRIEF, null, null); //in, out);

	    board = epi.getCurrentBoard();
	    
	    episodes.put( episodeId = epi.episodeId, epi);
	    
	    setError( false);
	
	} catch(Exception ex) {
	    setError(true);
	    setErrmsg(ex.getMessage());
	}
	      
	
    }

    
    
}

