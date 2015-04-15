///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package gnu.trove;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

/**
 * An open addressed Map implementation for Object keys and byte values.
 *
 * Created: Sun Nov  4 08:52:45 2001
 *
 * @author Eric D. Friedman
 * @version $Id: TObjectByteHashMap.java,v 2.0 2006/01/26 01:23:48 jonmay Exp $
 */
public class TObjectByteHashMap extends TObjectHash implements Serializable {

    /** compatible serialization ID - not present in 1.1b3 and earlier */
    static final long serialVersionUID = 1L;

    /** the values of the map */
    protected transient byte[] _values;

    /**
     * Creates a new <code>TObjectByteHashMap</code> instance with the default
     * capacity and load factor.
     */
    public TObjectByteHashMap() {
        super();
    }

    /**
     * Creates a new <code>TObjectByteHashMap</code> instance with a prime
     * capacity equal to or greater than <tt>initialCapacity</tt> and
     * with the default load factor.
     *
     * @param initialCapacity an <code>int</code> value
     */
    public TObjectByteHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Creates a new <code>TObjectByteHashMap</code> instance with a prime
     * capacity equal to or greater than <tt>initialCapacity</tt> and
     * with the specified load factor.
     *
     * @param initialCapacity an <code>int</code> value
     * @param loadFactor a <code>float</code> value
     */
    public TObjectByteHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    /**
     * Creates a new <code>TObjectByteHashMap</code> instance with the default
     * capacity and load factor.
     * @param strategy used to compute hash codes and to compare keys.
     */
    public TObjectByteHashMap(TObjectHashingStrategy strategy) {
        super(strategy);
    }

    /**
     * Creates a new <code>TObjectByteHashMap</code> instance whose capacity
     * is the next highest prime above <tt>initialCapacity + 1</tt>
     * unless that value is already prime.
     *
     * @param initialCapacity an <code>int</code> value
     * @param strategy used to compute hash codes and to compare keys.
     */
    public TObjectByteHashMap(int initialCapacity, TObjectHashingStrategy strategy) {
        super(initialCapacity, strategy);
    }

    /**
     * Creates a new <code>TObjectByteHashMap</code> instance with a prime
     * value at or near the specified capacity and load factor.
     *
     * @param initialCapacity used to find a prime capacity for the table.
     * @param loadFactor used to calculate the threshold over which
     * rehashing takes place.
     * @param strategy used to compute hash codes and to compare keys.
     */
    public TObjectByteHashMap(int initialCapacity, float loadFactor, TObjectHashingStrategy strategy) {
        super(initialCapacity, loadFactor, strategy);
    }

    /**
     * @return an iterator over the entries in this map
     */
    public TObjectByteIterator iterator() {
        return new TObjectByteIterator(this);
    }

    /**
     * initializes the hashtable to a prime capacity which is at least
     * <tt>initialCapacity + 1</tt>.  
     *
     * @param initialCapacity an <code>int</code> value
     * @return the actual capacity chosen
     */
    protected int setUp(int initialCapacity) {
        int capacity;

        capacity = super.setUp(initialCapacity);
        _values = new byte[capacity];
        return capacity;
    }

    /**
     * Inserts a key/value pair into the map.
     *
     * @param key an <code>Object</code> value
     * @param value an <code>byte</code> value
     * @return the previous value associated with <tt>key</tt>,
     * or null if none was found.
     */
    public byte put(Object key, byte value) {
        byte previous = (byte)0;
        int index = insertionIndex(key);
        boolean isNewMapping = true;
        if (index < 0) {
            index = -index -1;
            previous = _values[index];
            isNewMapping = false;
        }
        Object oldKey = _set[index];
        _set[index] = key;
        _values[index] = value;

        if (isNewMapping) {
            postInsertHook(oldKey == FREE);
        }
        return previous;
    }

    /**
     * rehashes the map to the new capacity.
     *
     * @param newCapacity an <code>int</code> value
     */
    protected void rehash(int newCapacity) {
        int oldCapacity = _set.length;
        Object oldKeys[] = _set;
        byte oldVals[] = _values;

        _set = new Object[newCapacity];
        Arrays.fill(_set, FREE);
        _values = new byte[newCapacity];

        for (int i = oldCapacity; i-- > 0;) {
          if(oldKeys[i] != FREE && oldKeys[i] != REMOVED) {
                Object o = oldKeys[i];
                int index = insertionIndex(o);
                if (index < 0) {
                    throwObjectContractViolation(_set[(-index -1)], o);
                }
                _set[index] = o;
                _values[index] = oldVals[i];
            }
        }
    }

    /**
     * retrieves the value for <tt>key</tt>
     *
     * @param key an <code>Object</code> value
     * @return the value of <tt>key</tt> or null if no such mapping exists.
     */
    public byte get(Object key) {
        int index = index(key);
        return index < 0 ? (byte)0 : _values[index];
    }

    /**
     * Empties the map.
     *
     */
    public void clear() {
        super.clear();
        Object[] keys = _set;
        byte[] vals = _values;

        for (int i = keys.length; i-- > 0;) {
            keys[i] = FREE;
            vals[i] = (byte)0;
        }
    }

    /**
     * Deletes a key/value pair from the map.
     *
     * @param key an <code>Object</code> value
     * @return an <code>byte</code> value
     */
    public byte remove(Object key) {
        byte prev = (byte)0;
        int index = index(key);
        if (index >= 0) {
            prev = _values[index];
            removeAt(index);    // clear key,state; adjust size
        }
        return prev;
    }

    /**
     * Compares this map with another map for equality of their stored
     * entries.
     *
     * @param other an <code>Object</code> value
     * @return a <code>boolean</code> value
     */
    public boolean equals(Object other) {
        if (! (other instanceof TObjectByteHashMap)) {
            return false;
        }
        TObjectByteHashMap that = (TObjectByteHashMap)other;
        if (that.size() != this.size()) {
            return false;
        }
        return forEachEntry(new EqProcedure(that));
    }

    private static final class EqProcedure implements TObjectByteProcedure {
        private final TObjectByteHashMap _otherMap;

        EqProcedure(TObjectByteHashMap otherMap) {
            _otherMap = otherMap;
        }

        public final boolean execute(Object key, byte value) {
            int index = _otherMap.index(key);
            if (index >= 0 && eq(value, _otherMap.get(key))) {
                return true;
            }
            return false;
        }

        /**
         * Compare two bytes for equality.
         */
        private final boolean eq(byte v1, byte v2) {
            return v1 == v2;
        }

    }

    /**
     * removes the mapping at <tt>index</tt> from the map.
     *
     * @param index an <code>int</code> value
     */
    protected void removeAt(int index) {
        super.removeAt(index);  // clear key, state; adjust size
        _values[index] = (byte)0;
    }

    /**
     * Returns the values of the map.
     *
     * @return a <code>Collection</code> value
     */
    public byte[] getValues() {
        byte[] vals = new byte[size()];
        byte[] v = _values;
        Object[] keys = _set;

        for (int i = v.length, j = 0; i-- > 0;) {
          if (keys[i] != FREE && keys[i] != REMOVED) {
            vals[j++] = v[i];
          }
        }
        return vals;
    }

    /**
     * returns the keys of the map.
     *
     * @return a <code>Set</code> value
     */
    public Object[] keys() {
        Object[] keys = new Object[size()];
        Object[] k = _set;

        for (int i = k.length, j = 0; i-- > 0;) {
          if (k[i] != FREE && k[i] != REMOVED) {
            keys[j++] = k[i];
          }
        }
        return keys;
    }

    /**
     * checks for the presence of <tt>val</tt> in the values of the map.
     *
     * @param val an <code>byte</code> value
     * @return a <code>boolean</code> value
     */
    public boolean containsValue(byte val) {
        Object[] keys = _set;
        byte[] vals = _values;

        for (int i = vals.length; i-- > 0;) {
            if (keys[i] != FREE && keys[i] != REMOVED && val == vals[i]) {
                return true;
            }
        }
        return false;
    }


    /**
     * checks for the present of <tt>key</tt> in the keys of the map.
     *
     * @param key an <code>Object</code> value
     * @return a <code>boolean</code> value
     */
    public boolean containsKey(Object key) {
        return contains(key);
    }

    /**
     * Executes <tt>procedure</tt> for each key in the map.
     *
     * @param procedure a <code>TObjectProcedure</code> value
     * @return false if the loop over the keys terminated because
     * the procedure returned false for some key.
     */
    public boolean forEachKey(TObjectProcedure procedure) {
        return forEach(procedure);
    }

    /**
     * Executes <tt>procedure</tt> for each value in the map.
     *
     * @param procedure a <code>TByteProcedure</code> value
     * @return false if the loop over the values terminated because
     * the procedure returned false for some value.
     */
    public boolean forEachValue(TByteProcedure procedure) {
        Object[] keys = _set;
        byte[] values = _values;
        for (int i = values.length; i-- > 0;) {
            if (keys[i] != FREE && keys[i] != REMOVED
                && ! procedure.execute(values[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Executes <tt>procedure</tt> for each key/value entry in the
     * map.
     *
     * @param procedure a <code>TOObjectByteProcedure</code> value
     * @return false if the loop over the entries terminated because
     * the procedure returned false for some entry.
     */
    public boolean forEachEntry(TObjectByteProcedure procedure) {
        Object[] keys = _set;
        byte[] values = _values;
        for (int i = keys.length; i-- > 0;) {
            if (keys[i] != FREE
                && keys[i] != REMOVED
                && ! procedure.execute(keys[i],values[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retains only those entries in the map for which the procedure
     * returns a true value.
     *
     * @param procedure determines which entries to keep
     * @return true if the map was modified.
     */
    public boolean retainEntries(TObjectByteProcedure procedure) {
        boolean modified = false;
        Object[] keys = _set;
        byte[] values = _values;
        for (int i = keys.length; i-- > 0;) {
            if (keys[i] != FREE
                && keys[i] != REMOVED
                && ! procedure.execute(keys[i],values[i])) {
                removeAt(i);
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Transform the values in this map using <tt>function</tt>.
     *
     * @param function a <code>TByteFunction</code> value
     */
    public void transformValues(TByteFunction function) {
        Object[] keys = _set;
        byte[] values = _values;
        for (int i = values.length; i-- > 0;) {
            if (keys[i] != FREE && keys[i] != REMOVED) {
                values[i] = function.execute(values[i]);
            }
        }
    }

    /**
     * Increments the primitive value mapped to key by 1
     *
     * @param key the key of the value to increment
     * @return true if a mapping was found and modified.
     */
    public boolean increment(Object key) {
        return adjustValue(key, (byte)1);
    }

    /**
     * Adjusts the primitive value mapped to key.
     *
     * @param key the key of the value to increment
     * @param amount the amount to adjust the value by.
     * @return true if a mapping was found and modified.
     */
    public boolean adjustValue(Object key, byte amount) {
        int index = index(key);
        if (index < 0) {
            return false;
        } else {
            _values[index] += amount;
            return true;
        }
    }


    private void writeObject(ObjectOutputStream stream)
        throws IOException {
        stream.defaultWriteObject();

        // number of entries
        stream.writeInt(_size);

        SerializationProcedure writeProcedure = new SerializationProcedure(stream);
        if (! forEachEntry(writeProcedure)) {
            throw writeProcedure.exception;
        }
    }

    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();

        int size = stream.readInt();
        setUp(size);
        while (size-- > 0) {
            Object key = stream.readObject();
            byte val = stream.readByte();
            put(key, val);
        }
    }
} // TObjectByteHashMap
