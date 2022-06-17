package edu.wisc.game.sql;

import java.io.*;
import java.util.*;

/** An auxiliary class used by the Captive Game Server for the optional 
    logging of MLC run results. */
public class MlcLog {
    final File f;
    final boolean append;
    public String nickname=null, rule_name=null;
    public int run= -1;
    public MlcLog(File _f, boolean _append) {
	f = _f;
	append = _append;
    }
	
    PrintWriter w;
    public void open() throws IOException {
	long len = (append && f.exists())? f.length(): 0;
	w = new PrintWriter(new FileWriter(f, append));
	if (len==0)  writeHeader();	    
    }
    public void close() {
	w.close();
    }

    private void writeHeader() {
	w.println("nickname,rule_name,trial_id,board_id,number_of_pieces,number_of_moves,move_acc,if_clear");
    }
    public void logEpisode(Episode e, int boardNo) {
	Vector<String> v = new Vector<>();
	v.add(nickname);
	v.add(rule_name);
	v.add("" + run);
	v.add("" + boardNo);
	v.add("" + e.getNPiecesStart());
	v.add("" + e.attemptCnt);
	double moveAcc=(e.attemptCnt==0)? 0: e.doneMoveCnt/(double)e.attemptCnt;
	v.add("" + moveAcc);
	boolean cleared = (e.doneMoveCnt==e.getNPiecesStart());
	v.add( cleared? "1":"0");
	w.println(String.join(",", v));
    }

}
    
