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

    private static final String START_PAGE =	 "/launch/index.jsp";

    public void service(HttpServletRequest request,HttpServletResponse response) {
	service0( request, response, START_PAGE, false);
    }

    private void refuse(HttpServletResponse response, String msg) {
	try {
	    response.sendError(HttpServletResponse.SC_UNAUTHORIZED,msg);
	} catch( IOException ex) {}
    }

    /** @param sp0 The default redirect URL
	@param needPw If true, expects a password to come with the request
     */
    void service0(HttpServletRequest request, HttpServletResponse response, String sp0, boolean needPw) {
	 String sp =  getParameter1(request, "sp");
	     
	 String qs =  request.getParameter("qs");
	 if (qs!=null && qs.equals("null")) qs=null;
	 String nickname =  getParameter1(request,"nickname");
	 String email =  getParameter1(request,"email");
	 String pw = null;
	 
	 if (needPw) {
	     pw = getParameter1(request,"password");
	     if (pw==null || pw.equals("null")) {
		 refuse(response, "No password supplied");
		 return;
	     }
	 }
	     
	 UserResponse ur = new UserResponse(email, nickname, pw, getAnon(request));
	 if (ur.getError()) {
	     refuse(response, ur.getErrmsg());
	     return;
	 }

	 User u = ur.getUser();

	 SessionData sd = SessionData.getSessionData(request);
	 sd.storeUserInfo(u, ur.getPasswordMatched());

	 String redirect = sp0;
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

