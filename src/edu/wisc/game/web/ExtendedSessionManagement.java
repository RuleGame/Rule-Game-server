package edu.wisc.game.web;

import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import javax.servlet.*;
import javax.servlet.http.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.User;

//import org.apache.catalina.realm.RealmBase;

/** This class is used to manage extended sessions, which survive over the
    web server restart. This feature is used when the user checks the
    "remember me" button on login. The information is stored
    persistently in a User structure in the database,

    <p>
    FIXME: Since a new cookie (with a new single-use password) is generated 
    every time a user tries to log in, and only one such single-use password 
    is stored, a user cannot be logged in from more than one user agent at
    once. 
 */
public class ExtendedSessionManagement {

    /** A random number generator initialized at startup */
    static private final Random random= new Random(System.currentTimeMillis());
    public final static String COOKIE_NAME = "MyArxivExtendedSession";

    /** max lifetime of an extended session, in hours */
    static public final int maxDays = 365;   
    static private final int maxHours = maxDays*24;   

    /** Creates a cookie  for an "extended session",
	and adds the pertinent information to the user record. This method
	should be called inside a transaction, and followed by a "persist"
	call.

	@param u The user record. Information about the new session will be added to this record by this method.
	@return The new cookie, to be sent to the user agent
    */
    static Cookie makeCookie(User u) {
	final int maxSec = 3600 * maxHours;

	String uid=""+u.getId();		
	String uCode = u.getIdCode();
	try {
	    uid = URLEncoder.encode(uid, "UTF-8");
	    uCode = URLEncoder.encode(uCode, "UTF-8");
	} catch(java.io.UnsupportedEncodingException ex) {}
	//	String x = org.apache.catalina.realm.RealmBase.Digest(tmpPass, "MD5", "utf-8" );

	Date now = new Date();	
	Date expiration = new Date( now.getTime() + 1000L * (long)maxSec ); // msec

	String val = uid + ":" + uCode;
	Cookie cookie=new Cookie(COOKIE_NAME, val);
	cookie.setMaxAge( maxSec); // max age in seconds
	cookie.setPath("/");

	Logging.info("Created cookie for user " + uid+ " ["+val+"]");

	return cookie; 
    }

    /** See 
	https://stackoverflow.com/questions/5285940/correct-way-to-delete-cookies-server-side
    */
    static Cookie makeLogoutCookie() {
	Cookie cookie=new Cookie(COOKIE_NAME, "");
	cookie.setMaxAge( 0); // max age in seconds
	cookie.setPath("/");
	return cookie; 	
    }
   

    /** Should be called upon logout, followed by a persist()
        call. This terminates all extended session for this user (on
        all machines). */
    /*
    static void invalidateEs(User u) {
	Set<ExtendedSession> s = u.getEs();
	if (s==null) {
	    s=new HashSet<ExtendedSession>();
	} else {
	    s.clear();
	}
	u.setEs(s);
	//	u.setEsEnd(null);
	//      u.setEncEsPass("");
    }
    */

    /** Checks if the cookie identifies a still-valid extended session, 
	and if so, returns the pertinent User instance.
	@param cookie The cookie received by the server from the user agent
	@return A User object, or null if there is no valid extended session. */
    static User getValidEsUser( EntityManager em, Cookie cookie) {
	if (cookie==null) return null;
	String val = cookie.getValue();
	if (val==null) return null;
	String[] z = val.split(":");
	if (z.length!=2) return null;

	//	Logging.info("received cookie: ["+val+"]");

	String uid = z[0], uCode=z[1];
	try {
	    uid=URLDecoder.decode(uid, "UTF-8");
	    uCode=URLDecoder.decode(uCode , "UTF-8");
	} catch(java.io.UnsupportedEncodingException ex) {}

	
	String queryText =  "select u from User u where u.id=:id and u.idCode=:code";
	Query q = em.createQuery(queryText);	
	q.setParameter("id", new Long(uid));
	q.setParameter("code",uCode);
	List<User> res = (List<User>)q.getResultList();
	if (res.size() == 0) return null;
	return res.iterator().next();

   }

    static Cookie findCookie( HttpServletRequest request) {
	Cookie[] cs = request.getCookies();
	if (cs==null) return null;
	for(Cookie c: cs) {
	    if (c.getName().equals(COOKIE_NAME)) {
		return c;
	    }
	}
	return null;
    }



}
