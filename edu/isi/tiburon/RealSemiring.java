package edu.isi.tiburon;

// real is +, *, 0, 1
public class RealSemiring extends Semiring {

	// NOTE: at one point I changed the offset from 16 to 50; this probably didn't help
	// so I changed it back, but if there are problems, look there...

	static private int TOLERANCE=16;
	public double plus(double a, double b) {
		// from jon's code...not sure if that's right, though...
		if (a >= ZERO()) return b;
		if (b >= ZERO()) return a;

		double x, y;
		if ((-a) > (-b)) {
			x = -a;
			y = -b;
		}
		else {
			x = -b;
			y = -a;
		}
		// x>=y. If x>>y, estimate as x

		if (x >= y+TOLERANCE) {
			//   Debug.prettyDebug("TOLERANCE shift hit");
			return -x;
		}


		double diff = y-x;
		double diffexp = Math.exp(diff);
		// 	Double diffobj = new Double(diff);
		// 	if (expmap.containsKey(diffobj)) {
		// 	    double comp = ((Double)expmap.get(diffobj)).doubleValue();
		// 	    if (comp != diffexp) {
		// 		Debug.debug(true, "Error: Exp of "+diff+" was originally "+comp+", now it's "+diffexp);
		// 		System.exit(0);
		// 	    }
		// 	}
		// 	else {
		// 	    expmap.put(diffobj, new Double(diffexp));
		// 	}

		double logtotal = Math.log1p(diffexp);
		// 	Double totalobj = new Double(total);
		// 	if (logmap.containsKey(totalobj)) {
		// 	    double comp = ((Double)logmap.get(totalobj)).doubleValue();
		// 	    if (comp != logtotal) {
		// 		Debug.debug(true, "Error: Log of "+total+" was originally "+comp+", now it's "+logtotal);
		// 		System.exit(0);
		// 	    }
		// 	}
		// 	else {
		// 	    logmap.put(totalobj, new Double(logtotal));
		// 	}
		return -(x + logtotal);
	}

	// TODO: add log1p fix here!!
	public double minus(double a, double b) {
		boolean debug = false;
		if (a >= ZERO()) {
			if (debug) Debug.debug(debug, "Error: tried to subtract from zero");
			return a;
		}
		if (b >= ZERO()) {
			return b;
		}
		if (a == b) {
			return ZERO();
		}
		if (better(b, a)) {
			if (debug)  Debug.debug(debug, "Error: tried to subtract to a negative; "+a+ " minus "+b);
			return ZERO();
		}
		double x = -a;
		double y = -b;
		if (x >= y+TOLERANCE)
			return -x;
		double diff = x-y;
		// more accurate than taking the exp then subtracting 1
		double total = Math.expm1(diff);
		double logtotal = Math.log(total);
		return -(y+logtotal);
	}

	public  double times(double a, double b) {
		return a+b;
	}
	public  double inverse(double a) {
		return -a;
	}

	// can be infinity if divergent (i.e. if < 0)!
	public double star(double a) throws UnusualConditionException{
		if (a >= ZERO())
			return ONE();
		else if (a > 0)
			return Math.log(1-Math.exp(-a));
		else
			return Double.NEGATIVE_INFINITY;
	}
	public boolean better(double a, double b) {
		return a<b;
	}
	public boolean betteroreq(double a, double b) {
		return a<=b;
	}
	// sort of redundant, now...
	public double convertFromReal(double a) {
		return -Math.log(a);
	}
	// should be pos inf, as below, but anything more than 745 leads to zero ultimately.
	// TODO: maybe have one zero for display, and one for internal calculations?
	//    public double ZERO(){return  Double.POSITIVE_INFINITY;}
	// anything more will convert to 0.0
	public double ZERO(){ return  745;}

	public double ONE() {
		return 0;
	}
	public double internalToPrint(double a) { return Math.exp(-a);}
	public double printToInternal(double a) { return -Math.log(a);}
}
