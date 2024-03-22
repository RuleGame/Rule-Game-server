package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;
import org.apache.openjpa.persistence.JPAProperties;


import edu.wisc.game.util.*;

public class Main {
   /** Finds the process id of the UNIX process for this application.

        FIXME: This obviously is non-portable outside of UNIX.

        @return PID, or -1 on failure
    */
    public static int getMyPid() {
        try {
            FileReader fr = new FileReader("/proc/self/stat");
            LineNumberReader r = new LineNumberReader(fr);
            String s = r.readLine();
            if (s==null) return -1;
            String[] q= s.split("\\s+");
            return Integer.parseInt(q[0]);
        } catch (IOException ex) {
            return -1;
        }
    }

    /** This name will be used to configure the EntityManagerFactory
        based on the corresponding name in the
        META-INF/persistence.xml file
     */
    final public static String persistenceUnitName = "w2020";

    private static EntityManagerFactory factory = null;

    /** The location of a site-specific config file */

    
   /** Initializes the EntityManagerFactory using the "persistence
       unit" (a section in META-INF/persistence.xml, with the name
       "w2020") and the properties in MainConfig, which have been
       loaded from the master config file (in the Game Server) or from
       a command-line provided config file (in analysis tools) (on top
       of any system properties, which we usually don't have). The
       latter override the former.


// org.apache.openjpa.lib.util.ParseException: 
// Equivalent property keys "openjpa.ConnectionPassword" and "javax.persistence.jdbc.password" are specified in configuration.


    */
    public static synchronized  EntityManagerFactory getFactory() {
        if (factory == null) {	    
	    Properties prop = System.getProperties();
	    Hashtable<Object,Object>  h = (Hashtable<Object,Object>) prop.clone();

	    //<property name="openjpa.ConnectionURL" 
	    //value="jdbc:mysql://localhost/game?serverTimezone=UTC"/>
	    //String url = MainConfig.getString("JDBC_URL", null);
	    String database = MainConfig.getString("JDBC_DATABASE", null);
	    if (database != null) {
		String url = "jdbc:mysql://localhost/"+database+"?serverTimezone=UTC";
	    
		h.put("openjpa.ConnectionURL" ,url);
	    }
	    

            //<property name="openjpa.ConnectionUserName" 
	    String s = MainConfig.getString("JDBC_USER", null);
	    if (s!=null) h.put("openjpa.ConnectionUserName" , s);



	    
	    s = MainConfig.getString("JDBC_PASSWORD", null);
	    if (s!=null) h.put("openjpa.ConnectionPassword" , s);

            factory = Persistence.
                createEntityManagerFactory(persistenceUnitName, h);
        }
        return factory;
    }

    private static EntityManager oneEm = null;
    
    /** Creates a new EntityManager from the EntityManagerFactory. 
     */
    public static synchronized EntityManager getEM() {
	if (oneEm!=null) return oneEm;
        // Create a new EntityManagerFactory if not created yet
        getFactory();
        // Create a new EntityManager from the EntityManagerFactory. The
        // EntityManager is the main object in the persistence API, and is
        // used to create, delete, and query objects, as well as access
        // the current transaction
        EntityManager em = factory.createEntityManager();
	Logging.info("EM created, flushMode=" + em.getFlushMode());
        return oneEm=em;
    }

    public static synchronized EntityManager getNewEM() {
        // Create a new EntityManagerFactory if not created yet
        getFactory();
        // Create a new EntityManager from the EntityManagerFactory. The
        // EntityManager is the main object in the persistence API, and is
        // used to create, delete, and query objects, as well as access
        // the current transaction
        EntityManager em = factory.createEntityManager();
        return em;
    }

    
    /** Reports memory use */
    public static void memory() {
        memory("");
    }

    /** Reports memory use */
    public static void memory(String title) {
        System.out.println(memoryInfo(title, true));       
    }

    static DecimalFormat memFmt  = new DecimalFormat("#,###");
  
  
    public static String memoryInfo(String title, boolean doGc) {
        Runtime run =  Runtime.getRuntime();
        if (doGc) run.gc();
        String s = (title !=null && title.length()>0) ? " ("+title+")" :"";
        long mmem = run.maxMemory();
        long tmem = run.totalMemory();
        long fmem = run.freeMemory();
        long used = tmem - fmem;
        return "[MEMORY]"+s+" max=" + memFmt.format(mmem) + ", total=" +  memFmt.format(tmem) +
            ", free=" +  memFmt.format(fmem) + ", used=" +  memFmt.format(used);
    }

    //    static void persistObject(Object o) {
    //	 persistObjects(Object[] v) {
    //    }

    /** See also
    https://download.oracle.com/otn-pub/jcp/persistence-2_1-fr-eval-spec/JavaPersistence.pdf, which says (in "3.2.2 Persisting an Entity Instance"):
<ul>
<li>If X is a new entity, it becomes managed. The entity X will be entered into the database at or before transaction commit or as a result of the flush operation.

<li>If X is a preexisting managed entity, it is ignored by the persist operation (...)

<li>If X is a detached object, the EntityExistsException may be thrown when the persist operation is invoked, or the EntityExistsException or another PersistenceException may be thrown at flush or commit time
</ul>
    */
    static public void persistObjects(Object... v) {
	EntityManager em = Main.getEM();
	synchronized(em) {
	    
	    try {
		em.getTransaction().begin();
		for(Object o: v) {
		    if (!em.contains(o)) {
			em.persist(o);
			String s = (o instanceof Episode) ?   ((Episode)o).report():
			    (o instanceof PlayerInfo) ?   ((PlayerInfo)o).report():
			    o.toString();
			Logging.info("Persisting " + o.getClass() + ": " + s);
		    }
		}
		em.getTransaction().commit();
	    } finally {
		try {
		    EntityTransaction tran = em.getTransaction();
		    if (tran.isActive()) tran.commit();
		} catch (Exception ex) {
		    Logging.error("Main.persistObjects(): exception in commit: " +ex);
		    ex.printStackTrace(System.err);
		}
		//em.close();
	    }
	}
    }

    /** @param o a detached object */
    static public <T> void saveObject(T o) {
	EntityManager em = Main.getNewEM();
	try {	    
	    Logging.info("Merging object " + o);
	    em.getTransaction().begin();
	    T om = em.merge(o);
	    Logging.info("Now, o=" + o +", om=" + om);
	    em.getTransaction().commit();	    
	} finally {
	    try {	    em.close();} catch(Exception ex) {}
	}
    }
    

}
