/*
 * @(#)Heap.java	1.0 2/23/96 Michael J. Radwin
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

import java.util.NoSuchElementException;
import mjr.treegraphics.*;

/**
 * A priority queue according to Cormen, Leiserson and Rivest.
 * <p>
 * The heap can be constructed in O(N) time by copying an array, or
 * O(N log N) time by staring with an empty heap and performing N
 * insertions.
 * <p>
 * The heap is visualized with a call to TreeGraphics.
 * @see TreeGraphics
 * @version 1.0 2/23/96
 * @author <A HREF="http://www.radwin.org/michael/">Michael J. Radwin</A>
 */
public
class Heap {
    HeapImpl theHeap;
    
    /**
     * Constructs the heap by copying an unordered array.  Sorts keys
     * in descending order if descending is true, ascending order
     * otherwise.  Takes O(N) time.
     */
    public Heap(Heapable anArray[], boolean descending)
    {
	super();
	if (descending)
	    theHeap = new HeapDescending(anArray);
	else
	    theHeap = new HeapAscending(anArray);
    }

    /**
     * Constructs the heap by copying an unordered array.  Sorts keys
     * in descending order. Takes O(N) time.
     */
    public Heap(Heapable anArray[])
    {
	this(anArray, true);
    }

    
    /**
     * Constructs a heap with the given sorting order. Takes O(N) time.
     *
     * @param descending true if keus should be sorted in descending order.
     */
    public Heap(boolean descending)
    {
	super();
	if (descending)
	    theHeap = new HeapDescending();
	else
	    theHeap = new HeapAscending();
    }

    /**
     * Constructs a heap with keys sorted in descending order.
     * Takes O(N) time.
     */
    public Heap()
    {
	this(true);
    }

    /**
     * Returns true if there are no keys in the heap, false otherwise.
     * Takes O(1) time.
     */
    public boolean isEmpty()
    {
 	return theHeap.isEmpty();
    }

    /**
     * Returns the number of keys in the heap.
     * Takes O(1) time.
     */
    public int size()
    {
	return theHeap.size();
    }
    
    /**
     * Removes all keys from the heap.
     * Takes O(N) time.
     */
    public synchronized void clear()
    {
	theHeap.removeAllElements();
    }

    /**
     * Removes the top key from the heap.
     * Takes O(N log N) time.
     */
    public synchronized Heapable remove() throws NoSuchElementException
    {
	return theHeap.remove();
    }
    
    /**
     * Inserts a key into the heap.
     * Takes O(N log N) time.
     */
    public synchronized void insert(Heapable key)
    {
	theHeap.insert(key);
    }

    /**
     * Visualizes every key in the heap using calls to TreeGraphics.
     * Takes O(N) time.
     */
    public void visualize(TreeGraphics tg)
    {
	theHeap.preorder(0, tg);
    }
}
