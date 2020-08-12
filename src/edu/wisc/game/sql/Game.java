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
    public int randomObjCnt;
    public Game(RuleSet _rules, int _randomObjCnt) {
	rules = _rules;
	randomObjCnt = _randomObjCnt;
    }
}
