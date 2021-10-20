package edu.wisc.game.util;


import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;


/** Convenience routines to determine the host name etc */
public class Hosts  {

    /** Returns true if this is a test run on a home PC. The list of
     such machines' host names is hard-coded inside this method. When
     such a machine is used, we can use the "localhost" URL in emails,
     since the emails will most likely be read in a web browser on the
     same machine, possibly even in off-line mode. This contrasts with
     production runs on a "real" web server, when the true hostname
     should always be used.
    */
    static public boolean isLocal(String hostname) {
	System.out.println("running on host = " + hostname);
	return hostname.equals("localhost") ||
	    hostname.startsWith("bixi-");
    }

    /** Returns the host name this application is running on. */
    static public String determineHostname() {
	try {
	    String hostname = InetAddress.getLocalHost().getHostName();
	    System.out.println("running on host = " + hostname);
	    return hostname;
	} catch(java.net.UnknownHostException ex) {
	    // This should not happen in any normal operation
	    return "localhost";
	}
    }

    /** Returns true if the application apparently runs on a home PC */
    static public boolean atHome() {
	return isLocal(determineHostname()); 
    }

}
