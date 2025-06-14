package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.rest.TrialList;

/** This class generates random games (with the same rule set and
    randomly created initial boards) based on the provided parameter
    range specifications */
public class RandomGameGenerator extends GameGenerator {

    final int[] nPiecesRange, nShapesRange, nColorsRange;
    final Piece.Shape[] allShapes;// = 	Piece.Shape.legacyShapes;
    final Piece.Color[] allColors;// = 	Piece.Color.legacyColors;

    public static String rangeToString(int [] range) {
	if (range[0]==range[1]) return "" + range[0];
	else return "["+range[0]+":"+range[1]+"]";
    }

    public String toString() {
	String s = "RandomGameGenerator with nPieces=" + rangeToString(nPiecesRange) + ", nShapes="  + rangeToString(nShapesRange) + ", nColors="  + rangeToString(nColorsRange);
	s += ". " + extraToString();
	return s;
	   
    }

      
    RandomGameGenerator(RandomRG _random, String ruleSetName, int[] _nPiecesRange, int[] _nShapesRange,
			int[] _nColorsRange,  Piece.Shape[] _allShapes, Piece.Color[] _allColors) throws IOException, RuleParseException {
	this(_random, AllRuleSets.obtain(ruleSetName), _nPiecesRange,  _nShapesRange,
	     _nColorsRange, _allShapes, _allColors);

    }

    public RandomGameGenerator(RandomRG _random, File ruleSetFile, int[] _nPiecesRange, int[] _nShapesRange,
		  int[] _nColorsRange,  Piece.Shape[] _allShapes, Piece.Color[] _allColors) throws IOException, RuleParseException {
	this(_random, AllRuleSets.read(ruleSetFile), _nPiecesRange,  _nShapesRange,
	     _nColorsRange, _allShapes, _allColors);

    }


    RandomGameGenerator(RandomRG _random, RuleSet _rules, int[] _nPiecesRange, int[] _nShapesRange,
		  int[] _nColorsRange,  Piece.Shape[] _allShapes, Piece.Color[] _allColors) throws IOException, RuleParseException {
	super(_random, _rules);
	nPiecesRange = _nPiecesRange;
	nShapesRange = _nShapesRange;
	nColorsRange = _nColorsRange;
	allShapes =  _allShapes;
	allColors = _allColors;
	if (nPiecesRange[0]>nPiecesRange[1] ||
	    nShapesRange[0]>nShapesRange[1] ||
	    nColorsRange[0]>nColorsRange[1])  throw new IOException("GameGenerator: Invalid param range");
	if (nPiecesRange[0]<=0) throw new IOException("GameGenerator: Number of pieces must be positive");

	if (nPiecesRange[1]>Board.N * Board.N) throw new IOException("GameGenerator: more pieces than cells: #pieces=" + nPiecesRange[1]);
    }

    /** Generates a game with a random initial board, in accordance with this 
	generator's parameters */
    public Game nextGame() {
	int nPieces = random.getInRange(nPiecesRange);
	int nShapes = random.getInRange(nShapesRange);
	int nColors = random.getInRange(nColorsRange);

	Game game = new Game(random, rules, nPieces, nShapes, nColors,allShapes,allColors);
	next(game);
	return game;
    }

        /** "3" to {3,3}; "3:5" to {3,5} */
    static private int[] range(String x) {
	String v[] = x.split(":");
	if (v.length==1) {
	    int n=Integer.parseInt(v[0]);	    
	    if (n<0) throw new IllegalArgumentException("Illegal value ("+x+") for the number of properties");
	    return new int[] {n, n};
	} else if (v.length==2) {
	    int z[]= {Integer.parseInt(v[0]),Integer.parseInt(v[1])};
	    if (z[0]>z[1] ||
		z[0]<0 || z[0]==0 && z[1]>0) throw new IllegalArgumentException("Illegal range: x");
	    
	    return z;
	} else {
	    throw new IllegalArgumentException("Cannot parse range spec: " + x);
	}
    }
    
    static private int[] range(String x, Class<? extends Enum> p) {
	int z[]=range(x);
	int nProp = p.getEnumConstants().length;
	if (z[1]>nProp) throw new IllegalArgumentException("Illegal value ("+z[1]+") for the number of " +p+ " properties");
	return z;
    }
    

    /** Builds a RandomGameGenerator from command-line arguments. Used in Captive Game Server, and for various other tools, such as the command-line random board generator.
	@param f If specified, this is the rule set file to use. It can be null, if you just need the generator to create boards, and not to play a game.
	@param ja Use argv[ja:...]. This can contain either  (trialListFile, rowNo), or (nPieceRanges, ...)
     */
    public static GameGenerator buildFromArgv(RandomRG _random, File f, ParseConfig ht, String[] argv, int ja) throws IOException, RuleParseException, IllegalInputException, ReflectiveOperationException {

	//System.out.println("#DEBUG: bFA, ht=\n" + ht);

	
	String b = argv[ja++];	
	if (b.endsWith(".csv")) {
	    File tf = new File(b);
	    TrialList trialList = new TrialList(tf);

	    if (ja >= argv.length) usage("Too few arguments: Missing row_number");
	    else if (ja+1 != argv.length) usage("Too many arguments");
	    b = argv[ja++];
	    int rowNo = Integer.parseInt(b);
	    //System.out.println("#DEBUG: tf=" + tf +", rowNo=" + rowNo);
	    if (rowNo<=0 || rowNo> trialList.size())   throw new IllegalInputException("Invalid row number (" + rowNo+ "). Row numbers should be positive, and should not exceed the size of the trial list ("+trialList.size()+")");
	    ParaSet para = trialList.elementAt(rowNo-1);
	    return mkGameGenerator(_random,  para);
	}

	//System.out.println("#DEBUG: rule file=" + f +", nPieceRange=" + b);
	    
	int[] nPiecesRange = range(b);
	if (nPiecesRange[0] <= 0) throw new IllegalArgumentException("Invalid number of pieces ("+b+"); The number of pieces must be positive");

	int[] zeros = {0,0};
	int[] nShapesRange=(ja<argv.length)? range(argv[ja++]) : zeros;
	//int[] nColorsRange=(ja<argv.length)? range(argv[ja++], Piece.Color.class) : zeros;
	int[] nColorsRange=(ja<argv.length)? range(argv[ja++]) : zeros;
	

	//System.out.println("#option shapes=" + ht.getOption("shapes",null));
	//System.out.println("#option colors=" + ht.getOption("colors",null));
	String shapesString = (ja<argv.length)? argv[ja++]: ht.getOption("shapes",null);
	Piece.Shape[] shapes = ParaSet.parseShapes(shapesString);
	if (shapes==null) shapes = Piece.Shape.legacyShapes;

	String colorsString = (ja<argv.length)? argv[ja++]: ht.getOption("colors",null);
	
	Piece.Color[] colors =  ParaSet.parseColors(colorsString);
	if (colors==null) colors = Piece.Color.legacyColors;

	return new RandomGameGenerator(_random, f, nPiecesRange, nShapesRange, nColorsRange, shapes, colors);    
    }

   static private void usage(String msg) {
	System.err.println("Usage 1:\n");
	System.err.println("  java [options]  edu.wisc.game.engine.RandomGameGenerator out-dir number-of-boards npieces [nshapes ncolors [shapes-list color-list]");
	System.err.println("Each of 'npieces', 'nshapes', and 'ncolors' is either 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	System.err.println("Usage 2:\n");
	System.err.println("  java [options]  edu.wisc.game.engine.RandomGameGenerator out-dir number-of-boards trial-list-file.csv row-number");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }
    

    /** Creates a bunch of random boards, from which one can later select
	those matching some additional criteria, and use them in an experiment 
	plan with predefined boards.

	<p>FIXME: if you plan to create games for bot assist games, you can
	add an option for needLabels
     */
    public static void main(String[] argv) throws IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{ 

	ParseConfig ht = new ParseConfig();

	// allows seed=... , colors=..., condTrain=... etc among argv
	argv = ht.enrichFromArgv(argv);

	
	int ja =0;
	if (argv.length<3) usage(null);
	String dirName = argv[ja++];
	File dir=new File(dirName);
	if (!dir.isDirectory()) usage("The output directory '" +dir + "' does not exist. Please create it before running this tool, or specify another directory");
	int nb = Integer.parseInt(argv[ja++]);
	System.out.println("Will generate " +nb + " boards in directory "+dir);

	String fs="000";
	for(int m=nb/1000; m>0; m /= 10) fs += "0";
	DecimalFormat fmt =new DecimalFormat(fs);

	GameGenerator gg=buildFromArgv(new RandomRG(), null, ht, argv, ja);
	gg.setConditionsFromHT(ht);

	RandomRG random = new RandomRG();
	for(int j=0; j<nb; j++) {
	    File f = new File(dir, fmt.format(j) +".json");
	    Game g =gg.nextGame();
	    Board b = g.giveBoard(false);
		//new Board(random, g.randomObjCnt, g.nShapes, g.nColors, g.allShapes, g.allColors);
	    PrintWriter w=new PrintWriter(new FileWriter(f));
	    String s = JsonReflect.reflectToJSONObject(b, true).toString();
	    // Insert a line break before each game piece
	    // "value":[{"id":0,"color":"BLUE",....}
	    s = s.replaceAll( "(.\"id\":)", "\n$1");
	    w.println(s);
	    w.close();

	}
    }
    
    
}
