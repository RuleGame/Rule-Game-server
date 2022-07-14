package edu.wisc.game.math;

import java.io.*;
import java.util.*;
//import java.text.*;

import edu.wisc.game.util.*;


/** The Mann-Whitney math */
public class MannWhitney {

    /** How many pairs (i,j) exist where a[i] &lt; b[j]? Ties are counted as 0.5
	@param a Ascending sorted
	@param b Ascending sorted
     */
    public static double count(int a[], int b[]) {
	double sum = 0;
	int ltCount=0; 
	for(int j=0; j<b.length; j++) {
	    if (j>0 && b[j]<b[j-1]) throw new IllegalArgumentException("b[] is not ascending sorted");
	    while(ltCount<a.length && a[ltCount] < b[j]) ltCount++;
	    int eqCount=0;
	    while(ltCount + eqCount<a.length && a[ltCount+eqCount] == b[j]) eqCount++;
	    sum += ltCount + 0.5*eqCount;
	}
	return sum;	
    }


    /** The element z[i][j] of the results is equal to the number of pairs
	(k,m) such that a[i][k] &lt; a[j][m].
	@param a Each row of this matrix represent a "cloud" of points to be
	compared. It will be sorted.
     */
    public static double[][] rawMatrix(int a[][]) {
	final int n = a.length;
	double[][] z = new double[n][];
	for(int j=0; j<n; j++) Arrays.sort(a[j]);
	for(int i=0; i<n; i++) {
	    z[i] = new double[n];
	    for(int j=0; j<n; j++) {
		z[i][j] = count(a[i], a[j]);
	    }
	}
	return z;	
    }

    /** @param z The raw matrix
	@return w[i][j] = (z[i][j]+1)/(z[j][i]+1)
     */
    public static double[][] ratioMatrix(double z[][]) {
	final int n = z.length;
	double[][] w = new double[n][];
	for(int j=0; j<n; j++) {
	    w[j] = new double[n];
	}
	    
	for(int j=0; j<n; j++) {
	    w[j][j] = 1;
	    for(int i=j; i<n; i++) {
		double r = (z[j][i]+1)/(z[i][j]+1),
		    ri = (z[i][j]+1)/(z[j][i]+1);
		w[j][i] = r;
		w[i][j] = ri;
	    }	
	}
	return w;	
    }

    /** Given a dense matrix with positive elements, find the eigenvector
	corresponding to the largest eigenvalue */
    static public double[] topEigenVector(double a[][]) {
	int n = a.length;
	double[] x = new double[n], y = new double[n];
	double x0 = 1.0/Math.sqrt(n);
	for(int j=0; j<n; j++) x[j]=x0;

	int cnt = 0;
	double lambda;
	while(true) {
	    double sq = 0;
	    for(int j=0; j<n; j++) {
		double s=0;
		for(int i=0; i<n; i++) s += a[j][i]*x[i];
		y[j]=s;
		sq += s*s;
	    }
	    lambda = Math.sqrt(sq);
	    double r = 0;
	    for(int j=0; j<n; j++) {
		y[j] /= lambda;
		double d  = y[j]-x[j];
		r *= d*d;
	    }	    
	    cnt ++;
	    double[] tmp = x;
	    x = y;
	    y = tmp;
	    if (r < 1e-8) break;
	}
	System.out.println("EV converged in " + cnt + " iterations, lambda=" + lambda);
	return x;
    }



    /** 
	@param argv   a,b,c,d e,f,g
     */
    public static void test1(String argv[]) {
	int [][]x=new int[2][];
	for(int k=0; k<2; k++) {
	    String q[] = argv[k].split(",");
	    x[k] = new int[q.length];
	    for(int j=0; j<q.length; j++) x[k][j] = Integer.parseInt(q[j]);
	}
	double c1 = count(x[0], x[1]);
	double c2 = count(x[1], x[0]);
	System.out.println("" + c1 + " + " + c2);
    }


    /** 
	@param argv   a,b,c,d e,f,g  h,i,j,k,l ....
    */
    public static void test2(String argv[]) {
	final int n = argv.length;
	
	int [][]x=new int[n][];
	for(int k=0; k<n; k++) {
	    String q[] = argv[k].split(",");
	    x[k] = new int[q.length];
	    for(int j=0; j<q.length; j++) x[k][j] = Integer.parseInt(q[j]);
	}
	double[][] z = rawMatrix(x);

	System.out.println("Raw matrix");
	for(int k=0; k<n; k++) {
	    String s = Util.joinNonBlank("\t", z[k]);
	    System.out.println(s);
	}

	double[][] w = ratioMatrix(z);
	System.out.println("Ratio matrix");
	for(int k=0; k<n; k++) {
	    String s = Util.joinNonBlank("\t", w[k]);
	    System.out.println(s);
	}
	
    }


    
    public static void main(String argv[]) {
	test2(argv);
    }
}
