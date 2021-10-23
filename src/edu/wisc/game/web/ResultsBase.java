package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;

import edu.wisc.game.util.*;
//import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
//import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;



/** The base of all "results object" classes used in JSP pages */
public class ResultsBase {
    boolean error=false;
    String errmsg=null;
   /** The JSP page should always print this message. Most often
        it is just an empty string, anyway; but it may be used
        for debugging and status messages. */
    public String infomsg = "";
  
    public boolean getError() { return error; }
    public void setError(boolean _error) { error = _error; }
    
    public String getErrmsg() { return errmsg; }
    public void setErrmsg(String _errmsg) { errmsg = _errmsg; }

    /** Sets the error flag and the error message */
    protected void giveError(String msg) {
	setError(true);
	setErrmsg(msg);
    }

    final HttpServletRequest request;
    final String cp;
    SessionData sd;
    /** The numeric user id (converted to string) associated with the current session, or null */
    public String uid=null;
    public String displayName=null;

    public boolean loggedIn() { return uid!=null; }

    public String getDisplayText() {
	if (uid==null) return "You are not logged in";
	else return "User No. " + uid + " ("+displayName+")";
    }

    boolean atHome;
    final HTMLFmter fm = HTMLFmter.htmlFmter;
    
    
    /** This one is to be used in all pages where we want to identify the 
	user to some extent (even as an anon with a cookie-based session) */
    ResultsBase(HttpServletRequest _request, HttpServletResponse response,
		 boolean requiresUser) {

	long tid=Thread.currentThread().getId();

	request = _request;
	cp = request.getContextPath();
	infomsg += " [cp="+cp+"]";
	
	if (!requiresUser) return;

	
	sd = SessionData.getSessionData(request);
	infomsg += " [sd="+sd+"]";

	StringBuffer msgBuffer=new StringBuffer();
	uid = sd.getRemoteUser(request,  msgBuffer);
	infomsg += msgBuffer;
	
	displayName= sd.getStoredDisplayName();
	atHome = Hosts.atHome();
	
    }

    ResultsBase( boolean _error,     String _errmsg) {
	request=null;
	cp=null;
	setError(_error);
	setErrmsg( _errmsg);
    }

    /** Marks this object as having an error resulting from an Exception being
	thrown somewhere
     */
    void hasException(Exception _ex) {
	ex = _ex;
	setError(true);
	String msg = ex.getMessage();
	if (msg==null) msg = "Unknown internal error ("+ex+"); see stack trace in the server logs";
	setErrmsg(msg);
	Logging.error("" +ex);
	ex.printStackTrace(System.err);
    }

    /** We don't try to send this to a web client over HTTP (in REST calls),
	but use it in server-side calls (from JSP pages) */
    //@XmlTransient
    Exception ex=null;
    public Exception getEx() { return ex; }
    public String exceptionTrace() {
        StringWriter sw = new StringWriter();
        try {
            if (ex==null) return "No exception was caught";
            ex.printStackTrace(new PrintWriter(sw));
            sw.close();
        } catch (IOException _ex){}
        return sw.toString();
    }

   
  
}
