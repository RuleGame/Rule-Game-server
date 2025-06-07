package edu.wisc.game.threads;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import edu.wisc.game.util.*;

/** This listener is used so that we can safely stop the
    MaintenanceThread when the web app is undeployed.
    
    <P>
    We can put an entry in web.xml for this listener, but 
    we don't actually do, because the @WebListener annotation
    does the trick.

    <p>
    For some examples of using context listeners, see e.g.
    https://www.digitalocean.com/community/tutorials/servletcontextlistener-servlet-listener-example
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    public void contextInitialized(ServletContextEvent servletContextEvent) {
    	ServletContext ctx = servletContextEvent.getServletContext();
    	
    	//String url = ctx.getInitParameter("DBURL");
    	//ctx.setAttribute("DBManager", dbManager);
	String cp = ctx.getContextPath();
    	Logging.info("Context "+cp+" initialized for Application.");
	MainConfig.setContextPath(cp);
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
    	ServletContext ctx = servletContextEvent.getServletContext();
    	//DBConnectionManager dbManager = (DBConnectionManager) ctx.getAttribute("DBManager");
	MaintenanceThread.destroy();
	Logging.info("Context destroyed for Application.");
    }
	
}

