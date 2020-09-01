package edu.wisc.game.sql;

import java.util.*;
import java.text.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

import edu.wisc.game.reflect.*;

/** A role is simply a database-storable wrapper around the role name */
@Entity    
    @Table(name="arxiv_Role")
   public class Role 
{   
    /** @Enumerated(EnumType.STRING)  means, "store string", as per
	http://openjpa.apache.org/builds/latest/docs/manual/manual.html#jpa_overview_mapping_enum
     */
    /*
    @Id  
	@Display(editable=false, order=1) 
	@Enumerated(EnumType.STRING) 
	@Column(length=15)
    	private Name
	role;   

    public Name getRole() { return role; }
    public void setRole( Name x) { role = x; }
    */

    //    public Name getRole() { return Enum.valueOf( Name.class, role); }
    //public void setRole( Name x) { role = x.toString(); }
  
    @Id  
	@Display(editable=false, order=1) 
	@Column(length=15)
    	private 	String
	role;   

    public String getRole() { return role; }
    public void setRole( String x) { role = x; }

    public void setRole( Name x) { role = x.toString(); }
    public Name getERole() { return Enum.valueOf( Name.class, role); }


    public static enum Name {
	admin, researcher, subscriber
    };

    public String toString() {
	return role.toString();
    }
    
    /** Equality is based on the role names */
    public boolean equals(Object x) {
	return (x instanceof Role) && ((Role)x).getRole()==getRole();
    }
    
    /** Based on underlying role name */
    public int 	hashCode() {
	return getRole().toString().hashCode();
    }



}
