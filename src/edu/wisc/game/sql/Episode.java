package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.json.*;
import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.sql.ImageObject;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.engine.RuleSet.BucketSelector;
import edu.wisc.game.formatter.*;

import javax.xml.bind.annotation.XmlElement; 

/** An Episode is a single instance of a Game played by a person or
    machine with our game server. It describes the current state of
    the game, and has methods for processing player's actions. The
    episode object contains the top-level controls describing the 
    current state of the rule set associated with this episode.
*/
@Entity
public class Episode {

        @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;


    /** This is used to assign episode IDs, which are unique within a
	given server run. The IDs are not meant to be persistent.
    */
    @Basic
    public String episodeId;
    public String getEpisodeId() { return episodeId; }
    public void setEpisodeId(String _episodeId) { episodeId = _episodeId; }
 
    // When this Episode was created
    Date startTime;    
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date _startTime) { startTime = _startTime; }

    // The number of buckets
    static public final int NBU = Board.buckets.length; // 4

    /** A Pick instance describes the act of picking a piece, without 
	specifying its destination */
    public static class Pick {
	/** The position of the piece being moved, in the [1:N*N] range */
	final int pos;
	Pick(int _pos) { pos = _pos; }
	int pieceId = -1;
	public int getPos() { return pos; }
        public int getPieceId() { return pieceId; }	
	Piece piece =  null;
	/** Acceptance code; will be recorded upon processing */
	int code;
 	public int getCode() { return code; }
	Date time = new Date();
	public String toString() {
	    return "PICK " + pos;
	}
    }

    /** A Move instance describes an [attempted] act of picking a piece
	and dropping it into a bucket.
     */
    public static class Move extends Pick {
 	/** (Attempted) destination, in the [0:3] range */
	final int bucketNo;
	public int getBucketNo() { return bucketNo ; }
 	Move(int _pos, int b) {
	    super(_pos);
	    bucketNo = b;
	}
	public String toString() {
	    return "MOVE " + pos + " to B" + bucketNo;
	}
   }
    
     @Transient
    //final
    RuleSet rules;
    /** Only used for Episodes restored from the SQL server, 
	just to keep the GUI client from crashing */
    void setRules(RuleSet _rules) { rules = _rules; }

    
    /** The current board: an array of N*N+1 elements (only positions
	[1..N*N] are used), with nulls for empty cells and non-nulls
	for positions where pieces currently are. */
    @Transient
    private Piece[] pieces = new Piece[Board.N*Board.N + 1];
    /** Pieces are moved into this array once they are removed from the board.
	This is only shown in web UI.
     */
    @Transient
    private Piece[] removedPieces = new Piece[Board.N*Board.N + 1];

    /** The cost of a pick in terms of the cost of a move. EpisodeInfo 
	overrides this method, making use of ParaSet */
    double xgetPickCost() { return 1.0;}

    
    /** The count of all attempts (move and pick) done so far, including successful and unsuccessful ones. */
    int attemptCnt=0;
    /** The total cost of all attempts (move and pick) done so far,
	including successful and unsuccessful ones. If cost_pick!=1,
	this value may be different from attemptCnt. */
    double attemptSpent=0;
    /** All successful moves so far */
    int doneMoveCnt;
    @Transient
    Vector<Pick> transcript = new Vector<>();
  
    /** Set when appropriate */
    boolean stalemate = false;
    boolean cleared = false;
    boolean givenUp = false;
    boolean lost = false;
    
    /** Which bucket was the last one to receive a piece of a given color? */
    @Transient
    private HashMap<Piece.Color, Integer> pcMap = new HashMap<>();
    /** Which bucket was the last  one to receive a piece of a given shape? */
    @Transient
    private HashMap<Piece.Shape, Integer> psMap = new HashMap<>();

     /** Which bucket was the last  one to receive a piece with a given value of each property? (get(propName).get(propValue)==bucket) */
    @Transient
    private HashMap<String, HashMap<String, Integer>> propMap = new HashMap<>();

    private String showPropMap() {
	Vector<String> v = new Vector<>();       
	for(String p: propMap.keySet()) {
	    HashMap<String, Integer> h = propMap.get(p);
	    Vector<String> w = new Vector<>();
	    for(String key: h.keySet()) {
		w.add("("+key+":"+h.get(key)+")");
	    }
	    v.add(p + " -> " + String.join(" ", w));
	}
	return String.join("\n", v);
    }
    
    /** Which bucket was the last one to receive a piece? */
    @Transient
    private Integer pMap=null;

    /** Which row of rules do we look at now? (0-based) */
    @Transient
    protected int ruleLineNo = 0;

    @Transient
    private RuleLine ruleLine = null;

    /** Will return true if this is, apparently, an episode restored
	from SQL server, and cannot be played anymore because the boad
	position and the egine state (ruleLine) is not persisted. This
	is a rare event; it may only become an issue if an episode for
	some reason became persisted before it was finished (this is
	rare; can be caused by cascading e.g. on an "Accept Bonus" event), 
	and then the web app was restarted before the episode
	was finished (this is even rarer).
    */
    boolean isNotPlayable() {
	return ruleLine == null;
    }
    
    
    /** Our interface to the current rule line. It includes the underlying 
	rule line and the current counters which indicate how many times
	the atoms in that line have been used. 	When pieces are removed, this
	structure updates itself, until it cannot pick any pieces anymore. */
    class RuleLine {
	final RuleSet.Row row;
	/** Negative values mean "no restriction" */
	private int ourGlobalCounter;
	private int ourCounter[];

	private String showCounter(int k) {
	    return k<0? "*" : "" + k;
	}

	public String explainCounters() {
	    Vector<String> v = new Vector<>();
	    for(int k:  ourCounter) v.add(showCounter(k));
	    return 
		showCounter(ourGlobalCounter)+ " / " + String.join(",", v);
	}

	
	public String toString() {
	    return "[RL: " + row.toSrc() + " / " + explainCounters() + "]";
	}

	
	RuleLine(RuleSet rules, int rowNo) {
	    if (rowNo < 0 || rowNo >= rules.rows.size()) throw new IllegalArgumentException("Invalid row number");
	    row = rules.rows.get(rowNo);
	    ourGlobalCounter = row.globalCounter;
	    ourCounter = new int[row.size()];
	    for(int i=0; i<ourCounter.length; i++) ourCounter[i] = row.get(i).counter;
	    doneWith = exhausted() || !buildAcceptanceMap();
	    //   	    System.err.println("Constructed "+ this+"; exhausted()=" + exhausted()+"; doneWith =" + doneWith);
	}

	/** This is set once all counters are exhausted, or no more
	    pieces can be picked by this rule. The caller should check
	    this flag after every use, and advance ruleLine */
	boolean doneWith =false;

	/** acceptanceMap[pos][atomNo][dest] if the rule atom[atomNo] 
	    in the current rule line allows moving the piece currently
	    at pos to bucket[dest]. */
	private BitSet[][] acceptanceMap = new BitSet[Board.N*Board.N+1][];
	private boolean[] isMoveable = new boolean[Board.N*Board.N+1];

	/** For each piece, where can it go? (OR of all rules) */
	BitSet[] moveableTo() {
	    BitSet[] q =  new BitSet[Board.N*Board.N+1];
	    for(int pos=0; pos<pieces.length; pos++) {
		q[pos] = new BitSet();
		if (pieces[pos]!=null) {
		    BitSet r = new BitSet(); // to what buckets this piece can go
		    for(BitSet b: acceptanceMap[pos]) {
			q[pos].or(b);
		    }	

		}
	    }
	    return q;	    
	}
	
	/** For each piece currently on the board, find which rules in the 
	    current rule line allow this piece to be moved, and to which buckets.
	    @return true if at least one piece can be moved
	*/
	private boolean buildAcceptanceMap() {

	    EligibilityForOrders eligibleForEachOrder = new EligibilityForOrders();
	    //System.err.println("eligibileForEachOrder=" + eligibleForEachOrder);

	    
	    for(int pos=0; pos<pieces.length; pos++) {
		if (pieces[pos]==null) {
		    acceptanceMap[pos]=null;
		    isMoveable[pos]=false;
		} else {
		    acceptanceMap[pos] = pieceAcceptance(pieces[pos], eligibleForEachOrder);
		    BitSet r = new BitSet(); // to what buckets this piece can go
		    for(BitSet b: acceptanceMap[pos]) {
			r.or(b);
		    }
		    isMoveable[pos]= !r.isEmpty();
		}
	    }
	    return  isAnythingMoveable();
	}

	/** Looks at the current acceptance map to see if any of the 
	    currently present pieces can be moved */
	private boolean isAnythingMoveable() {
	    for(int pos=0; pos<pieces.length; pos++) {
		if (pieces[pos]!=null && isMoveable[pos]) return true;
	    }
	    return false;	
	}

	
	/** Into which buckets, if any, can the specified piece be moved?
	   @return result[j] is the set of buckets into which the j-th
	   rule (atom) allows the specified piece to be moved.
	*/
	private BitSet[] pieceAcceptance(Piece p,  EligibilityForOrders eligibleForEachOrder) {
	    
	    //	    System.err.println("DEBUG: pieceAcceptance(p=" +p+")");
	    if (doneWith) throw new IllegalArgumentException("Forgot to scroll?");

	    if (row.globalCounter>=0 &&  ourGlobalCounter<=0)  throw new IllegalArgumentException("Forgot to set the scroll flag on 0 counter!");
	    
	    // for each rule, the list of accepting buckets
	    BitSet whoAccepts[] = new BitSet[row.size()];
	    Pos pos = p.pos();
	    BucketVarMap  varMap = new  BucketVarMap(p);

	    //System.err.println("varMap=" + varMap);

	    
	    for(int j=0; j<row.size(); j++) {
		whoAccepts[j] = new BitSet(NBU);
		RuleSet.Atom atom = row.get(j);
		if (atom.counter>=0 && ourCounter[j]==0) continue;
		if (!atom.acceptsColorShapeAndProperties(p)) continue;
		//System.err.println("Atom " +j+" shape and color OK");
		if (!atom.plist.allowsPicking(pos.num(), eligibleForEachOrder)) continue;
		//System.err.println("DEBUG: Atom " +j+" allowsPicking ok");
		BitSet d = atom.bucketList.destinations( varMap);
		whoAccepts[j].or(d);
		//System.err.println("pieceAcceptance(p=" +p+"), dest="+d+", whoAccepts["+j+"]=" + 	whoAccepts[j]);
	    }
	    return whoAccepts;
	}


	/** Requests acceptance for this move or pick. In case of
	    acceptance of an actual move (not just a pick), decrements
	    appropriate counters, and removes the piece from the
	    board.
	    @return result  (accept/deny)
	*/
	int accept(Pick pick) {

	    //System.err.println("RL.accept: "+this+", move="+ move);
	    
	    if (doneWith) throw  new IllegalArgumentException("Forgot to scroll?");
	    transcript.add(pick);
	    attemptCnt++;
	    attemptSpent += (pick instanceof Move) ? 1.0: xgetPickCost();

	    pick.piece =pieces[pick.pos];
	    if (pick.piece==null) return pick.code=CODE.EMPTY_CELL;	    
	    pick.pieceId = (int)pick.piece.getId();
	    
	    if (!(pick instanceof Move)) {
		pick.code = isMoveable[pick.pos]? CODE.ACCEPT: CODE.DENY;
		return pick.code;
	    }
	    Move move  = (Move) pick;

	    BitSet[] r = acceptanceMap[pick.pos];
	    
	    Vector<Integer> acceptingAtoms = new  Vector<>();
	    Vector<String> v = new Vector<>();
	    for(int j=0; j<row.size(); j++) {
		if (r[j].get(move.bucketNo)) {
		    v.add("" + j + "(c=" + ourCounter[j]+")");
		    acceptingAtoms.add(j);
		    if (ourCounter[j]>0) {
			ourCounter[j]--;
		    }
		}
	    }
	    
	    //System.err.println("Acceptors: " + String.join(" ", v));
	    if (acceptingAtoms.isEmpty()) return  move.code=CODE.DENY;

	    //--- Process the acceptance -------------------------
	    if (ourGlobalCounter>0) ourGlobalCounter--;

	    doneMoveCnt++;

	    // Remember where this piece was moved
	    if (move.piece.xgetColor()!=null) pcMap.put(move.piece.xgetColor(), move.bucketNo);
	    if (move.piece.xgetShape()!=null) psMap.put(move.piece.xgetShape(), move.bucketNo);

	    ImageObject io = move.piece.getImageObject();
	    if (io!=null) {
		for(String key: io.keySet()) {
		    HashMap<String, Integer> h=propMap.get(key);
		    if (h==null) propMap.put(key,h=new HashMap<>());
		    h.put(io.get(key), move.bucketNo);
		}
	    }
	    //System.out.println("DEBUG: propMap=\n" + showPropMap());
	    
	    pMap = move.bucketNo;

	    pieces[move.pos].setBuckets(new int[0]); // empty the bucket list for the removed piece
	    removedPieces[move.pos] = pieces[move.pos];
	    removedPieces[move.pos].setDropped(move.bucketNo);
	    pieces[move.pos] = null; // remove the piece
	    //System.err.println("Removed piece from pos " + move.pos);
		    
	    // Check if this rule can continue to be used, and if so,
	    // update its acceptance map
	    doneWith = exhausted() || !buildAcceptanceMap();
	    //System.err.println("doneWith=" + doneWith);
	    
	    //System.err.println("Episode.accept: move accepted. card=" + onBoard().cardinality());
	    cleared = (onBoard().cardinality()==0);
	    
	    //	    Logging.info("Accepted, return " + CODE.ACCEPT);
	    return  move.code=CODE.ACCEPT;
	}

	/** Is this row of rules "exhausted", based either on the
	    global counter for the row, or the individual rules?
	    "Control moves to the next row when either all counters OR
	    the global counter reach zero", as per Paul's specs.
	*/
	boolean exhausted() {
	    if (row.globalCounter>=0 && ourGlobalCounter==0) return true;
	    for(int j=0; j<row.size(); j++) {
		if (row.get(j).counter<0) return false; // "*" cannot be exhaustd
		if (ourCounter[j]>0) return false;
	    }
	    return true;
	}
	
    }
    
  
    
    /** Contains the values of various variables that may be used in 
	finding the destination buckets for a given piece */
    class BucketVarMap extends HashMap<String, HashSet<Integer>> {

	/** @param key A variable name, such as "p", "pc", "ps", or "propName.propValue" */
	private void pu( String /*BucketSelector*/ key, int k) {
	    HashSet<Integer> h = new  HashSet<>();
	    h.add(k);
	    put(key/*.toString()*/, h);
	}
	
	/** Puts together the values of the variables that may be used in 
	    finding the destination buckets */
	BucketVarMap(Piece p) {
	    Integer z = (p.xgetColor()==null)? null: pcMap.get(p.xgetColor());
	    if (z!=null) pu(BucketSelector.pc.toString(), z);
	    z = (p.xgetShape()==null)? null: psMap.get(p.xgetShape());
	    if (z!=null) pu(BucketSelector.ps.toString(), z);
	    if (pMap!=null) pu(BucketSelector.p.toString(), pMap);
	    ImageObject io = p.getImageObject();
	    if (io!=null) {
		for(String key: io.keySet()) {
		    String val = io.get(key);
		    z = (propMap.get(key)==null)?null:propMap.get(key).get(val);
		    if (z!=null) pu("p."+key, z);
		}
	    }
	    Pos pos = p.pos();
	    put(BucketSelector.Nearby.toString(), pos.nearestBucket());
	    put(BucketSelector.Remotest.toString(), pos.remotestBucket());
	    //System.out.println("DEBUG: For piece="+p+ ", BucketVarMap=" + this);
	}


	public String toString() {
	    Vector<String> v = new Vector<>();
	    for(String key: keySet()) {
		Vector<String> w = new Vector<>();
		for(int z:  get(key)) w.add("" + z);
		v.add("["+key+":"+ Util.join(",", w)+ "]");
	    }
	    return String.join(" ", v);
	}
	
    }

    @Transient
    private OutputMode outputMode;
    @Transient
    private final PrintWriter out;
    @Transient
    private final Reader in;
   
    static final DateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    /** with milliseconds */
    static final DateFormat sdf2 = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");

    /** Creates a more or less unique string ID for this Episode object */
    private String buildId() {
	String s = sdf.format(startTime) + "-";
	for(int i=0; i<6; i++) {
	    int k =  Board.random.nextInt(10 + 'Z'-'A'+1);
	    char c = (k<10) ? (char)('0' + k) : (char)('A' + k-10);
	    s += c;
	}
	return s;
    }

    /** Dummy constructor; only used for error code production, and maybe
	also by JPA when restoring a player's info (with all episodes)
	from the database.
    */
    public Episode() {
	episodeId=null;
	rules=null;
	out=null;
	in=null;
	nPiecesStart = 0;
	startTime = new Date();
    };

    /** The initial number of pieces on the board */
    @Basic
    private int nPiecesStart;
    public int getNPiecesStart() { return nPiecesStart; }
    public void setNPiecesStart(int _nPiecesStart) { nPiecesStart = _nPiecesStart; }


    /** Creates a new Episode for a given Game (which defines rules and the 
	properties of the initial board). 
	@param _in The input stream for commands; it will be null in the web app
	@param _out Will be null in the web app.
    */
    public Episode(Game game, OutputMode _outputMode, Reader _in, PrintWriter _out) {
	startTime = new Date();    
	in = _in;
	out = _out;
	outputMode = _outputMode;
	episodeId = buildId();
	
	rules = game.rules;
	Board b =  game.initialBoard;
	if (b==null) {
	    if (game.allImages!=null) {
		b = new Board( game.randomObjCnt,  game.allImages);
	    } else { 
		b = new Board( game.randomObjCnt, game.nShapes, game.nColors, game.allShapes, game.allColors);
	    }
	}
	nPiecesStart = b.getValue().size();
	for(Piece p: b.getValue()) {
	    Pos pos = p.pos();
	    pieces[pos.num()] = p;
	}
	doPrep();
	
    }

    /** Return codes for the /move and /display API web API calls,
	and for the MOVE command in the captive game server.
     */
    public static class CODE {
	public static final int
	/** move accepted and processed */
	    ACCEPT = 0,
	/** Move rejected, and no other move is possible
	    (stalemate). This means that the rule set is bad, and we
	    owe an apology to the player */
	    STALEMATE=2,
	/** move rejected, because there is no piece in the cell */
	    EMPTY_CELL= 3,
	/** move rejected, because this destination is not allowed */
	    DENY = 4,
	/** Exit requested */
	    EXIT = 5,
	/** New game requested */
	    NEW_GAME = 6
	    ;
	
	public static final int
	    INVALID_COMMAND= -1,
	    INVALID_ARGUMENTS= -2,
	    INVALID_POS= -3,
	// No game is on now. Start a game first!
	    NO_GAME = -4,
	// Used in socket server GAME  command
	    INVALID_RULES = -5,
	// Used in web app, when trying to access a non-existent episode
	    NO_SUCH_EPISODE = -6,
	// The number of preceding attempts does not match. This may indicate
	// that some HTTP requests have been lost, or a duplicate request
	    ATTEMPT_CNT_MISMATCH = -7,
	// This code is returned on successful DISPLAY calls, to
	// indicate that it was a display (no actual move requested)
	// and not a MOVE	    
	    JUST_A_DISPLAY = -8;
	    
    }

    public static class FINISH_CODE {
	public static final int
	/** there are still pieces on the board, and some are moveable */
	    NO = 0,
	/** no pieces left on the board */
	    FINISH = 1,
	/** there are some pieces on the board, but none can be moved anymore */
	    STALEMATE=2,
	/** The player has said he does not want to play any more. This
	    may only happen in some GUI versions. */
	    GIVEN_UP =3,
	/** This was a bonus round which has been terminated by the 
	    system because the player has failed to complete it within the 
	    required number of steps */
	    LOST = 4
	    ;
    }

    /** Creates a bit set with bits set in the positions where there are
	pieces */
    private BitSet onBoard() {
	BitSet onBoard = new BitSet(Board.N*Board.N+1);
	for(int i=0; i<pieces.length; i++) {
	    if (pieces[i]!=null) onBoard.set(i);
	}
	return onBoard;
    }

    @Transient
    private boolean prepReady = false;

    
    /** At present, which pieces are eligible for picking under each of
	the existing orders? This structure needs to be updated every time
	a piece is removed from the board.
    */
    private class EligibilityForOrders extends HashMap<String, BitSet>  {
	/** Finds pieces eligible for pick up under each orders based on
	    the current board content */
	void update() {
	    clear();
	    // Which pieces may be currently picked under various ordering schemes?
	    for(String name: rules.orders.keySet()) {
		Order order = rules.orders.get(name);
		BitSet eligible = order.findEligiblePieces(onBoard());
		put(name,  eligible);
	    }
	}

	EligibilityForOrders() {
	    super();
	    update();			
	}

	public String toString() {
	    Vector<String> v= new Vector<>();
	    for(String key: keySet()) {
		v.add("Eli(" + key+")="+ get(key));
	    }
	    return "[Eligibility: "+String.join("; " , v)+"]";
	}
	
    }


    /** Run this method at the beginning of the game, and
	every time a piece has been removed, to update various
	auxiliary data structures.
	@return true, unless stalemeate (no piece can be picked) is
	detected, in which case it return false
    */
    private boolean doPrep() {

	// Which pieces currently on the border can be picked under
	// various ordering schemes?
	//	eligibleForEachOrder.update();
	//	System.err.println("doPrep: eligibileForEachOrder=" + eligibleForEachOrder);

	boolean mustUpdateRules = (ruleLine==null || ruleLine.doneWith);

	//	System.err.println("doPrep: mustUpdateRules=" + mustUpdateRules +", ruleLineNo="+ ruleLineNo);


	
	if (mustUpdateRules) {
	    ruleLineNo= (ruleLine==null) ? 0: (ruleLineNo+1) %rules.rows.size();
	    ruleLine = new RuleLine(rules, ruleLineNo);
	    //System.err.println("doPrep: updated ruleLineNo="+ ruleLineNo+", doneWith=" + ruleLine.doneWith);

	    final int no0 = ruleLineNo;
	    // if the new rule is exhausted, or allows no pieces to be moved,
	    // scroll on
	    //	    System.out.println("# BAM(line " + ruleLineNo + ")");
	    while( ruleLine.doneWith) {
		 ruleLineNo=  (ruleLineNo+1) %rules.rows.size();
		 //		 System.err.println("# BAM(scroll to line " + ruleLineNo + ")");
		 // If we have checked all lines, and no line contains
		 // a rule that can move any piece, then it's a stalemate
		 if (ruleLineNo==no0) {
		     stalemate=true;
		     return false;
		 }
		 ruleLine = new RuleLine(rules, ruleLineNo);
	    }
	}
	
	prepReady = true;
	return true;
    }

    /** The last pick or move (successful or failed attempt) */
    @Transient
    private Pick lastMove = null;
    
    private int accept(Pick move) {
	lastMove = move;
	if (stalemate) {
	    return CODE.STALEMATE;
	}
	if (move.pos<1 || move.pos>=pieces.length) return CODE.INVALID_POS;

	int code = ruleLine.accept(move);

	// Update the data structures describing the current rule line, acceptance, etc
	if (move instanceof Move && code==CODE.ACCEPT && !cleared && !stalemate && !givenUp) {
	    doPrep();
	}
	
	return code;
    }

    /** The basic mode tells the player where all movable pieces are, 
	but EpisodeInfor will override it if the para set mandates "free" mode.
     */
    boolean weShowAllMovables() {
	return true;
    }
 

    private static HTMLFmter fm = new HTMLFmter(null);


    /** Graphic display of the board */
    public String graphicDisplay() {
	return graphicDisplay(false);
    }
    
    public String graphicDisplay(boolean html) {

	if (isNotPlayable() || !html) return graphicDisplayAscii(html);

	String result="";

	String s = 
	    fm.wrap("li", "(X) - a movable piece" +
		     (!weShowAllMovables()? " (only marked on the last touched piece)": "")) +
	    fm.wrap("li","[X] - the position to which the last move or pick attempt (whether successful or not) was applied");
	result += fm.para( "Notation: " + fm.wrap("ul",s));
	
	ColorMap cm = new ColorMap();
 	
	Vector<String> rows = new Vector<>();
	
	Vector<String> v = new Vector<>();
	v.add(fm.td(""));
	for(int x=1; x<=Board.N; x++) v.add(fm.td("align='center'", "" + x));
	String topRow = fm.tr(String.join("", v));
	rows.add(topRow);
	
	
	for(int y=Board.N; y>0; y--) {
	    v.clear();
	    v.add(fm.td(""+y));

	    for(int x=1; x<=Board.N; x++) {
		int pos = (new Pos(x,y)).num();
		String sh = "BLANK";
		String hexColor = "#FFFFFF";
		ImageObject io = null;
		
		if (pieces[pos]!=null) {
		    Piece p = pieces[pos];
		    io = p.getImageObject();		    
		    sh = (io!=null) ? io.key : p.xgetShape().toString();
		    hexColor = "#"+ (io!=null? "FFFFFF" : cm.getHex(p.xgetColor(), true));
		}
		
		String z = "<img width='80' src=\"../../GetImageServlet?image="+sh+"\">";
		//z = (lastMove!=null && lastMove.pos==pos) ?    "[" + z + "]" :
		//    ruleLine.isMoveable[pos]?     "(" + z + ")" :
		//    "&nbsp;" + z + "&nbsp;";

		boolean isLastMovePos =  (lastMove!=null && lastMove.pos==pos);
		boolean padded=true;
		
		if (ruleLine.isMoveable[pos] && (weShowAllMovables() || isLastMovePos)) {
		    z="(" + z + ")";
		    padded=true;
		}

		if (isLastMovePos) {
		    z="[" + z + "]";
		    padded=true;
		}

		if (!padded) z = "&nbsp;" + z + "&nbsp;";
		String td = (io!=null)?
		    fm.td( z):
		    fm.td("bgcolor=\"" + hexColor+"\"", z);
		v.add(td);
	    }
	    rows.add(fm.tr(String.join("", v)));
	}
	rows.add(topRow);
	result+= fm.table("border='1'", rows);
	return result; 
    }

    /** Retired from the web game server; still used in Captive Game Server. */
      public String graphicDisplayAscii(boolean html) {

	if (isNotPlayable()) {
	    return "This episode must have been restored from SQL server, and does not have the details necessary to show the board";
	}
	
	Vector<String> w = new Vector<>();

	String div = "#---+";
	for(int x=1; x<=Board.N; x++) div += "-----";
	w.add(div);
	
	
	for(int y=Board.N; y>0; y--) {
	    String s = "# " + y + " |";
	    for(int x=1; x<=Board.N; x++) {
		int pos = (new Pos(x,y)).num();
		String z = html? "." :   " .";
		if (pieces[pos]!=null) {
		    Piece p = pieces[pos];
		    ImageObject io = p.getImageObject();
		    z = (io!=null)? io.symbol() :  p.xgetShape().symbol();
		    if (html) {
			String color =  p.getColor();
			if (color!=null) z=fm.colored( color.toLowerCase(), z);
			z = fm.wrap("strong",z);
		    } else {
			Piece.Color color = p.xgetColor();
			if (color!=null) z = color.symbol() + z;
		    }
		}

		z = (lastMove!=null && lastMove.pos==pos) ?    "[" + z + "]" :
		    ruleLine.isMoveable[pos]?     "(" + z + ")" :
		    " " + z + " ";

		s += " " + z;
	    }
	    w.add(s);
	}
	w.add(div);
	String s = "#   |";
	for(int x=1; x<=Board.N; x++) s += "  "+(html?"": " ") + x + " ";
	w.add(s);
	return String.join("\n", w);
    }

    int getFinishCode() {
	 return cleared? FINISH_CODE.FINISH :
	     stalemate? FINISH_CODE.STALEMATE :
	     givenUp?  FINISH_CODE.GIVEN_UP :
	     lost?  FINISH_CODE.LOST :
	     FINISH_CODE.NO;
    }
       

    private void respond(int code, String msg) {
	String s = "" + code + " " + getFinishCode() +" "+attemptCnt;
	if (msg!=null) s += "\n" + msg;
	out.println(s);
    }    
    

    /** Returns the current board, or, on a restored-from-SQL-server episodes,
	null (or empty board, to keep the client from crashing).
    */
    Board getCurrentBoard(boolean showRemoved) {
	if (isNotPlayable()) {
	    return cleared? new Board() : null;
	} else {
	    return new Board(pieces,
			     (showRemoved?removedPieces:null),
			     ruleLine.moveableTo());
	}
    }

    /** Shows the current board (without removed [dropped] pieces) */
    public Board getCurrentBoard() {
	return getCurrentBoard(false);
    }

    /** No need to show this field */
    private static final HashSet<String> excludableNames =  Util.array2set("dropped");
    
    private String displayJson() {
	Board b = getCurrentBoard();
	JsonObject json = JsonReflect.reflectToJSONObject(b, true, excludableNames);
	return json.toString();
    }

    public static final String version = "3.001";

    private String readLine( LineNumberReader​ r) throws IOException {
	out.flush();
	return r.readLine();
    }

    /** Can be sent to the web client in JSON format, where it would 
	be used to display the current state of the episode */
    public class Display     {
	// The following describe the state of this episode, and are only used in the web GUI
	int finishCode = Episode.this.getFinishCode();
	Board board =  getCurrentBoard(true);

	/** What pieces are on the board now, and what pieces have been removed */
        public Board getBoard() { return board; }
        @XmlElement
        public void setBoard(Board _b) { board = _b; }

	/** Is this episode still continues (code 0), has stalemated (2), or has the board been cleared (4)? */
	public int getFinishCode() { return finishCode; }
    
	int code;
	String errmsg;

	/** A kludgy attempt to get the client not to display the board when
	    the episode is not playable */
	boolean error=false;
	public boolean getError() { return error; }
	@XmlElement
	public void setError(boolean _error) { error = _error; }
 
	
	/** On a /move call: Has this move been accepted or rejected? (When returned by /display response, the value is -8). */
        public int getCode() { return code; }
        @XmlElement
        public void setCode(int _code) { code = _code; }
	/** The error or debug messsage, if any */
        public String getErrmsg() { return errmsg; }
        @XmlElement
        public void setErrmsg(String _msg) { errmsg = _msg; }

       	int numMovesMade = Episode.this.attemptCnt;
	/** How many move attempts (successful or not) has been made so far */
        public int getNumMovesMade() { return numMovesMade; }
        @XmlElement
        public void setNumMovesMade(int _numMovesMade) { numMovesMade = _numMovesMade;}
	
	private Vector<Pick> transcript =  Episode.this.transcript;
	/** The list of all move attempts (successful or not) done so far
	    in this episode */
	public Vector<Pick> getTranscript() { return transcript; }

	RuleSet.ReportedSrc rulesSrc = (rules==null)? null:rules.reportSrc();
	/** A structure that describes the rules of the game being played in this episode. */
	public RuleSet.ReportedSrc getRulesSrc() { return rulesSrc; }

	
	String explainCounters =
	    Episode.this.ruleLine==null? null:
	    Episode.this.ruleLine.explainCounters();
	/** The "explanation" of the current state of the current rule line */
	public String getExplainCounters() { return explainCounters; }
	

	int ruleLineNo = Episode.this.ruleLineNo;
	/** Zero-based position of the line of the rule set that the
	    game engine is currently looking at. This line will be the
	    first line the engine will look at when accepting the
	    player's next move. */
	public int getRuleLineNo() { return ruleLineNo; }


	public Display(int _code, 	String _errmsg) {
	    code = _code;
	    errmsg = _errmsg;
	}
    }

    /** Builds a Display objecy to be sent out over the web UI upon a /display
	call (rather than a /move or /pick) */
    public Display mkDisplay() {
    	return new Display(Episode.CODE.JUST_A_DISPLAY, "Display requested");
    }

    /** Checks for errors in some of the arguments of a /pick or /move call.
	@return a Display reporting an error, or null if no error has been found
    */
    private Display inputErrorCheck1(int y, int x, int _attemptCnt) {
	if (isCompleted()) {
	    return new Display(CODE.NO_GAME, "No game is on right now (cleared="+cleared+", stalemate="+stalemate+"). Use NEW to start a new game");
	}

	if (_attemptCnt != attemptCnt)  return new Display(CODE.ATTEMPT_CNT_MISMATCH, "Given attemptCnt="+_attemptCnt+", expected " + attemptCnt);
		
	if (x<1 || x>Board.N) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: column="+x+" out of range");
	if (y<1 || y>Board.N) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: row="+y+" out of range");
	return null;
    }
    
    /** Evaluate a pick attempt */
    public Display doPick(int y, int x, int _attemptCnt) throws IOException {
	Display errorDisplay =inputErrorCheck1(y, x, _attemptCnt);
	if (errorDisplay!=null) return errorDisplay;
	Pos pos = new Pos(x,y);
	Pick move = new Pick( pos.num());
	int code = accept(move);
	return new Display(code, mkDisplayMsg());
    }
    
    /** Evaluate a move attempt */
    public Display doMove(int y, int x, int by, int bx, int _attemptCnt) throws IOException {
	Display errorDisplay =inputErrorCheck1(y, x, _attemptCnt);
	if (errorDisplay!=null) return errorDisplay;
	if (bx!=0 && bx!=Board.N+1) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: bucket column="+bx+" is not 0 or "+(Board.N+1));
	if (by!=0 && by!=Board.N+1) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: bucket row="+by+" is not 0 or "+(Board.N+1));

	Pos pos = new Pos(x,y), bu =  new Pos(bx, by);
	int buNo=bu.bucketNo();
	if (buNo<0 || buNo>=Board.buckets.length) {
	    return new Display(CODE.INVALID_ARGUMENTS, "Invalid bucket coordinates");
	}
	Move move = new Move(pos.num(), buNo);
	int code = accept(move);
	return new Display(code, mkDisplayMsg());
    }

    /** A message to put along with the accept/deny code into the Display object */
    private String mkDisplayMsg() {
    	return 
	    (outputMode==OutputMode.BRIEF) ? null:
	    cleared?  "Game cleared - the board is clear" :
	    stalemate?  "Stalemate - no piece can be moved any more. Apology for these rules!" :
	    givenUp? "You have given up this episode" :
	    null;
    }
    
    /** Lets this episode play out until either all pieces are cleared, or
	a stalemate is reached, or the player gives up (sends an EXIT or NEW command)
	@return true if another game is requested */
    public boolean playGame(int gameCnt) throws IOException {
	try {
	String msg = "# Hello. This is Captive Game Server ver. "+version+". Starting a new game (no. "+gameCnt+")";
	if (stalemate) {
	    respond(CODE.STALEMATE, msg + " -- immediate stalemate. Our apologies!");
	} else {
	    respond(CODE.NEW_GAME, msg);
	}
	out.println(displayJson());
	if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
	
	LineNumberReader​ r = new LineNumberReader​(in);
	String line = null;
	while((line=readLine(r))!=null) {
	    line = line.trim();
	    if (line.equals("")) continue;
	    Vector<Token> tokens;
	    try {
		tokens    = Token.tokenize(line);
	    } catch(RuleParseException ex) {
		respond(CODE.INVALID_COMMAND,"# Invalid input - cannot parse");
		continue; 		
	    }
	    if (tokens.size()==0 || tokens.get(0).type!=Token.Type.ID) {
		respond(CODE.INVALID_COMMAND, "# Invalid input");
		continue;
	    }
	    String cmd = tokens.get(0).sVal.toUpperCase();
	    if (cmd.equals("EXIT")) {
		respond(CODE.EXIT, "# Goodbye");
		return false;
	    } else if (cmd.equals("VERSION")) {
		out.println("# " + version);
	    } else if (cmd.equals("NEW")) {
		return true;
	    } else if (cmd.equals("HELP")) {		
		out.println("# Commands available:");
		out.println("# MOVE row col bucket_row bucket_col");
		out.println("# NEW");
		out.println("# DISPLAY");
		out.println("# DISPLAYFULL");
		out.println("# MODE <BRIEF|STANDARD|FULL>");
		out.println("# EXIT");
	    } else if (cmd.equals("DISPLAY")) {
		out.println(displayJson());
		if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
	    } else if (cmd.equals("DISPLAYFULL")) {
		out.println(displayJson());
		out.println(graphicDisplay());
	    } else if (cmd.equals("MODE")) {
		tokens.remove(0);
		if (tokens.size()!=1 || tokens.get(0).type!=Token.Type.ID) {
		    respond(CODE.INVALID_ARGUMENTS, "# MODE command must be followed by the new mode value");
		    continue;
		}
		String s = tokens.get(0).sVal.toUpperCase();
		try {
		    outputMode= Enum.valueOf( OutputMode.class, s);
		} catch(IllegalArgumentException ex) {
		    respond(CODE.INVALID_ARGUMENTS, "# Not a known mode: " + s);
		    continue;
		}
		out.println("# OK, mode=" + outputMode);
	    } else if (cmd.equals("MOVE")) {
		
		tokens.remove(0);
		if (tokens.size()!=4) {
		    respond(CODE.INVALID_ARGUMENTS, "# Invalid input");
		    continue;
		}
		int q[] = new int[4];
		// y x By Bx

		boolean invalid=false;
		for(int j=0; j<4; j++) {
		    if (tokens.get(j).type!=Token.Type.NUMBER) {
			respond(CODE.INVALID_ARGUMENTS, "# Invalid input: "+tokens.get(j));
			invalid=true;
			break;
		    }
		    q[j] = tokens.get(j).nVal;

		}
		if (invalid) continue;

		Display mr = doMove(q[0], q[1], q[2], q[3], attemptCnt);
		respond(mr.code, "# " + mr.errmsg);
		if (outputMode!=OutputMode.BRIEF) out.println(displayJson());
		if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
		
	    } else {
		respond(CODE.INVALID_COMMAND, "# Invalid command: " +cmd);
		continue;
	    }
	}
	} finally {
	    out.flush();
	}
	return false;
    }

    public enum OutputMode {
	// One-line response on MOVE; no comments
	BRIEF,
	// Two-line response on MOVE; no comments
	STANDARD,
	// Two-line response on MOVE; human-readable comments
	FULL}; 

    /** @return true if this episode cannot accept any more move attempts,
	for any reason (board cleared, stalemate, given up, lost).
     */
    boolean isCompleted() {
	return cleared || stalemate || givenUp || lost;
    }

    /** Marks this episode as "given up" (unless it's already marked
	as completed in some other way) */
    void giveUp() {
	if (!isCompleted()) givenUp = true;
    }

   /** Let's just write one file at a time */
    static final String file_writing_lock = "Board file writing lock";
	
    /* Saves all the recorded moves (the transcript of the episode) into a CSV file.
       <pre>
       transcripts/pid.transcript.csv
      pid,episodeId,moveNo,y,x,by,bx,code
</pre>
    */    
    void saveTranscriptToFile(String pid, String eid, File f) {
	synchronized(file_writing_lock) {
	try {	    
	    PrintWriter w = new PrintWriter(new	FileWriter(f, true));
	    if (f.length()==0) w.println("#pid,episodeId,moveNo,timestamp,y,x,by,bx,code");
	    Vector<String> v = new Vector<>();
	    int k=0;
	    for(Pick move: transcript) {
		v.clear();
		v.add(pid);
		v.add(eid);
		v.add(""+(k++));
		v.add( sdf2.format(move.time));
		Board.Pos q = new Board.Pos(move.pos);
		v.add(""+q.y);
		v.add(""+q.x);
		if (move instanceof Move) { // a real move with a destination
		    Move m = (Move)move;
		    Board.Pos b = Board.buckets[m.bucketNo];
		    v.add(""+b.y);
		    v.add(""+b.x);
		} else { // just a pick -- no destination
		    v.add("");
		    v.add("");
		}
		v.add(""+move.code);
		w.println(String.join(",", v));
	    }
	    w.close();
	} catch(IOException ex) {
	    System.err.println("Error writing the transcript: " + ex);
	    ex.printStackTrace(System.err);
	}	    
	}  
    }

        /** Concise report, handy for debugging */
    public String report() {
	return "["+episodeId+"; FC="+getFinishCode()+
	    " " +
	    attemptCnt + "/"+getNPiecesStart()  +
	    "]";
    }
    

}
