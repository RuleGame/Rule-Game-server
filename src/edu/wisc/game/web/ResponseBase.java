package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;

//import javax.xml.bind.annotation.XmlTransient; 
//import javax.json.*;




import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;



/** The base of all response objects returned by our REST API methods */
public class ResponseBase {
    boolean error=false;
    String errmsg=null;
    
    public boolean getError() { return error; }
    public void setError(boolean _error) { error = _error; }
    
    public String getErrmsg() { return errmsg; }
    public void setErrmsg(String _errmsg) { errmsg = _errmsg; }

    /** Sets the error flag and the error message */
    protected void giveError(String msg) {
	setError(true);
	setErrmsg(msg);
    }

    ResponseBase( ) {}

    ResponseBase( boolean _error,     String _errmsg) {
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
