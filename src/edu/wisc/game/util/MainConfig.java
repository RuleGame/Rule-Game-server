package edu.wisc.game.util;

import java.io.*;
import java.util.*;

/** A MainConfig is a structure storing the content of a configuration file.
    Typically, an application uses only one such file -- the master
    config file of the Game Server running on this host.
    It can be used by the site administrator to override some
    defaults. The master config file is located outside of the WAR file, so that
    adjustments can be made without rebuilding the WAR file.

    <p>
    In particular, the main config file (usually located in /opt/w2020/w2020.conf) may override the user name and password for accessing the MySQL database password specified in META-INF/persistence.xml (packaged into the WAR file). Thus changing the database password, or asking the server to work with a different database, can be accommodated without having to rebuild the WAR file.

    <p>The MainConfig object is used in sql.Main

    <p>In some applications  there are multiple instances of MainConfig. E.g.
    MergeDatasets uses the config file for the merge data set as the main config
    file, and additionally has an instance of MainConfig describing the
    data set being merged into the merge data set.
*/

public class MainConfig //extends ParseConfig
{

    /*
    public static interface Updater {
	// Updates some other object based on the specified MainConfig object 
	void update(MainConfig mc);
    }

    private static Set<Updater> updaters = new HashSet<Updater>();

    public static void addUpdater(Updater up) { updaters.add(up); }
    */

    /** The default instance, to be used in static calls */
    private static MainConfig mainConfig = null;

    /** Gets the default instance. */
    public static MainConfig getMainConfig() { return  mainConfig;}
    
    /** The default location of the main config file for this application.
	Could be overridden with setPath() */
    static private String defaultPath = "/opt/w2020/w2020.conf";
    /** The path in this instance */
    private final String path;
    
    private ParseConfig ht = null;

    /** This method is used by analysis tools who work with different
	databases than the default one. It is also used by the Captive
	Server, with null argument, to disable the attempts to look
	for the master config file (which CGS users likely won't
	have).

	@param _path The location of the main config file to be used in this
	application, or null to indicate that we don't use a config file in
	this app.
     */
    public static void setPath(String _path) {
	defaultPath = _path;

	if (mainConfig!=null) {
	    Logging.warning("MainConfig.setPath("+_path+") invoked when mainConfig already exists, with path=" + mainConfig.path);
	}
	
	if (mainConfig==null || !mainConfig.path.equals(_path)) {
	    mainConfig = (_path==null)? null: new MainConfig(_path);
	    //for(Updater up: updaters) up.update(mainConfig);
	}

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

    /** @param _path The location of the master config file (or an alternative
	config file) from which this structure will be initialized */
    public MainConfig(String _path) {
	path = adjustPath(_path);
	try {
	    if (_path==null) throw new IllegalArgumentException("Master config path not specified");
	    
	    ht = new ParseConfig(path);
	} catch(Exception ex) {
	    System.err.println("Warning: Problem reading master configuration file '"+path +"'. Will use built-in default values instead, which can cause problems accessing the database server and data files. If you are running the Captive Server, you can ignore this message. " + ex);
	    ex.printStackTrace(System.err);
	}
    }

    static public String getString(String name, String defVal) {
	if (mainConfig==null) mainConfig = new MainConfig(defaultPath);
	return mainConfig.doGetString(name, defVal);
    }
    
    public String doGetString(String name, String defVal) {
	return (ht==null)? defVal: ht.getString(name, defVal);
    }

    /** Looks up the path, adjusts it if necessary (when on a DoIT
	shared hosting host), and converts it to a File object */
    static public File getFile(String name, String defVal) {
	if (mainConfig==null) mainConfig = new MainConfig(defaultPath);
	return mainConfig.doGetFile(name, defVal);
    }
    
    public File doGetFile(String name, String defVal) {
	String path = getString(name, defVal);
	path = adjustPath(path);
	return new File(path);
    }
	   
    
    /** The URL string for the Rule Game GUI Client.
	@param dev True for the dev version, false for prod
	@return The URL string, or the default (a URL on same server and port)
     */
    static public String getGuiClientUrl(boolean dev) {	
	if (mainConfig==null) mainConfig = new MainConfig(defaultPath);
	return mainConfig.doGetGuiClientUrl(dev);
    }
    
    public String doGetGuiClientUrl(boolean dev) {
	String name = dev? "GUI_DEV" : "GUI_PROD";
	String def = "/rule-game/" + (dev? "dev/" : "prod/");
	return  getString(name, def);
    }
	
    public String toString() {


	Vector<String> v = new Vector<>();

	Collection<String> keys = ht.keySet();
	//String keys[] = {"openjpa.ConnectionURL"};
	for(String key: keys) {
	    v.add(key + " ==> " + ht.get(key));
	}
	
	String s = Util.joinNonBlank("; ", v);
   
	return "("+s+")";
    }
    
}
