package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.persistence.*;

import edu.wisc.game.sql.*;
import edu.wisc.game.util.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.UserResponse;


public class LoginServlet extends HttpServlet {

    private String getParameter(HttpServletRequest request, String name) {
	String x =  request.getParameter(name);
	return x==null? x : x.trim();
    }

    private String getParameter1(HttpServletRequest request, String name) {
	String x =  getParameter(request,name);
	return x==null || x.equals("") ? null: x;
    }

    private boolean getAnon(HttpServletRequest request) {	
	String x =  getParameter1(request,"anon");
	return x!=null && x.toLowerCase().equals("true");
    }

    static final String START_PAGE =	 "/launch/index.jsp";
    
    public void service(HttpServletRequest request,HttpServletResponse response) {
	 String sp =  getParameter1(request, "sp");
	 String qs =  request.getParameter("qs");
	 if (qs!=null && qs.equals("null")) qs=null;
	 String nickname =  getParameter1(request,"nickname");
	 String email =  getParameter1(request,"email");
	 
	 UserResponse ur = new UserResponse(email, nickname, getAnon(request));
	 if (ur.getError()) {
	     try {
		 response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
				    ur.getErrmsg());
	     } catch( IOException ex) {}
	     return;
	 }

	 User u = ur.getUser();

	 SessionData sd = SessionData.getSessionData(request);
	 sd.storeUserInfo(u);

	 String redirect = START_PAGE;
	 if (sp!=null && !sp.equals("null")) {
	     redirect = sp;
	     if (qs != null) {
		 redirect += "?" + qs;
	     }
	 }

	     
	 String cp = request.getContextPath(); 
	 String eurl = response.encodeRedirectURL(cp + "/"+redirect);

	 Cookie cookie =  ExtendedSessionManagement.makeCookie(u);     
	 if (cookie!=null) {
	     Logging.info("LoginServlet: [Sending cookie="+cookie.getValue()+"]");
	     response.addCookie(cookie);
	 }
	 try {
	     response.sendRedirect(eurl);
	 } catch( IOException ex) {}

	     

    }

	
}

