package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import jakarta.json.*;
import javax.persistence.*;


import jakarta.xml.bind.annotation.XmlElement; 
import jakarta.xml.bind.annotation.XmlRootElement;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;

/** The parent class for various structures that are returned, in JSON form,'
    by REST calls. */
public class ResponseBase {
    boolean error=false;
    String errmsg=null;
    
    public boolean getError() { return error; }
    @XmlElement
    public void setError(boolean _error) { error = _error; }
    
    public String getErrmsg() { return errmsg; }
    @XmlElement
    public void setErrmsg(String _errmsg) { errmsg = _errmsg;
	if (errmsg!=null) {
	    //Exception ex = new IllegalArgumentException("ERRMSG=`" + errmsg+"'");

	    
	    //ex.printStackTrace(System.err);


	}

    }

    protected ResponseBase( ) {}

    protected ResponseBase( boolean _error,     String _errmsg) {
	setError(_error);
	setErrmsg( _errmsg);
    }

    protected void hasError(String msg) {
	Logging.error(msg);
	setError(true);
	setErrmsg(msg);
    }

    /** Regularizes an input parameter, trimming leading/trailing white space
	and converting a blank string to null.
	Since ver 8.030, we also trim the trailing slashes, since it has
	turned out lately that one sometimes comes in with the exp=... !
 */
    static String regularize(String x) {
	if (x==null) return null;
	x = x.trim();
	while (x.endsWith("/")) { x = x.substring(0, x.length()-1); }
	if (x.equals("") || x.equals("null")) return null;
	return x;
    }


    
}
