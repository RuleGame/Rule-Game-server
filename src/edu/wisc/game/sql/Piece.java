package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import java.io.Serializable;  
import jakarta.xml.bind.annotation.XmlElement; 
import jakarta.xml.bind.annotation.XmlRootElement; 
import jakarta.xml.bind.annotation.XmlTransient; 

import edu.wisc.game.parser.Expression.PropMap;


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

    /** The color of a piece */
    /*
    public enum Color {
	RED, BLACK, BLUE, YELLOW;
	// "r" for RED, "b" for BLACK, etc; BLUE becomes "g", as if it's GREEN
	public String symbol() {
	    String s = toString().toLowerCase().substring(0,1);
	    if (this==BLUE) s = "g";
	    return s;
	}
    };
    */

    /** A flexible replacement for an Enum */
    public static class PseudoEnum  {
	final String name;
	PseudoEnum(String _name) {
	    name=_name.toUpperCase();
	}
	public int hashCode() { return name.hashCode(); }
	public String toString() { return name; }	

    }
    
    public static class Color extends PseudoEnum {
	private Color(String _name) {
	    super(_name);
	}

	public boolean equals(Object o) {
	    return (o instanceof Color) && ((Color)o).name.equals(name);
	}


	private static HashMap<String,Color> allColors = new HashMap<>();

	/** @return The Color object for a given name */
	static synchronized public Color findColor(String s) {
	    s = s.toUpperCase();
	    Color c = allColors.get(s);
	    if (c==null) allColors.put(s, c=new Color(s));
	    return c;	    
	}

	static public Color[] findColors(String[] v) {
	    Color[] w = new Color[v.length];
	    for(int j=0; j<v.length; j++) w[j] = findColor(v[j]);
	    return w;
	}
	
	
	static final public  Color RED=findColor("RED"), BLACK=findColor("BLACK"),
	    BLUE=findColor("BLUE"), YELLOW=findColor("YELLOW");
	// "r" for RED, "b" for BLACK, etc; BLUE becomes "g", as if it's GREEN
	public String symbol() {
	    String s = toString().toLowerCase().substring(0,1);
	    if (this==BLUE) s = "g";
	    return s;
	}

	/** The four original colors, inherited from Game Engine 1.0, and
	    used for compatibility with old trial list files. */
	static public final Piece.Color[] legacyColors = {RED, BLACK, BLUE, YELLOW};
	
    };

    public static class Shape extends PseudoEnum {
	private Shape(String _name) {
	    super(_name);
	}

	public boolean equals(Object o) {
	    return (o instanceof Shape) && ((Shape)o).name.equals(name);
	}

	private static HashMap<String,Shape> allShapes = new HashMap<>();

	/** Lists all shapes known to the system so far */
	public Collection<Shape> listAllShapes() {
	    return allShapes.values();
	}
	
	/** Finds an already existing Shape object with a specified name,
	    or creates a new one */
	static synchronized public Shape findShape(String s) {
	    s = s.toUpperCase();
	    Shape c = allShapes.get(s);
	    if (c==null) allShapes.put(s, c=new Shape(s));
	    return c;	    
	}

	static public Shape[] findShapes(String[] v) {
	    Shape[] w = new Shape[v.length];
	    for(int j=0; j<v.length; j++) w[j] = findShape(v[j]);
	    return w;
	}


	
	static final public Shape SQUARE=findShape("SQUARE"), STAR=findShape("STAR"), CIRCLE=findShape("CIRCLE"), TRIANGLE=findShape("TRIANGLE");

	static public final Piece.Shape[] legacyShapes = {SQUARE, STAR, CIRCLE, TRIANGLE};

	/** A human-readable representation of the shape, for use in ASCII graphics */
	public String symbol() {
	    return this==SQUARE? "#":
		this==STAR? "*":
		this==CIRCLE? "O":
		this==TRIANGLE? "T":
		""+name; // .charAt(0);
	}
	
    };
 
    
    /** The shape of a piece */
    /*    public enum Shape {
	SQUARE, STAR, CIRCLE, TRIANGLE;
	// Character to display a piece in ASCII graphics 
	public String symbol() {
	    return this==SQUARE? "#":
		this==STAR? "*":
		this==CIRCLE? "O":
		this==TRIANGLE? "T":
		"?";
	}
	static public final Piece.Shape[] legacyShapes = {SQUARE, STAR, CIRCLE, TRIANGLE};
	}; */
    
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

    @XmlTransient // we return ColorName instead!
    private Color color; 
    private Shape shape;  

    private int x;
    private int y;

    /** Contains null if the piece is still on the board; contains the
	destination bucket number for the piece that have been	removed */
    Integer dropped=null;
    
    public long getId() { return id; }
    @XmlElement 
    public void setId(long _id) { id = _id; }

    /** Exists on IPB objects; null on SC objects */
    private String image;
    public String getImage() { return image; }
    @XmlElement
    public void setImage(String _image) { image = _image; }

    /** @return null for a SC object; the value of the specified property for an IPB object */    
    public String getProperty(String name) {
	ImageObject io=getImageObject();
    	return io==null? null: io.get(name);
    }

    public ImageObject getImageObject() {
	ImageObject x= image==null? null: ImageObject.obtainImageObjectPlain(null, image, false);
	//System.out.println("DEBUG:  getImageObject,image="+image+", return="+x);
	return x;
    }
  
    
    
    @XmlTransient // we return ColorName instead!
    public Color xgetColor() { return color; }

    /** This method is used just for Jersey/REST, to simplify JSON output
	structure, making it similar to that used in Game Engine 1.0 */
    public String getColor() {
	return color==null? getProperty("color"): color.toString();
    }

    /** Used when loading a board from a JSON file */
    //@XmlElement 
    //public void setColor(String x) { color=Piece.Color.findColor(x); }
    /** This is how it used by our JsonToJava, when reading board files */
    public void setColor(Color x) { color=x; }

    /** For JSON */
    public String getShape() {
	return shape==null? getProperty("shape"): shape.toString();
    }
    //    @XmlElement 
    //    public void setShape(String x) { shape=Piece.Shape.findShape(x); }
    /** This is how it used by our JsonToJava */
    public void setShape(Shape x) { shape=x; }
    /** For use in our application */
    public Shape xgetShape() { return shape; }
  
    public String objectType() {
	return (image!=null)? image : "" + color + "_" + shape;
    }

    public int getX() { return x; }
    @XmlElement 
    public void setX(int _x) { x = _x; }

    public int getY() { return y; }
    @XmlElement 
    public void setY(int _y) { y = _y; }

    public Integer getDropped() { return dropped; }
    @XmlElement
    public void setDropped(Integer _dropped) { dropped = _dropped; }

    static private long maxId = 1;
    
    public Piece(){} 
     
    public Piece(//int _id,
		 Shape _shape, Color _color, int _x, int _y) {
	//	id = ""+_id;
	shape = _shape;//.toString().toLowerCase();
	color = _color;//.toString().toLowerCase();
	image = null;
	x = _x;
	y = _y;
	id = (maxId++);
    }

    public Piece(String _image, int _x, int _y) {
	//	id = ""+_id;
	image = _image;
	shape = null;
	color = null;
	x = _x;
	y = _y;
	id = (maxId++);
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

    public String toString() {
	return "["+objectType()+", x="+x+", y="+y+"]";
    }

    /** Converts this Piece, or its ImageObject, to a PropMap object, which stores
	values as String or Integer, rather than as String only.
	This is used in expression evaluation.
     */
    PropMap toPropMap() {
	PropMap pm;
	ImageObject io = getImageObject();
	if (io!=null) {
	    pm = io.toPropMap();
	} else {
	    pm = new  PropMap();
	    pm.put("shape", getShape());
	    pm.put("color", getColor());
	}
	pm.put("pos", pos().num());
	return pm;
    }
    

    
}
