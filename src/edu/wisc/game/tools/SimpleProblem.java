package edu.wisc.game.tools;

import java.io.*;
import java.util.*;

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
class SimpleProblem
//implements DifferentiableMultivariateFunction//, Serializable
{
    final int[] y;
    SimpleProblem(	int[] _y) {
	y = _y;
    }

    static final double eps = 1e-6;

    static double regLog(double x) {
	double s = (x>eps)? Math.log(x):
	    Math.log(eps) + (x-eps)/eps;
	if (x>1) s += x*(1-x);
	return s;
    }

    static double regLogDerivative(double x) {
	double s = (x>eps)?  1/x : 1/eps;
	if (x>1) s += 1-2*x;
	return s;
    }

    
    public ObjectiveFunction getObjectiveFunction() {
	return new ObjectiveFunction(new MultivariateFunction() {
		public double value(double[] point) {
		    double B=point[0];//, C=point[1], tI = point[2], k=point[3];

		    //tI=0;
		    double sum=0;
		    for(int t=0; t<y.length; t++) {
			//double u = (t-tI) * k;
			//double ex = Math.exp(-u);
			double p = B;//( B*ex + C)/(1.0 + ex);
			double w = (y[t]==1) ? regLog(p):
			    regLog(1.0 - p);

			sum += w;
			//double d = (y[t]-p);
			//sum += -d*d;
		    }
		    return sum;
		}
	    });
    }


    public ObjectiveFunctionGradient getObjectiveFunctionGradient() {
	return new ObjectiveFunctionGradient(new MultivariateVectorFunction() {
		public double[] value(double[] point) {
		    double B=point[0];//, C=point[1], tI = point[2], k=point[3];
		    //tI=0;
		    
		    double[] sum=new double[point.length];
		    for(int t=0; t<y.length; t++) {
			//double u = (t-tI) * k;
			//double ex = Math.exp(-u);
			double p = B;//( B*ex + C)/(1.0 + ex);
			double r = (y[t]==1)? regLogDerivative(p):
			    -regLogDerivative(1-p);
			
			sum[0] += r;//r*ex/(1+ex);
			/*
			sum[1] += r/(1+ex);
			double z= r*(C-B)*ex/((1+ex)*(1+ex));
			sum[2] += 0; // -k*z;
			sum[3] += (t-tI)*z;
			*/

			//double d = (y[t]-p);
			//sum[0] += 2*d;

			
		    }
		    return sum;
		}
	    });
    }
		
	

}
    
