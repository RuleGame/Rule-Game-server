package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;

/* Writes a chat message to a file */
public class ChatWriteReport extends FileWriteReport {

    ChatWriteReport(File f, long _byteCnt) {
	super(f,  _byteCnt);
    }

    static public String findPartnerPlayerId(String moverPlayerId) {	
	try {
	    PlayerInfo moverX = PlayerResponse.findPlayerInfo(null,moverPlayerId);
	    return moverX.getPartnerPlayerId();
	} catch(Exception ex) {
	    Logging.error("ChatWriteReport.findPartnerId("+ moverPlayerId+"): " + ex);
	    return  null;
	}
    }

    
    /** Records a chat line (a text message sent by a 2PG player to his partner)
	@param moverPlayerId The player who sent the message
	@param text The text of the message, entered by the player, to record

    */
    static public FileWriteReport writeChat(String moverPlayerId,
				     String text)  {
	
	try {
	    if (text==null) {
		text = "";
		//return new FileWriteReport(true,"No chat text supplied");
	    }

	    PlayerInfo moverX = PlayerResponse.findPlayerInfo(null,moverPlayerId);
	    // The sender's role in the pair
	    int mover = moverX.getPairState();
	    // The player who owns the chat file
	    PlayerInfo x = (mover==Pairing.State.ZERO)? moverX : moverX.xgetPartner();
	    
	    EpisodeInfo epi = x.mostRecentEpisode();
	    if (epi==null)  {
		return new FileWriteReport(true,"No episodes exist for player " + moverPlayerId);
	    }

	    String pid = x.getPlayerId();
	    File f= Files.chatFile(pid);
	    
	    epi.saveChatToFile(f, mover, text);
	    GuessWriteReport g = new GuessWriteReport(f, f.length());
	    return g;
	} catch(Exception ex) {
	    Logging.error("Error in ChatWriteReport(): " + ex);
	    return  new	    FileWriteReport(true,ex.getMessage());
	}
    }

    //    private static final String file_writing_lock = "lock";
    

    
}
