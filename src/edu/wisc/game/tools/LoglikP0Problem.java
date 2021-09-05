package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.text.*;

import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.*;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.*;
import org.apache.commons.math3.analysis.*;

import edu.wisc.game.util.*;

/** v={B,C,tI,k}
    u = (t-tI)*k
    p(t) = p0(t) + (1-p0(t)*g(t)
    g(t) = B + 0.5*(C-B)*(1+tanh(u/2)) =
    B + (C-B)/(1+exp(-u)) = 
       ( B*exp(-u) + C)/(1+exp(-u))
    L(v) = sum_t ( y(t) log p(t) + (1-y(t)) log(1 - p(t))
*/
class LoglikP0Problem extends LoglikProblem{

    final double[] p0;
    /** The number of points on which p0(t)==1, i.e. the move should be obvious
	to a frugal player. We exclude such point from consideration.
     */
    final int defect;
    LoglikP0Problem(	int[] _y, double[] _p0) {
	super(_y);
	p0 = _p0;
	int n = 0;
	for(double p: p0) {
	    if (p==1) n++;
	}
	defect = n;
    }

    /** The number of points over which L is computed */
    int size() { return y.length - defect; }
 
   
    /** Taking care to avoid NaN when doing ex/(1+ex) */
    public ObjectiveFunction getObjectiveFunction() { 
	return new ObjectiveFunction(new MultivariateFunction() {
		public double value(double[] point) {
		    final double B=point[0], C=point[1], tI = point[2], k=point[3];

		    double sum=0;
		    for(int t=0; t<y.length; t++) {
			double u = (t-tI) * k;
			double ex = Math.exp(-u);
			double rex = Math.exp(u);
			//double p = ( B*ex + C)/(1 + ex);
			double g = B/(1+rex) + C/(1 + ex);
			if (p0[t]==1.0) continue;
			double p = p0[t] + (1-p0[t])*g;
			
			sum += regLog(y[t]==1? p: 1-p);
		    }
		    if (verbose) System.out.println("f(" + Util.joinNonBlank(",", df, point)+
				       ") = " + df.format(sum));
		    return sum;
		}
	    });
    }


    public ObjectiveFunctionGradient getObjectiveFunctionGradient() {
	return new ObjectiveFunctionGradient(new MultivariateVectorFunction() {
		public double[] value(double[] point) {
		    final double B=point[0], C=point[1], tI = point[2], k=point[3];
		    
		    double[] sum=new double[point.length];
		    for(int t=0; t<y.length; t++) {
			double u = (t-tI) * k;
			double ex = Math.exp(-u);
			double rex = Math.exp(u);
			//double p = ( B*ex + C)/(1 + ex);
			double g = B/(1+rex) + C/(1 + ex);
			if (p0[t]==1.0) continue;
			double op = 1-p0[t];
			double p = p0[t] + op*g;
	
			//double r = (y[t]-p)/(p*(1-p));
			double r = (y[t]==1)? regLogDerivative(p):
			    -regLogDerivative(1-p);		
			
			sum[0] += op*r/(1+rex);
			sum[1] += op*r/(1+ex);
			//double z= r*(C-B)*ex/((1+ex)*(1+ex));
			double z= op*r*(C-B)/(2+ex+rex);
			sum[2] += -k*z;
			sum[3] += (t-tI)*z;
		    }
		    if (verbose) System.out.println("gradF(" + Util.joinNonBlank(",", df, point) +
				       ") = " + Util.joinNonBlank(",", df, sum));
		    return sum;
		}
	    });
    }
		
	
}
    
