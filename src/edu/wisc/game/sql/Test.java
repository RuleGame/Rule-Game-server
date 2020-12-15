package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.json.*;
import javax.persistence.*;


/** Used for testing JPA persistence features
*/
@Entity
public class Test {

    @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;

    String name;

    int a, b;

    @OneToMany(
        mappedBy = "player",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
	fetch = FetchType.EAGER)
    private Vector<TestChild> allEpisodes = new  Vector<>();


    
    Test() {}
    Test(String _name) { name = _name; }

    public void addEpisode(TestChild c) {
        allEpisodes.add(c);
        c.setPlayer(this);
    }
 
    static public void main(String [] argv) {

	int N = 10;
	Test q[] = new Test[N];

	for(int j=0; j<N; j++) {
	    String name = "user_" + j;
	    q[j] = findTest(name);
	    if (q[j]==null) {
		q[j] = new Test(name);
		TestChild x = new TestChild("child_" + j, j, 0);
		q[j].addEpisode(x);
		Main.persistObjects(q[j], x);
	    }
	    q[j].a = j;
	}


	for(int j=0; j<N; j++) {
	    q[j].a += 100;
	    q[j].b = 1;
	    for(int k=0; k<=j; k++) {
		TestChild x =  q[k].allEpisodes.firstElement();
		x.a ++;
		x.b ++;
	    }
	    if ((j-2)%5 == 0) {
		EntityManager em = Main.getEM();

		em.getTransaction().begin();
		// em.flush();  // Just begin/commit is enough; flush not needed();
		em.getTransaction().commit();
		System.out.println("Done begin/commit, for " + q[j]);
		
		//System.out.println("Done flush, for " + q[j]);
		
	    }
	}
	
    }

    public String toString() {
	return "["+name + "; a=" +a+"; b=" + b+"]";
    }
    
    

    static Test findTest(String pid) {

	EntityManager em=Main.getEM();
	
	Test x;
	synchronized(em) {

	    Query q = em.createQuery("select m from Test m where m.name=:c");
	    q.setParameter("c", pid);
	    List<Test> res = (List<Test>)q.getResultList();
	    if (res.size() != 0) {
		x = res.iterator().next();
	    } else {
		return null;
	    }
	}
	return x;
    }    


}
