package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.servlet.*;
import javax.servlet.http.*;
//import javax.servlet.jsp.PageContext;

import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;

/** A single instance of this class is associated with a particular
    session of the Rule Game web app.
 */
public class SessionData {

    /** Back-pointer to the web server's object associated with this session. */
    final private HttpSession session;

    // static private EntityManagerFactory factory;// = null;

      /** Used for debugging, to figure who has hogged the static-synchronized
	methods */
    private static String whoSync = null;
    static String getWhoSync() { return whoSync; }

    private static void addSync(String msg) {
	Logging.info(msg);
	whoSync += "; " + msg;	
    }



    public String toString() {
	return "(SD)";
    }

 
    /** Creates a SessionData object for a new Session. It also makes
	a record of the session in the SQL database... 

	FIXME: Occasionally, a "synchronization block" appears to
	happen here, which may be triggered by Transaction.commit()
	failure to return (??)

	@param _session The underlying web session object (in a web
	 app), or null (in a command line app)
     */
    private SessionData( HttpSession _session) {//throws WebException, IOException {

	long tid=Thread.currentThread().getId();

	whoSync = "SessionData.in("+tid+")";
	try {
	    session = _session;
	    addSync("SD.SD.A");
	    //initFactory( session );

	} finally {
	    whoSync = "SessionData.out";
	}
    }


    private final static String ATTRIBUTE_SD = "sd";

    /** Looks up the SessionData object already associated with the
	current session, or creates a new one. This is done atomically,
        synchronized on the session object.
	
	<p>This can also be used in command-like app with simulated
	session, when this method will simply create a new dummy SessionData
	object.

	@param request The current HTTP request (or null in a command-like app
	with simulated session).
     */
    static public synchronized SessionData getSessionData(HttpServletRequest request) 
					   //throws WebException, IOException
    {

	whoSync = "getSessionData.in";
	try {

	    addSync("SD.gSD.A, request is " + (request==null? "null" : "not null"));
	    if (request==null) return new SessionData(null);

	    HttpSession session = request.getSession();
	    SessionData sd  = null;
	    addSync("SD.gSD.B");
	    synchronized(session) {
		addSync("SD.gSD.C");
		sd  = ( SessionData) session.getAttribute(ATTRIBUTE_SD);

		addSync("SD.gSD.D, old sd= " + sd);
		if (sd == null) {
		    sd = new SessionData(session);
		    session.setAttribute(ATTRIBUTE_SD, sd);
		}
	    }
	    return sd;
	} finally {
	    whoSync = "getSessionData.out";
	}

    }

 
  

    /** Simply invalidates the HttpSession. This means that the associated 
	SessionData object will be discarded as well. 
     */
    static synchronized void discardSessionData(HttpServletRequest request) 
	throws WebException, IOException {
	whoSync = "discardSD.in";
	try {
	    HttpSession session = request.getSession();
	    session.invalidate();
	} finally {
	    whoSync = "discardSD.out";
	}
    }

    ServletContext getServletContext() {
	return session.getServletContext(); 
    }


    //final static NumberFormat pcfmt = new DecimalFormat("#0.##");
    //final static NumberFormat ratefmt = new DecimalFormat("0.###");


 
  
    //    static final String defaultUser = "anonymous";
    //static final String defaultUser = null;
    

    /** The int user id, converted to string */
    private String storedUserName = null;
    /** Something to print, which can be email or nickname or "anonymous"  */
    private String storedDisplayName = null;

    /** Unlike getRemoteUser(), this method does not recheck the
	extended  session cookie. It is safe to use if we know
	that  getRemoteUser() has been recently called.
     */
    public String getStoredUserName() {
    	return storedUserName;
    }

    public String getStoredDisplayName() {
	return  storedDisplayName;
    }

    
    /** Gets the user name associated with this session. Originally,
	this method relied on Tomcat keeping track of this stuff, but
	as Tomcat is not always deployed quite right, we don't do it
	this way anymore. Instead, an instance variable
	(storedUserName) in this SessionData object is used to keep
	track of the user name within the current Tomcat
	session. Between Tomcat sessions (e.g., after server restart),
	the ExtendedSessionManagement module is used to find the user
	name based on a cookie sent by the browser.

     */
    String getRemoteUser(HttpServletRequest request, StringBuffer msgBuffer) {
	msgBuffer.setLength(0);
	String u;
	// first, check this server session
	u = storedUserName;
	String msg = "gRemoteUser: stored="+u;
	if (u==null) {
	    // maybe there is an extended session?
	    Cookie cookie =  ExtendedSessionManagement.findCookie(request);
	    if (cookie!=null) {
		msg += "; cookie.val=" +cookie.getValue();
		EntityManager em = Main.getNewEM();
		try {
		    User user=  ExtendedSessionManagement.getValidEsUser( em, cookie);
		    if (user!=null) {
			storeUserInfo(user);
			u = storedUserName;
			msg += "; user="+user.getId();
		    } else {
			msg += "; no user";
		    }
		} finally {
		    try { em.close();} catch(Exception ex) {}
		}
	    } else {
		msg += "; no cookie";
	    }
	}
	msgBuffer.append(" ["+msg+"]");
	return  (u!=null)? u : 	null;
    }

    

    /** Returns the user object for the specified   user.*/
    /*
    static User getUserEntry(EntityManager em, String user) {
	return User.findByName(em, user);
    }
    */

    /** Saves the user name (received from the [validated] login form, 
	or recovered via a persistent cookie) into the session's memory.
     */
    public void storeUserInfo(User user) {


	String u = "" + user.getId();
	storedUserName = u;	
	String nickname = user.getNickname();
	String email = user.getEmail();
	if (nickname != null) 		storedDisplayName  = nickname;
	else if (email!=null)  	storedDisplayName  = email;
	else 	storedDisplayName  = "An anonymous session-based user";	
	
    }

 
  
    /** Is the current user authorized to access this url? */
    /*
    boolean isAuthorized(HttpServletRequest request) {
    	return isAuthorized(request, getRemoteUser(request));
    }
    */
    
    /** Is the specified user authorized to access the url requested
	in the given request?
    */
    /*
    boolean isAuthorized(HttpServletRequest request, String user) {
	String sp = request.getServletPath();
	String qs = request.getQueryString();
	//	Logging.info("isAuthorized("+user+", " + sp + ")?");
	Role.Name[] ar = authorizedRoles(sp,qs);
	if (ar==null) return true; // no restrictions
	if (user==null) return false; // no user 
	EntityManager em = getEM();
	User u = User.findByName(em, user);
	em.close();
	boolean b = u!=null && u.hasAnyRole(ar);
	//	Logging.info("isAuthorized("+user+", " + sp + ")=" + b);
	return b;
    }
    */
    
  
}
