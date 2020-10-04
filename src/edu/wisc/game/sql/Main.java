package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;
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

   /** Initializes the EntityManagerFactory using the System properties.
        The "icd" name will be used to configure based on the
        corresponding name in the META-INF/persistence.xml file
    */
    public static synchronized  EntityManagerFactory getFactory() {
        if (factory == null) {
            factory = Persistence.
                createEntityManagerFactory(persistenceUnitName,
                                           System.getProperties());
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
        return oneEm=em;
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
	 
    static public void persistObjects(Object... v) {
	EntityManager em = Main.getEM();
	synchronized(em) {
	try {
	    em.getTransaction().begin();
	    for(Object o: v) {		
		em.persist(o);
		String s = (o instanceof Episode) ?   ((Episode)o).report():
		    (o instanceof PlayerInfo) ?   ((PlayerInfo)o).report():
		    o.toString();
		Logging.info("Persisted " + o.getClass() + ": " + s);
	    }
	    em.getTransaction().commit();
	} finally {
	    try {em.getTransaction().commit();} catch (Exception _e) {}
	    //em.close();
	}
	}
    }


    

}
