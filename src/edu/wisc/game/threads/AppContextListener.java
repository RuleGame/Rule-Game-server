package edu.wisc.game.threads;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import edu.wisc.game.util.*;

/** This listener is used so that we can safely stop the
    MaintenanceThread when the web app is undeployed.
    
    There is an entry in web.xml for this listener.

    For some examples of using context listeners, see e.g.
    https://www.digitalocean.com/community/tutorials/servletcontextlistener-servlet-listener-example
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    public void contextInitialized(ServletContextEvent servletContextEvent) {
    	ServletContext ctx = servletContextEvent.getServletContext();
    	
    	//String url = ctx.getInitParameter("DBURL");
    	//ctx.setAttribute("DBManager", dbManager);
    	Logging.info("Context initialized for Application.");
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
    	ServletContext ctx = servletContextEvent.getServletContext();
    	//DBConnectionManager dbManager = (DBConnectionManager) ctx.getAttribute("DBManager");
	Logging.info("Context destroyed for Application.");
	MaintenanceThread.destroy();
    }
	
}

