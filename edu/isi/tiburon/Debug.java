package edu.isi.tiburon;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
// debugging things. useful static methods and the like
public class Debug {

    static String encoding = "utf-8";
    public static void setEncoding(String s) {
	encoding = s;
	initializeStream();
    }

    private static OutputStreamWriter w=null;
    private static void initializeStream() {
	try {
	    w = new OutputStreamWriter(System.err, encoding);
	}
	catch (UnsupportedEncodingException e) {
	    System.err.println("Warning: encoding "+encoding+" not supported; using default");
	    w = new OutputStreamWriter(System.err);
	}
    }
    
    // for debugging

    // stuff we always print to stderr
    public static void prettyDebug(String s) {
	if (w == null)
	    initializeStream();
	try {
	    w.write(s+"\n");
	    w.flush();
	}
	catch (IOException e) {
	    System.err.println("IOException while trying to print "+s);
	}
    }
    // true debugging stuff
    public static void debug(boolean d, String s)  {
	debug(d, 0, ""+(new Throwable().getStackTrace()[1].getClassName())+":"+(new Throwable().getStackTrace()[1].getMethodName()), s);
    }
    public static void debug(boolean d, int i, String s) {
	debug(d, i, ""+(new Throwable().getStackTrace()[1].getClassName())+":"+(new Throwable().getStackTrace()[1].getMethodName()), s);
    }
    private static void debug(boolean d, int i, String caller, String s) {   
	if (w == null)
	    initializeStream();
	if (d) {
	    try {
		for (int x = 0; x < i; x++)
		    w.write(" ");
		w.write(caller+" : "+s+"\n");
		w.flush();
	    }
	    catch (IOException e) {
		System.err.println("IOException while trying to print "+s);
	    }
	}
    }
    private static int dblevel=0;
    public static void setDbLevel(int i) {
	dblevel = i;
    }
    // print time debug info if the level is proper
    public static void dbtime(int currlevel, int needlevel, Date pta, Date ptb, String msg) {
	if (w == null)
	    initializeStream();
	if (currlevel < needlevel)
	    return;
	long x = ptb.getTime() - pta.getTime();
	try {
	    w.write(msg+": "+x+" ms\n");
	    w.flush();
	}
	catch (IOException e) {
	    System.err.println("IOException while trying to print "+msg);
	}
    }

    // global version of dbtime
    public static void dbtime(int needlevel, Date pta, String msg) {
	if (w == null)
	    initializeStream();
	Date ptb = new Date();
	if (dblevel < needlevel)
	    return;
	long x = ptb.getTime() - pta.getTime();
	try {
	    w.write(msg+": "+x+" ms\n");
	}
	catch (IOException e) {
	    System.err.println("IOException while trying to print "+msg);
	}
    }

}
