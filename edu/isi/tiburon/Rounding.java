
package edu.isi.tiburon;

import java.text.DecimalFormat;
/**
 * Rounding.java - 
 *   Program that uses text formatting to round doubles
 *   or floats to a specified number of decimal places.
 *
 * @author Jonathan May
 * @version 11/11/2005
 */
public class Rounding {

    // test function
    public static void main (String[] args) {
	double x = 1.23456789;
	double y = 0.00000463999898;

	String z;

	
	z = round(x,4);
	Debug.debug(true, z);
	z = round(y,4);
	Debug.debug(true, z);
    }

    /**
     * Round a double value to a specified number of decimal 
     * places. If value is < 0, round so that that many decimal
     * places may be seen in scientific expansion (added by jm, 11/11)
     *
     * @param val the value to be rounded.
     * @param places the number of decimal places to round to.
     * @return string version of val rounded to places decimal places.
     */
    public static String round(double val, int places) {
	StringBuffer lpattern = new StringBuffer("0.");
	StringBuffer spattern = new StringBuffer("0.");
	for (int i = 0; i < places; i++) {
	    lpattern.append('0');
	    spattern.append('#');
	}
	spattern.append("E0");
	DecimalFormat largeform = new DecimalFormat(lpattern.toString());
	DecimalFormat smallForm = new DecimalFormat(spattern.toString());
	if (val == 0.0)
	    return "0.0";
	if (Math.abs(val) < Math.pow(10, -places))
	    return smallForm.format(val).toString();
	return largeform.format(val).toString();

    }

    /**
     * Round a float value to a specified number of decimal 
     * places.
     *
     * @param val the value to be rounded.
     * @param places the number of decimal places to round to.
     * @return string version of val rounded to places decimal places.
     */
    public static String round(float val, int places) {
	return round((double)val, places);
    }
}

