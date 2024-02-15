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
    /** True if we're to use the dev version of the GUI client rather than
	the prod one. (This flag comes from the URL query string, dev=true,
	and controls the choice of the clientUrl).
    */
    public boolean dev;
    /** The Game Server URL for REST calls (based on the HTTP request
	we have just received). This can be passed to the GUI client so 
	that it would be using our Game Server (and not a Game Server
	somewhere else). The value is, for example,
	http://localhost:8080/w2020-dev
	or
	https://rulegame.wisc.edu/w2020
    */
    public String serverUrl;
    /** The main GUI Client URL to use (dev or prod). It normally comes
	from the master config file.
     */
    public String clientUrl;

    /** Optional; used in some pages. More usually, null. */
    public String exp=null;
    
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
	//dev = cp.endsWith("-dev");
	String proto = (port==443)? "https" : "http";
	serverUrl= proto + "://" + host + ":" + port + cp;

	dev = "true".equals( request.getParameter("dev"));
	clientUrl = MainConfig.getGuiClientUrl(dev);

	exp=  request.getParameter("exp");

    }

    /** Do we use the "dev" or "prod" client? */
    public String devProd() {
	return dev? "dev": "prod";
    }

    public String getVersion() {
	return Episode.getVersion();
    }

	
    
}
