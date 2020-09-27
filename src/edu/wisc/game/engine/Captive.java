package edu.wisc.game.engine;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Episode.OutputMode;

/** The main class for the Captive Game Server */
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
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt board-file.json");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt pieces [shapes colors]");
	System.err.println("Each of 'pieces', 'shapes', and 'colors' is either 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    /** @param x "n" or "n1:n2"
     */
    /*
    static private int randomFromRange(String x, Class<? extends Enum> p) {
	int nProp = p.getEnumConstants().length;
	String v[] = x.split(":");
	if (v.length==1) {
	    int n=Integer.parseInt(v[0]);
	    if (n<=0 || n>nProp) throw new IllegalArgumentException("Illegal value (" + n+") for the number of " + p + " properties");
	    return n;
	} else if (v.length==2) {
	    int z[]= {Integer.parseInt(v[0]),Integer.parseInt(v[1])};
	    for(int n: z) {
		if (n<=0 || n>nProp) throw new IllegalArgumentException("Illegal value (" + n+") for the number of " + p + " properties");
	    }
	    return Board.random.getInRange(z[0], z[1]);
	} else {
	    throw new IllegalArgumentException("Cannot parse range spec: " + x);
	}
    }
    */


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


    public static void main(String[] argv) throws IOException,  RuleParseException, ReflectiveOperationException { 

	ParseConfig ht = new ParseConfig();
	//System.out.println("output=" +  ht.getOption("output", null));
	OutputMode outputMode = ht.getOptionEnum(OutputMode.class, "output", OutputMode.FULL);

	long seed = ht.getOptionLong("seed", 0L);
	if (seed != 0L) Board.initRandom(seed);


	
	//System.out.println("output mode=" +  outputMode);
	int ja=0;
	if (argv.length<2) usage();
	File f = new File(argv[ja++]);
	if (!f.canRead()) usage("Cannot read file " + f);

	GameGenerator gg;
	String a = argv[ja++];
	if (a.indexOf(".")>=0) {
	    File bf = new File(a);
	    Board board = Board.readBoard(bf);
	    gg = new GameGenerator(new Game(AllRuleSets.read(f), board));
	} else {
	    int[] nPiecesRange = range(a);
	    if (nPiecesRange[0] <= 0) usage("The number of pieces must be positive");

	    int[] zeros = {0,0};
	    int[] nShapesRange=(ja<argv.length)? range(argv[ja++], Piece.Shape.class) : zeros;
	    int[] nColorsRange=(ja<argv.length)? range(argv[ja++], Piece.Color.class) : zeros;

	    gg =new GameGenerator(f, nPiecesRange, nShapesRange, nColorsRange);    
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
