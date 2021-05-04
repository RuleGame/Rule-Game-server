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
    /** A game with IPB pieces have allImages!=non-null, while allShapes and allColors are both nulls; a color-and-shape game will have allImages==null */
    final String[] allImages;
    
    public Game(RuleSet _rules, Board _initialBoard) {
	rules = _rules;
	initialBoard = _initialBoard;
	randomObjCnt = 0;
	allShapes=null;
	allColors=null;
	allImages=null;
    }
    public Game(RuleSet _rules, int _randomObjCnt, Piece.Shape[] _allShapes,    Piece.Color[] _allColors) {
	rules = _rules;
	randomObjCnt = _randomObjCnt;
	allShapes =_allShapes;
	allColors =_allColors;
	allImages=null;
    }
    /** A game with shape-and-color objects used as game pieces */
    public Game(RuleSet _rules, int _randomObjCnt, int _nShapes, int _nColors,
		Piece.Shape[] _allShapes,    Piece.Color[] _allColors ) {
	this(_rules,  _randomObjCnt, _allShapes,_allColors);
	nShapes =  _nShapes;
	nColors  = _nColors;
    }
    /** A game with image-and-properties-based objects used as game pieces */
    public Game(RuleSet _rules, int _randomObjCnt, String[] _allImages) {
	rules = _rules;
	randomObjCnt = _randomObjCnt;
	allShapes = null;
	allColors = null;
	allImages = _allImages;
    }

    
}
