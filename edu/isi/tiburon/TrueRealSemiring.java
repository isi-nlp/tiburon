package edu.isi.tiburon;

// real is +, *, 0, 1
// lots of underflows, but useful for checking the functionality of RealSemiring
public class TrueRealSemiring extends Semiring {

	public TrueRealSemiring() {

	}
	public double plus(double a, double b) {
		return a+b;
	}
	public double minus(double a, double b) {
		return a-b;
	}
	public  double times(double a, double b) {
		double prod = a*b;
		if (prod == 0 && a > 0 && b > 0) {
			//    System.err.println(a+" * "+b+" = "+prod);
			if (a > b)
				return b;
			return a;
		}
		return a*b;
	}
	public  double inverse(double a) {
		return 1/a;
	}
	// can be infinity if divergent (i.e. if < 0)!
	public double star(double a) throws UnusualConditionException {
		if (a >-1 && a < 1)
			return 1/(1-a);
		else if (a > 1)
			return Double.POSITIVE_INFINITY;
		else throw new UnusualConditionException("Tried to take star of "+a);
	}
	public boolean better(double a, double b) {
		return a>b;
	}
	public boolean betteroreq(double a, double b) {
		return a>=b;
	}
	public double convertFromReal(double a) {
		return a;
	}
	public double ZERO(){return 0;}
	public double ONE() {return 1;}


	public double internalToPrint(double a) { return a;}
	public double printToInternal(double a) { return a;}
}
