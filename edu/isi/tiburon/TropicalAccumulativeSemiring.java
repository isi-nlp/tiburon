package edu.isi.tiburon;

// tropical accumulative is special form of addition, +, +INF, 0
public class TropicalAccumulativeSemiring extends Semiring {
	//     static private Hashtable expmap;
	//     static private Hashtable logmap;
	//     static {
	// 	expmap = new Hashtable();
	// 	logmap = new Hashtable();
	//     }
	static private int TOLERANCE=16;
	public double plus(double a, double b) {
		// from jon's code...not sure if that's right, though...
		if (a == ZERO()) return b;
		if (b == ZERO()) return a;

		double x, y;
		if ((-a) > (-b)) {
			x = -a;
			y = -b;
		}
		else {
			x = -b;
			y = -a;
		}
		// x>=b. If x>>b, estimate as a
		if (x >= y+TOLERANCE)
			return -x;
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

	public double minus(double a, double b) {
		boolean debug = false;
		if (a == ZERO()) {
			if (debug) Debug.debug(debug, "Error: tried to subtract from zero");
			return a;
		}
		if (b == ZERO()) {
			return b;
		}
		if (a == b) {
			return ZERO();
		}
		if (better(b, a)) {
			if (debug) Debug.debug(debug, "Error: tried to subtract to a negative");
			return a;
		}
		double x = -a;
		double y = -b;
		if (x >= y+TOLERANCE)
			return -x;
		double diff = x-y;
		double total = Math.expm1(diff);
		double logtotal = Math.log(total);
		return -(x+logtotal);
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
	public double convertFromReal(double a) {
		return -Math.log(a);
	}
	public double ZERO(){return  Double.POSITIVE_INFINITY;}
	public double ONE() {
		return 0;
	}
	public double internalToPrint(double a) { return a;}
	public double printToInternal(double a) { return a;}
}
