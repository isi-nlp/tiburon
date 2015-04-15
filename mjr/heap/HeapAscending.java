/*
 * @(#)HeapAscending.java	1.0 2/23/96 Michael J. Radwin
 *
 *  Copyright (c) 1996  Michael J. Radwin.
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or
 *  without modification, are permitted provided that the following
 *  conditions are met:
 * 
 *   * Redistributions of source code must retain the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 * 
 *   * Neither the name of Radwin.org nor the names of its
 *     contributors may be used to endorse or promote products
 *     derived from this software without specific prior written
 *     permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 *  CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 *  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 *  HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package mjr.heap;

import java.util.Vector;
import java.util.NoSuchElementException;
import mjr.treegraphics.*;

/**
 * An implementation of a priority queue according to Cormen,
 * Leiserson and Rivest.  Sorts in ascending order.
 *
 * @version 1.0 2/23/96
 * @author <A HREF="http://www.radwin.org/michael/">Michael J. Radwin</A> */

// extension - decrease key. to do this the heapables need to have some notion of where
// they are.

public
class HeapAscending extends Vector implements HeapImpl {
    /**
     * Constructs the heap in O(N) time, using a technique similar to
     * bottom-up construction.
     */
    public HeapAscending(Heapable anArray[])
    {
	super(anArray.length);
	setSize(anArray.length);

	for (int i = 0; i < anArray.length; i++) {
	    setElementAt(anArray[i], i);
	    anArray[i].setPos(i);
	}
	
	for (int i = (int)Math.floor(size() / 2) - 1; i >= 0; i--)
	    heapify(i);
    }

    /**
     * Constructs a heap with no elements.
     */
    public HeapAscending()
    {
	super(0);
    }
    
    /**
     * Returns the Vector index of the left child
     */
    protected int left(int i)
    {
	return ((i + 1) << 1) - 1;
//	return (2 * (i + 1)) - 1;
    }

    /**
     * Returns the Vector index of the right child
     */
    protected int right(int i)
    {
	return ((i + 1) << 1);
//	return (2 * (i + 1));
    }

    /**
     * Returns the Vector index of the parent
     */
    protected int parent(int i)
    {
	return ((i + 1) >> 1) - 1;
//	return (int)Math.floor((i + 1) / 2) - 1;
    }

    /**
     * Exchanges the elements stored at the two locations
     */
    protected synchronized void exchange(int i, int j)
    {
	Object temp = elementAt(j);
	setElementAt(elementAt(i), j);
	setElementAt(temp, i);
	((Heapable)elementAt(i)).setPos(i);
	((Heapable)elementAt(j)).setPos(j);
    }

    /**
     * Also known as downheap, restores the heap condition
     * starting at node i and working its way down.
     */
    protected synchronized void heapify(int i)
    {
	int l = left(i);
	int r = right(i);
	int smallest;

	if (l < size() &&
	    ((Heapable)elementAt(l)).lessThan(elementAt(i)))
	    smallest = l;
	else
	    smallest = i;

	if (r < size() &&
	    ((Heapable)elementAt(r)).lessThan(elementAt(smallest)))
	    smallest = r;

	if (smallest != i) {
	    exchange(i, smallest);
	    heapify(smallest);
	}
    }

    /**
     * Removes the minimum (top) element from the Heap, decreases the
     * size of the heap by one, and returns the minimum element.
     */
    public synchronized Heapable extractMin() throws NoSuchElementException
    {
	if (size() == 0)
	    throw new NoSuchElementException();

	Object min = elementAt(0);

	// move the last key to the top, decrease size, and downheap
	setElementAt(lastElement(), 0);
	((Heapable)elementAt(0)).setPos(0);
	((Heapable)elementAt(size()-1)).setPos(-1);
	removeElementAt(size() - 1);
	heapify(0);

	return (Heapable)min;
    }

    /**
     * Removes an element from the heap.
     */
    public Heapable remove() throws NoSuchElementException
    {
	return extractMin();
    }


    /**
     * Inserts key into the heap, and then upheaps that key to a
     * position where the heap property is satisfied.
     */
    public synchronized void insert(Heapable key)
    {
	int i = size();
	setSize(size() + 1);

	// upheap if necessary
	while (i > 0 && ((Heapable)elementAt(parent(i))).greaterThan(key)) {
	    setElementAt(elementAt(parent(i)), i);
	    ((Heapable)elementAt(i)).setPos(i);
	    i = parent(i);
	}
	
	setElementAt(key, i);
	((Heapable)elementAt(i)).setPos(i);
    }

    /**
     * Decreases a key that is already in the heap, if applicable, and then
     * and then upheaps that key to a position where the heap property is
     * satisfied (new implementation by jon may)
     */
    public synchronized void decrease(Heapable key, Heapable newKey)
    {
	if (key.getPos() < 0)
	    return;
	if (!newKey.lessThan(key))
	    return;

	setElementAt(newKey, key.getPos());
	newKey.setPos(key.getPos());
	key.setPos(-1);
	// upheap if necessary
	int i = newKey.getPos();
	while (i > 0 && ((Heapable)elementAt(parent(i))).greaterThan(newKey)) {
	    exchange(i, parent(i));
	    i = parent(i);
	}
    }

    /**
     * Performs a preorder traversal of the heap, calling
     * tg.DrawInternal on every key and tg.DrawLeaf for every child
     * that exceeds the length of the heap (and is therefore a "leaf")
     */
    public void preorder(int i, TreeGraphics tg)
    {
	if (i >= size()) {
	    tg.DrawLeaf("red");
	    return;
	}
	
	tg.DrawInternal(elementAt(i).toString(), "blue");
	preorder(left(i), tg);
	preorder(right(i), tg);
    }
}
