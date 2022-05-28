package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.util.*;
//import edu.wisc.game.saved.*;

/** Information about a repeat user (who may own multiple playerId) stored in the SQL database.
 */

@Entity  
public class User {
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

    public String toString() {
	return "[User: id=" + id+", email="+email+", nickname="+nickname+", date=" + date+"]";
    }
    
}
