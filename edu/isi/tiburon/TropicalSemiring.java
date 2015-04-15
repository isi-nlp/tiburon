package edu.isi.tiburon;

// tropical is min, +, +INF, 0
public class TropicalSemiring extends Semiring {
	public double plus(double a, double b) {
		return Math.min(a, b);
	}
	public double minus(double a, double b) {
		Debug.debug(true, "Error: subtraction not supported in tropical semiring");
		return a;
	}
	public  double times(double a, double b) {
		return a+b;
	}
	public  double inverse(double a) {
		return -a;
	}
	// fewest paths always the best
	public double star(double a) throws UnusualConditionException {
		return ONE();
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
