package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.ParaSet;


/** This class generates random games (with the same rule set and
    randomly created initial boards) using image-and-property based
    game pieces based on the provided parameter
    range specifications */
public class RandomImageGameGenerator extends GameGenerator {

    final int[] nPiecesRange;
    final ImageObject.Generator imageGenerator; 
    
    RandomImageGameGenerator(RandomRG _random, String ruleSetName, int[] _nPiecesRange, ImageObject.Generator _imageGenerator) throws IOException, RuleParseException {
	this(_random, AllRuleSets.obtain(ruleSetName), _nPiecesRange, _imageGenerator);

    }

    public RandomImageGameGenerator(RandomRG _random, File ruleSetFile, int[] _nPiecesRange, ImageObject.Generator _imageGenerator) throws IOException, RuleParseException {
	this(_random, AllRuleSets.read(ruleSetFile), _nPiecesRange,  _imageGenerator);

    }


    RandomImageGameGenerator(RandomRG _random, RuleSet _rules, int[] _nPiecesRange,
			     ImageObject.Generator _imageGenerator
			     //String[] _allImages
			     ) throws IOException, RuleParseException {
	super(_random, _rules);
	nPiecesRange = _nPiecesRange;
	imageGenerator = _imageGenerator;
	if (nPiecesRange[0]>nPiecesRange[1])  throw new IOException("GameGenerator: Invalid param range");
	if (nPiecesRange[0]<=0) throw new IOException("GameGenerator: Number of pieces must be positive");

	if (nPiecesRange[1]>Board.N * Board.N) throw new IOException("GameGenerator: more pieces than cells: #pieces=" + nPiecesRange[1]);
    }

    /** Generates a game with a random initial board, in accordance with this 
	generator's parameters */
    public Game nextGame() {
	int nPieces = random.getInRange(nPiecesRange);

	Game game = new Game(random, rules, nPieces,  imageGenerator);
	next(game);
	return game;
    }

    /** For Captive server, to be printed via JSON Reflect */
    //    public Map<String, Vector<Object>> getExtraFields() {
    //	return imageGenerator.getAllFeatures();
    //}

    /** Creates a bunch of random boards, from which one can later select
	those matching some additional criteria, and use them in an experiment 
	plan with predefined boards.
     */
    /*
    public static void main(String[] argv) throws IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{ 

	ParseConfig ht = new ParseConfig();
	int ja =0;
	if (argv.length<3) usage(null);
	String dirName = argv[ja++];
	File dir=new File(dirName);
	if (!dir.isDirectory()) usage("The output directory '" +dir + "' does not exist. Please create it before running this tool, or specify another directory");
	int nb = Integer.parseInt(argv[ja++]);
	System.out.println("Will generate " +nb + " boards in directory "+dir);
	RandomGameGenerator gg=buildFromArgv(new RandomRG(), null, ht, argv, ja);
	String fs="000";
	for(int m=nb/1000; m>0; m /= 10) fs += "0";
	DecimalFormat fmt =new DecimalFormat(fs);

	RandomRG random = new RandomRG();
	for(int j=0; j<nb; j++) {
	    File f = new File(dir, fmt.format(j) +".json");
	    Game g =gg.nextGame();
	    Board b = new Board(random, g.randomObjCnt, g.nShapes, g.nColors, g.allShapes, g.allColors);
	    PrintWriter w=new PrintWriter(new FileWriter(f));
	    String s = JsonReflect.reflectToJSONObject(b, true).toString();
	    // Insert a line break before each game piece
	    // "value":[{"id":0,"color":"BLUE",....}
	    s = s.replaceAll( "(.\"id\":)", "\n$1");
	    w.println(s);
	    w.close();

	}
    }
    */

    
}
