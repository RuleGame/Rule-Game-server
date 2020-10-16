package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.rest.ParaSet;


/** This class generates games based on a set of predefined initial boards */
public class PredefinedBoardGameGenerator extends GameGenerator {
    /** The rule set */
    final RuleSet rules;
    /** The list of boards, either in the order they will be served, or (for the RANDOM mode) in any order */
    final Board[] boards;
    /** For RANDOM mode, the list of indexes of boards yet-to-be-presented, in the order they will be presented */
    private Vector<Integer> remainingRandom=new  Vector<Integer>();
     
    enum Mode { RANDOM, ALPHA, LIST};

    final Mode mode;

    private static final String ext=".json";	    

    public Game nextGame() {
	
	int pos=0;
	if (mode==Mode.ALPHA || mode==Mode.LIST) {
	    pos = produceCnt % boards.length;
	} else if (mode==Mode.RANDOM) {
	    if (remainingRandom.size()==0) {
		int n = boards.length;
		remainingRandom = Board.random.randomSubsetPermuted(n,n);
	    }
	    pos = remainingRandom.remove(0);
	} 
	    
	Board b = boards[pos];
	next();
	return new Game(rules, b);
    }
    
    PredefinedBoardGameGenerator(String ruleSetName, File boardDir, String modeString) throws  IOException, IllegalInputException, RuleParseException, ReflectiveOperationException {
	super();
	rules = AllRuleSets.obtain(ruleSetName); 
	File orderFile = null;
	Mode _mode;
	try {
	    _mode = Enum.valueOf( Mode.class,  modeString.toUpperCase());
	} catch( IllegalArgumentException ex) {
	    _mode = Mode.LIST;
	    if (!modeString.toLowerCase().endsWith(".csv")) throw new IllegalArgumentException("The initial_boards_order='" + modeString+"', which is neither RANDOM nor ALPHA nor a CSV file name");
	    orderFile = modeString.startsWith("/")? new File(modeString) :
		new File(boardDir, modeString);
	}
	mode = _mode;
	
	Vector<Board> vb= new Vector<Board>();

	Vector<File> fv = new Vector<>();
	System.out.println("PredefinedBoardGameGenerator, dir=" + boardDir+", mode=" + mode);
	if (mode==Mode.RANDOM || mode==Mode.ALPHA) {
	    File[] ff =  boardDir.listFiles();	    
	    if (mode==Mode.ALPHA) Arrays.sort(ff);
	    System.out.println("ff=" + Util.joinNonBlank(", ", ff));
	    for(File cf: ff) {
		if (!cf.isFile()) continue;
		String fname = cf.getName();
		if (!fname.endsWith(ext)) continue;
		fv.add(cf);
	    }    
	} else if (mode==Mode.LIST) {
	    if (orderFile==null) throw new IllegalArgumentException("Board order file nor specified");
	    if (!orderFile.canRead())  throw new IllegalArgumentException("Cannor read board order file: " + orderFile);
	    CsvData csv = new CsvData(orderFile);
	    for(CsvData.LineEntry e: csv.entries) {
		String s = e.getKey();
		if (!s.endsWith(ext)) s += ext;
		File f = new File(boardDir, s);
		if (!f.canRead()) throw new IllegalArgumentException("The order file mentions board file that cannot be found or read: " + f);
		fv.add(f);
	    }
	    
	}
	boards = new Board[fv.size()];
	int j=0;
	for(File bf: fv) boards[j++] = Board.readBoard(bf);	    	
    }
    

}
