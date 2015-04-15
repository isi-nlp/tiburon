package edu.isi.tiburon;

import java.util.Iterator;
import java.util.NoSuchElementException;

// peekable iterator
public class WrappedPIterator <E extends Object> implements PIterator<E> {
	private Iterator<E> it;
	private E next;
	public WrappedPIterator (Iterator<E> iterator) {
		it = iterator;
		boolean debug = false;
		if (it.hasNext()) {
			next = it.next();
			if (debug) Debug.debug(debug, "Initiated iterator with item "+next);
		}
		else
			next = null;
	}
	// special version for single item
	public WrappedPIterator(E item) {
		next = item;
//		Debug.debug(true, "Stored "+next+" in empty iterator");
		it = null;
	}
	public boolean hasNext() {
		return next != null;
	}
	public E next() throws NoSuchElementException {
		boolean debug = false;
		if (next == null)
			throw new NoSuchElementException("Asked for next on empty PIterator");
		E ret = next;
		if (debug) Debug.debug(debug, "Going to return "+ret);
		if (it != null && it.hasNext())
			next = it.next();
		else
			next = null;
		return ret;
	}
	public void remove() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("Didn't bother with remove for PIterator");
	}
	public E peek() throws NoSuchElementException {
		if (next == null)
			throw new NoSuchElementException("Asked for peek on empty PIterator");
		return next;
	}

}
