package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.ParaSet;


/** This class generates random games (with the same rule set and
    randomly created initial boards) using image-and-property based
    game pieces based on the provided parameter
    range specifications */
public class RandomImageGameGenerator extends GameGenerator {

    final int[] nPiecesRange;
    final String[] allImages;
  
    
    RandomImageGameGenerator(String ruleSetName, int[] _nPiecesRange, String[] _allImages) throws IOException, RuleParseException {
	this(AllRuleSets.obtain(ruleSetName), _nPiecesRange,  _allImages);

    }

    public RandomImageGameGenerator(File ruleSetFile, int[] _nPiecesRange, String[] _allImages) throws IOException, RuleParseException {
	this(AllRuleSets.read(ruleSetFile), _nPiecesRange, _allImages);

    }


    RandomImageGameGenerator(RuleSet _rules, int[] _nPiecesRange, String[] _allImages) throws IOException, RuleParseException {
	super(_rules);
	nPiecesRange = _nPiecesRange;
	allImages = _allImages;
	if (nPiecesRange[0]>nPiecesRange[1])  throw new IOException("GameGenerator: Invalid param range");
	if (nPiecesRange[0]<=0) throw new IOException("GameGenerator: Number of pieces must be positive");

	if (nPiecesRange[1]>Board.N * Board.N) throw new IOException("GameGenerator: more pieces than cells: #pieces=" + nPiecesRange[1]);
    }

    /** Generates a game with a random initial board, in accordance with this 
	generator's parameters */
    public Game nextGame() {
	int nPieces = Board.random.getInRange(nPiecesRange);

	Game game = new Game(rules, nPieces, allImages);
	next();
	return game;
    }

 
}
