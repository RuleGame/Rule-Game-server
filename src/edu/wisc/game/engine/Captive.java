package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
//import java.text.*;

import javax.json.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Episode.OutputMode;

public class Captive {

    static String asComment(String s) {
	String[] v = s.split("\n");
	for(int i=0; i<v.length; i++) v[i] = "#" + v[i];
	return String.join("\n", v);			  
    }



   static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("Usage:\n");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt [nPieces|board.json]");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    public static void main(String[] argv) throws IOException,  RuleParseException {

	ParseConfig ht = new ParseConfig();
	//System.out.println("output=" +  ht.getOption("output", null));
	OutputMode outputMode = ht.getOptionEnum(OutputMode.class, "output", OutputMode.FULL);
	
	//System.out.println("output mode=" +  outputMode);
	int ja=0;
	if (argv.length!=2) usage();
	File f = new File(argv[ja++]);
	if (!f.canRead()) usage("Cannot read file " + f);

	// System.out.println("Reading file " + f);
	String text = Util.readTextFile(f);
	RuleSet rules = new RuleSet(text);

	Game game;
	String a = argv[ja++];
	if (a.indexOf(".")>=0) {
	    throw new IllegalArgumentException("Board JSON not supported yet");
	} else {
	    int nPieces = Integer.parseInt(a);
	    if (nPieces <= 0) usage("The number of pieces must be positive");
	    game = new Game(rules, nPieces);
	}
	       
	if (outputMode== OutputMode.FULL)  System.out.println(asComment(rules.toString()));

	int gameCnt=0;

	while(true) {
	    gameCnt++;
	    Episode epi = new Episode(game, outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));
  
	    if (!epi.playGame(gameCnt)) break;
	}
    }

}
