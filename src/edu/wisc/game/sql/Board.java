package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.lang.reflect.*;

import javax.persistence.*;
import jakarta.json.*;

import jakarta.xml.bind.annotation.XmlElement; 
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.openjpa.persistence.jdbc.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.rest.Files;

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
public class Board {
 	
    static public final int N = 6;

    /** Can be used to describe the position of a piece on the [1:N]x[1:N] grid,
	or that of a bucket (at (0,0), ... (N+1,N+1) */
    public static class Pos {
	/** Coordinates counted from the bottom left corner. The left bottom
	    bucket is at (0,0), the corner cell is at (1,1). */
	public final int x, y;


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

	public String toString() {
	    return "(x=" +x+", y=" +y+")";
	}
	
    }

    /** The position number for the point at (x,y) */
    public static int xynum(int x, int y) {
	return (new Pos(x,y)).num();
    }

    /** The positions of the 4 buckets */
    final static public Pos buckets[] = {
	new Pos(0, N+1), 	new Pos(N+1, N+1), 
	new Pos(N+1, 0), 	new Pos(0, 0) };
	

    
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

    /** Creates an empty board */
    public Board() {
	setName("Sample board");
	setId(0);
	value = new Vector<>();
    }

    //    static public RandomRG random = new RandomRG();

    /** This can be called on startup (from main()) if we want to initialize
	the random number generator with a specific seed */
    /*
//    static public void initRandom(long seed) {
//	random = new RandomRG(seed);	
//    }
    */
    
    /** The simple constructor, creates a random board with a given number  
     of pieces, using the 4 legacy colors. */
    public Board(RandomRG random, int randomCnt) {
	setName("Random board with " + randomCnt + " pieces");
	Piece.Shape[] shapes = 	Piece.Shape.legacyShapes;
	Piece.Color[] colors = 	Piece.Color.legacyColors;
	if (randomCnt>N*N) throw new IllegalArgumentException("Cannot fit " + randomCnt + " pieces on an "+ N + " square board!");

	Vector<Integer> w  = random.randomSubsetOrdered(N*N, randomCnt); 
	
	for(int i=0; i<randomCnt; i++) {
	    Pos pos = new Pos(w.get(i)+1);
	    
	    Piece p =new Piece( shapes[random.nextInt(shapes.length)],
				colors[random.nextInt(colors.length)],
				pos.x, pos.y);
	    p.setId(i);
	    value.add(p);
	}
    }

    /** Fills the array results[] with random values from allProps[], ensuring that results will contain exactly nProp distinct values. 
	@param nProp If 0 is given, each object is assigned properties independently from the entire available range; so the resulting scheme may have any number of distinct properties. If non-zero is given, this will be the exact number of distinct values in the resulting scheme.
     */
    private void designatedProps(RandomRG random, Object[] allProps, Object results[], int nProp) {
	if (nProp < 0 || nProp>allProps.length) throw new IllegalArgumentException("Illegal number of values ("+nProp+") to pick out of "+allProps.length);
	final int m = results.length;
	if (nProp==0) {
	    for(int j=0; j<m; j++) {
		int k = random.nextInt(allProps.length);
		results[j] = allProps[k];
	    }
	} else {
	    // each value r[j] will be in the range [0:nProp-1]
	    int[] r = new int[m];
	    for(int j=0; j<m; j++) {
		int k = random.nextInt(nProp);
		r[j] = k;
	    }
	    // change a few values to ensure that every color is present at least once
	    Vector<Integer> placesToChange=random.randomSubsetPermuted(m,nProp);
	    for(int  i=0; i<nProp; i++) {
		r[ placesToChange.get(i)] = i;
	    }

	    // indexes of the colors to use
	    Vector<Integer> shallUse  = 
		random.randomSubsetOrdered(allProps.length,  nProp); 
	    for(int j=0; j<m; j++) {
		results[j] = allProps[ shallUse.get(r[j])];
	    }	    
	}	
    }
    
    
    /** The main constructor for a random initial board in GS 2.*.
	@param random The random number generator to use
	@param randomCnt required number of pieces. 
	@param nShapes required number of shapes. If 0 is passed, there is no restriction (independent decision is made for each piece)
	@param nColors required number of colors. If 0 is passed, there is no restriction (independent decision is made for each piece)
	@param allShapes the set from which shapes are drawn
	@param allColors the set from which colors are drawn
     */
    public Board(RandomRG random, int randomCnt, int nShapes, int nColors, Piece.Shape[] allShapes, Piece.Color[] allColors) {
	setName("Random board with " + randomCnt + " pieces, "+nShapes+" shapes, and " + nColors+" colors");
	if (randomCnt>N*N) throw new IllegalArgumentException("Cannot fit " + randomCnt + " pieces on an "+ N + " square board!");
	if (nShapes<0 || nShapes>allShapes.length) throw new IllegalArgumentException("Invalid number of shapes: " + nShapes);
	if (nColors<0 || nColors>allColors.length) throw new IllegalArgumentException("Invalid number of colors: " + nColors);
	
	Vector<Integer> w  = random.randomSubsetOrdered(N*N, randomCnt); 

	Piece.Shape[] useShapes = new Piece.Shape[randomCnt];
	Piece.Color[] useColors = new Piece.Color[randomCnt];	
	designatedProps(random, allShapes, useShapes, nShapes);
	designatedProps(random, allColors, useColors, nColors);

	
	for(int i=0; i<randomCnt; i++) {
	    Pos pos = new Pos(w.get(i)+1);
	    
	    Piece p = new Piece( useShapes[i], useColors[i],  pos.x, pos.y);
	    p.setId(i);
	    value.add(p);
	}
    }

      /** The main constructor for a random image-and-property-based initial board in GS 3.*.
	@param randomCnt required number of pieces. 
	@param allImages the set from which images are drawn
     */
    public Board(RandomRG random, int randomCnt,
		  ImageObject.Generator imageGenerator
		 //String[] allImages
		 ) {
	setName("Random board with " + randomCnt + " pieces, drawn from "+ imageGenerator.describeBrief());
	if (randomCnt>N*N) throw new IllegalArgumentException("Cannot fit " + randomCnt + " pieces on an "+ N + " square board!");
	
	Vector<Integer> w  = random.randomSubsetOrdered(N*N, randomCnt); 

	
	for(int i=0; i<randomCnt; i++) {
	    Pos pos = new Pos(w.get(i)+1);
	    String image = imageGenerator.getOneKey(random);
	    //allImages[random.nextInt(allImages.length)];
	    Piece p = new Piece(image,  pos.x, pos.y);
	    p.setId(i);
	    value.add(p);
	}
    }
    

    /** Creates a board object to be sent out (as JSON) to the player's client,
	based on the current state of the episode.
	@param pieces The pieces still on the board. (An array of N^2 elements,
	with nulls)
	@param removedPieces If not null, these pieces will also be included
	into the generated Board object, with the flag dropped=true. This is 
	what the GUI client wants.
	@param moveableTo Specifies to which buckets each piece can be moved to.
     */
    public Board(Piece[] pieces, Piece[] removedPieces, BitSet[] moveableTo) {
	
	for(Piece p: pieces) {
	    if (p!=null) {
		BitSet bi = (moveableTo!=null)?  moveableTo[ p.pos().num()]:
		    new BitSet();
		int[] z = new int[bi.cardinality()];
		int k=0;
		for(int i=0; i<bi.length(); i++) {
		    if (bi.get(i)) z[k++] = i;
		}
		p.setBuckets(z);
		value.add(p);
	    }
	}
	if (removedPieces==null) return;
	for(Piece p: removedPieces) {
	    if (p!=null) {
		value.add(p);
	    }
	}
    }


    /** Produces an array of pieces with N*N elements, with nulls
	for empty cells. Used in BoardDisplayService only. */
    public Piece[] asBoardPieces() {
	Piece[] pieces = new Piece[N*N + 1];
	for(Piece p: value) {
	    pieces[p.pos().num()] =p;
	}
	return pieces;
    }

    
    /** We aren't actually using SQL server to store boards, even though we 
	have support for this */
    public long persistNewBoard() {
	this.setId(0); // default value?
	//this.setLongId(0L);
	System.out.println("Creating board: " + name );
	Main.persistObjects(this);
	return this.getId();	    	
    }

    /** Reads a board description from a JSON file */
    public static Board readBoard(File f) throws IOException, ReflectiveOperationException { 
	//System.err.println("ReadBoard " + f);
	return readBoard(new FileReader(f));
    }

    public static Board readBoardFromString(String jsonText) throws IOException, ReflectiveOperationException {
	return readBoard(new StringReader(jsonText));
    }
    
    public static Board readBoard(Reader r) throws IOException, ReflectiveOperationException { 
	//System.err.println("ReadBoard " + f);
	JsonReader jsonReader = Json.createReader(r);
	JsonObject obj = jsonReader.readObject();
	jsonReader.close();

	Board board = new Board();
	JsonToJava.json2java(obj, board);

	//System.err.println("Have imported the board:\n" + 
	// JsonReflect.reflectToJSONObject(board, true));	

	return board;
    }


    /** Checking that a board description does not include any colors
	or shapes that cannot be displayed */
    public void checkShapesAndColors(ColorMap cm) throws IOException {

	HashSet<Long> h = new HashSet<>();
	
	for(Piece p: value) {
	    long id = p.getId();
	    if (h.contains(id)) throw new IOException( "Id="+id+" occurs in more than one game piece. The second piece: "+p);
	    h.add(id);
	    
	    if (p.getImage()!=null) {
		ImageObject io = p.getImageObject();
		if (io==null) throw new IOException( "Cannot load image object named '"+p.getImage()+"' for game piece: "+p);
	    } else {	    
		Piece.Color color =p.xgetColor();
		if (color==null) throw new IOException("Game piece has no color: " + p);
		if (!cm.hasColor(color)) throw new IOException("Color " + color + " is not in the color map");
		Piece.Shape shape =p.xgetShape();
		if (shape==null) throw new IOException("Game piece has no shape: " + p);
		File f = Files.getSvgFile(shape);
		if (!f.canRead())  throw new IOException("For shape "+shape+",  Cannot read shape file: " + f);
	    }
	}
    }
    

    /** Removes the "dropped" field from all game pieces. This method can be used when an already-played board is to
	be reused (e.g. in a PredefinedBoardGameGenerator) 
     */
    public void scrubDropped() {
	for(Piece p: value) {
	    p.setDropped(null);
	}
    }

    public Piece[] toPieceList() {
	Piece[] pieces = new Piece[N*N + 1];
	for(Piece p: getValue()) {
	    Pos pos = p.pos();
	    pieces[pos.num()] = p;
	}
	return pieces;
    }


    
}
