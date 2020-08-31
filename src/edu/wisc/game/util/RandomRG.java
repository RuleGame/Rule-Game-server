package edu.wisc.game.util;

import java.util.*;
import java.math.*;

public class RandomRG extends Random {
    public RandomRG() { super(); }
    public RandomRG(long seed) { super(seed); }

    /** @return a random boolean number. 
	@param p The probability of the return value being true
     */
    public boolean getBoolean(double p) {
	if (p<0 || p>1.0)  throw new IllegalArgumentException();
	return nextDouble()<p;
    }

    /** Generates a random integer number x,  uniformly distributed among the
	(max-min+1) values: 	min &le; x &le; max. */
    public int getInRange(int min, int max) {
	return (max<=min)? min :  min + nextInt(max - min + 1);
    }
    public int getInRange(int z[]) {
	return  getInRange(z[0], z[1]);
    }


    /** A random subset of m numbers out of [0:n-1], in random order */
    public Vector<Integer> randomSubsetPermuted(int n, int m) {
	if (m>n) throw new IllegalArgumentException("Cannot select "+m+" values out of " + n +"!");
	Vector<Integer> v = new Vector<>(n), result =  new Vector<>(m);
	for(int j=0; j<n; j++) v.add(j);
	for(int j=0; j<m; j++) {
	    int pos = nextInt(v.size());
	    result.add( v.get(pos));
	    v.removeElementAt(pos);
	}
	return result;
    }


    class IntCmp implements Comparator<Integer> {
	public int compare(Integer o1, Integer o2) { return o1.compareTo(o2);}
    }
	 
    public Vector<Integer> randomSubsetOrdered(int n, int m) {
	Vector<Integer> q = randomSubsetPermuted(n,  m);
	q.sort( new IntCmp());
	return q;
    }

    
    /** Returns an exponentially distributed random real number in the range [0, M], with the probability density dropping by e at L. */
    public double getExpDouble(double M, double L) {
	if (M<0 || L<0) throw new IllegalArgumentException();
	if (M==0 || L==0) return 0;
	double x = nextDouble();
	double z = -L * Math.log( 1 - x * (1 - Math.exp(-M/L)));
	if (z>M) z=M; // just in case the rounding has played a game on us
	return z;
    }

      public int getExp(int M, double L) {
	if (M<0 || L<0) throw new IllegalArgumentException();
	if (M==0 || L==0) return 0;
	double q = L/(L+1);
	double x = nextDouble();
	x = x / (1 + Math.pow( q, M+1));
	int z = (int)(Math.log(1-x) / Math.log(q));
	if (z>M) z= M; // just in case the rounding has played a game on us
	return z;
    }

    /** Get a random value of a specified enum class */
    public  <T extends Enum<T>> T getEnum(Class<T> retType) {
	T[] w = retType.getEnumConstants();
	return w[ nextInt(w.length)];
    }

    
}
    
