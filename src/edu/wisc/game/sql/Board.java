package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import javax.persistence.*;

import org.apache.openjpa.persistence.jdbc.*;

import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement;


import edu.wisc.game.util.*;

@XmlRootElement(name = "board") 

/** Represents an initial board. 

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
public class Board extends OurTable {
 	
    static public final int N = 6;

    /** Can be used to describe the position of a piece on the [1:N]x[1:N] grid,
	or that of a bucket (at (0,0), ... (N+1,N+1) */
    public static class Pos {
	/** Coordinates counted from the bottom left corner. The left bottom
	    bucket is at (0,0), the corner cell is at (1,1). */
	final int x, y;


	/** Counted by row (left-to-right), rows being arranged bottom-to-top.
	    In other words, the lexicoraphic order for the (y,x) pairs. The
	    ordering for cells is 1-based.
	*/
	public int num() {
	    if (x<1 || x>Board.N) throw new IllegalArgumentException("x out of range");
	    if (y<1 || y>Board.N) throw new IllegalArgumentException("y out of range");
	    return x + (y-1)*Board.N;
	}
	/** Converts the coordinates of a bucket to a bucket number. 
	    @return the bucket number, or -1 if the coordinates are not valid */
	public int bucketNo() {
	    return (y==Board.N+1)?	(x==0? 0 : x==Board.N+1? 1: -1) :
		 (y==0)?	(x==0? 3 : x==Board.N+1? 2: -1) :
		-1;		
	}

	public Pos(int _x, int _y) { x = _x; y=_y; }
	
	/** @param num in the [0 .. N*N-1] range */
	public Pos(int num) {
	    if (num<1 || num>Board.N*Board.N) throw new IllegalArgumentException("num (="+num+") out of range");
	    y = (num-1)/Board.N + 1;
	    x = (num-1)%Board.N + 1;
	}

	/** Square of 2-norm distance */
	public int norm2sq(Pos q) {
	    int dx=q.x-x, dy=q.y-y;
	    return dx*dx + dy*dy;
	}

	/** 1-norm distance */
	public int norm1(Pos q) {
	    int dx=q.x-x, dy=q.y-y;
	    return Math.abs(dx) + Math.abs(dy);
	}

	/** Mirror image of this cell into the bottom left corner */
	public Pos flip2corner() {
	    return new Pos( (2*x > N+1) ? (N+1)-x : x,
			    (2*y > N+1) ? (N+1)-y : y);
	}

	/** Which bucket(s) is/are the nearest to this cell? */
	public HashSet<Integer> nearestBucket() {
	    HashSet<Integer> h = new  HashSet<>();
	    if (2*x <= N+1) {
		if (2*y >= N+1) h.add(0);
		if (2*y <= N+1) h.add(3);
	    }
	    if (2*x >= N+1) {
		if (2*y >= N+1) h.add(1);
		if (2*y <= N+1) h.add(2);
	    }
	    return h;
	}
	/** Which bucket(s) is/are the most remote from this cell? */
	public HashSet<Integer> remotestBucket() {
	    Pos q = new Pos(N+1-x, N+1-y);
	    return q.nearestBucket();
	}
	
    }

    /** The position number for the point at (x,y) */
    public static int xynum(int x, int y) {
	return (new Pos(x,y)).num();
    }

    /** The positions of the 4 buckets */
    final static public Pos buckets[] = {
	new Pos(0, N), 	new Pos(N, N), 
	new Pos(N, 0), 	new Pos(0, 0) };
	

    
    @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;
    @Basic 
    private String name;
    // See examples in    https://vladmihalcea.com/the-best-way-to-map-a-onetomany-association-with-jpa-and-hibernate/
    //@OneToMany(cascade={CascadeType.ALL},        orphanRemoval = true)
    //@JoinColumn(name = "board_id")


     @OneToMany(
        mappedBy = "board",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
	fetch = FetchType.EAGER)
    
      private Vector<Piece> value = new  Vector<>();

     public void addPiece(Piece c) {
        value.add(c);
        c.setBoard(this);
    }
 
    public void removePiece(Piece c) {
        value.remove(c);
        c.setBoard(null);
    }

   public long getId() { return id; }
   @XmlElement
   public void setId(long _id) { id = _id; }

    /*     public String getId() { return id; }
 @XmlElement
 public void setId(String _id) { id = _id; } */
    public String getName() { return name; }
    @XmlElement
    public void setName(String _name) { name = _name; }
    public Vector<Piece> getValue() { return value; }
    @XmlElement
    public void setValue(Vector<Piece> _value) {
	//value = _value;
	for(Piece p: value) p.setBoard(null);
	value.setSize(0);
	for(Piece p: _value) addPiece(p);
    }

    public Board() {
	setName("Sample board");
	setId(0);
	value = new Vector<>();
    }

    static public RandomRG random = new RandomRG();
    
    public Board(int randomCnt) {
	setName("Random board with " + randomCnt + " pieces");
	Piece.Shape[] shapes = 	Piece.Shape.values();
	Piece.Color[] colors = 	Piece.Color.values();
	if (randomCnt>N*N) throw new IllegalArgumentException("Cannot fit " + randomCnt + " pieces on an "+ N + " square board!");

	Vector<Integer> w  = random.randomSubsetOrdered(N*N, randomCnt); 
	
	for(int i=0; i<randomCnt; i++) {
	    Pos pos = new Pos(w.get(i)+1);
	    
	    value.add( new Piece( random.getEnum(Piece.Shape.class),
				  random.getEnum(Piece.Color.class),
				  pos.x, pos.y));
	}
    }

    /** Only used for JSON, not for persistence */
    public Board(Piece[] pieces, BitSet[] moveableTo) {
	for(Piece p: pieces) {
	    if (p!=null) {
		BitSet bi = moveableTo[ p.pos().num()];
		int[] z = new int[bi.cardinality()];
		int k=0;
		for(int i=0; i<bi.length(); i++) {
		    if (bi.get(i)) z[k++] = i;
		}
		p.setBuckets(z);
		value.add(p);
	    }
	}
    }

    
    /** Find the matching record for a board object
	@return The Article object with the matching name, or null if none is found */
    /*
@SuppressWarnings("unchecked")
    public static  findByAid( EntityManager em, String _aid) {

	Role r = (Role)em.find(Role.class, name.toString());
	
    
	Query q = em.createQuery("select m from Article m where m.aid=:c");
	q.setParameter("c", _aid);
	List<Article> res = (List<Article>)q.getResultList();
	if (res.size() != 0) {
	    return  res.iterator().next();
	} else {
	    return null;
	}
    }   
    */

    public long persistNewBoard() {
	EntityManager em = Main.getEM();
	try {

	    em.getTransaction().begin();
	    this.setId(0); // default value?
	    //this.setLongId(0L);
	    System.out.println("Creating board: " + name );
	    em.persist(this);
	    em.getTransaction().commit();

	    return this.getId();
	    /*
	    Board b1 = (Board)em.find(Board.class, id);
	    if (b1==null) {
		// FIXME: must return an error message of some kind...
		return null;
	    } else {
		return b1;
	    }
	    */

	    
	} finally {
	    try {em.getTransaction().commit();} catch (Exception _e) {}
	    em.close();
	}
	
    }

    
    static public void main(String[] argv) throws IOException {
   }

 

    
}
