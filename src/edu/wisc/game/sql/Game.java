package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.parser.*;
import edu.wisc.game.engine.RuleSet;

public class Game {
    public RuleSet rules;
    /** Only if fixed; null if random */
    public Board initialBoard;
    /** If starting with a random board, the number of pieces to use. Only used if initialBoard==null */
    public int randomObjCnt, nShapes=0, nColors=0;

    final Piece.Shape[] allShapes;
    final Piece.Color[] allColors;
    
    
    public Game(RuleSet _rules, Board _initialBoard) {
	rules = _rules;
	initialBoard = _initialBoard;
	randomObjCnt = 0;
	allShapes=null;
	allColors=null;
    }
    public Game(RuleSet _rules, int _randomObjCnt, Piece.Shape[] _allShapes,    Piece.Color[] _allColors) {
	rules = _rules;
	randomObjCnt = _randomObjCnt;
	allShapes =_allShapes;
	allColors =_allColors;
    }
    public Game(RuleSet _rules, int _randomObjCnt, int _nShapes, int _nColors,
		Piece.Shape[] _allShapes,    Piece.Color[] _allColors ) {
	this(_rules,  _randomObjCnt, _allShapes,_allColors);
	nShapes =  _nShapes;
	nColors  = _nColors;
    }
}
