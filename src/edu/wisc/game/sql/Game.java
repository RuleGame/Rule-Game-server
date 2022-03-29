package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.RuleSet;

/** A Game object defines how an Episode may be created. A Game object
    may either consists of a rule set + a predefined board, or of a
    rule set + the procedure for creating a random board. In the
    latter case, the procedure for creating a random board may include
    a random number generator and the parameters of the distribution
    from which a random board may be drawn (e.g. a color set and the
    the number of colors, and the same for shapes).

 */
public class Game {
    /** This should be non-null if this game involves a random board
	creation is */
    final RandomRG random;

    public RuleSet rules;
    /** Only if fixed; null if random */
    public Board initialBoard;
    /** If starting with a random board, the number of pieces to use. Only used if initialBoard==null */
    public int randomObjCnt, nShapes=0, nColors=0;

    final Piece.Shape[] allShapes;
    final Piece.Color[] allColors;
    /** A game with IPB pieces have allImages!=non-null, while allShapes and allColors are both nulls; a color-and-shape game will have allImages==null. Array elements  */
    //final String[] allImages;
    final ImageObject.Generator imageGenerator;
  
    public Game(RuleSet _rules, Board _initialBoard) {
	random = null;
	rules = _rules;
	initialBoard = _initialBoard;
	randomObjCnt = 0;
	allShapes=null;
	allColors=null;
	imageGenerator=null;
    }
    public Game(RandomRG _random, RuleSet _rules, int _randomObjCnt, Piece.Shape[] _allShapes,    Piece.Color[] _allColors) {
	random = _random;
	rules = _rules;
	randomObjCnt = _randomObjCnt;
	allShapes =_allShapes;
	allColors =_allColors;
	imageGenerator=null;
    }
    /** A game with shape-and-color objects used as game pieces */
    public Game(RandomRG _random, RuleSet _rules, int _randomObjCnt, int _nShapes, int _nColors,
		Piece.Shape[] _allShapes,    Piece.Color[] _allColors ) {
	this(_random, _rules,  _randomObjCnt, _allShapes,_allColors);
	nShapes =  _nShapes;
	nColors  = _nColors;
    }
    /** A game with image-and-properties-based objects used as game pieces */
    public Game(RandomRG _random, RuleSet _rules, int _randomObjCnt,
		ImageObject.Generator _imageGenerator
		//String[] _allImages
		) {
	random = _random;
	rules = _rules;
	randomObjCnt = _randomObjCnt;
	allShapes = null;
	allColors = null;
	imageGenerator = _imageGenerator;
	//	allImages = _allImages;
    }

    
}
