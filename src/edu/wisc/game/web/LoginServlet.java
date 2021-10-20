package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import edu.wisc.game.sql.*;
import edu.wisc.game.util.*;
import edu.wisc.game.formatter.*;


public class LoginServlet extends HttpServlet {

    private String getParameter(HttpServletRequest request, String name) {
	String x =  request.getParameter(name);
	return x==null? x : x.trim();
    }

    private String getParameter1(HttpServletRequest request, String name) {
	String x =  getParameter(request,name);
	return x==null || x.equals("") ? null: x;
    }

    private boolean getAnon(HttpServletRequest request) {	
	String x =  getParameter1(request,"anon");
	return x!=null && x.toLowerCase().equals("true");
    }

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

    static final String START_PAGE =	 "/launch/index.jsp";
    
    public void service(HttpServletRequest request,HttpServletResponse response) {
	 String sp =  getParameter1(request, "sp");
	 String qs =  request.getParameter("qs");
	 if (qs!=null && qs.equals("null")) qs=null;
	 String nickname =  getParameter1(request,"nickname");
	 String email =  getParameter1(request,"email");
	 EntityManager em = null;
	 
	 boolean isAnon = false;
	 User u= null;

	 try {
	     if (nickname==null && email==null) {
		 if (getAnon(request)) {
		     isAnon =true;
		 } else {
		     response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please go back to the login page and provide either an email address or a nickname, or check the 'anonymous' box.");
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
			     response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Cannot use email '" + email + "' with nickname '" + nickname +"', because this email address has been previously used with a different nickname");
			     return;
			 } else {
			     // all fine, full match	 
			 }
		     } else {
			 if (u.getNickname()!=null) {
			     response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "This email was originally registered with a nickname. Please go back to the login page and provide the same nickname along with the email address. Alternatively, you can choose to check the 'anonymous' box instead.");
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
			 response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "This nickname ('" + nickname +"') cannot be used without an email address, because it has been previously used with an email address. To resume using it, go back and supply the email address as well.");
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

	     }

	     SessionData sd = SessionData.getSessionData(request);
	     sd.storeUserInfo(u);

	     String redirect = START_PAGE;
	     if (sp!=null && !sp.equals("null")) {
		 redirect = sp;
		 if (qs != null) {
		     redirect += "?" + qs;
		 }
	     }

	     
	     String cp = request.getContextPath(); 
	     String eurl = response.encodeRedirectURL(cp + "/"+redirect);

	     Cookie cookie =  ExtendedSessionManagement.makeCookie(u);     
	     if (cookie!=null) {
		 Logging.info("LoginServlet: [Sending cookie="+cookie.getValue()+"]");
		 response.addCookie(cookie);
	     }
	     response.sendRedirect(eurl);

	     
	} catch(Exception e) {
	    System.err.println(e);
	    e.printStackTrace(System.err);
	    String msg  = "Error in LoginServlet: " + e; //e.getMessage());
	    try {
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
	    } catch(IOException e1) {
	    }

	} finally {
	   if (em!=null) try {
		   Logging.info("Closing EM, hoping to persist new user: " + u);		   
		   em.close();
	       } catch(Exception ex) {}
	}
	 
     }
}

