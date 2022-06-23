package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import javax.persistence.*;

import java.security.*;
    
import org.apache.openjpa.persistence.jdbc.*;

import jakarta.xml.bind.annotation.XmlElement; 
import jakarta.xml.bind.DatatypeConverter;
    
import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
//import edu.wisc.game.saved.*;

/** Information about a repeat user (who may own multiple playerId) stored in the SQL database.
 */

@Entity  
public class User extends OurTable {
    @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;
    public long getId() { return id; }
    public void setId(long _id) { id = _id; }

    private String nickname;
    private String email;
    /** Sent to the Android client as an authentication token */
    private String idCode;

    public String getNickname() { return nickname; }
    public void setNickname(String _nickname) { nickname = _nickname; }
    public String getEmail() { return email; }
    public void setEmail(String _email) { email = _email; }
    public String getIdCode() { return idCode; }
    public void setIdCode(String _idCode) { idCode = _idCode; }

    /** The date of first activity */
    @Basic
    private Date date; 
    public Date getDate() { return date; }
    public void setDate(Date _date) { date = _date; }

    @Basic      @Column(length=64) 
    //@Display(order=2, editable=false, text="encrypted password", digest=true) 
	String digest;
    /** Encrypted password (or, more precisely, the MD5 digest of the
     password). If an empty string is stored here, AND the user
     has roles that require a password, it means that the entry 
     is   disabled, because the digest of any string is a non-empty string. */
    public  String getDigest() { return digest; }
    public void setDigest(       String x) { digest = x; }

    private static String encryptPassword( String clearPassword) {

	final String algo="MD5";
	try {
	    MessageDigest md = MessageDigest.getInstance("MD5");
	    md.update(clearPassword.getBytes());
	    byte[] digest = md.digest();
	    String x = DatatypeConverter.printHexBinary(digest).toUpperCase();
    
	    //String x = org.apache.catalina.realm.RealmBase.Digest(clearPassword, "MD5", "utf-8" );
	    System.err.println("Hash=" + x);
	    return x;
	} catch( NoSuchAlgorithmException ex) {
	    System.err.println("No such digest algo: " + algo);
	    return null;
	}
    }
    
   /** Encrypts the passed password, and stores the encrypted
     * value. This enables the user for logging in */
    public void encryptAndSetPassword( String clearPassword) {

	String enc = encryptPassword( clearPassword);
	if (enc != null) 	    setDigest( enc);
   }

    public boolean passwordMatches( String clearPassword) {
	if (clearPassword==null) return false;
	String enc = encryptPassword( clearPassword);
	return enc!=null && enc.equals(getDigest());
    }

    
    public String toString() {
	return "[User: id=" + id+", email="+email+", nickname="+nickname+", date=" + date+"]";
    }

      /** Can be used instead of (User)em.find(User.class, un);
     @return The User object with  the matching name, or null if none is found */
    public static User findByName( EntityManager em, String nickname) {
	Query q = em.createQuery("select m from User m where m.nickname=:c");
	q.setParameter("c", nickname);
	try {
	    return (User)q.getSingleResult();
	} catch(NoResultException ex) { 
	    // no such user
	    return null;
	}  catch(NonUniqueResultException ex) {
	    // this should not happen, as we have a uniqueness constraint
	    Logging.error("Non-unique user entry for nickname='"+nickname+"'!");
	    return null;
	}
    }

    //----------------------- roles, since GS 5.003
        /** This is how it's described in context.xml:
    //	     userRoleTable="user_roles" roleNameCol="role_name"
    create table user_roles (
			     user_name         varchar(15) not null,
			     role_name         varchar(15) not null)
    */
   @ManyToMany(cascade=CascadeType.ALL)
   /*
   @JoinTable(name="arxiv_user_roles",
		 joinColumns=@JoinColumn(name="user_name", 
					 referencedColumnName="user_name",
					 nullable=false,
					 columnDefinition="varchar(15)"),
		 inverseJoinColumns=
		  @JoinColumn(name="role_name", referencedColumnName="role",
			      nullable=false,
			      columnDefinition="varchar(15)"  //   length=15
			      ))
   */
   private Set<Role> roles = new LinkedHashSet<Role>();

    public Set<Role> getRoles() {
	return roles;
    }


    synchronized public void addRole(Role r) {
	if (getRoles().contains(r)) return;
	else roles.add(r);
    }
    synchronized public void removeRole(Role r) {
	roles.remove(r);
    }

    public boolean hasRole(Role.Name name) {
	if (getRoles()==null) Logging.warning(" hasRole() : getRoles==null!");
	for(Role r: getRoles()) {
	    if (r.getERole() == name) return true;
	}
	return false;
    }

   /** Does this user have any of the roles in the specifed list? 

       @param names An array of roles. It must be non-null, but may be empty
       (in which case, of course, false will be returned). 

       @return True if the user has any of the listed roles. 
   */
     public boolean hasAnyRole(Role.Name[] names) {
	for(Role.Name r: names) {
	    if (hasRole(r)) return true;
	}
	return false;
    }

    /** Does this user have the "admin" role? */
    //    public boolean isAdmin() {
    //	return hasRole(Role.Name.admin);
    //    }

    /** Does this user have the "researcher" role? */
    //public boolean isResearcher() {
    //	return hasRole(Role.Name.researcher);
    //    }

    public boolean isMlc() {
	return hasRole(Role.Name.mlc);
    }
    
    /** For some reason, the getRoles call here seems to be essential to ensure
     that roles are available later! */
    @PostLoad
	void postLoad() {
	int n = 0;	for(Role r: getRoles()) {	    n++;	}
	//	Logging.info("Loaded a user entry with "+n +" roles: " + reflectToString());
    }

    public String listRoles() {
	String s="";
	for(Role r: getRoles()) {
	    s += " " + r;
	}
	return s; 	
    }

 
    
}
