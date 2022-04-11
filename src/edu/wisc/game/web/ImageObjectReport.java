package edu.wisc.game.web;

//import java.io.*;
import java.util.*;
//import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;
//import javax.persistence.*;


import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
//import edu.wisc.game.rest.*;
import edu.wisc.game.svg.*;


public class ImageObjectReport  extends ResultsBase  {

    public String name = null;
    public ImageObject io = null;
    
    public ImageObjectReport(HttpServletRequest request, HttpServletResponse response)  {
	super(request,response, false);
	if (error) return;

	try {
	    name = request.getParameter("image");	
	    io = ImageObject.obtainImageObjectPlain(null, name, false);
	} catch(Exception ex) {
	    hasException(ex);
	}

      }
    
}
