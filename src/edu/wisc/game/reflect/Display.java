package edu.wisc.game.reflect;

import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

/** An annotation describing how, if at all, a particular data field
    (content of a SQL database column) is to be displayed in HTML
    tables and data entry forms etc.  */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Display {
    double order() default 0;
    boolean editable() default true;
    /** This flag is set true for those fields that can be supplied by
	a referrer patient.
     */
    boolean rp() default false;
    /** This flag is set true for those fields that must be verified when
	sending a payment
     */    
    boolean payment() default false;
    /** If not null and not empty, this text will be displayed in
      parnethesis <em>after</em> the field's name in entry tables
      etc. */
    String text() default "";

    /** If not null and not empty, this text will be displayed
	 <em>instead</em> of the field's name in entry tables etc. */
    String alt() default "";

    /** If true, the field actually stores the MD5-digest of the
	relevant value.  This is typically used to store users'
	passwords. Moreover, if a null or empy string is entered, we'll
	store null.	
    */
    boolean digest() default false;
    /** Should be treated as illegal value, if submitted */
    //String illegal() default USStates.Select_One.toString();

    /** */
    String link() default "";

}

