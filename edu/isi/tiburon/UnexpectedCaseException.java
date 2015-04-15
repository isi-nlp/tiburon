package edu.isi.tiburon;
/** for errors of an unexpected type */

public class UnexpectedCaseException extends Exception {
    /**          Constructs a new exception with null as its detail message. */
    public UnexpectedCaseException() { super(); }
    /**      Constructs a new exception with the specified detail message. */
    public UnexpectedCaseException(String message) { super(message); }
    /**      Constructs a new exception with the specified detail message and cause.    */
    public UnexpectedCaseException(String message, Throwable cause) { super(message, cause); }
    /**     Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). */
    public UnexpectedCaseException(Throwable cause) { super(cause); } 
}
