/* ©2019 Rutgers, The State University of New Jersey */
package edu.wisc.game.util; 

import java.util.logging.*;
import java.io.*;

/** Methods used by CHEETA to log warning and error
 * messages. These methdos are simply wrappers around the respective
 * methods of  java.util.logging.Logging.
 */
public class Logging {
    public final static String NAME = "w2020";

    static {
        //try {
	    //            Handler fh = new FileHandler("/mnt/cactext/home/SCILSNET/vmenkov/logs/arxiv1.log");
	    //            fh.setFormatter(new SimpleFormatter());
	    //            Logger.getLogger(NAME).addHandler(fh);
        //} catch (IOException e) {}
    }

    public static void error(String msg) {
	Logger logger = Logger.getLogger(NAME);
	//System.err.println("ERROR: " + msg);
	//OnceMessage.add("ERROR: " + msg);
	logger.severe(msg);
    }

    public static void warning(String msg) {
	Logger logger = Logger.getLogger(NAME);
	//System.err.println("WARNING: " + msg);
	//OnceMessage.add("ERROR: " + msg);
	logger.warning(msg);
    }

    public static void info(String msg) {
	Logger logger = Logger.getLogger(NAME);
	//System.err.println("INFO: " +msg);
	logger.info(msg);
    }

    public static void debug(String msg) {
	if (verbose) System.err.println(msg);
    }

    public static void setLevel(java.util.logging.Level newLevel) {
	Logger logger = Logger.getLogger(NAME);
	logger.setLevel(newLevel);
    }

    static private boolean verbose;
    static public void setVerbose(boolean v) { verbose=v;}

}

