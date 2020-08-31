package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
//import edu.wisc.game.rest.ParaSet;



/** This class generates random games based on the provided parameter range specifications */
public class GameGenerator {

    final int[] nPiecesRange, nShapesRange, nColorsRange;
    final RuleSet rules;
    
    GameGenerator(String ruleSetName, int[] _nPiecesRange, int[] _nShapesRange,
		  int[] _nColorsRange) throws IOException, RuleParseException {
	this(AllRuleSets.obtain(ruleSetName), _nPiecesRange,  _nShapesRange,
	     _nColorsRange);

    }

    public GameGenerator(File ruleSetFile, int[] _nPiecesRange, int[] _nShapesRange,
		  int[] _nColorsRange) throws IOException, RuleParseException {
	this(AllRuleSets.read(ruleSetFile), _nPiecesRange,  _nShapesRange,
	     _nColorsRange);

    }


    GameGenerator(RuleSet _rules, int[] _nPiecesRange, int[] _nShapesRange,
		  int[] _nColorsRange) throws IOException, RuleParseException {

	nPiecesRange = _nPiecesRange;
	nShapesRange = _nShapesRange;
	nColorsRange = _nColorsRange;
	if (nPiecesRange[0]> nPiecesRange[1] ||
	    nShapesRange[0]>nShapesRange[1] ||
	    nColorsRange[0]>nColorsRange[1])  throw new IOException("GameGenerator: Invalid param range");
	if (nPiecesRange[0]<=0) throw new IOException("GameGenerator: Number of pieces must be positive");

	if (nPiecesRange[1]>Board.N * Board.N) throw new IOException("GameGenerator: more pieces than cells: #pieces=" + nPiecesRange[1]);
	rules = _rules;
    }

    
    public Game nextGame() {
	int nPieces = Board.random.getInRange(nPiecesRange);
	int nShapes = Board.random.getInRange(nShapesRange);
	int nColors = Board.random.getInRange(nColorsRange);

	Game game = new  Game(rules, nPieces, nShapes, nColors);
	return game;
    }

}
