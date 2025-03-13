package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
//import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;

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

    /** For games with a random board generator and S+C game pieces, the set of shapes from
	which shapes of game pieces are pulled. Null otherwise (i.e. for IPB games, or
	games with predefined boards). */
    final Piece.Shape[] allShapes;
    final Piece.Color[] allColors;

    /** This is null in games with the traditional shape+color game
      pieces (which rely on allShapes and allColors). In games with
      IPB game pieces or with composite game pieces (where allShapes
      and allColors are null) it will be an object from which random
      object names can be pulled. This is based on the "images" column
      of the ParaSet */ 
    final ImageObject.Generator imageGenerator;

    /** For Captive server, to be printed via JSON Reflect */
    /*    public Map<String, Vector<Object>> getExtraFields() {
	if (imageGenerator!=null) return imageGenerator.getAllFeatures();
	HashMap<String, Vector<Object>> m=new HashMap();
	m.put("shape", listNames(allShapes));
	m.put("color", listNames(allColors));
	return m;
	}*/

    private static <T> Vector<Object> listNames(T[] a) {
	Vector<Object> v=new Vector();
	for(T x: a) v.add(x.toString());
	return v;
    }
    
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

    private RuleSet condRules=null;
    private boolean testing = false;
    private boolean hasPositionMask = false;
    
    /** For the training/testing restrictions on boards, as introduced in GS 6.010. See email discusion with Paul on 2023-03-08, and captive.html#cond
     */
    void setConditions(boolean _testing, RuleSet _condRules, boolean _hasPositionMask) {
	testing = _testing;
	condRules = _condRules;
	hasPositionMask = _hasPositionMask;
    }

    /** Produces the board for this game. If additional train/test conditions
	exist, tries to satisfy them by repeated tries.
	<p>
	FIXME: if an additional condition is difficult to satisfy (e.g.
	less than 1 out of 1000 random boards do it), the method may fail
	to find a satisfactory board, even though some such boards exist.
     
     */
    Board giveBoard() {

	final int M = 1000;

	Board b;
	for(int tryCnt = 0;  tryCnt<M; tryCnt++) {
	    b = initialBoard;
	    if (b==null) {
		if (imageGenerator!=null) {
		    b = new Board(random,  randomObjCnt,  imageGenerator);
		} else { 
		    b = new Board(random,  randomObjCnt, nShapes, nColors, allShapes, allColors);
		}
	    }

	    if (condRules == null ||
		BoardConditionsChecker.boardIsAcceptable(b,condRules,testing)) {
		return b;
	    }
	    	    
	}

	String msg = "Cannot create a board satisfying conditions, even after "+M+" attempts. condRules from file " + condRules.getFile() +", testing=" + testing;
	System.err.println(msg);
	throw new IllegalArgumentException(msg);
	    
    }

    
    /** Checks if the board satisfies any training/testing conditions that
	may be in effect */
    boolean boardIsAcceptable(Board board) {
	if (condRules==null) return true;
	return true;
    }
    

    
    
}
