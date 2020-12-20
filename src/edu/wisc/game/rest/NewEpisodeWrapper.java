package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;

public class NewEpisodeWrapper extends ResponseBase {
    String episodeId=null;

    public String getEpisodeId() { return episodeId; }
    @XmlElement
    public void setEpisodeId(String _episodeId) { episodeId = _episodeId; }

    Board board = null;
    public Board getBoard() { return board; }
    @XmlElement
    public void setBoard(Board _b) { board = _b; }

     Episode.Display display;
   /** The structure with a lot of information about the current episode,
       and its place in the experiment's framework.
       (See {@link edu.wisc.game.sql.Episode.Display} for the full structure that's actually found here)
    */
    public Episode.Display getDisplay() { return display; }
    private void setDisplay(Episode.Display _display) { display = _display; }

    
    NewEpisodeWrapper(String ruleSetName, int nPieces,   int nShapes,  int nColors,
		      String boardName) {
	try {
	    /*
	    if (nPiecesString==null)  throw new IOException("The number of pieces is not specified");
	    int nPieces;
	    try {
		nPieces= Integer.parseInt(nPiecesString);
	    } catch( NumberFormatException ex) {
		throw new IOException("Cannot parse nPieces as a number: " + nPiecesString);
	    }
	    */
	    if (ruleSetName==null ||ruleSetName.trim().equals("")) throw new IOException("No rules set specified");
	    ruleSetName = ruleSetName.trim();
	    
	    RuleSet rules = AllRuleSets.obtain(ruleSetName);

	    Game game;
	    if (boardName!=null && boardName.trim().length()>0 && !boardName.equalsIgnoreCase("null")) {
		boardName = boardName.trim();
		File bf = Files.initialBoardFile(boardName);
		if (!bf.canRead()) {
		    //		    Logging.errror("Cannot read board file: " +bf);
		    throw new IOException("Cannot read board file: " +bf);
		}
		Board board = Board.readBoard(bf);
		game = new Game(rules, board);
	    } else {
	
		if (nPieces<=0 || nPieces>Board.N * Board.N) throw new IOException("Invalid #pieces=" + nPieces);

		game = new  Game(rules, nPieces, nShapes, nColors,
					 Piece.Shape.legacyShapes,
					 Piece.Color.legacyColors);
	    }
	    Episode epi = new Episode(game, Episode.OutputMode.BRIEF, null, null); //in, out);

	    board = epi.getCurrentBoard();
	    
	    EpisodeInfo.globalAllEpisodes.put( episodeId = epi.episodeId, epi);
	    setDisplay(epi.mkDisplay());

	    setError( false);
	
	} catch(Exception ex) {
	    System.err.print("NewServiceWrapper: " + ex);
	    ex.printStackTrace(System.err);

	    setError(true);
	    setErrmsg(ex.getMessage());
	}
	      
	
    }

    
    
}

