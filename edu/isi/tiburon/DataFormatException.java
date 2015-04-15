package edu.isi.tiburon;
/** for errors in the data format of files */

public class DataFormatException extends Exception {
    /**          Constructs a new exception with null as its detail message. */
    public DataFormatException() { super(); }
    /**      Constructs a new exception with the specified detail message. */
    public DataFormatException(String message) { super(message); }
    /**      Constructs a new exception with the specified detail message and cause.    */
    public DataFormatException(String message, Throwable cause) { super(message, cause); }
    /**     Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). */
    public DataFormatException(Throwable cause) { super(cause); } 
}
