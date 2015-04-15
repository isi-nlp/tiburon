package edu.isi.tiburon;
/** for errors of conversion, like epsilons in an epsilon-free form, etc. */
public class ImproperConversionException extends Exception {
    /**          Constructs a new exception with null as its detail message. */
    public ImproperConversionException() { super(); }
    /**      Constructs a new exception with the specified detail message. */
    public ImproperConversionException(String message) { super(message); }
    /**      Constructs a new exception with the specified detail message and cause.    */
    public ImproperConversionException(String message, Throwable cause) { super(message, cause); }
    /**     Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). */
    public ImproperConversionException(Throwable cause) { super(cause); } 
}
