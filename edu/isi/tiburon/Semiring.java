package edu.isi.tiburon;
import java.io.Serializable;
// the general semiring. Subclasses do the operations
public abstract class Semiring implements Serializable {
    public  abstract double plus(double a, double b);
    public  abstract double minus(double a, double b);
    public  abstract double times(double a, double b);
    // for closure
    public abstract double star(double a) throws UnusualConditionException;
    public  abstract double inverse(double a);
    // better means "closer to one"...sort of
    public abstract boolean better(double a, double b);
    // only exists for the lame zero thing
    public abstract boolean betteroreq(double a, double b);
    public abstract double ONE();
    public abstract double ZERO();
    // assuming that the "real" semiring is standard, we may want to convert from it
    public abstract double convertFromReal(double a);
    // so we can represent things differently on the inside
    public abstract double internalToPrint(double a);
    public abstract double printToInternal(double a);

//     // a standard amount of rounding
//     protected double round(double a) {
// 				return a;
// 				//	double mult = Math.pow(10.0d, 16);
// 				//	return Math.round(a*mult)/mult;
//     }
}
