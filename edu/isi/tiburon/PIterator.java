package edu.isi.tiburon;

import java.util.Iterator;
import java.util.NoSuchElementException;


public interface PIterator<E> extends Iterator<E> {
	public E peek() throws NoSuchElementException;
}
