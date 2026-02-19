package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.text.*;
import jakarta.json.*;


import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.sql.Episode.OutputMode;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.rest.*;
import edu.wisc.game.engine.*;

/** What you need to know about an episode when sending that info to
	Gemini */
class EpisodeHistory {
	final Episode epi;
	final Board initialBoard;
	/** Info about any unnecessarily repeated move attempts in this episode */
	Repeats repeats = new Repeats();
	EpisodeHistory(Episode _epi) {
	    epi = _epi;
	    initialBoard = epi.getInitialBoard();
	}
	static HashSet<String> excludableNames =
	    Util.array2set("buckets", 
			   "dropped",
			   "0.id");
	
	static String boardAsString(Board b) {
	    JsonObject jo = JsonReflect.reflectToJSONObject(b, true,  excludableNames);
	    return jo.toString();
	}
	String initialBoardAsString() {
	    return boardAsString(initialBoard);
	}
	String currentBoardAsString() {
	    return boardAsString(epi.getCurrentBoard(false));
	}
}

