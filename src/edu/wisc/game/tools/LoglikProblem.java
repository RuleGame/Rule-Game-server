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
	p(t) = B + 0.5*(C-B)*(1+tanh(u/2)) =
              B + (C-B)/(1+exp(-u)) = 
             ( B*exp(-u) + C)/(1+exp(-u))
	L(v) = sum_t ( y(t) log p(t) + (1-y(t)) log(1 - p(t))
     */
class LoglikProblem
//implements DifferentiableMultivariateFunction//, Serializable
{


    static boolean verbose=false;
    
    final int[] y;
    LoglikProblem(	int[] _y) {
	y = _y;
    }

    
    static final double eps = 1e-6, M=1000;

    static double regLog(double x) {
	double s = (x>eps)? Math.log(x):
	    Math.log(eps) + (x-eps)/eps;
	if (x>1) s += -M*(1-x)*(1-x);
	return s;
    }

    static double regLogDerivative(double x) {
	double s = (x>eps)?  1/x : 1/eps;
	if (x>1) s += M*2*(1-x);
	return s;
    }

   final static DecimalFormat df = new DecimalFormat("0.000");

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
			double p = B/(1+rex) + C/(1 + ex);
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
			double p = B/(1+rex) + C/(1 + ex);



			//double r = (y[t]-p)/(p*(1-p));
			double r = (y[t]==1)? regLogDerivative(p):
			    -regLogDerivative(1-p);
			
			
			//sum[0] += r*ex/(1+ex);
			sum[0] += r/(1+rex);
			sum[1] += r/(1+ex);
			//double z= r*(C-B)*ex/((1+ex)*(1+ex));
			double z= r*(C-B)/(2+ex+rex);
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
    
