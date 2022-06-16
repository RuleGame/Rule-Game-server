package edu.wisc.game.sql;

import java.util.*;
import java.text.*;
import javax.persistence.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

import edu.wisc.game.reflect.*;

/** A role is simply a database-storable wrapper around the role name */
@Entity    
//   @Table(name="arxiv_Role")
   public class Role {   
  
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
	//admin, researcher, subscriber
	mlc
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
