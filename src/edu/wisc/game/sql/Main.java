package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;
import org.apache.openjpa.persistence.JPAProperties;


import edu.wisc.game.util.*;

/** An object that is responsible for getting EntityManager object(s)
    for the application. Normally, an app would have just 1 instance
    of Main (Main.oneMain), because we work with just 1 database. Only
    applications that work with 2 (or more) databases, e.g. copying
    data between databases, would need additional instances of Main.
*/
  
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


    private static Main defaultMain=null;
    
    /** This name will be used to configure the EntityManagerFactory
        based on the corresponding name in the
        META-INF/persistence.xml file
     */
    final public static String persistenceUnitName = "w2020";

    private EntityManagerFactory factory = null;

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
    private synchronized  EntityManagerFactory getFactory() {
        if (factory == null) {	    
	    Properties prop = System.getProperties();
	    Hashtable<Object,Object>  h = (Hashtable<Object,Object>) prop.clone();

	    //<property name="openjpa.ConnectionURL" 
	    //value="jdbc:mysql://localhost/game?serverTimezone=UTC"/>
	    //String url = config.doGetString("JDBC_URL", null);
	    String database = config.doGetString("JDBC_DATABASE", null);

	    //System.out.println("Creating factory for Main=" + this +". database="  + database);
	    
	    if (database != null) {
		String url = "jdbc:mysql://localhost/"+database+"?serverTimezone=UTC";
	    
		h.put("openjpa.ConnectionURL" ,url);
	    }
	    

            //<property name="openjpa.ConnectionUserName" 
	    String s = config.doGetString("JDBC_USER", null);
	    if (s!=null) h.put("openjpa.ConnectionUserName" , s);
	    
	    s = config.doGetString("JDBC_PASSWORD", null);
	    if (s!=null) h.put("openjpa.ConnectionPassword" , s);

            factory = Persistence.
                createEntityManagerFactory(persistenceUnitName, h);
        }
        return factory;
    }
    
    private EntityManager oneEm = null;
    /** This should be initialized in a "lazy" manner, i.e. only when
	actually needed. This will ensure that MainConfig.mainConfig
	has been properly initialized by the time Main.oneMain is created. */
    private static Main oneMain = null;


    private final MainConfig config;

    public Main(MainConfig _config) {
	config = _config;
    }
	
    
    /** Creates a new EntityManager from the EntityManagerFactory. 
     */
    public static synchronized EntityManager getEM() {
	if (oneMain==null) oneMain=new Main(MainConfig.getMainConfig());
	return oneMain.doGetEM();
	
    }
    public synchronized EntityManager doGetEM() {
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
	if (oneMain==null) oneMain=new Main(MainConfig.getMainConfig());
	return oneMain.doGetNewEM();
	
    }
    public synchronized EntityManager doGetNewEM() {
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
	if (oneMain==null) oneMain=new Main(MainConfig.getMainConfig());
	oneMain.doPersistObjects(v);
    }

    public void doPersistObjects(Object... v) {
	EntityManager em = doGetEM();
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
	if (oneMain==null) oneMain=new Main(MainConfig.getMainConfig());
	oneMain.doSaveObject(o);
    }
    
    public <T> void doSaveObject(T o) {
	EntityManager em = doGetNewEM();
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

    public String toString() {
	return "(config="+config+")";
    }

}
