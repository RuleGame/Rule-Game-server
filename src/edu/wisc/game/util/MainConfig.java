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
    static private String path = "/opt/w2020/w2020.conf";

    static private ParseConfig ht = null;

    /** Used by the Captive Server, with null argument, to disable the
	attempts to look for the master config file (which CGS users
	likely won't have).
     */
    public static void setPath(String _path) {
	path = _path;
	initConf();
    }
    
    static private void initConf() {
	try {
	    if (path==null) return;
	    ht = new ParseConfig(path);
	} catch(Exception ex) {
	    System.err.println("Warning: Problem reading master configuration file '"+path +"'. Will use built-in default values instead, which can cause problems accessing the database server and data files. If you are running the Captive Server, you can ignore this message. " + ex);
	    ex.printStackTrace(System.err);
	}
    }

    static public String getString(String name, String defVal) {
	if (ht==null) initConf();
	return (ht==null)? defVal: ht.getString(name, defVal);
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
