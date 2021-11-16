package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.util.regex.*;


import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Episode.OutputMode;
import edu.wisc.game.rest.*;


/** The main class for the Captive Game Server */
public class Captive {

    /** Produces a single-line or multi-line comment to be used in stdout */
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
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt board-file.json");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt npieces [nshapes ncolors]");
	System.err.println("Each of 'npieces', 'nshapes', and 'ncolors' is either 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }



    /** Creates a GameGenerator based on the parameters found in the command
	line 
	@param argv The argv array (from the command line or the GAME
	command in the pipe or socket stream), from which any superfluous 
	quotes must have already been stripped.
    */
    static GameGenerator buildGameGenerator(ParseConfig ht, String[] argv) throws IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{

	long seed = ht.getOptionLong("seed", 0L);
	//if (seed != 0L) Board.initRandom(seed);

	final RandomRG random= (seed != 0L)? new RandomRG(seed): new RandomRG();
  	
	//System.out.println("output mode=" +  outputMode);
	int ja=0;
	if (argv.length<2) throw new IllegalInputException("No params specified");
	String fname = argv[ja++];
	/*
	if (fname.length()>=2 &&
	    (fname.startsWith("\"") && fname.endsWith("\"") ||
	     fname.startsWith("'") && fname.endsWith("'"))) {
	    fname = fname.substring(1, fname.length()-1);
	}
	*/
	File f = new File(fname);
	if (!f.canRead())  throw new IllegalInputException("Cannot read file " + f);

	String b = argv[ja++];

	if (f.getName().endsWith(".csv")) { // Trial list file + row number
	    TrialList trialList = new TrialList(f);
	    int rowNo = Integer.parseInt(b);
	    if (rowNo<=0 || rowNo> trialList.size())   throw new IllegalInputException("Invalid row number (" + rowNo+ "). Row numbers should be positive, and should not exceed the size of the trial list ("+trialList.size()+")");
	    ParaSet para = trialList.elementAt(rowNo-1);
	    return  GameGenerator.mkGameGenerator(random, para);

	} else if (b.indexOf(".")>=0) { // Rule file + initial board file
	    File bf = new File(b);
	    Board board = Board.readBoard(bf);
	    return new TrivialGameGenerator(random, new Game(AllRuleSets.read(f), board));
	} else { // Rule file + numeric params
	    try {
		return RandomGameGenerator.buildFromArgv(random, f, ht, argv, ja-1);
	    } catch(IllegalArgumentException ex) {
		throw ex;
		//usage(ex.getMessage());
		//return null;
	    }

	}
    }
	       


    /** A complete CGS session. Creates a game generator, creates a
	game, plays one or several episodes, and exits.
     */
    public static void main(String[] argv) throws IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{ 

	ParseConfig ht = new ParseConfig();

	// allows seed=... , colors=.... etc among argv
	argv = ht.enrichFromArgv(argv);

	//System.out.println("output=" +  ht.getOption("output", null));
	OutputMode outputMode = ht.getOptionEnum(OutputMode.class, "output", OutputMode.FULL);

	String inputDir=ht.getOption("inputDir", null);
	if (inputDir!=null) Files.setInputDir(inputDir);
	
	GameGenerator gg=null;
	try {
	    gg = buildGameGenerator(ht, argv);
	} catch(Exception ex) {
	    usage("Cannot create game generator. Problem: " + ex.getMessage());
	}
	        	
	int gameCnt=0;

	while(true) {
	    gameCnt++;
	    Game game = gg.nextGame();
	    if (outputMode== OutputMode.FULL) System.out.println(asComment(game.rules.toString()));

	    Episode epi = new Episode(game, outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));
  
	    if (!epi.playGame(gameCnt)) break;
	}
    }

}
