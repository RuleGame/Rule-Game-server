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
    
    public Game(RuleSet _rules, Board _initialBoard) {
	rules = _rules;
	initialBoard = _initialBoard;
	randomObjCnt = 0;
    }
    public Game(RuleSet _rules, int _randomObjCnt) {
	rules = _rules;
	randomObjCnt = _randomObjCnt;
    }
    public Game(RuleSet _rules, int _randomObjCnt, int _nShapes, int _nColors) {
	this(_rules,  _randomObjCnt);
	nShapes =  _nShapes;
	nColors  = _nColors;	
    }
}
