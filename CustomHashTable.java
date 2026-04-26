/**
 * Custom HashTable Implementation with Multiple Collision Resolution Strategies
 *
 * This implementation supports three collision resolution strategies:
 *   - Separate Chaining  (using linked lists)
 *   - Linear Probing     (open addressing)
 *   - Quadratic Probing  (open addressing)
 *
 * Design rationale
 * ----------------
 * A single generic class drives all three strategies so that the same public
 * API (put / get / remove / containsKey / size / isEmpty / clear) is available
 * regardless of which strategy is selected at construction time.
 *
 * Separate chaining stores a linked list of entries at each bucket index.
 * Open-addressing strategies use a flat array and rely on lazy deletion
 * (an isDeleted tombstone flag) to keep probe sequences intact after removal.
 *
 * Time complexity summary
 * -----------------------
 * Operation         | Separate Chaining   | Linear / Quad Probing
 * ------------------+---------------------+----------------------
 * put               | O(1) avg, O(n) wc   | O(1) avg, O(n) wc
 * get               | O(1) avg, O(n) wc   | O(1) avg, O(n) wc
 * remove            | O(1) avg, O(n) wc   | O(1) avg, O(n) wc
 * resize            | O(n)                | O(n)
 *
 * Worst-case O(n) occurs only with a fully degenerate hash distribution.
 * In practice, with a good hash code and load factor kept below 0.75,
 * each operation runs in O(1).
 *
 * @author Group Assignment
 * @version 1.0
 */

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CustomHashTable<K, V> {

    // -------------------------------------------------------------------------
    // Collision strategy enum
    // -------------------------------------------------------------------------

    /**
     * Selects how collisions are resolved at construction time.
     */
    public enum CollisionStrategy {
        /** Each bucket holds a linked list of entries. */
        SEPARATE_CHAINING,
        /** Collisions resolved by scanning forward one slot at a time. */
        LINEAR_PROBING,
        /** Collisions resolved by scanning i^2 slots ahead for step i. */
        QUADRATIC_PROBING
    }

    // -------------------------------------------------------------------------
    // Inner class: Entry
    // -------------------------------------------------------------------------

    /**
     * A single key-value pair stored inside the table.
     *
     * The {@code isDeleted} flag supports lazy deletion in open-addressing
     * modes: a deleted slot is marked so that probe sequences can continue
     * past it during a search without breaking correctness.
     */
    private static class Entry<K, V> {
        K key;
        V value;
        /** True when this slot is logically deleted (open-addressing only). */
        boolean isDeleted;

        Entry(K key, V value) {
            this.key       = key;
            this.value     = value;
            this.isDeleted = false;
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private CollisionStrategy strategy;
    private int    capacity;
    private int    size;
    private double loadFactorThreshold;

    /** Bucket array used by SEPARATE_CHAINING. */
    private LinkedList<Entry<K, V>>[] chainTable;

    /** Flat array used by LINEAR_PROBING and QUADRATIC_PROBING. */
    private Entry<K, V>[] probeTable;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Creates a table with default capacity (16) and SEPARATE_CHAINING. */
    @SuppressWarnings("unchecked")
    public CustomHashTable() {
        this(16, CollisionStrategy.SEPARATE_CHAINING);
    }

    /**
     * Creates a table with the given capacity and collision strategy.
     *
     * @param capacity initial number of buckets
     * @param strategy collision resolution strategy
     */
    @SuppressWarnings("unchecked")
    public CustomHashTable(int capacity, CollisionStrategy strategy) {
        this.capacity            = capacity;
        this.strategy            = strategy;
        this.size                = 0;
        this.loadFactorThreshold = 0.75;

        if (strategy == CollisionStrategy.SEPARATE_CHAINING) {
            chainTable = (LinkedList<Entry<K, V>>[]) new LinkedList[capacity];
            for (int i = 0; i < capacity; i++) chainTable[i] = new LinkedList<>();
        } else {
            probeTable = (Entry<K, V>[]) new Entry[capacity];
        }
    }

    // -------------------------------------------------------------------------
    // Hash functions
    // -------------------------------------------------------------------------

    /**
     * Primary hash function -- maps a key to a bucket index in [0, capacity).
     * Math.abs guards against negative hash codes. Time complexity: O(1).
     */
    private int hash(K key) {
        return Math.abs(key.hashCode()) % capacity;
    }

    // -------------------------------------------------------------------------
    // Public API -- INSERT / UPDATE
    // -------------------------------------------------------------------------

    /**
     * Inserts a new key-value pair or updates the value for an existing key.
     *
     * Automatically resizes when the load factor exceeds loadFactorThreshold.
     *
     * Time complexity: O(1) average, O(n) on resize.
     *
     * @param key   must not be null
     * @param value value to associate with the key
     * @throws IllegalArgumentException if key is null
     */
    public void put(K key, V value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");

        if (getLoadFactor() > loadFactorThreshold) resize();

        switch (strategy) {
            case SEPARATE_CHAINING:  putChaining(key, value);        break;
            case LINEAR_PROBING:     putLinearProbing(key, value);    break;
            case QUADRATIC_PROBING:  putQuadraticProbing(key, value); break;
        }
    }

    /** Inserts or updates using separate chaining. */
    private void putChaining(K key, V value) {
        int index = hash(key);
        for (Entry<K, V> e : chainTable[index]) {
            if (e.key.equals(key)) { e.value = value; return; }
        }
        chainTable[index].add(new Entry<>(key, value));
        size++;
    }

    /**
     * Inserts or updates using linear probing.
     * Probe sequence: h, h+1, h+2, ... (mod capacity).
     * A deleted tombstone is reused for insertion but the loop continues
     * scanning to detect any duplicate key further along the chain.
     */
    private void putLinearProbing(K key, V value) {
        int index     = hash(key);
        int firstDead = -1;

        for (int i = 0; i < capacity; i++) {
            int probe = (index + i) % capacity;

            if (probeTable[probe] == null) {
                int target = (firstDead != -1) ? firstDead : probe;
                probeTable[target] = new Entry<>(key, value);
                size++;
                return;
            }

            if (probeTable[probe].isDeleted) {
                if (firstDead == -1) firstDead = probe;
                continue;
            }

            if (probeTable[probe].key.equals(key)) {
                probeTable[probe].value = value;
                return;
            }
        }

        if (firstDead != -1) {
            probeTable[firstDead] = new Entry<>(key, value);
            size++;
        } else {
            // Linear probe sequence exhausted without a free slot.
            // This should not happen in normal use because resize() is triggered
            // before the load factor reaches 1.0.  Guard here defensively.
            throw new RuntimeException("Hash table is full");
        }
    }

    /**
     * Inserts or updates using quadratic probing.
     * Probe sequence: h, h+1^2, h+2^2, h+3^2, ... (mod capacity).
     * Reduces primary clustering vs linear probing.
     *
     * Caveat: quadratic probing is only guaranteed to visit every slot when
     * capacity is a prime number or a power of two AND the load factor stays
     * below 0.5.  If the probe sequence cycles without visiting a free slot,
     * the table is resized and the insertion is retried, which guarantees
     * eventual success.
     */
    private void putQuadraticProbing(K key, V value) {
        int index     = hash(key);
        int firstDead = -1;

        for (int i = 0; i < capacity; i++) {
            int probe = (index + i * i) % capacity;

            if (probeTable[probe] == null) {
                int target = (firstDead != -1) ? firstDead : probe;
                probeTable[target] = new Entry<>(key, value);
                size++;
                return;
            }

            if (probeTable[probe].isDeleted) {
                if (firstDead == -1) firstDead = probe;
                continue;
            }

            if (probeTable[probe].key.equals(key)) {
                probeTable[probe].value = value;
                return;
            }
        }

        // The quadratic probe sequence has cycled without finding a free slot.
        // Resize to break the cycle, then retry.
        if (firstDead != -1) {
            probeTable[firstDead] = new Entry<>(key, value);
            size++;
        } else {
            // Probe cycle with no tombstone reuse available -- must resize.
            resize();
            put(key, value);
        }
    }

    // -------------------------------------------------------------------------
    // Public API -- SEARCH
    // -------------------------------------------------------------------------

    /**
     * Returns the value mapped to {@code key}, or null if absent.
     * Time complexity: O(1) average, O(n) worst case.
     *
     * @param key key to look up; null returns null
     */
    public V get(K key) {
        if (key == null) return null;
        switch (strategy) {
            case SEPARATE_CHAINING:  return getChaining(key);
            case LINEAR_PROBING:     return getLinearProbing(key);
            case QUADRATIC_PROBING:  return getQuadraticProbing(key);
            default:                 return null;
        }
    }

    private V getChaining(K key) {
        for (Entry<K, V> e : chainTable[hash(key)])
            if (e.key.equals(key)) return e.value;
        return null;
    }

    private V getLinearProbing(K key) {
        int index = hash(key);
        for (int i = 0; i < capacity; i++) {
            int probe = (index + i) % capacity;
            if (probeTable[probe] == null) return null;
            if (!probeTable[probe].isDeleted && probeTable[probe].key.equals(key))
                return probeTable[probe].value;
        }
        return null;
    }

    private V getQuadraticProbing(K key) {
        int index = hash(key);
        for (int i = 0; i < capacity; i++) {
            int probe = (index + i * i) % capacity;
            if (probeTable[probe] == null) return null;
            if (!probeTable[probe].isDeleted && probeTable[probe].key.equals(key))
                return probeTable[probe].value;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Public API -- DELETE
    // -------------------------------------------------------------------------

    /**
     * Removes the entry for {@code key} and returns its value, or null if absent.
     *
     * Open-addressing strategies use lazy deletion (setting isDeleted = true)
     * so that ongoing probe sequences are not broken.
     *
     * Time complexity: O(1) average, O(n) worst case.
     *
     * @param key key to remove
     */
    public V remove(K key) {
        if (key == null) return null;
        switch (strategy) {
            case SEPARATE_CHAINING:  return removeChaining(key);
            case LINEAR_PROBING:     return removeLinearProbing(key);
            case QUADRATIC_PROBING:  return removeQuadraticProbing(key);
            default:                 return null;
        }
    }

    private V removeChaining(K key) {
        int index = hash(key);
        for (Entry<K, V> e : chainTable[index]) {
            if (e.key.equals(key)) {
                chainTable[index].remove(e);
                size--;
                return e.value;
            }
        }
        return null;
    }

    private V removeLinearProbing(K key) {
        int index = hash(key);
        for (int i = 0; i < capacity; i++) {
            int probe = (index + i) % capacity;
            if (probeTable[probe] == null) return null;
            if (!probeTable[probe].isDeleted && probeTable[probe].key.equals(key)) {
                V val = probeTable[probe].value;
                probeTable[probe].isDeleted = true;
                size--;
                return val;
            }
        }
        return null;
    }

    private V removeQuadraticProbing(K key) {
        int index = hash(key);
        for (int i = 0; i < capacity; i++) {
            int probe = (index + i * i) % capacity;
            if (probeTable[probe] == null) return null;
            if (!probeTable[probe].isDeleted && probeTable[probe].key.equals(key)) {
                V val = probeTable[probe].value;
                probeTable[probe].isDeleted = true;
                size--;
                return val;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Public API -- UTILITY
    // -------------------------------------------------------------------------

    /** Returns true if this table contains a mapping for key. */
    public boolean containsKey(K key) { return get(key) != null; }

    /** Returns the number of key-value mappings currently stored. */
    public int size() { return size; }

    /** Returns true if no mappings are present. */
    public boolean isEmpty() { return size == 0; }

    /** Returns the current load factor (size / capacity). */
    public double getLoadFactor() { return (double) size / capacity; }

    /** Returns the active collision strategy. */
    public CollisionStrategy getStrategy() { return strategy; }

    /** Returns all currently stored keys. */
    public List<K> keys() {
        List<K> keyList = new ArrayList<>();
        if (strategy == CollisionStrategy.SEPARATE_CHAINING) {
            for (int i = 0; i < capacity; i++)
                for (Entry<K, V> e : chainTable[i]) keyList.add(e.key);
        } else {
            for (int i = 0; i < capacity; i++)
                if (probeTable[i] != null && !probeTable[i].isDeleted)
                    keyList.add(probeTable[i].key);
        }
        return keyList;
    }

    /** Removes all key-value mappings from this table. */
    @SuppressWarnings("unchecked")
    public void clear() {
        size = 0;
        if (strategy == CollisionStrategy.SEPARATE_CHAINING) {
            for (int i = 0; i < capacity; i++) chainTable[i].clear();
        } else {
            probeTable = (Entry<K, V>[]) new Entry[capacity];
        }
    }

    // -------------------------------------------------------------------------
    // Internal -- RESIZE / REHASH
    // -------------------------------------------------------------------------

    /**
     * Doubles the table capacity and reinserts all live entries.
     *
     * Resizing is triggered automatically when the load factor exceeds
     * loadFactorThreshold (default 0.75). Tombstones are discarded during
     * rehashing, which reclaims slots wasted by lazy deletion.
     *
     * Time complexity: O(n).
     */
    @SuppressWarnings("unchecked")
    private void resize() {
        int oldCapacity = capacity;
        capacity = capacity * 2;

        if (strategy == CollisionStrategy.SEPARATE_CHAINING) {
            LinkedList<Entry<K, V>>[] old = chainTable;
            chainTable = (LinkedList<Entry<K, V>>[]) new LinkedList[capacity];
            for (int i = 0; i < capacity; i++) chainTable[i] = new LinkedList<>();
            size = 0;
            for (int i = 0; i < oldCapacity; i++)
                for (Entry<K, V> e : old[i]) put(e.key, e.value);
        } else {
            Entry<K, V>[] old = probeTable;
            probeTable = (Entry<K, V>[]) new Entry[capacity];
            size = 0;
            for (int i = 0; i < oldCapacity; i++)
                if (old[i] != null && !old[i].isDeleted) put(old[i].key, old[i].value);
        }
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CustomHashTable [strategy=").append(strategy)
          .append(", size=").append(size)
          .append(", capacity=").append(capacity)
          .append(", loadFactor=").append(String.format("%.2f", getLoadFactor()))
          .append("]\n");

        if (strategy == CollisionStrategy.SEPARATE_CHAINING) {
            for (int i = 0; i < capacity; i++)
                if (!chainTable[i].isEmpty())
                    sb.append("  [").append(i).append("]: ").append(chainTable[i]).append("\n");
        } else {
            for (int i = 0; i < capacity; i++)
                if (probeTable[i] != null && !probeTable[i].isDeleted)
                    sb.append("  [").append(i).append("]: ").append(probeTable[i]).append("\n");
        }
        return sb.toString();
    }
}
