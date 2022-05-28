package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.persistence.*;

import edu.wisc.game.sql.*;
import edu.wisc.game.util.*;
import edu.wisc.game.formatter.*;


public class LogoutServlet extends HttpServlet {
    public void service(HttpServletRequest request, HttpServletResponse response) {
        EntityManager em=null;
        try {

	    
            // where to go after?
            String redirect = Tools.getString(request,"redirect",    LoginServlet.START_PAGE);
    
            SessionData sd = SessionData.getSessionData(request);         
          
            //String user = sd.getRemoteUser(request);

	    SessionData.discardSessionData(request);
         
	    Cookie cookie =  ExtendedSessionManagement.makeLogoutCookie();     


	    RequestDispatcher dis = request.getRequestDispatcher(redirect);
            //Logging.info("Log out (user="+user+"): forward to=" + redirect);      
            //dis.forward(request, response);

	    String cp = request.getContextPath(); 
	    String eurl = response.encodeRedirectURL(cp + "/"+redirect);

	    if (cookie!=null) response.addCookie(cookie);
	    response.sendRedirect(eurl);
	    
        } catch (Exception e) {
	    try {
                e.printStackTrace(System.out);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in LogoutServlet: " + e); //e.getMessage());
            } catch(IOException ex) {};
        } finally {
 	   if (em!=null) try {	    em.close();} catch(Exception ex) {}
        }
    }

    public String getServletInfo() {
        return "Rule Game LogoutServlet";
    }



}
