package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;
//import java.net.*;
import javax.persistence.*;



/** An EpisodeInfo instance extends an Episode, containing additional
    information related to it being played as part of an
    experiment. That includes support for creating an Episode based on
    a parameter set, and for managing earned reward amount.
 */
@Entity  
public class TestChild extends TestParent {

    /** Back link to the player, for JPA's use */
    @ManyToOne(fetch = FetchType.EAGER)
    private Test player;
    public Test getPlayer() { return player; }
    public void setPlayer(Test _player) { player = _player; }

    TestChild(){}

    String name;
    int b;
    TestChild(String _name, int _a, int _b) {
	name = _name;
	a = _a;
	b = _b;
    }

    
}
