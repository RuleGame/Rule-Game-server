package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
//import javax.persistence.*;

//import edu.wisc.game.sql.*;
//import edu.wisc.game.util.*;
//import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.UserResponse;

/** The login page for the MLC results sumbission tool */
public class MlcLoginServlet extends LoginServlet {

  private static final String START_PAGE =	"/mlc/index.jsp";

    public void service(HttpServletRequest request,HttpServletResponse response) {
	service0( request, response, START_PAGE, true); 
    }

	
}

