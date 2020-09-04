package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

//import org.apache.openjpa.persistence.jdbc.*;

//import javax.ws.rs.*;
//import javax.ws.rs.core.*;
//import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;  
import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement; 
import javax.xml.bind.annotation.XmlTransient; 
@XmlRootElement(name = "piece") 



/** Represents a piece of a specified type at a specified location. Used
    in board description.

<pre>
 "boardObjectsArrays":{
     "Cvu0lwRnl":{
     "id":"Cvu0lwRnl",
     "value":
     [{"color":"yellow","shape":"square","id":"1","x":1,"y":1},
     {"color":"black","shape":"square","id":"6","x":6,"y":1},
     {"color":"red","shape":"square","id":"31","x":1,"y":6},
     {"color":"blue","shape":"square","id":"36","x":6,"y":6}],
     "name":"Four squares in corners"}
     }
}
</pre>
*/
@Entity  
//@Embeddable
public class Piece  implements Serializable {

    public enum Color {
	RED, BLACK, BLUE, YELLOW;
	/** "r", "b", etc; BLUE becomes "g", as if its GREEN */
	public String symbol() {
	    String s = toString().toLowerCase().substring(0,1);
	    if (this==BLUE) s = "g";
	    return s;
	}
    };
    public enum Shape {
	SQUARE, STAR, CIRCLE, TRIANGLE;
	public String symbol() {
	    return this==SQUARE? "#":
		this==STAR? "*":
		this==CIRCLE? "O":
		this==TRIANGLE? "T":
		"?";
	}
    };
    
    private static final long serialVersionUID = 1L;

    // This back link and its "get" method are used for JPA, but not JSON
    //    @JsonIgnore
    @XmlTransient
    @ManyToOne(fetch = FetchType.EAGER)
    private Board board;
 

    // Using "xget" instead of "get" to avoid looping (Board to Piece to Board...) during the conversion to JSON
    //@XmlTransient
    //public Board getBoard() { return board; }
    //   @XmlElement
    public void setBoard(Board _board) { board = _board; }

    // Not that we need an ID here, but it seems to be necessary for
    // cascading
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY) 
    private long id;
    
    private Color color; 
    private Shape shape;  

    private int x;
    private int y;

    /** Empty vector for the pieces still on the board; contains the destination
	bucket number for the pieces that have been removed */
    Integer dropped=null;
    
    public long getId() { return id; }
  @XmlElement 
    public void setId(long _id) { id = _id; }
    

    /*
    public String getColor() { return color; }
  @XmlElement 
    public void setColor(String _color) { color = _color; }
    
    
    public String getShape() { return shape; }
  @XmlElement 
    public void setShape(String _shape) { shape = _shape; }
    */
    
   public Color getColor() { return color; }
   @XmlElement
   public void setColor(Color _color) { color = _color; }
    public Shape getShape() { return shape; }
    @XmlElement
    public void setShape(Shape _shape) { shape = _shape; }

    
    public int getX() { return x; }
    @XmlElement 
    public void setX(int _x) { x = _x; }

    public int getY() { return y; }
    @XmlElement 
    public void setY(int _y) { y = _y; }

    public Integer getDropped() { return dropped; }
    @XmlElement
    public void setDropped(Integer _dropped) { dropped = _dropped; }

    public Piece(){} 
     
    public Piece(//int _id,
		 Shape _shape, Color _color, int _x, int _y) {
	//	id = ""+_id;
	shape = _shape;//.toString().toLowerCase();
	color = _color;//.toString().toLowerCase();
	x = _x;
	y = _y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Piece)) return false;
        return id == ((Piece) o).getId();
    }
 
    @Override
    public int hashCode() {
        return 31;
    }

    public Board.Pos pos() {
	return new Board.Pos(x,y);
    }

    /* Only used in JSON reporting in the captive server */
    @Transient 
    private int[] buckets = new int[0];

    public int[] getBuckets() { return buckets; }
    @XmlElement
    public void setBuckets(int[] _buckets) { buckets = _buckets; }

    
}
