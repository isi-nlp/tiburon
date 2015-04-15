package edu.isi.tiburon;
/** for errors where something funny happened */

public class UnusualConditionException extends Exception {
    /**          Constructs a new exception with null as its detail message. */
    public UnusualConditionException() { super(); }
    /**      Constructs a new exception with the specified detail message. */
    public UnusualConditionException(String message) { super(message); }
    /**      Constructs a new exception with the specified detail message and cause.    */
    public UnusualConditionException(String message, Throwable cause) { super(message, cause); }
    /**     Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). */
    public UnusualConditionException(Throwable cause) { super(cause); } 
}
