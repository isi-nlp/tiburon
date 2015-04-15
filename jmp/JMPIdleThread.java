package jmp;

/** This class is used to keep the VM open after the main application exits.
 *   This trick is done by creating a non-daemonized thread (which keeps the
 *   JVM running after the main application non-daemonized thread terminates)
 *   Then the profiler can call this Singleton's jmp.JMPIdleThread#stop()
 *   to make it wake up and terminate, causing the JVM itself to terminate.
 *
 *  In the future we need to consider failure scenarios, what-if JMP does not
 *   signal for this thread to stop() ?  If there anyway this thread can poll
 *   into the JMP code to confirm a watchdog value has been updated recently
 *   to make a decision that JMP is dead/hung and automatically kill this
 *   non-daemonized thread.  Otherwise the JVM might never exit.
 *
 *   This is a bigger problem for profiling on a remote JVM since you can't
 *    always see the process listing to see the old dead/hung JVMs are still
 *    running.
 *
 */
public class JMPIdleThread implements java.lang.Runnable {
    private static final String JMP_IDLE_THREAD_NAME = "jmp-shutdown-holder";
    //public Object lockProfiler;
    private Object lock;
    private int value;
    private long threadId;

    static {
    }

    public JMPIdleThread () {
        lock = new Object();
        //lockProfiler = new Object();
        value = -1;
    }

    public /*static*/ void stop () {
        synchronized(lock) {
            value = 1;
            lock.notifyAll ();
        }
    }

    public /*static*/ void waitForStartup () {
        while(true) {
            synchronized(lock) {
                if(value >= 0)
                    break;
                try {
                    lock.wait ();
                    break;
                } catch(java.lang.InterruptedException nop) {
                }
            }
        }
    }

    public /*static*/ void waitForStartup (long timeout, int nanos) {
        while(true) {
            synchronized(lock) {
                if(value >= 0)
                    break;
                try {
                    lock.wait (timeout, nanos);
                    break;
                } catch(java.lang.InterruptedException nop) {
                }
            }
        }
    }

    public /*static*/ void waitForShutdown () {
        while(true) {
            synchronized(lock) {
                if(value >= 2)
                    break;
                try {
                    lock.wait ();
                    break;
                } catch(java.lang.InterruptedException nop) {
                }
            }
        }
    }

    public /*static*/ void waitForShutdown (long timeout, int nanos) {
        while(true) {
            synchronized(lock) {
                if(value >= 2)
                    break;
                try {
                    lock.wait (timeout, nanos);
                    break;
                } catch(java.lang.InterruptedException nop) {
                }
            }
        }
    }

    public /*static*/ long getThreadId () {
	synchronized(lock) {
		if (value < 0)
			return -1;
		return threadId;
	}
    }

    public void run () {
	synchronized(lock) {
		Thread.currentThread().setName(JMP_IDLE_THREAD_NAME);
	        /* We probably want to use the thread id later on, 
		 * but that method is java/5 only. 
		 * so we do not do that for now.
		 */
		/*threadId = Thread.currentThread().getId(); */
        	value = 0;
		lock.notifyAll ();
	}
        for(;;) {
            synchronized(lock) {
                if(value > 0)
                    break;
                try {
                    lock.wait ();
                    break;
                } catch(java.lang.InterruptedException nop) {
                }
            }
        }
        synchronized(lock) {
            value = 2;
            lock.notifyAll ();
        }
    }
}
