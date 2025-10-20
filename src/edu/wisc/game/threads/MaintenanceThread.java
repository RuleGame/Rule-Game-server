package edu.wisc.game.threads;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.pseudo.Pseudo;
import edu.wisc.game.rest.PlayerResponse;

/** This thread performs maintenance functions, such as marking 
    episodes as "abandoned"
 */
public class MaintenanceThread extends Thread {

    static MaintenanceThread oneMaintenanceThread = null;
    boolean pleaseStop = false;

    /** Creates a thread. You must call its start() method next.
	@param _runID Run id, which identifies this run (and its results)
	within the session.
     */
    MaintenanceThread(String name) {
	super(name);
    }

    /** This can be called any time someone wants to
	make sure the maintenance thread has been started */
    synchronized public static void init() {
	if ( oneMaintenanceThread != null) {
	    return;
	}

	final DateFormat sqlDf = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
	String name = "MaintenanceThread-" + sqlDf.format(new Date());
    	oneMaintenanceThread = new MaintenanceThread(name);
	oneMaintenanceThread.start();
    }


    /** This should be called when the application is undeployed,
	from the context listener */
    synchronized public static void destroy() {
	if ( oneMaintenanceThread == null) {
	    return;
	}
	oneMaintenanceThread.pleaseStop = true;
	Logging.info("Calling thread.interrupt()...");
	oneMaintenanceThread.interrupt();
    }

    
    int timeout2pg , timeout1pg;
  
    public void run()  {

	timeout2pg = MainConfig.getInt("TIMEOUT_2PG", 301);
	timeout1pg = MainConfig.getInt("TIMEOUT_1PG", 36001);

	Logging.info("Started MaintenanceThread " + getName() + " with timeout2pg="+timeout2pg+", timeout1pg="+timeout1pg);
	
	//EntityManager em=null;

	while(!pleaseStop) {

	    long sleepMsec = 15*1000;
	    try {
		Thread.sleep(sleepMsec); 
	    } catch (InterruptedException e) {
		if (pleaseStop) break;
		e.printStackTrace();
	    }

	    if (pleaseStop) break;

	    int pseudoCnt = Pseudo.checkTasks();
	    if (pseudoCnt>0) Logging.info("MaintenanceThread.run(): done " + pseudoCnt + " pseudo-AI moves");
	    if (pleaseStop) break;

	    
	    try {
		//Logging.info("MaintenanceThread.run() wakes up"); 
		Date now  = new Date();

		HashMap<String, PlayerInfo> allPlayers = PlayerResponse.getAllCachedPlayers();
		int cntAll1=0, cntTimeout1=0;
		int cntAll2=0, cntTimeout2=0;
		for(String playerId: allPlayers.keySet()) {
		    if (pleaseStop) break;
		    PlayerInfo p = allPlayers.get(playerId);
		    if (p.getCompletionCode()!=null || p.getCompletionMode()>0) continue;
		    if (p.is2PG() && !p.isBotGame()) {
			cntAll2++;
			if (p.getPartnerPlayerId() == null) continue; // not paired yet
			PlayerInfo y = p;
			//-- if it's a 2PG, all episodes are stored by player ZERO
			if (p.xgetPartner()!=null && p.getPairState()==Pairing.State.ONE) {
			    y = p.xgetPartner();
			}	    
			
			EpisodeInfo epi = y.mostRecentEpisode();

			// If the previous episode has finished, but nobody has pressed "NEXT" yet, we create a new episode right here
			// episodeInfo epi = y.episodeToDo();
			if (epi.getFinishCode()==Episode.FINISH_CODE.NO)  { // episode in progress
			    int whoMustPlay = epi.whoMustMakeNextMove();
			    if (whoMustPlay != p.getPairState()) {
				// this player is waiting for its partner
				continue;
			    } else {
				// test this one
			    }
			} else {
			    // there is no active episode at the moment anyway,
			    // so no one is waiting for your move. Both players
			    // have the NEXT button, and both can press it any time
			    continue;
			}
			if (p.getLastActivityTime().getTime() + timeout2pg *1000 < now.getTime()) {
			    cntTimeout2 ++;
			    Logging.info("MaintenanceThread: Detected a 2PG walk-away: " + playerId + ", lastActive=" + p.getLastActivityTime());
			    p.abandon();
			}
		    } else { // 1PG, or bot game
			cntAll1++;
			if (p.getCompletionCode()!=null || p.getCompletionMode()>0) continue;
			if (p.getLastActivityTime().getTime() + timeout1pg *1000 < now.getTime()) {
			    cntTimeout1 ++;
			    Logging.info("MaintenanceThread: Detected a 1PG walk-away: " + playerId + ", lastActive=" + p.getLastActivityTime());
			    p.abandon();
			}
		    }
			
		}

		if ( cntTimeout1 + cntTimeout2 > 0) {
		    Logging.info("MaintenanceThread: walk-away count: 1PG: " + cntTimeout1 + "/" +  cntAll1 +
				 "; 2PG: " + cntTimeout2 + "/" +  cntAll2); 
		}
		

	    } catch(Exception ex) {


		StringWriter sw = new StringWriter();		
		ex.printStackTrace(new PrintWriter(sw));

		
		//error = true;
		//errmsg = ex.getMessage();
		Logging.error("Exception for Maintenance thread " + getName() + ": " + ex);
		Logging.error("Trace: " + sw);
		ex.printStackTrace(System.out);
	    } finally {
	    } 
	}
	Logging.info("MaintenanceThread "+getName()+" finished");
    }

    public String toString() {
	return "[Maintenance thread " +// getId() + "; " + parent.toString() +
	    "]";
    }
 
}

