package edu.wisc.game.util;


import java.io.*;
//import java.util.*;
//import java.text.*;
//import java.net.*;

/** Accessing the main configuration file, which can be used by the
    site administrator to override some defaults. It is located
    outside of the WAR file, so that adjustments can be made without
    rebuilding the WAR file.
*/

public class MainConfig //extends ParseConfig
{

    /** The file location */
    static final private String path = "/opt/w2020/w2020.conf";

    static private ParseConfig ht = null;

    static {
	try {
	    ht = new ParseConfig(path);
	} catch(Exception ex) {
	    System.err.println("ERROR: Problem reading master configuration file '"+path +"'. Will use built-in default values instead, which can cause problems accessing the database server and data files. " + ex);
	    ex.printStackTrace(System.err);
	}
    }

    static public String getString(String name, String defVal) {
	return ht.getString(name, defVal);
    }

    /** The URL string for the Rule Game GUI Client.
	@param dev True for the dev version, false for prod
	@return The URL string, or the default (a URL on same server and port)
     */
    static public String getGuiClientUrl(boolean dev) {
	String name = dev? "GUI_DEV" : "GUI_PROD";
	String def = "/rule-game/" + (dev? "dev/" : "prod/");
	return  getString(name, def);
    }
	
    
}
