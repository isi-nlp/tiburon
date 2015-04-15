package edu.isi.tiburon;
/** for erros in the configuration scheme, like undefined 
    constants, illegal combinations, etc. */
public class ConfigureException extends Exception {
    /**          Constructs a new exception with null as its detail message. */
    public ConfigureException() { super(); }
    /**      Constructs a new exception with the specified detail message. */
    public ConfigureException(String message) { super(message); }
    /**      Constructs a new exception with the specified detail message and cause.    */
    public ConfigureException(String message, Throwable cause) { super(message, cause); }
    /**     Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). */
    public ConfigureException(Throwable cause) { super(cause); } 
}
