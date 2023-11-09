package edu.wisc.game.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
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

    /** Note that request.getProtocol() is not helpful to distinguish HTTP from
	HTTPS, as it seems to always return "HTTP". OTOH, using port 443 is
	a good indicator of HTTPS being used.
     */
    public ContextInfo(HttpServletRequest request, HttpServletResponse response){
	super(request,response,false);	
	if (error) return;

	//String proto = request.getProtocol(); // could give "HTTP/1.0"
	//proto = proto.replaceAll("/.*", "");
	
	host = request.getLocalName();
	port= request.getLocalPort();
	cp= request.getContextPath();
	dev = cp.endsWith("-dev");
	String proto = (port==443)? "https" : "http";
	serverUrl= proto + "://" + host + ":" + port + cp;
	clientUrl = MainConfig.getGuiClientUrl(dev);	
    }

    public String devProd() {
	return dev? "dev": "prod";
    }

}
