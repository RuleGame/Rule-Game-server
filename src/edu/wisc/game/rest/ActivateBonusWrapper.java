package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;
import javax.persistence.*;


import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;


public class ActivateBonusWrapper extends ResponseBase {
      
    EntityManager em = Main.getEM();
  
    ActivateBonusWrapper(String pid) {
	try {
	    PlayerInfo x = PlayerResponse.findPlayerInfo(em, pid);
	    if (x==null) {
		setError(true);
		setErrmsg("Player not found: " + pid);
		return;
	    }
	    x.activateBonus();	   
	    setError( false);
	    setErrmsg("Bonus activated successfully");
	} catch(Exception ex) {
	    setError(true);
	    setErrmsg(ex.getMessage());
	    System.err.print(ex);
	    ex.printStackTrace(System.err);
	} finally {
	    //em.close();
	}
	      	
    }
    
}
