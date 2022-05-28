package edu.wisc.game.web;

import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.svg.*;


public class ImageObjectReport  extends ResultsBase  {

    public boolean wild=false;
    public String name0 = null, name=null, nameEncoded=null;
    public ImageObject io = null;
    
    public ImageObjectReport(HttpServletRequest request, HttpServletResponse response)  {
	super(request,response, false);
	if (error) return;

	try {
	    name0 = name = request.getParameter("image");
	    if (Composite.isCompositeName(name)) {
		Composite compo = new Composite(name);
		wild = compo.isWild();
		if (wild) {
		    io = compo.sample(new RandomRG());
		    name = io.getKey();
		} else io = compo;
	    } else {
		io = ImageObject.obtainImageObjectPlain(null, name, false);
	    }
	    nameEncoded =  java.net.URLEncoder.encode(name, "UTF-8");
	} catch(Exception ex) {
	    hasException(ex);
	}

    }

    
}
