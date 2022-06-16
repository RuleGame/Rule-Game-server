package edu.wisc.game.sql;

import java.util.*;
import java.util.regex.*;
//import java.lang.reflect.*;
import javax.persistence.*;
//import edu.rutgers.axs.web.WebException;
//import edu.wisc.game.reflect.Reflect;

/** A very simple, stand-alone program that creates necessary Role
    entities in the roles table, and at least one User entity.
    This has been in use since GS 5.003, initially with just one role,
    "mlc" (for MLC result submitters).
 */
public class CreateRoles {

   static private void usage() {
	usage(null);
    }
    
    static private void usage(String msg) {
	System.err.println("Usage:\n");
	System.err.println("To create a user with the 'mlc' role:\n");
	System.err.println("  java [options]  edu.wisc.game.sql.CreateRoles nickname email password");
	System.err.println("For testing:\n");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive");
	if (msg!=null) 	System.err.println( "\n" + msg + "\n");
	System.exit(1);
    }


    
    static void doUser(EntityManager em, String nickname, String email, String pw) {
	em.getTransaction().begin();
	
	User u = User.findByName(em, nickname);
	
	if (u == null) {
	    System.out.println("Creating user: " + nickname + ", email="+email+", password=" + pw);
	    u = new User();
	    u.setNickname(nickname);
	    u.encryptAndSetPassword(pw);
	    em.persist(u);
	} else {
	    System.out.println("User already exists: " + nickname );
	    System.out.println("Setting his password to " + pw);
	    u.encryptAndSetPassword(pw);
	    em.persist(u);
	}
	em.getTransaction().commit();


	em.getTransaction().begin();
	u = User.findByName(em, nickname);
	System.out.println("Reading back user record: " + u.reflectToString() );

	for( Role.Name rn : new Role.Name[] { 
		Role.Name.mlc}) {
	    Role r = (Role)em.find(Role.class, rn.toString());
	    if (r == null) {
		System.out.println("No role found: " + rn);
	    } else {	    
		System.out.println("Adding role '"+rn.toString()+"' to user: "+nickname+ ", role=" + r);
		u.addRole(r);
	    }
	}

	em.persist(u);
	em.getTransaction().commit();


    }

    
    /** For each role name, checks if a Role object with that name
	already exists in the database, and if not, creates it.
	Also, creates certain users, or, if they already exists,
	resets their passwords.
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] argv) //throws WebException
    {
	if (argv.length!=0 && argv.length!=3) usage("Wrong number of arguments");


	EntityManager em = Main.getNewEM();
	try {

	    for(Role.Name name: Role.Name.class.getEnumConstants()) {
		em.getTransaction().begin();
		Role r = (Role)em.find(Role.class, name.toString());
		if (r==null) {
		    System.out.println("Creating role: " + name );
		    r=new Role();
		    r.setRole(name);
		    em.persist(r);
		    em.getTransaction().commit();
		} else {
		    System.out.println("Role '" + name + "' already exists");
		    em.getTransaction().rollback();
		}
	    }


	    if (argv.length==3) {
		String un = argv[0];
		String email = argv[1];
		String pw = argv[2];

		if (!Pattern.matches("[a-zA-Z0-9_.-]+", un)) {
		    usage("Invalid user nickname. Nickname may only contain the following characters: a-zA-Z0-9_.-");
		}
		final int L = 6;
		if (email.length()<L)  usage("Invalid user nickname. Nickname must contain at least " +L + " characters");
		if (!Pattern.matches("[a-zA-Z0-9_.-]+@[a-zA-Z0-9_.-]+", email)) {
		    usage("Apparently invalid email address: " + email);
		}
		
		if (pw.length()<L) usage("Password is too short. Must contain at least " +L + " characters");

		doUser(em, un, email, pw);
	    }
	    /*	    
	    String[] users = {"vmenkov", "anonymous"};
	    for(String un: users) {
		String pw = "xxx", email=null;		
		doUser(em, un, email, pw);
	    }
	    */

	    System.out.println("-----------------------------------");
	    System.out.println("Reading back all user records:");
	    Query q = em.createQuery("select m from User m order by m.nickname");
	    for (User m : (List<User>) q.getResultList()) {
		System.out.println("User record: " + m.reflectToString() );
	    }


	} finally {
	    try {		em.getTransaction().commit();	    } catch (Exception _e) {}
	    em.close();
	}

    }
}
