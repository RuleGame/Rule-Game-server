package edu.wisc.game.web;

import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.svg.*;


public class ImageObjectReport  extends ResultsBase  {

    public boolean wild=false;
    public String name0 = null, name=null;
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
	} catch(Exception ex) {
	    hasException(ex);
	}

      }
    
}
