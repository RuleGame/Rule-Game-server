package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.rest.Files;
import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.ParaSet;
import edu.wisc.game.sql.Episode.CODE;

/** This class generates random games (with the same rule set and
    randomly created initial boards) based on the provided parameter
    range specifications */
abstract public class GameGenerator {

    final RandomRG random;// = new RandomRG();
    
    //    final int[] nPiecesRange, nShapesRange, nColorsRange;

    /** The rule set */
    final RuleSet rules;
    public RuleSet getRules() {return rules;}

    /** How many boards has it produced so far? */
    int produceCnt=0;

    /** Only used by subclasses */
    GameGenerator(RandomRG _random, RuleSet _rules) {
	rules = _rules;
	random = _random;
    }

    /** Generates the next game to play */
    abstract public Game nextGame();

    
    /** This must be called by every "concrete" game generator after it has created a Game object. It attaches any conditions to the Game object, and adjusts the "produced games" counter.

	@param g The Game object that has just been created
     */

    void next(Game game) {
	game.setConditions(testing, condRules);       
	produceCnt++;
    }

    /** Advances the counter. This can be used to resume an interrupted series,
	so that e.g. a predefined board from the correct position in the list
	would be used next.
	@param n advance the counter as if n games have been created
     */
    public void advance(int n) {
	if (n<0) throw new IllegalArgumentException("Negative n");
	for(int k=0; k<n; k++) nextGame();
    }

    /** Creates a GameGenerator based on a parameter set. Depending on
	which parameters are set, a PredefinedBoardGameGenerator or a
	RandomGameGenerator may be returned.
	@param para The parameter set for which we will create a suitable
	GameGenerator
     */
    public static GameGenerator mkGameGenerator(RandomRG _random, ParaSet para) throws IOException, RuleParseException, IllegalInputException, ReflectiveOperationException {

	String ruleSetName = para.getRuleSetName();

	String initial_boards = (String)para.get("initial_boards");
	String initial_boards_order = (String)para.get("initial_boards_order");

	GameGenerator gg;

	//System.out.println("mkGameGenerator: para=" +para);

	
	if (initial_boards!=null && initial_boards.length()>0) {
	    if (initial_boards_order==null ||initial_boards_order.length()==0) throw new  IllegalInputException("Parameter sets specifies initial_boards, but not initial_boards_order");
	    File boardDir = Files.inputBoardSubdir(initial_boards);
	    gg = new PredefinedBoardGameGenerator(_random, ruleSetName,  boardDir, initial_boards_order);
	} else if  (para.imageGenerator!=null) {
	    
	    int[] nPiecesRange = {para.getInt("min_objects"),
				  para.getInt("max_objects")};
	    gg =new RandomImageGameGenerator(_random, ruleSetName, nPiecesRange, para.imageGenerator);
	} else {

	    int[] nPiecesRange = {para.getInt("min_objects"),
				  para.getInt("max_objects")},
		nShapesRange = {para.getInt("min_shapes"),
				para.getInt("max_shapes")},
		nColorsRange = {para.getInt("min_colors"),
				para.getInt("max_colors")};

	    gg =new RandomGameGenerator(_random, ruleSetName, nPiecesRange, nShapesRange,
					nColorsRange, para.shapes, para.colors);
	}


	return gg;
    }

    RuleSet condRules=null;
    boolean testing = false;
    public boolean getTesting() { return testing; }
    public void setTesting(boolean _testing) { testing = _testing; }
   
    /** For the training/testing restrictions on boards, as introduced in GS 6.010. See email discusion with Paul on 2023-03-08, and captive.html#cond
     */
    public void setConditions(boolean _testing, RuleSet _condRules) {
	testing = _testing;
	condRules = _condRules;
    }

    
}
