package edu.wisc.game.sql;

import java.io.*;
import java.util.*;

/** An auxiliary class used by the Captive Game Server for the optional 
    logging of MLC run results. The log files so produced can be later
    imported by MclUploadService.
*/
public class MlcLog {

    /** Supported log file formats  */
    public enum LogFormat {
	Long, Compact, Run;

	public String[] header() {
	    if (this==LogFormat.Long) {
		return new String[] {"#nickname,rule_name,trial_id,board_id,number_of_pieces,number_of_moves,move_acc,if_clear"};
	    } else if (this==LogFormat.Compact) {
		return new String[] {
		    "#.nickname,rule_name,trial_id",
		    "#number_of_moves,number_of_errors,if_clear"};
	    } else throw new IllegalArgumentException("Unsupported format: " + this);
	}


    };


    final File f;
    final boolean append;
    public String nickname=null, rule_name=null;
    public LogFormat format=LogFormat.Long;

    public int run= -1;
    public MlcLog(File _f, boolean _append) {
	f = _f;
	append = _append;
    }
	
    PrintWriter w;
    /** Opens the file for writing (or appending) and writes the header line
	if necessary. */
    public void open() throws IOException {
	long len = (append && f.exists())? f.length(): 0;
	w = new PrintWriter(new FileWriter(f, append));
	if (len==0)  writeHeader();	    
    }
    public void close() {
	w.close();
    }

    private void writeHeader() {
	for(String s: format.header()) {
	    w.println(s);
	} 

    }

    //    private Episode lastEpisode=null;
    private int lastBoardNo = -1;

    
    /** Computes the ratios etc and writes the entry for a specified episode 
	@param boardNo Sequential number (0-based) of the episode in the series
	(run)
     */
    public void logEpisode(Episode e, int boardNo) {
	Vector<String> v = new Vector<>();

	boolean cleared = (e.doneMoveCnt==e.getNPiecesStart());

	if (format==LogFormat.Long) {
	    v.add(nickname);
	    v.add(rule_name);
	    v.add("" + run);
	    v.add("" + boardNo);
	    v.add("" + e.getNPiecesStart());
	    v.add("" + e.attemptCnt);
	    double moveAcc=(e.attemptCnt==0)? 0: e.doneMoveCnt/(double)e.attemptCnt;
	    v.add("" + moveAcc);
	    v.add( cleared? "1":"0");
	    w.println(String.join(",", v));
	} else if (format==LogFormat.Compact) {
	    if (lastBoardNo < 0) {
		//w.println("#.nickname,rule_name,trial_id");
		v.add("." + nickname);
		v.add(rule_name);
		v.add("" + run);
		w.println(String.join(",", v));
		v.clear();
		lastBoardNo = boardNo;
	    }

	    int successCnt = e.successfulPickCnt + e.doneMoveCnt;
	    int errorCnt = e.attemptCnt - successCnt;
	    
	    //   "#number_of_moves,number_of_errors,if_clear");
	    v.add("" + e.attemptCnt);
	    v.add("" + errorCnt);
	    v.add( cleared? "1":"0");
	    w.println(String.join(",", v));

	} else throw new IllegalArgumentException("Unsupported format: " + format);
    }

}
    
