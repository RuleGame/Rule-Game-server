package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.persistence.*;

import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.sql.*;
import edu.wisc.game.util.*;
import edu.wisc.game.formatter.*;


/** Used by LoginServlet and the Android app for registering new users and logging in */
public class UserResponse extends ResponseBase {

    private User u=null;
    public User getUser() { return u; }
    //@XmlElement
    //public void setUser(User _u) { u = _u; }

    boolean newlyRegistered;
    public boolean getNewlyRegistered() { return newlyRegistered; }
    @XmlElement
    void setNewlyRegistered(boolean _newlyRegistered) { newlyRegistered = _newlyRegistered; }


    static private User findUser(EntityManager em, String queryText, String val) {
	Query q = em.createQuery(queryText);
	q.setParameter("c",val);
	List<User> res = (List<User>)q.getResultList();
	if (res.size() == 0) return null;
	return res.iterator().next();
    }

    static final DateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");    

    /** Creates a more or less unique ID that can be used as 
	a "secret ID" for a User object */
    private static String buildCodeId(String prefix, Date now) {
	return prefix + "-" + sdf.format(now) + "-" + Episode.randomWord(6);
    }


	
    public UserResponse(String email, String nickname, boolean anon) {
	email = regularize(email);
	nickname = regularize(nickname);

	
	EntityManager em = null;	 
	boolean isAnon = false;
	    
	try {
	    if (nickname==null && email==null) {
		if (anon) {
		    isAnon =true;
		} else {
		    hasError("Please go back to the login page and provide either an email address or a nickname, or check the 'anonymous' box.");
		    return;
		}
	    } else if (email!=null) {
		boolean mustClose=(em==null);
		
		
		if (em==null) em=Main.getNewEM();
		u = findUser(em, "select u from User u where u.email=:c", email);
		if (u != null) {			 
		    if (nickname!=null) {
			if (u.getNickname()==null) {
			    // Add nickname to the existing entry.
			    // Hope that the change will be saved to the database once we close the connection
			    u.setNickname(nickname);
			} if (!u.getNickname().equals(nickname)) {
			    hasError("Cannot use email '" + email + "' with nickname '" + nickname +"', because this email address has been previously used with a different nickname");
			    return;
			} else {
			    // all fine, full match	 
			}
		    } else {
			if (u.getNickname()!=null) {
			    hasError( "This email was originally registered with a nickname. Please go back to the login page and provide the same nickname along with the email address. Alternatively, you can choose to check the 'anonymous' box instead.");
			    return;			     
			} else {
			    // full match (email only, no nicknames ever)
			}			     
		    }
		} else {
		    u = null; // must create and persist a new record
		}
	    } else { //no email; lookup by nickname
		if (em==null) em=Main.getNewEM();
		u = findUser(em, "select u from User u where u.nickname=:c", nickname);
		if (u != null) {
		    if (u.getEmail()!=null) {
			hasError("This nickname ('" + nickname +"') cannot be used without an email address, because it has been previously used with an email address. To resume using it, go back and supply the email address as well.");
			return;
		    }
		}
	    }
		
	    if (u==null) {
		u = new User();
		if (email!=null) u.setEmail(email);
		if (nickname!=null) u.setNickname(nickname);
		Date now = new Date();
		u.setIdCode( buildCodeId(isAnon? "anon" : "user", now));
		u.setDate( now );
		Logging.info("Created new user: " + u);
		// SAVE THE new USER info into the SQL database
		if (em==null) em=Main.getNewEM();
		em.getTransaction().begin();
		em.persist(u);
		em.flush(); // to get the new ID in    
		em.getTransaction().commit();
		setNewlyRegistered(true);
	    } else {
		setNewlyRegistered(false);
	    }
	    
	} catch(Exception e) {
	    System.err.println(e);
	    e.printStackTrace(System.err);
	    String msg  = "Error in LoginServlet: " + e; //e.getMessage());
	    hasError( msg);
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}
	
    }
}

