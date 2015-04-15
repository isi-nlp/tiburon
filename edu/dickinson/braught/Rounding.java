// note: believe it or not, you have to wait till java 1.5 for rounding, so i'll just use this code.
package edu.dickinson.braught;

/**
 * Rounding.java - 
 *   Program that uses two overloaded methods to round doubles
 *   or floats to a specified number of decimal places.
 *
 * @author Grant William Braught
 * @author Dickinson College
 * @version 2/14/2001
 */
public class Rounding {

    // test function
    public static void main (String[] args) {
	double x = 1.23456789;
	float y = 9.87654f;
	double z;
	float w;

	z = round(x,2);
	System.out.println(z);
	z = round(x,5);
	System.out.println(z);

	System.out.println();

	w = round(y,3);
	System.out.println(w);
	w = round(y,0);
	System.out.println(w);
    }

    /**
     * Round a double value to a specified number of decimal 
     * places. If value is < 0, round so that that many decimal
     * places may be seen in scientific expansion (added by jm, 11/11)
     *
     * @param val the value to be rounded.
     * @param places the number of decimal places to round to.
     * @return val rounded to places decimal places.
     */
    public static double round(double val, int places) {
	if (val == 0.0)
	    return val;
	System.out.println("Starting with "+val);
	long factor = (long)Math.pow(10,places);
	System.out.println("Factor of "+factor);
	// Shift the decimal the correct number of places
	// to the right.
	val = val * factor;

	// continue shifting if the shifted integer is too small
	while (Math.abs(val) < Math.pow(10, places-1)) {
	    System.out.println("Shifting "+val);
	    val *= 10;
	    factor *= 10;
	    System.out.println("Factor to "+factor);
	}

	// Round to the nearest integer.
	long tmp = Math.round(val);
	System.out.println("Rounding "+val+" to "+tmp);
	double retval = (double)tmp / factor;
	System.out.println("After dividing by "+factor+", returning "+retval);
	// Shift the decimal the correct number of places
	// back to the left.
	return (double)tmp / factor;
    }

    /**
     * Round a float value to a specified number of decimal 
     * places.
     *
     * @param val the value to be rounded.
     * @param places the number of decimal places to round to.
     * @return val rounded to places decimal places.
     */
    public static float round(float val, int places) {
	return (float)round((double)val, places);
    }
}

