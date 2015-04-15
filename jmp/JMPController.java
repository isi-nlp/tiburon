package jmp;

/** This is a class that can be used to control the jmp profiler from within java code.
 *
 */
public class JMPController {
    static {
	System.loadLibrary ("jmp");
    }

    /** Ask jmp to perform a data dump.
     * @return the id of the file data was dumped to.
     */
    public static native int runDataDump ();
    
    /** Ask jmp to perform a heap dump. */
    public static native void runHeapDump ();

    /** Ask jmp to perform a string dump. */
    public static native void runStringDump ();
    
    /** This will run the garbage collector. 
     *  According to the specification this type of gc will 
     *  actually be guaranteed to run the garbage collector.
     */
    public static native void runGC ();

    /** Enable object events. */
    public static native void enableObjectEvents ();

    /** Disable object events. */
    public static native void disableObjectEvents ();

    /** Enable method events. */
    public static native void enableMethodEvents ();

    /** Disable method events. */
    public static native void disableMethodEvents ();

    /** Enable monitor events. */
    public static native void enableMonitorEvents ();

    /** Disable monitor events. */
    public static native void disableMonitorEvents ();

    /** Reset all counters to zero. 
     *  This will cause all current counters to be zero, 
     *  however no information is lost, calling restore will 
     *  bring back the old information.
     *
     *  Calling reset multiple times is allowed and it will store
     *  information correctly. Reset is not a stack operation, that
     *  is one restore will bring back all information saved from 
     *  all resets.
     */
    public static native void resetCounters ();

    /** Restores all information from previous resets.
     * @see #resetCounters()
     */
    public static native void restoreCounters ();
}
