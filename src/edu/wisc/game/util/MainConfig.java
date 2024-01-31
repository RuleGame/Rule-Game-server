package edu.wisc.game.util;


import java.io.*;
//import java.util.*;
//import java.text.*;
//import java.net.*;

/** Accessing the main configuration file of the Game Server, which
    can be used by the site administrator to override some
    defaults. It is located outside of the WAR file, so that
    adjustments can be made without rebuilding the WAR file.

    <p>
    In particular, the main config file (usually located in /opt/w2020/w2020.conf) may override the user name and password for accessing the MySQL database password specified in META-INF/persistence.xml (packaged into the WAR file). Thus changing the database password, or asking the server to work with a different database, can be accommodated without having to rebuild the WAR file.

    <p>The MainConfig object is used in sql.Main
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

    /** Tries to figure if we're running on a DoIT shared hosting host,
	and the path such as /opt/w2020/something has to be understood
	with respect to the root of the chrooted directory (such
	as "/var/www/vhosts/wwwtest.rulegame.wisc.edu"), rather than
	with respect to the root of the file system. 

	@param path Something like "/opt/foo"
	@return Either the original path, or something like "/var/www/vhosts/wwwtest.rulegame.wisc.edu/opt/foo"
     */
    static public String adjustPath(String path) {
	final String path0 = path;
	if (path==null) return path;
	File f = new File(path);
	if (f.exists()) return path; // can't complain about success
	if (f.getParentFile().exists()) return path;
	// The desired directory does not exist. See if we're
	// on a DoIT shared hosting machine, where
	// user.dir=/var/www/vhosts/wwwtest.rulegame.wisc.edu/tomcat/work
	// and the conf file is in e.g.
	// "/var/www/vhosts/wwwtest.rulegame.wisc.edu/opt/w2020"
	// rather than "/opt/w2020"
	String tomcatWorkDir = System.getProperty("user.dir");
	System.err.println("user.dir=" +tomcatWorkDir);
	if (tomcatWorkDir == null) return path;
	File d = new File(tomcatWorkDir);
	if (d.getName().equals("work")) d = d.getParentFile();
	if (d.getName().equals("tomcat")) d = d.getParentFile();
	d = new File(d, path.replaceAll("^/", ""));
	path = d.toString();
	System.err.println("Converted path0=" +path0 + " to path=" + path);
	return path;
    }

    static private void initConf() {
	try {
	    if (path==null) return;
	    path = adjustPath(path);

	    
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

    /** Looks up the path, adjusts it if necessary (when on a DoIT
	shared hosting host), and converts it to a File object */
    static public File getFile(String name, String defVal) {
	String path = getString(name, defVal);
	path = adjustPath(path);
	return new File(path);
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
