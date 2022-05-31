package edu.wisc.game.web;

//import java.io.*;
//import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
//import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.*;


/** Prepares some information needed for proper URL construction in 
    our app.
*/
public class ContextInfo    extends ResultsBase  {

    public String host;
    public int port;
    public String cp;
    /** True if it is believed that this web app runs in dev, rather than prod */
    public boolean dev;
    /** The Game Server URL for REST calls (based on the HTTP request
	we have just received). This can be passed to the GUI client so 
	that it would be using our Game Server (and not a Game Server
	somewhere else).
    */
    public String serverUrl;
    /** The main GUI Client URL to use (dev or prod). It normally comes
	from the master config file.
     */
    public String clientUrl;

    
    public ContextInfo(HttpServletRequest request, HttpServletResponse response){
	super(request,response,false);	
	if (error) return;

	host = request.getLocalName();
	port= request.getLocalPort();
	cp= request.getContextPath();
	dev = cp.endsWith("-dev");
	serverUrl="http://" + host + ":" + port + cp;
	clientUrl = MainConfig.getGuiClientUrl(dev);	
    }

    public String devProd() {
	return dev? "dev": "prod";
    }

}
