package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;

import javax.json.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.sql.Episode.OutputMode;


class GameSocketServerThread extends Thread {

    private final Socket socket;
 
    public GameSocketServerThread(Socket _socket) {
        super("GameSocketServerThread " + _socket.getInetAddress());
        socket = _socket;
 }


    static private void respond(PrintWriter out, int code, String msg) {
	String s = "" + code;
	int finishCode = 0, attemptCnt=0;
	s +=  " " +finishCode +" "+attemptCnt;
	if (msg!=null) s += "\n" + msg;
	out.println(s);
    }    

    
    public void run() {
	long id = getId();
	InetAddress ia = socket.getInetAddress();
	System.out.println("Thread " + id + ": connection from " + ia);
	//System.out.println("Thread " + id + ": isClosed=" + socket.isClosed());
	int gameCnt=0;
	    
        try (
	    // Using default character encoding (UTF-8)
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        ) {

	    LineNumberReader​ r = new LineNumberReader​(in);
	    String line=null;
	    while((line=r.readLine())!=null) {
		line = line.trim();
		if (line.equals("")) continue;


		Vector<Token> tokens;
		try {
		    tokens    = Token.tokenize(line);
		} catch(RuleParseException ex) {
		    respond(out, CODE.INVALID_COMMAND,"# Invalid input - cannot parse");
		    return;
		}
		if (tokens.size()==0 || tokens.get(0).type!=Token.Type.ID) {
		    respond(out, CODE.INVALID_COMMAND, "# Invalid input - not a command");
		    return;
		}
		String cmd = tokens.get(0).sVal.toUpperCase();

		if (cmd.equals("EXIT")) {
		    return;
		} else if (cmd.equals("GAME")) {
		    // GAME "rule-file.json" n
		    if (tokens.size()!=3) {
			respond(out, Episode.CODE.INVALID_RULES, "No rule file or piece count specified");			       
			return;
		    }
		    if (tokens.get(1).type!=Token.Type.STRING ||
			tokens.get(2).type!=Token.Type.NUMBER) {
			respond(out, CODE.INVALID_RULES, "# Expected double-quoted string and a number after the GAME command, found: " + tokens.get(1) + " " + tokens.get(2));
			return;
		    }
		    
		    String ruleFile = tokens.get(1).sVal;
		    File f = new File(ruleFile);
		    if (!f.canRead()) {
			respond(out, Episode.CODE.INVALID_RULES,"Cannot read rule file: " + f);
			return;
		    }

		    System.out.println("Thread " + id + ": reading rules from file " + f);
		    String text = Util.readTextFile(f);
		    RuleSet rules;
		    try {
			rules = new RuleSet(text);
		    } catch(RuleParseException ex) {
			respond(out, Episode.CODE.INVALID_RULES,"Parse error in rule file " + f);
			return;		 	
		    }
		    
		    int nPieces = tokens.get(2).nVal;
		    if (nPieces <= 0) {
			respond(out, Episode.CODE.INVALID_RULES, "Must specify a positive number of pieces");
			return;
		    }
		    Game game = new Game(rules, nPieces,
					 Piece.Shape.legacyShapes,
					 Piece.Color.legacyColors);  

		    OutputMode outputMode = OutputMode.STANDARD;
		    while(true) {
			gameCnt++;
			Episode epi = new Episode(game, outputMode, in, out);
			if (!epi.playGame(gameCnt)) return;
		    }
		    
		} else {
		    respond(out, Episode.CODE.INVALID_COMMAND, "Invalid command " + cmd);
		    return;
		}
	    }
	    
	} catch (IOException e) {
            e.printStackTrace();
	} finally {
	    try {
		socket.close();
	    } catch(IOException e) {}
	    System.out.println("Thread " + id + ": finishing after playing " + gameCnt + " episodes");		
	}
    }
}

  
