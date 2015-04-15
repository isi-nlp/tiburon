/*
 * @(#)Heapable.java	1.0 2/23/96 Michael J. Radwin
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

/**
 * An interface for keys in the heap.  Allows the heap to make
 * comparisons to upheap or downheap.
 *
 * @see Heap
 * @version 1.0 2/23/96
 * @author <A HREF="http://www.radwin.org/michael/">Michael J. Radwin</A>
 */
public
interface Heapable {
    /**
     * Determines if this key is greater than the other key.
     * For example, to compare keys that are subclasses of
     * Integer:
     * <pre>
     *     return (intValue() > ((Integer)other).intValue());
     * </pre>
     *
     * @return true if this key is greater than the other key
     * @param other the key to compare this key to.
     */
    public boolean greaterThan(Object other);

    /**
     * Determines if this key is less than the other key.
     * For example, to compare keys that are subclasses of
     * Integer:
     * <pre>
     *     return (intValue() < ((Integer)other).intValue());
     * </pre>
     *
     * @return true if this key is less than the other key
     * @param other the key to compare this key to.
     */
    public boolean lessThan(Object other);

    /**
     * Determines if this key is equal to the other key.
     * For example, to compare keys that are subclasses of
     * Integer:
     * <pre>
     *     return (intValue() == ((Integer)other).intValue());
     * </pre>
     *
     * @return true if this key is equal to the other key
     * @param other the key to compare this key to.
     */
    public boolean equalTo(Object other); 

    // heapables need some notion of where they are
    public void setPos(int i);
    public int getPos();

}
