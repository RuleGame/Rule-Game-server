package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.net.*;

import jakarta.json.*;

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

		final Pattern pat = Pattern.compile("^(\\S+)\\s*");
		Matcher m = pat.matcher(line);
		if (!m.find()) {
		    respond(out, CODE.INVALID_COMMAND,"# Invalid input - cannot find the command in this line: " + line);
		    return;
		}
		String cmd = m.group(1), tail = line.substring(m.end());

		/*
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
		*/
		
		if (cmd.equals("EXIT")) {
		    return;
		} else if (cmd.equals("GAME")) {
		    // GAME game-generator-parameters
		    if (tail.length()==0) {
			respond(out, Episode.CODE.INVALID_RULES, "No rule file or trial list file specified");			       
			return;
		    }

		    String[] argv = tail.split("\\s+");
		    stripQuotes(argv);
		    ParseConfig ht = new ParseConfig();
		    // allows seed=..., colors=.... etc among argv.
		    // (Cannot set inputDir though, since it's static and
		    // shared by all threads)
		    argv = ht.enrichFromArgv(argv);
		    GameGenerator gg;
		    try {
			gg = Captive.buildGameGenerator(ht, argv);
		    } catch(Exception ex) {
			respond(out, Episode.CODE.INVALID_RULES, "Exception: " + ex.getMessage());
			return;		
		    }

		    OutputMode outputMode = OutputMode.STANDARD;
		    while(true) {
			gameCnt++;
			Game game = gg.nextGame();
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

    static void stripQuotes(String[] argv) {
	for(int j=0; j<argv.length; j++) {
	    String a = argv[j];	    
	    if (a.length()<2) continue;
	    if (a.startsWith("\"") && a.endsWith("\"") ||
		a.startsWith("'") && a.endsWith("'")) {
		argv[j] = a.substring(1, a.length()-1);
	    }
	}
    }


    
}

  
