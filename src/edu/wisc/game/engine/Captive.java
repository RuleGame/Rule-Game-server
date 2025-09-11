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
import edu.wisc.game.saved.TranscriptManager;


/** The main class for the Captive Game Server */
public class Captive {

    /** Produces a single-line or multi-line comment to be used in stdout */
    public static String asComment(String s) {
	String[] v = s.split("\n");
	for(int i=0; i<v.length; i++) v[i] = "#" + v[i];
	return String.join("\n", v);			  
    }



   static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("Captive Game Server (https://rulegame.wisc.edu/w2020/captive.html)\n");
	System.err.println("Usage:\n");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt board-file.json");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt npieces [nshapes ncolors]");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive trial-list-file.csv rowNumber");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive R:rule-file.txt:modifier-file.csv");
	System.err.println("Each of 'npieces', 'nshapes', and 'ncolors' is either 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    /** A wrapper for the simplified rule set name; it's extracted from 
	the command line, and is used in the MLC results file */
    /* private static class RuleSetNameWrapper {
	String simpleRuleSetName;
	init(String r) {
	    simpleRuleSetName = r.replaceAll(".*" + '/', "").replaceAll("\\.txt$", "");	    
	}
    }*/

    /** Creates a GameGenerator based on the parameters found in the command
	line 
	@param argv The argv array (from the command line or the GAME
	command in the pipe or socket stream), from which any superfluous 
	quotes must have already been stripped. 
	@param simpleRuleSetName An output parameter, into which
	the simplified rule set name (no dir name and no extension)
	will be put.
    */
    public static GameGenerator buildGameGenerator(ParseConfig ht, String[] argv//,
					    // Vector<String> simpleRuleSetName
					    ) throws IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{
	GameGenerator gg = buildGameGenerator2(ht, argv);
	gg.setConditionsFromHT(ht);
	return gg;
    }

    /** The inner part of the above */
    private static GameGenerator buildGameGenerator2(ParseConfig ht, String[] argv//,
					    // Vector<String> simpleRuleSetName
					    ) throws IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{

	
	long seed = ht.getOptionLong("seed", 0L);
	final RandomRG random= (seed != 0L)? new RandomRG(seed): new RandomRG();
  	
	int ja=0;
	if (argv.length<1) throw new IllegalInputException("No params specified");
	String fname = argv[ja++];
	boolean isR = fname.startsWith("R:"); // R:ruleSet:modifier

	if (isR) {
	    //ExperimentPlanHandle eph = new TrialList.ExperimentPlanHandle(exp);
	    //String r = eph.mainRuleSetName.replaceAll(".*/", "").replaceAll("\\.txt$", "");
	    //simpleRuleSetName.add(r);
	    TrialList trialList = new TrialList(fname, null);
	    ParaSet para = trialList.elementAt(0);
	    return  GameGenerator.mkGameGenerator(random, para);
	}

	
	File f =  new File(fname);
	if (!f.canRead())  throw new IllegalInputException("Cannot read file " + f);


	if (argv.length<2) throw new IllegalInputException("No params specified");

	//System.out.println("isR=" + isR+"; fname=" + fname);
	
	if (f.getName().endsWith(".csv")) { // Trial list file + row number

		// argv[ja:...] can contain either  (trialListFile, rowNo), or (nPieceRanges, ...)
 
		return RandomGameGenerator.buildFromArgv(random, null, ht, argv, ja-1);

	}

	String b = argv[ja++];
	if (b.indexOf(".")>=0) { // Rule file + initial board file
	    File bf = new File(b);
	    Board board = Board.readBoard(bf);
	    return new TrivialGameGenerator(random, new Game(AllRuleSets.read(f), board));
	} else { // Rule file + numeric params
	    try {
		// argv[ja:...] can contain either  (trialListFile, rowNo), or (nPieceRanges, ...)
 
		return RandomGameGenerator.buildFromArgv(random, f, ht, argv, ja-1);
	    } catch(IllegalArgumentException ex) {
		throw ex;
		//usage(ex.getMessage());
		//return null;
	    }

	}
    }

    
    /** Creates an MLC results logger object, if required by the command
	line params.
	@return an MLC results logger object, or null if one is not requested
     */
    static public MlcLog mkLog(ParseConfig ht) {

	String logFileName=ht.getOption("log", null);
	boolean append = false;
	if (logFileName==null) {
	    logFileName=ht.getOption("logappend", null);
	    append = (logFileName!=null);
	}
	
	if (logFileName==null) return null;

	MlcLog log = new MlcLog(new File(logFileName), append);

	log.format=ht. getOptionEnum(MlcLog.LogFormat.class,"log.format", MlcLog.LogFormat.Long);
	
	log.nickname=ht.getOption("log.nickname", log.nickname);
	log.run=ht.getOption("log.run", log.run);
	if (log.nickname==null) usage("Since you have specified -log, you also must supply -log.nickname");
	if (log.run<0) usage("Since you have specified -log, you also must supply -log.run");	
	
	return log;

    }
 


    /** A complete CGS session. Creates a game generator, creates a
	game, plays one or several episodes, and exits.
     */
    public static void main(String[] argv) throws IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{ 

	Files.allowCachingAllRules(true); // for greater efficiency
	
	// The captive server does not need the master conf file in /opt/w2020
	MainConfig.setPath(null);
	// Enable the computing of feature-lists for Composite objects
	// (which is normally turned off)
	edu.wisc.game.svg.Composite.setNeedFeatures(true);
	
	ParseConfig ht = new ParseConfig();

	// allows seed=... , colors=..., condTrain=..., crowded=... etc among argv
	argv = ht.enrichFromArgv(argv);

	//System.out.println("output=" +  ht.getOption("output", null));
	OutputMode outputMode = ht.getOptionEnum(OutputMode.class, "output", OutputMode.FULL);

	String inputDir=ht.getOption("inputDir", null);
	//System.out.println("#inputDir=" + inputDir);
	if (inputDir!=null) Files.setInputDir(inputDir);

	MlcLog log=mkLog(ht);		      

	GameGenerator gg=null;
	try {
	    gg = buildGameGenerator(ht, argv);
	} catch(Exception ex) {
	    usage("Cannot create game generator. Problem: " + ex.getMessage());
	}
	        	
	if (log!=null) log.rule_name = gg.getRules().file.getName().replaceAll("\\.txt$", "");


	int gameCnt=0;

	if (log!=null) log.open();
		
	while(true) {
	    Game game = gg.nextGame();
	    //	    if (outputMode== OutputMode.FULL) System.out.println(asComment(game.rules.toString()));

	    Episode epi = new Episode(game, outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));
  
	    boolean z = epi.playGame(gg,gameCnt+1);
	    if (log!=null) log.logEpisode(epi, gameCnt);


	    //-- transcript
	    if (false) {
		//zzz
		TranscriptManager.ExtraTranscriptInfo extra = new  TranscriptManager.ExtraTranscriptInfo();
		extra.playerId = log.nickname;
		extra.trialListId = x.getTrialListId();
		extra.seriesNo = 0;
		extra.ruleId = ser.para.getRuleSetName();
		extra.episodeNo = gameCnt;
	    }
  

	    
	    if (!z) break;
	    gameCnt++;
	}

	if (log!=null) log.close();
    }

}
