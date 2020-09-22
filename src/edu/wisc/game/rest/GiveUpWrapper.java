package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;
//import javax.persistence.*;


import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;


public class GiveUpWrapper extends ResponseBase {
  
    GiveUpWrapper(String pid) {
	this(pid, -1);
    }
    
    GiveUpWrapper(String pid, int seriesNo) {

	Logging.info("GiveUpWrapper(pid="+ pid+", seriesNo=" + seriesNo+")");
		
	try {
	    PlayerInfo x = PlayerResponse.findPlayerInfo(pid);
	    if (x==null) {
		setError(true);
		setErrmsg("Player not found: " + pid);
		return;
	    }
	    if (seriesNo<0) seriesNo=x.getCurrentSeriesNo();
	    x.giveUp(seriesNo);	   
	    setError( false);
	    setErrmsg("Gave up series "+seriesNo);
	} catch(Exception ex) {
	    setError(true);
	    setErrmsg(ex.getMessage());
	    System.err.print(ex);
	    ex.printStackTrace(System.err);
	}	      	
    }    
}
