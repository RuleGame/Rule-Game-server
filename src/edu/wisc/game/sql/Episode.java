 package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import jakarta.json.*;
import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.engine.RuleSet.BucketSelector;
import edu.wisc.game.formatter.*;

import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.sql.EpisodeMemory.BucketVarMap;
import edu.wisc.game.sql.EpisodeMemory.BucketVarMap2;


/** An Episode is a single instance of a Game played by a person or
    machine with our game server. It describes the current state of
    the game, and has methods for processing player's actions. The
    episode object contains the top-level controls describing the 
    current state of the rule set associated with this episode.
*/
@Entity
public class Episode {

    /** Temporary flag used during the introduction of support
	for "abandoned" players (8.012). True if we want to work
	with the old client. */
    @Transient
    boolean useOldClient = false;
    
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
	/** Creation mode */
	enum Mode { BY_ID, BY_POS};

	/** The ID of the piece being moved. Normally, a non-negative integer
	    corresponding to the ID of a game piece that actually was on the
	    border when the Player made the pick or move.
	    In an invalid Pick or Move    (e.g. the player tried to access a
s	    non-existing piece) the value may be different from those of
	    real pieces: it may refer to a piece that had been removed earlier,
	    or one that never existed, or may even be negative. In such a pick,
	    the code will reflect the invalidity of the player's action.
	   
	*/
	int pieceId = -1;


	/** The position of the piece being moved, in the [1:N*N] range.
	    May contain an invalid value on an invalid /move or /pick call.
	 */
	public final int pos;

	/** The normal constructor used when the player accesses a piece
	    by its ID, which is considered the right way to do since GS 8.0
	    @param _piece A game piece that actually is on the board at this
	    moment
	*/
	Pick(Piece _piece) {
	    piece = _piece;
	    pieceId = (int)piece.getId();
	    pos = piece.xgetPos().num();	    
	}

	/** The legacy constructor, used before GS 8.0. It may still be
	    used with calls made through the GUI client. */
	Pick(int _pos) { pos = _pos; }

	/** This is typically used when one neeeds to create a Pick object
	    representing an invalid /pick or /move call, especially in Gemini
	    games */
	Pick(Mode mode, int q) {
	    if (mode == Mode.BY_POS) {
		pos = q;
	    } else 	    if (mode == Mode.BY_ID) {
		pieceId = q;
		pos = -1;
	    } else throw new IllegalArgumentException();
	}
	public Pick(Pos  pos) { this(pos.num()); }
	public int getPos() { return pos; }
        public int getPieceId() { return pieceId; }	
        public void setPieceId(int id) {  pieceId=id; }
	/** This method should only be used if the Pick object 
	    has been created by a constructor that takes a Piece
	    argument; otherwise, null may be returned */
	public Piece getPiece() {
	    return piece;
	}
	Piece piece =  null;
	/** Acceptance code; will be recorded upon processing. The value is from
	 Episode.CODE */
	int code;
 	public int getCode() { return code; }
	public void setCode(int _code) { code = _code; }
	final public Date time = new Date();
	public String toString() {
	    return "PICK " + pos + " " +new Pos(pos) +", id="+pieceId+", code=" + code;
	}

	/** The measure of "unlikelihood" of this move being made
	    at random. Used for Bayesian-based intervention. Computed
	    during the acceptance process. */
	double rValue = 0;
	void setRValue(double r)  { rValue = r; }
	public double getRValue()  { return rValue; }

	/** In a two-player game, which player made this move? (Pairing.State.ZERO or ONE)
	 */
	int mover=0;
	public int getMover() { return mover; }

	/** Did the other Pick pick the same game piece? */
	boolean pickedSamePiece(Pick pick) {
	    return (pieceId >= 0 && pick.pieceId >= 0) && pieceId == pick.pieceId;
	}

	/** In Bot Assist games, this is set to true when the player
	    is seen to follow the preceding bot suggestion */
	@Basic boolean didFollow = false;
	public boolean getDidFollow() { return didFollow; }
	@XmlElement
	public void setDidFollow(boolean _didFollow) { didFollow = _didFollow; }    

    }

    /** A Move instance describes an [attempted] act of picking a piece
	and dropping it into a bucket.
     */
    public static class Move extends Pick {
 	/** (Attempted) destination, in the [0:3] range */
	public final int bucketNo;
	public int getBucketNo() { return bucketNo ; }
 	private Move(int _pos, int b) {
	    super(_pos);
	    bucketNo = b;
	}
	public Move(Pos pos, Pos bu) {
	    this(pos.num(), bu.bucketNo());
	}
	public Move(Piece _piece, int b) {
	    super(_piece);
	    bucketNo = b;
	}
	Move(Mode mode, int q, int b) {
	    super(mode,q);
	    bucketNo = b;
	}

	public String toString() {
	    String s = "MOVE object " + pieceId;
	    if (pos >= 0) s += " (" + new Pos(pos) +")";
	    s += " to B" + bucketNo+", code=" + code;
	    return s;
	}

	/** Do the two moves describe the same operation
	    (in terms of the piece ID and bucket ID)? */
	public boolean sameMove(Pick pick) {
	    if (pick==null) return false;
	    if (!(pick instanceof Move)) return false;
	    Move move = (Move) pick;
	    return pickedSamePiece(move) && bucketNo==move.bucketNo;
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
    //@Transient
    //private Piece[] pieces = null;
    //public Piece[] getPieces() { return  pieces;}

    /** An array of game pieces currently on the board. No nulls in this
	array. Access to anything by scanning the whole thing */
    @Transient
    private Vector<Piece> values = new Vector<>();
    public Vector<Piece> getValues() { return  values;}


    /** Where in the values array do we have a game piece with the specified id?
	@return array position, or -1 if no object with a matching ID can be found.
     */
    private int findJforIdZ(long id) {
	for(int j=0; j<values.size(); j++) {
	    if (values.get(j).getId()==id) return j;
	}
	return -1;
    }
    
    /** Where in the values array do we have a game piece with the specified id?
	@return array position
	@throws IllegalArgumentException no object with a matching ID can be found.
     */
    private int findJforId(long id) {
	int j = findJforIdZ(id);
	if (j>=0) return j;
	//System.err.println("Board has no game piece with id=" + id +". values=" + Util.joinNonBlank(", ", values));
	throw new IllegalArgumentException("Board has no game piece with id=" + id);
    }

    /** Where in the values array do we have a game piece referred to by this Pick or Move? (The match is by piece ID) */ 
    protected int findJ(Pick pick) {
	return findJforId(pick.getPieceId());
    }

    /** What pieces, if any, are in the specified cell? */
    private int[] findJforPos(int pos) {
	return findJforPos(pos, values);
    }
    static int[] findJforPos(int pos,  Vector<Piece> values) {
	Vector<Integer> v = new Vector<>();
	for(int j=0; j<values.size(); j++) {
	    if (values.get(j).pos().num()==pos) v.add(j);
	}
	int [] a = new int[v.size()];
	for(int k=0; k<a.length; k++) a[k] = v.get(k);
	return a;
    }
    
    /** Pieces are moved into this array once they are removed from the board.
	This is only shown in web UI.
     */
    @Transient
    private Vector<Piece> removedValues = new Vector<>();

    /** The cost of a pick in terms of the cost of a move. EpisodeInfo 
	overrides this method, making use of ParaSet */
    double xgetPickCost() { return 1.0;}

    
    /** The count of all attempts (move and pick) done so far, including successful and unsuccessful ones. 
	(Ignoring certain "client errors" moves, such as EMPTY_CELL)
	(In 2PG, this includes moves of both players. If there is Player 1, his moves are in EpisodeInfo.attemptCnt1, etc).
	This counter is increased whenever a move is added to transcript.
     */
    int attemptCnt=0;
    public int getAttemptCnt() { return attemptCnt; }
    /** The total cost of all attempts (move and pick) done so far. (If it's 2PG, by both players),
	including successful and unsuccessful ones. If cost_pick!=1,
	this value may be different from attemptCnt. */
    double attemptSpent=0;
    /** All successful moves (not picks) so far. (If it's 2PG, by both players) Since each successful move removes
	a game piece, the number of pieces remaining on the board can be 
	computed as (nPiecesStart - doneMoveCnt).
     */
    int doneMoveCnt=0;
    public int getDoneMoveCnt() { return doneMoveCnt; }

    /** The count of successful picks (not moves) so far */
    int successfulPickCnt=0;
    /** The list of all move/pick attempts (successful or not) done so far
	in this episode */
    @Transient
    Vector<Pick> transcript = new Vector<>();
    @Transient
    public Vector<Pick> getTranscript() { return transcript; }

    /** This is used in Gemini app, when the same Episode object
	is replayed from scratch with a different sequence of move attempts.
    */
    public void reset() {	
	attemptCnt=0;
	attemptSpent=0;	
	doneMoveCnt=0;
	transcript.setSize(0);
	stalemate = false;
	cleared = false;
	givenUp = false;
	lost = false;
	earlyWin = false;
	abandoned = false;
	ruleLineNo = 0;
	ruleLine = null;

	values.addAll(removedValues);
	removedValues.clear();
	doPrep();
    }
	
    /** Set when appropriate at the end of the episode */
    boolean stalemate = false;
    boolean cleared = false;
    boolean givenUp = false;
    boolean lost = false;
    boolean earlyWin = false;
    boolean abandoned = false;

    @Transient
    public boolean getCleared() { return cleared; }

    
    /** Which row of rules do we look at now? (0-based) */
    @Transient
    protected int ruleLineNo = 0;

    @Transient
    protected RuleLine ruleLine = null;

    @Transient
    private EpisodeMemory memory = new EpisodeMemory();
    
    /** Will return true if this is, apparently, an episode restored
	from SQL server, and cannot be played anymore because the board
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
    
    
    /** Our interface to the current rule line (a row of the rule
	set). It includes the underlying rule line (RuleSet.Row) and
	the current counters which indicate how many times the atoms
	in that line have been used.  When pieces are removed, this
	structure updates itself, until it cannot pick any pieces
	anymore. */
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
	    doneWith = exhausted() || !buildJAcceptanceMap();
	    //   	    System.err.println("Constructed "+ this+"; exhausted()=" + exhausted()+"; doneWith =" + doneWith);
	}

	/** This is set once all counters are exhausted, or no more
	    pieces can be picked by this rule. The caller should check
	    this flag after every use, and advance ruleLine */
	boolean doneWith =false;

	/** The bit acceptanceMap[pos][atomNo][dest] is set if the
	    rule atom[atomNo] in the current rule line allows moving
	    the piece currently at pos to bucket[dest]. */
	//private BitSet[][] acceptanceMap = new BitSet[Board.N*Board.N+1][];
	//protected boolean[] isMoveable = new boolean[Board.N*Board.N+1];
	/** Based on the pieces currently on the board; the arrays are coordinate with values[] */
	  
	private BitSet[][] jAcceptanceMap = null;//new BitSet[][];
	protected boolean[] isJMoveable = null; //new boolean[];

	/** Computes the probability that a random pick by a frugal player
	    (one who does not repeat a failed pick) would be successful.
	    @param knownFailedPicks The number of distinct pieces that the 
	    player has already tried to pick (and failed) from this board.
	    This reduces the denominator (= the number of still-not-tested
	    pieces). 

	    This method should not be called on episodes where movable
	    pieces are shown to the player, since a frugal player won't
	    ever do picks there. (And the GUI should not allow picks in
	    such episodes anyway).	    
	 */

	double computeP0ForPicks(int knownFailedPicks) {
	    int countPieces=values.size(), countMovablePieces=0;
	    for(int j=0; j<values.size(); j++) {
		if (isJMoveable[j]) countMovablePieces++;
	    }		
	    
	    if ( knownFailedPicks>countPieces) throw new IllegalArgumentException("You could not have really tried to pick " +knownFailedPicks+ " pieces, as the board only has " +  countPieces);
	    countPieces -= knownFailedPicks;
	    if (countMovablePieces>countPieces)  throw new IllegalArgumentException("What, there are more moveable pieces (" + countMovablePieces+") than not-tested-yet pieces ("+countPieces+")?");
	    return (countPieces==0)? 0.0: countMovablePieces/(double)countPieces;
	}

	 
	/**  Computes the probability that a random move by a MCP1
	     player would be successful */
  	double computeP0ForMoves(int knownFailedMoves) {
	    int countMoves=0, countAllowedMoves=0;
	    for(int j=0; j<values.size(); j++) {
		// In the show-movables ("fixed") mode, only movable pieces are taken into account
		if (weShowAllMovables() && !isJMoveable[j]) continue;

		    
		countMoves += 4;

		BitSet a = new BitSet();
		for(BitSet z: jAcceptanceMap[j]) {
		    a.or(z);
		}
		countAllowedMoves += a.cardinality();
	    }
	    if ( knownFailedMoves>countMoves) throw new IllegalArgumentException("You could not have really tried to make " +knownFailedMoves+ " moves, as the board only has enough pieces for " +  countMoves);
	    countMoves -= knownFailedMoves;
	    
	    if (countAllowedMoves>countMoves)  throw new IllegalArgumentException("What, there are more allowed moves (" + countAllowedMoves+") than not-tested-yet moves ("+countMoves+")?");
	    return (countMoves==0)? 0.0: countAllowedMoves/(double)countMoves;
	}

	/** For the "Bayesian-based intervention": computes the ratio
	     Pr(move|knowsTheRule) / (Pr(move|playsRandomly)
	     for the current move.
	     @param move the successful move that has just been completed
	 */
	double computeR(Move move) {

	    int countMovables = 0, countTryMovables = 0;
	    for(int j=0; j<values.size(); j++) {
		// In the show-movables ("fixed") mode, only movable pieces are taken into account
		if (weShowAllMovables() && !isJMoveable[j]) continue;

		
		if (isJMoveable[j]) countMovables ++;
		countTryMovables ++;
	    }		
	    
	    int j = findJforId( move.getPieceId());
	    int countDest = pieceJMovableTo(j).cardinality();
	    
	    double inverseProbRandom = countTryMovables * 4;
	    double inverseProbFullKnowledge = countMovables * countDest;
	    double r = inverseProbRandom/inverseProbFullKnowledge;

	    //Logging.debug("r=("+countTryMovables+"/"+countMovables+")*(4/"+countDest+")=" + r);
	    
	    return r;
	    
	}

	/** At this moment, which buckets accept the game piece with the
	    specified number?

	    @param j index into values[] */
	private BitSet pieceJMovableTo(int j) {
	    BitSet r = new BitSet(); // to what buckets this piece can go
	    for(BitSet b: jAcceptanceMap[j]) {
		r.or(b);
	    }
	    return r;
	}

	/** For each piece, where can it go? (OR of all currently
	    active rules).  The acceptanceMap needs to be computed
	    before this method can be called.
	    @return an array with 1 element per game piece, coordinated with values[]
	 */
	BitSet[] jMoveableTo() {
	    BitSet[] q =  new BitSet[values.size()];
	    for(int j=0; j<values.size(); j++) {
		q[j] = pieceJMovableTo(j);
	    }
	    return q;	    
	}
	
	/** For each piece currently on the board, this method finds
	    which rules in the current rule line allow this piece to
	    be moved, and to which buckets. This method fills
	    acceptanceMap[] and isMoveable[].
	
	    @return true if at least one piece can be moved
	*/
	private boolean buildJAcceptanceMap() {
	    EligibilityForOrders eligibleForEachOrder = new EligibilityForOrders(rules, onBoard());
	    //System.err.println("DEBUG: eligibileForEachOrder=" + eligibleForEachOrder);

	    jAcceptanceMap = new BitSet[values.size()][];
	    for(int j=0; j<values.size(); j++) {
		jAcceptanceMap[j] = pieceAcceptance(values.get(j), eligibleForEachOrder);
		
	    }

	    // modify the acceptance map as per any post-orders
	    PostOrder.applyPostPosToAcceptanceMap(rules, row,  values, jAcceptanceMap);

	    isJMoveable = new boolean[values.size()];
	    for(int j=0; j<values.size(); j++) {
		isJMoveable[j]= !pieceJMovableTo(j).isEmpty();		
	    }
	    return  isAnythingMoveable();
	}

	/** Looks at the current acceptance map to see if any of the 
	    currently present pieces can be moved */
	private boolean isAnythingMoveable() {
	    for(int j=0; j<values.size(); j++) {
		if (isJMoveable[j]) return true;
	    }
	    return false;	
	}

	
	/** For GS 5. Here, we try each bucket separately, as the
	    destination bucket may affect some variables.

	   @return result[j] is the set of buckets into which the j-th
	   rule (atom) allows the specified piece to be moved.
	*/	
	private BitSet[] pieceAcceptance(Piece p,  EligibilityForOrders eligibleForEachOrder) {
	    
	    //	    System.err.println("DEBUG: pieceAcceptance(p=" +p+")");
	    if (doneWith) throw new IllegalArgumentException("Forgot to scroll?");

	    if (row.globalCounter>=0 &&  ourGlobalCounter<=0)  throw new IllegalArgumentException("Forgot to set the scroll flag on 0 counter!");

	    
	    // for each rule, the list of accepting buckets
	    BitSet whoAccepts[] = new BitSet[row.size()];
	    for(int j=0; j<row.size(); j++) {
		whoAccepts[j] = new BitSet(NBU);
	    }
	    Pos pos = p.pos();

	    // try each bucket separately
	    for(int bucketNo=0; bucketNo<NBU; bucketNo++) {
	    
		BucketVarMap2  varMap = memory.new BucketVarMap2(p, bucketNo);

		for(int j=0; j<row.size(); j++) {
		    RuleSet.Atom atom = row.get(j);
		    if (atom.counter>=0 && ourCounter[j]==0) continue;
		    if (!atom.acceptsColorShapeAndProperties(p, varMap)) continue;
		    if (!atom.plist.allowsPicking(pos.num(), eligibleForEachOrder)) continue;
		    boolean can = atom.bucketList.destinationAllowed( varMap, bucketNo);
		    if (can) whoAccepts[j].set(bucketNo);
		}
	    }
	    return whoAccepts;
	}

	
	/** Sets the R-value of a pick/move */
	private void callComputeR(Pick pick) {
	    int j =  findJ(pick);
	    boolean movable = isJMoveable[j];
	    if (!movable) {  // immovable piece
		pick.setRValue(0);
	    } else if (!(pick instanceof Move)) {  // accepted pick
		// accepted picks don't affect the cumulative R, so just use 1
		pick.setRValue(1);
	    } else {
		// Move attempted on a moveable piece
		Move move  = (Move) pick;
		double rValue = ruleLine.computeR(move);
		move.setRValue(rValue);
	    }
	}

	
	/** Requests acceptance for this move or pick. In case of
	    acceptance of an actual move (not just a pick), decrements
	    appropriate counters, removes the piece from the board;
	    then, as appropriate, either marks the current RuleLine as
	    exhausted, or updates its acceptance map.

	    <p> If the "pick" object is a Move, this method also
	    computes and sets the R-value (see "Bayesian-based
	    intervention") in the "pick" object.	    

	    @return result  (accept/deny)
	*/
	int accept(Pick pick) {

	    //System.err.println("RL.accept: "+this+", move="+ move);
	    
	    if (doneWith) throw  new IllegalArgumentException("Forgot to scroll?");
	    transcript.add(pick);
	    attemptCnt++;
	    //System.out.println("DEBUG A: attemptCnt:=" + attemptCnt); //transcript=" + getTranscript());
	    attemptSpent += (pick instanceof Move) ? 1.0: xgetPickCost();


	    int j=-1;
	    if (pick.getPieceId()>=0) { // modern client supplies piece ID
		try {
		    j = findJ(pick);
		} catch(IllegalArgumentException ex) {
		    return pick.code=CODE.INVALID_OBJECT_ID;
		}
	    } else {
	        int[] jj = findJforPos(pick.pos);
		if (jj.length==0) return pick.code=CODE.EMPTY_CELL;
		else if (jj.length>0) return pick.code=CODE.MULTIPLE_OBJECTS_IN_CELL;
		j = jj[0];
		pick.pieceId = (int)values.get(j).getId();
	    }
	    
	    pick.piece = values.get(j);

	    boolean movable = isJMoveable[j];
	    callComputeR(pick);

	    if (!movable) {  // immovable piece
		return pick.code =  CODE.IMMOVABLE;
	    } else if (!(pick instanceof Move)) {  // accepted pick
		successfulPickCnt++;
		return pick.code = CODE.ACCEPT;
	    }
	    Move move = (Move)pick;
		
	    BitSet[] r = jAcceptanceMap[j];
	    
	    Vector<Integer> acceptingAtoms = new  Vector<>();
	    Vector<String> v = new Vector<>();
	    for(int i=0; i<row.size(); i++) {
		if (r[i].get(move.bucketNo)) {
		    v.add("" + i + "(c=" + ourCounter[i]+")");
		    acceptingAtoms.add(i);
		    if (ourCounter[i]>0) {
			ourCounter[i]--;
		    }
		}
	    }
	    
	    //System.err.println("Acceptors: " + String.join(" ", v));
	    if (acceptingAtoms.isEmpty()) return  move.code=CODE.DENY;

	    //--- Process the acceptance -------------------------
	    if (ourGlobalCounter>0) ourGlobalCounter--;

	    doneMoveCnt++;

	    // Remember where this piece was moved
	    memory.enterMove(move.piece, move.bucketNo);
	    pick.piece.setBuckets(new int[0]); // empty the bucket list for the removed piece
	    
	    values.remove(j);  // remove the piece
	    removedValues.add(pick.piece);
	    pick.piece.setDropped(move.bucketNo);
	    //System.err.println("Removed piece from pos " + move.pos);

	    // Check if this rule can continue to be used, and if so,
	    // update its acceptance map
	    doneWith = exhausted() || !buildJAcceptanceMap();
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
    
    
    @Transient
    private OutputMode outputMode;
    @Transient
    private final PrintWriter out;
    /** In the captive server, this is where the client's commands come from.
	This is null in web server. */
    @Transient
    private final Reader in;
   
    public static final DateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    /** with milliseconds */
    public static final DateFormat sdf2 = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");

    public static final RandomRG random = new RandomRG();
    
    /** Creates a word made out of random letters and digits */
    public static String randomWord(int len) {
	StringBuffer b=new StringBuffer(len);
	for(int i=0; i<len; i++) {
	    int k =  random.nextInt(10 + 'Z'-'A'+1);
	    char c = (k<10) ? (char)('0' + k) : (char)('A' + k-10);
	    b.append(c);
	}
	return b.toString();
    }

    
    /** Creates a more or less unique string ID for this Episode object */
    private String buildId() {
	return sdf.format(startTime) + "-" +randomWord(6);
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
	properties of the initial board). Depending on what the Game is,
	the episode may use a pre-created board stored in the Game object,
	or a random board created on the fly.
	@param _in The input stream for commands; it will be null in the web app
	@param _out Will be null in the web app.
    */
    public Episode(Game game, OutputMode _outputMode, Reader _in, PrintWriter _out) {
	this( game,  _outputMode,  _in,  _out, null, false);	
    }

    /** @param _episodeId  This is non-null when we create a ReplayedEpisode, and want it to have the authentic original episodeId
	
     */
    protected Episode(Game game, OutputMode _outputMode, Reader _in, PrintWriter _out, String _episodeId, boolean needLabels) {
	startTime = new Date();    
	in = _in;
	out = _out;
	outputMode = _outputMode;
	episodeId = (_episodeId==null)?  buildId():    _episodeId;
    
	rules = game.rules;
	Board b =  game.giveBoard(needLabels);

	nPiecesStart = b.getValue().size();
	values = new Vector<>();
	for(Piece p: b.getValue()) values.add(p);
	doPrep();	
    }

    /** Special-purpose constructor, used for replaying episodes from Gemini logs */
    public Episode(RuleSet _rules, Board b, OutputMode _outputMode, Reader _in, PrintWriter _out) {
	startTime = new Date();    
	in = _in;
	out = _out;
	outputMode = _outputMode;
	episodeId = buildId();
    
	rules = _rules;

	nPiecesStart = b.getValue().size();
	values = new Vector<>();
	for(Piece p: b.getValue()) values.add(p);
	doPrep();	
    }



    
    /** Return codes for the /move and /display API web API calls,
	and for the MOVE command in the captive game server.
     */
    public static class CODE {
	public static final int
	/** Move accepted and processed. This is recorded for 
	    a successful move or successful pick.
	 */
	    ACCEPT = 0,
	/** Move rejected, and no other move is possible
	    (stalemate). This means that the rule set is bad, and we
	    owe an apology to the player */
	    STALEMATE=2,
	/** Move rejected, because there is no piece in the cell.
	 */
	    EMPTY_CELL= 3,
	/** Move rejected, because this destination is not allowed.
	    (Prior to GS 5.007, this code was also returned instead of
	    IMMOVABLE).  */
	    DENY = 4,
	/** Exit requested */
	    EXIT = 5,
	/** New game requested */
	    NEW_GAME = 6,
	/** Move rejected, because no destination is allowed for this
	    game piece. (Similar to DENY, but with extra info that the
	    piece cannot be moved to any bucket). (Introduced in GS 5.007;
	    previouslsy, DENY was returned in this situation). */
	    IMMOVABLE = 7
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
	    JUST_A_DISPLAY = -8,
	// A /pick or /move call made by a wrong player in a 2PG
	    OUT_OF_TURN = -9,
	// No game piece with specified ID exists. (This happens, for
	// example, when Gemini tries to grab an already-removed game piece).
	    INVALID_OBJECT_ID = -10,
	// A /move or /pick call referencing a cell that has multiple pieces
	    MULTIPLE_OBJECTS_IN_CELL  = -11;
	    

	/** Used to talk to Gemini */
	public static String toBasicName(int code) {
	    return code==ACCEPT? "ACCEPT":
		code==DENY? "DENY":
		code==IMMOVABLE? "IMMOVABLE":
		"INVALID";
	}
	/** This is used when comparing codes read from old
	    transcripts and recomputed codes, to take into account
	    a more recent creation of code 7 */
	public static int legacy(int code) {
	    return code==IMMOVABLE? DENY: code;
	}
	public static boolean areSimilar(int code1, int code2) {
	    return legacy(code1)==legacy(code2);
	}
	
    }

    /** Values that may be returned by getFinishCode(), describing
	the episode's status */
    public static class FINISH_CODE {
	public static final int
	/** there are still pieces on the board, and some are moveable;
	    playing may still continue
	 */
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
	    LOST = 4,
	/** The server decided that the player is so good that 
	    it terminated the episode, giving the player full
	    points that he'd get if he completed the episode 
	    without any additional errors. This is sometimes done
	    when the DOUBLING (or LIKELIHOOD) incentive scheme is in effect.
	    (Note, that since ver 7.0, this code is assigned even if
	    "displaying mastery" happens to coincide with the removal of the
	    last game piece, so the win isn't really "early".)
	*/
	    EARLY_WIN = 5,
	/** This may be recorded in a 2PG episode when one player has 
	    "walked away" and abandoned the other. One should check
	    PlayerInfo.completionMode to see who's at fault.
	*/
	    WALKED_AWAY = 6,
	/** This is returned by EpisodeInfo.getFinishCode to
	    the player who was "abandoned" by his partner.
	*/
	    ABANDONED = 7;
    }

    /** Creates a bit set with bits set in the positions where there are
	pieces. The positions are, as usual, numbered 1 thru N^2.  */
    private BitSet onBoard() {
	return onBoard(values);
    }
    
    static public BitSet onBoard(Vector<Piece> values) {
	BitSet onBoard = new BitSet(Board.N*Board.N+1);
	for(Piece p: values) {
	    onBoard.set(p.pos().num());
	}
	return onBoard;
    }

    @Transient
    private boolean prepReady = false;

    /** Run this method at the beginning of the game, and
	every time a piece has been removed, to update various
	auxiliary data structures.
	@return true, unless stalemate (no piece can be picked) is
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
    public Pick getLastMove() {
	return lastMove;
    }

    
    /// FIXME - who uses it and what does he need?
    public int getLastMovePos() {
	return lastMove==null? -1:  lastMove.pos;
    }

    /**    	    One normally should not use this method directly; use
	    doPick() or doMove() instead.
    */
    protected int accept(Pick move) {
	lastMove = move;
	if (stalemate) {
	    return CODE.STALEMATE;
	}

	int j=-1;
	if (move.getPieceId()>=0) { // modern client supplies piece ID
	    try {
		j = findJ(move);
	    } catch(IllegalArgumentException ex) {
		return move.code=CODE.INVALID_OBJECT_ID;
	    }
	} else {
	    if (move.pos<1 || move.pos>Board.N*Board.N) return CODE.INVALID_POS;
	    int[] jj = findJforPos(move.pos);
	    if (jj.length==0) return move.code=CODE.EMPTY_CELL;
	    else if (jj.length>1) return move.code=CODE.MULTIPLE_OBJECTS_IN_CELL;
	    j = jj[0];
	    move.pieceId = (int)values.get(j).getId();
	}
	    
	move.piece = values.get(j);

	int code = ruleLine.accept(move); // this is where the move may be added to the transcript

	// Update the data structures describing the current rule line, acceptance, etc
	if (move instanceof Move && code==CODE.ACCEPT && !cleared && !earlyWin && !stalemate && !givenUp) {
	    doPrep();
	}
	
	return code;
    }


    /** The basic mode tells the player where all movable pieces are, 
	but EpisodeInfo will override it if the para set mandates "free" mode.
     */
    public boolean weShowAllMovables() {
	return true;
    }
 

    private static HTMLFmter fm = new HTMLFmter();


    /** Graphic display of the board */
    public String graphicDisplay() {
	return graphicDisplay(false);
    }

    /** Generates an HTML table displaying the current board state,
	@param html If false, call the ASCII-graphics routine instead (for
	printing on terminal, instead of a web browser)
    */
    public String graphicDisplay(boolean html) {

	boolean[] isJMoveable = ruleLine.isJMoveable;

	if (isNotPlayable()) {
	    return "This episode must have been restored from SQL server, and does not have the details necessary to show the board";
	}
	
	if (!html) return graphicDisplayAscii(values, lastMove,  weShowAllMovables(), isJMoveable, html);

	String notation = HtmlDisplay.notation(weShowAllMovables());	
	String display =HtmlDisplay.htmlDisplay(values, getLastMove(),  weShowAllMovables(), isJMoveable, 80, false);
	String result = fm.td(display)  + fm.td("valign='top'", notation);
	result = fm.tr(result);
	result = fm.table("", result);
	return result;
    }

   
    
    /** Retired from the web game server; still used in Captive Game Server. */
    static public String graphicDisplayAscii(
Vector<Piece> values, Pick lastMove, boolean weShowAllMovables, boolean[] isJMoveable, 

					boolean html) {

	//boolean debug=true;
	Vector<String> w = new Vector<>();
	int m = maxCrowd(values);
	if (m==0) m=1;

	// may need an extra space for the last-removed piece, to put brackewts around it
	if (lastMove != null && lastMove instanceof Move && lastMove.code == CODE.ACCEPT) {
	    int m1 = findJforPos(lastMove.pos, values).length + 1;
	    //if (debug) System.out.println("m=" + m+", m1=" + m1);
	    if (m1>m) m = m1;
	}
     
	String div = "#---+";
	String seg = "--";
	String sem = "";
	for(int k=0; k<m; k++) seg += "---";
	for(int k=0; k<m; k++) sem += "   ";
	for(int x=1; x<=Board.N; x++) div += seg;
	w.add(div);
	
	
	for(int y=Board.N; y>0; y--) {
	    String s = "# " + y + " |";
	    for(int x=1; x<=Board.N; x++) {
		int pos = (new Pos(x,y)).num();
	        int[] jj = findJforPos(pos, values);

		//----
		String[] icons = new String[m], paren=new String[m+1];
		for(int i=0; i<m; i++) {
		    icons[i] = "  ";
		    paren[i] = " ";
		}
		icons[0] =  html? "." :   " .";
		paren[m] = " ";


		//		if (jj.length>m) System.err.println("for pos=" + pos+", jj=" + jj + " while m is only " + m + "!");
		
		for(int i=0; i<jj.length; i++) {
		    Piece p = values.get(jj[i]);
		    ImageObject io = p.getImageObject();
		    String z = (io!=null)? io.symbol() :  p.xgetShape().symbol();
		    if (html) {
			String color =  p.getColor();
			if (color!=null) z=fm.colored( color.toLowerCase(), z);
			z = fm.wrap("strong",z);
		    } else {
			Piece.Color color = p.xgetColor();
			if (color!=null) z = color.symbol() + z;
		    }
		    icons[i] = z;


		    if (lastMove != null && lastMove.getPieceId()==p.getId()) {
			//if (i+1 >= paren.length) System.err.println("It will break for id=" + p.getId() +", i=" +i);
			paren[i] = "[";
			paren[i+1] = "]";
		    }
		    
		}
		

		if (lastMove != null && lastMove.pos==pos && (lastMove instanceof Move) && lastMove.code == CODE.ACCEPT) {

		    // put the brackets around a blank spot (or dot), where a piece was removed
		    int i = jj.length;

		    //if (i+1 >= paren.length) System.err.println("Accept at pos=" + pos+", jj.length=" + jj.length+" : It will break for removed id=" + lastMove.pieceId +", i=" +i);

		    
		    paren[i] = "(";
		    paren[i+1] = ")";
		} 

		s += " ";
	        for(int i=0; i<m; i++) {
		    s += paren[i] + icons[i];
		}
		s += paren[m];
	    }
	    w.add(s);
	}
	w.add(div);
	String s = "#   |  ";
	for(int x=1; x<=Board.N; x++) s += (html?"": " ") + x  + sem;
	w.add(s);
	return String.join("\n", w);
    }

    /** Since ver 8.020, it is "EARLY_WIN" (and not just normal "FINISH") even if the mastery 
	was achieved on the very last move of an episode, and the board was cleared.
    */
    int getFinishCode() {
	 return
	     earlyWin? FINISH_CODE.EARLY_WIN :
	     cleared? FINISH_CODE.FINISH :
	     stalemate? FINISH_CODE.STALEMATE :
	     givenUp?  FINISH_CODE.GIVEN_UP :
	     lost?  FINISH_CODE.LOST :
	     abandoned?  FINISH_CODE.ABANDONED :
	     FINISH_CODE.NO;
    }
       

    void respond(int code, String msg) {
	String s = "" + code + " " + getFinishCode() +" "+attemptCnt;
	if (msg!=null) s += "\n" + msg;
	out.println(s);
    }    

    /** Returns the current board, or, on a restored-from-SQL-server episodes,
	null (or empty board, to keep the client from crashing).
    */
    public Board getCurrentBoard(boolean showRemoved) {
	if (isNotPlayable()) {
	    return cleared? new Board() : null;
	} else {
	    return new Board(values,
			     (showRemoved?removedValues:null),
			     ruleLine.jMoveableTo());
	}
	
    }

    /** Shows the current board (without removed [dropped] pieces) */
    public Board getCurrentBoard() {
	return getCurrentBoard(false);
    }

    /** No need to show this field */
    private static final HashSet<String> excludableNames =  Util.array2set("dropped");

    /** @return The description of the current board in the form of a JSON string.
     */
    protected String displayJson() {
	Board b = getCurrentBoard();
	JsonObject json = JsonReflect.reflectToJSONObject(b, true, excludableNames);
	return json.toString();
    }

    /** The current version of the application */
    public static final String version = "8.031";

    /** FIXME: this shows up in Reflection, as if it's a property of each object */
    public static String getVersion() { return version; }

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

	/** If this Display was created in response to a pick/move
	    attempt, this is the attempted pick/move in question.
	    It may be successful or unsuccessful. */
	Pick pick = null;
	
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
	/** The list of all move/picks attempts (successful or not) done so far
	    in this episode */
	public Vector<Pick> getTranscript() { return transcript; }
	
	private RecentKnowledge recentKnowledge =  new RecentKnowledge(Episode.this.transcript, false);
	/** Keyed by object ID */
	public RecentKnowledge getRecentKnowledge() {
	    return recentKnowledge;
	}
	/** Keyed by object ID */
	public void setRecentKnowledge(RecentKnowledge x) {
	    recentKnowledge = x;
	}

	/** Keyed by position (GS 7 compatibility mode, for use with
	    the GUI client which has not been upgraded).
	*/
	private RecentKnowledge recentKnowledge0 =  new RecentKnowledge(Episode.this.transcript, true);
	public RecentKnowledge getRecentKnowledge0() {
	    return recentKnowledge0;
	}
	public void setRecentKnowledge0(RecentKnowledge x) {
	    recentKnowledge0 = x;
	}
	
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


	public Display(int _code, Pick _pick,	String _errmsg) {
	    code = _code;
	    pick = _pick;
	    errmsg = _errmsg;
	}
	/** This one is mostly used to report an error, and also to 
	    create a return structure for a /display call
	 */
	public Display(int _code, 	String _errmsg) {
	    this(_code, null, _errmsg);
	}


	public String toString() {
	    Object ro = JsonReflect.reflectToJSONObject(this, true, null, 6);
	    return "" + ro;
	}	
    }

    /** Builds a Display objecy to be sent out over the web UI upon a /display
	call (rather than a /move or /pick) */
    public Display mkDisplay0() {
    	return new Display(Episode.CODE.JUST_A_DISPLAY, "Display requested");
    }

    /** Checks for errors in some of the arguments of a /pick or /move call.
	@return a Display reporting an error, or null if no error has been found
    */
    private Display inputErrorCheck0(int _attemptCnt) {
	if (isCompleted()) {
	    return new Display(CODE.NO_GAME, "No game is on right now (cleared="+cleared+", stalemate="+stalemate+", earlyWin="+earlyWin+"). Use NEW to start a new game");
	}

	if (_attemptCnt != attemptCnt) {
	    String msg =  "Attempt count mismatch: client sends attemptCnt="+_attemptCnt+", expected " + attemptCnt;
	    Logging.info(msg);
	    return new Display(CODE.ATTEMPT_CNT_MISMATCH, msg);
	}
	return null;
    }

    private Display inputErrorCheck1(int y, int x, int _attemptCnt) {
	Display errorDisplay = inputErrorCheck0( _attemptCnt);
	if (errorDisplay != null) return errorDisplay;
	if (x<1 || x>Board.N) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: column="+x+" out of range");
	if (y<1 || y>Board.N) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: row="+y+" out of range");
	return null;
    }

    private Display inputErrorCheck2(int pieceId, int _attemptCnt) {
	Display errorDisplay = inputErrorCheck0( _attemptCnt);
	if (errorDisplay != null) return errorDisplay;
	if (pieceId<0) return new Display(CODE.INVALID_ARGUMENTS,  "Invalid pieceId=" + pieceId);
	return null;
    }


    private Pick formPick( int y, int x) {
	Pos pos = new Pos(x,y);
	Pick move = new Pick( pos);
	return move;	
    }

    /** Forms a Move or Pick, depending on whether bucketId is valid
	@param bucketId If its negative, it's a pick
     */
    private Pick form2( int pieceId, int bucketId) {

	int j = findJforIdZ(pieceId);

	if (j<0) {
	    // Invalid ID. Form a Move to be recorded in transcript.
	    // (Needed in Gemini plays)
	    Pick move =	bucketId<0?
		new Pick(Pick.Mode.BY_ID, pieceId):
		new Move(Pick.Mode.BY_ID, pieceId, bucketId);
	    move.code = CODE.INVALID_OBJECT_ID;
	    return move;
	} else {	
	    Piece p = values.get(j);	    
	    Pick move = bucketId<0?
		new Pick(p): new Move(p, bucketId);
	    return move;
	}
    }

	    
    
    /** Evaluate a pick attempt */
    public Display doPick(int y, int x, int _attemptCnt) throws IOException {
	Display errorDisplay =inputErrorCheck1(y, x, _attemptCnt);
	if (errorDisplay!=null) return errorDisplay;
	Pick move = formPick(y,x);
	int code = accept(move);
	return new Display(code, move, mkDisplayMsg());
    }

    /** Processes a /pick?id=... call.  The call is only entered into the 
	transcript if the args are valid, or if the error is due to 
	invalid object id ZZZZ
     */
    public Display doPick2(int pieceId, int _attemptCnt) throws IOException {
	Display errorDisplay =inputErrorCheck2(pieceId, _attemptCnt);
	if (errorDisplay!=null) return errorDisplay;
	Pick move = form2(pieceId, -1);

	int code = move.code;
	if (code == CODE.INVALID_ARGUMENTS ||
	    code == CODE.INVALID_OBJECT_ID	    ) {
	    // Unlike other "client error" moves, this is a bad pieceId
	    // case, and is saved in transcript, for Gemini runs
	    transcript.add(move);
	    attemptCnt++;
	    System.out.println("DEBUG B: attemptCnt:=" + attemptCnt); //transcript=" + getTranscript());
	} else {
	    code = accept(move);
	}
	return new Display(code, move, mkDisplayMsg());
    }

    /** Evaluate a move attempt
	@param x the x-coordinate (1 thru 6) of the game piece the player tries to move
	@param y the y-coordinate of the game piece the player tries to move
	@param bx the x-coordinate of the destination bucket (0 or 7)
	@param by the y-coordinate of the destination bucket (0 or 7)
	@param _attemptCnt the number of previously made attempts in this episode, according to the client that has sent this request. This number must match this.attemptCnt
     */
    public Display doMove(int y, int x, int by, int bx, int _attemptCnt) throws IOException {
	Display errorDisplay =inputErrorCheck1(y, x, _attemptCnt);
	if (errorDisplay!=null) return errorDisplay;

	if (bx!=0 && bx!=Board.N+1) return new Display(CODE.INVALID_ARGUMENTS, 
						       "Invalid input: bucket row="+by+" is not 0 or "+(Board.N+1));

	Pos pos = new Pos(x,y), bu =  new Pos(bx, by);
	int buNo=bu.bucketNo();
	if (buNo<0 || buNo>=NBU) {
	    if (bx!=0 && bx!=Board.N+1) return new Display(CODE.INVALID_ARGUMENTS, "Invalid converted bucket no " + buNo); 
	}
	Move move = new Move(pos, bu);
	int code = accept(move);
	return new Display(code, move, mkDisplayMsg());
    }


    public Display doMove2(int pieceId, int bucketId, int _attemptCnt)// throws IOException
    {
	Display errorDisplay =inputErrorCheck2(pieceId, _attemptCnt);
	if (errorDisplay!=null) return errorDisplay;

	if (bucketId<0 || bucketId >= NBU)
	    return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: bucket ID=" + bucketId);
	
	Move move= (Move)form2(pieceId,bucketId);
	int code = move.code;
	if (code == CODE.INVALID_ARGUMENTS) {
	    // Unlike other "client error" moves, this is a bad pieceId
	    // case, and is saved in transcript, for Gemini runs
	    transcript.add(move);
	    attemptCnt++;
	    System.out.println("DEBUG C: attemptCnt:=" + attemptCnt); //transcript=" + getTranscript());
	} else {
	    code = accept(move);
	}
	return new Display(code, move, mkDisplayMsg());
    }

    /** A message to put along with the accept/deny code into the Display object */
    private String mkDisplayMsg() {
    	return 
	    (outputMode==OutputMode.BRIEF) ? null:
	    cleared?  "Game cleared - the board is clear" :
	    stalemate?  "Stalemate - no piece can be moved any more. Apology for these rules!" :
	    givenUp? "You have given up this episode" :
	    earlyWin?  "You won by a long error-free stretch" :
	    null;
    }

    /** Lets this episode play out until either all pieces are
	cleared, or a stalemate is reached, or the player gives up
	(sends an EXIT or NEW command). The episode takes commands from
	the reader, as in the Captive Game Server.

	@param game This is passed just so that we can access the feature list for the FEATURES command
	@param gameCnt The sequential number of the current episode. This is only used in a message.
	@return true if another episode is requested, i.e. the player
	has entered a NEW command. false is returned if the player
	enters an EXIT command, or simply closes the input stream.
    */
    public boolean playGame(GameGenerator gg, //Game game,
			    int gameCnt) throws IOException {
	try {
	String msg = "# Hello. This is Captive Game Server ver. "+version+". Starting a new episode (no. "+gameCnt+")";
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
		out.println("# FEATURES");
		out.println("# MOVE row col bucket_row bucket_col");
		out.println("# MOVE piece_id bucket_id");
		out.println("# NEW");
		out.println("# DISPLAY");
		out.println("# DISPLAYFULL");
		out.println("# MODE <BRIEF|STANDARD|FULL>");
		out.println("# COND <train|test>");
		out.println("# EXIT");
	    } else if (cmd.equals("DISPLAY")) {
		out.println(displayJson());
		if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
	    } else if (cmd.equals("FEATURES")) {
		//Map<String, Vector<Object>> features = game.getAllFeatures();
		JsonObject json = JsonReflect.reflectToJSONObject(gg.getAllFeatures(), true);
		out.println(json);
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
	    } else if (cmd.equals("COND")) {
		tokens.remove(0);		
		if (tokens.size()==0) {
		    //-- just show the current state
		} else if (tokens.size()!=1 || tokens.get(0).type!=Token.Type.ID) {
		    respond(CODE.INVALID_ARGUMENTS, "# COND command must be followed by 'train' or 'test'");
		    continue;
		} else {
		    String s = tokens.get(0).sVal.toLowerCase();
		    if (s.equals("train")) {
			gg.setTesting(false);
		    } else if (s.equals("test")) {
			gg.setTesting(true);
		    } else {
			respond(CODE.INVALID_ARGUMENTS, "# COND command  must be followed by 'train' or 'test'; invalid value=" + s);
		    }
		}
		out.println("# OK, cond=" + (gg.getTesting()? "test" : "train"));
	    } else if (cmd.equals("MOVE")) {
		
		tokens.remove(0);
		int q[] = new int[tokens.size()];
		if (tokens.size()!=4 && tokens.size()!=2) {
		    respond(CODE.INVALID_ARGUMENTS, "# Invalid input");
		    continue;
		}

		// y x By Bx
		// pieceId bucketId
		
		boolean invalid=false;
		for(int j=0; j<q.length; j++) {
		    if (tokens.get(j).type!=Token.Type.NUMBER) {
			respond(CODE.INVALID_ARGUMENTS, "# Invalid input: "+tokens.get(j));
			invalid=true;
			break;
		    }
		    q[j] = tokens.get(j).nVal;

		}
		if (invalid) continue;

		Display mr =
		    (q.length==2)?
		    doMove2(q[0], q[1],  attemptCnt):
		    doMove(q[0], q[1], q[2], q[3], attemptCnt);
		respond(mr.code, "# " + mr.errmsg);
		if (outputMode!=OutputMode.BRIEF) out.println(displayJson());
		if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
		
	    } else {
		respond(CODE.INVALID_COMMAND, "# Invalid command: " +cmd);
		continue;
	    }
	}
	return false;
	} finally {
	    out.flush();
	}
    }

    public enum OutputMode {
	// One-line response on MOVE; no comments
	BRIEF,
	// Two-line response on MOVE; no comments
	STANDARD,
	// Two-line response on MOVE; human-readable comments
	FULL}; 

    /** @return true if this episode cannot accept any more move attempts,
	for any reason (board cleared, stalemate, given up, lost, abandoned).
     */
    public boolean isCompleted() {
	return cleared || earlyWin|| stalemate || givenUp || lost || abandoned;
    }

    /** Marks this episode as "given up" (unless it's already marked
	as completed in some other way) */
    void giveUp() {
	if (!isCompleted()) givenUp = true;
    }

    static final String file_writing_lock = "Board file writing lock";
	
        /** Concise report, handy for debugging */
    public String report() {
	return "["+episodeId+"; FC="+getFinishCode()+
	    " " +
	    attemptCnt + "/"+getNPiecesStart()  +
	    "]";
    }

    
    public boolean[] getIsJMoveable() { return ruleLine.isJMoveable;}

    /** @return maxPopulation of a cell */
    static int maxCrowd(Vector<Piece> values) {
	int pop[] = new int[Board.N * Board.N + 1];
	for(Piece p: values) {
	    int pos = p.pos().num();
	    pop[pos]++;
	}
	int max = 0;
	for(int x: pop) {
	    if (max<x) max=x;
	}
	return max;
	
    }

    /** Only counts failed and successful moves, and failed picks,
	but not successful picks. Used for pseudo learning in bot assist. */
    int getAttemptCntExcludingSuccessfulPicks() {

	int n = 0;
	for(Pick pick: transcript) {
	    if (pick instanceof Move ||
		pick.code != CODE.ACCEPT) n++;
	}
	return n;
    }

    
}
