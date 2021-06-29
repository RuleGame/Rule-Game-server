package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.ParaSet;


/** This class generates random games (with the same rule set and
    randomly created initial boards) based on the provided parameter
    range specifications */
public class RandomGameGenerator extends GameGenerator {

    final int[] nPiecesRange, nShapesRange, nColorsRange;
    final Piece.Shape[] allShapes;// = 	Piece.Shape.legacyShapes;
    final Piece.Color[] allColors;// = 	Piece.Color.legacyColors;
  
    //final RuleSet rules;
    
    RandomGameGenerator(String ruleSetName, int[] _nPiecesRange, int[] _nShapesRange,
			int[] _nColorsRange,  Piece.Shape[] _allShapes, Piece.Color[] _allColors) throws IOException, RuleParseException {
	this(AllRuleSets.obtain(ruleSetName), _nPiecesRange,  _nShapesRange,
	     _nColorsRange, _allShapes, _allColors);

    }

    public RandomGameGenerator(File ruleSetFile, int[] _nPiecesRange, int[] _nShapesRange,
		  int[] _nColorsRange,  Piece.Shape[] _allShapes, Piece.Color[] _allColors) throws IOException, RuleParseException {
	this(AllRuleSets.read(ruleSetFile), _nPiecesRange,  _nShapesRange,
	     _nColorsRange, _allShapes, _allColors);

    }


    RandomGameGenerator(RuleSet _rules, int[] _nPiecesRange, int[] _nShapesRange,
		  int[] _nColorsRange,  Piece.Shape[] _allShapes, Piece.Color[] _allColors) throws IOException, RuleParseException {
	super(_rules);
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
	int nPieces = Board.random.getInRange(nPiecesRange);
	int nShapes = Board.random.getInRange(nShapesRange);
	int nColors = Board.random.getInRange(nColorsRange);

	Game game = new Game(rules, nPieces, nShapes, nColors,allShapes,allColors);
	next();
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
    


    /** Builds a RandomGameGenerator from command-line arguments */
    public static RandomGameGenerator buildFromArgv(File f, ParseConfig ht, String[] argv, int ja) throws IOException, RuleParseException {
	String b = argv[ja++];

	int[] nPiecesRange = range(b);
	if (nPiecesRange[0] <= 0) throw new IllegalArgumentException("Invalid number of pieces ("+b+"); The number of pieces must be positive");

	int[] zeros = {0,0};
	int[] nShapesRange=(ja<argv.length)? range(argv[ja++]) : zeros;
	//int[] nColorsRange=(ja<argv.length)? range(argv[ja++], Piece.Color.class) : zeros;
	int[] nColorsRange=(ja<argv.length)? range(argv[ja++]) : zeros;
	

	//System.out.println("#option shapes=" + ht.getOption("shapes",null));
	//System.out.println("#option colors=" + ht.getOption("colors",null));
	Piece.Shape[] shapes = ParaSet.parseShapes(ht.getOption("shapes",null));
	if (shapes==null) shapes = Piece.Shape.legacyShapes;	    
	Piece.Color[] colors =  ParaSet.parseColors(ht.getOption("colors",null));
	if (colors==null) colors = Piece.Color.legacyColors;
		    
	return new RandomGameGenerator(f, nPiecesRange, nShapesRange, nColorsRange, shapes, colors);    
    }

   static private void usage(String msg) {
	System.err.println("Usage:\n");
	System.err.println("  java [options]  edu.wisc.game.engine.RandomGameGenerator out-dir number-of-boards npieces [nshapes ncolors [shapes-list color-list]");
	System.err.println("Each of 'npieces', 'nshapes', and 'ncolors' is either 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }
    

    /** Creates a bunch of random boards, from which one can later select
	those matching some additional criteria, and use them in an experiment 
	plan with predefined boards.
     */
    public static void main(String[] argv) throws IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{ 

	ParseConfig ht = new ParseConfig();
	int ja =0;
	if (argv.length<3) usage(null);
	String dirName = argv[ja++];
	File dir=new File(dirName);
	if (!dir.isDirectory()) usage("Not a directory: " +dir);
	int nb = Integer.parseInt(argv[ja++]);
	System.out.println("Will generate " +nb + " boards in directory "+dir);
	RandomGameGenerator gg=buildFromArgv(null, ht, argv, ja);
	String fs="000";
	for(int m=nb/100; m>0; m /= 10) fs += "0";
	DecimalFormat fmt =new DecimalFormat(fs);


	for(int j=0; j<nb; j++) {
	    File f = new File(dir, fmt.format(j) +".json");
	    Game g =gg.nextGame();
	    Board b = new Board( g.randomObjCnt, g.nShapes, g.nColors, g.allShapes, g.allColors);
	    PrintWriter w=new PrintWriter(new FileWriter(f));
	    w.println(JsonReflect.reflectToJSONObject(b, true));	
	    w.close();

	}
    }
    
    
}
