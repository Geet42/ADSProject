/**
 * Cuckoo Hashing Implementation
 *
 * Cuckoo hashing (Pagh and Rodler, 2004) uses two independent hash functions
 * and two backing arrays of equal capacity.  Each key has exactly two possible
 * positions: slot hash1(key) in table1, or slot hash2(key) in table2.
 *
 * Core idea
 * ---------
 * On insert, if the preferred slot in table1 is occupied, the sitting tenant
 * is "kicked out" and relocated to its alternate slot in table2.  If that slot
 * is also occupied, that tenant is kicked out too, and so on.  This chain of
 * evictions (the "cuckoo" metaphor) continues until either an empty slot is
 * found or a cycle is detected.  On a cycle the table is resized and all
 * entries are rehashed.
 *
 * Lookup guarantee
 * ----------------
 * Because each key can only reside in one of exactly two positions, get() and
 * remove() need to inspect at most 2 slots regardless of table occupancy.
 * This gives a true worst-case O(1) lookup -- not amortised, not expected.
 *
 * Load factor
 * -----------
 * The total number of slots is 2 * capacity (one capacity per table).
 * The load factor is therefore size / (2 * capacity).  Cuckoo hashing works
 * best when this stays below 0.5; above that threshold cycle probability rises
 * sharply.  The implementation enforces a 0.5 threshold and resizes eagerly.
 *
 * Time complexity summary
 * -----------------------
 * Operation  | Average      | Worst-case
 * -----------+--------------+-----------------------------
 * get        | O(1)         | O(1)  (checks exactly 2 slots)
 * remove     | O(1)         | O(1)  (checks exactly 2 slots)
 * put        | O(1) amrt    | O(n)  when resize is triggered
 * resize     |    --        | O(n)
 *
 * @author Group Assignment
 * @version 1.0
 */
public class CuckooHashTable<K, V> {

    // -------------------------------------------------------------------------
    // Inner class: Entry
    // -------------------------------------------------------------------------

    /**
     * A key-value pair stored in one of the two backing arrays.
     */
    private static class Entry<K, V> {
        K key;
        V value;

        Entry(K key, V value) {
            this.key   = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final int    DEFAULT_CAPACITY      = 16;
    /**
     * Load factor threshold computed over the combined capacity (2 * capacity).
     * Kept at 0.5 because cuckoo hashing's insertion success rate degrades
     * rapidly above this point.
     */
    private static final double LOAD_FACTOR_THRESHOLD = 0.5;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** First backing array: keys reside at hash1(key) in this table. */
    private Entry<K, V>[] table1;
    /** Second backing array: keys reside at hash2(key) in this table. */
    private Entry<K, V>[] table2;

    /** Capacity of each individual table (total slots = 2 * capacity). */
    private int capacity;
    /** Number of currently stored key-value pairs. */
    private int size;
    /**
     * Maximum eviction chain length before we declare a cycle.
     * Set to {@code capacity} -- a chain longer than the table size must
     * have revisited a slot, proving a cycle.
     */
    private int maxIterations;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Creates a cuckoo table with default capacity per sub-table. */
    @SuppressWarnings("unchecked")
    public CuckooHashTable() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a cuckoo table with the given capacity per sub-table.
     *
     * @param capacity initial number of slots in each of the two tables
     */
    @SuppressWarnings("unchecked")
    public CuckooHashTable(int capacity) {
        this.capacity      = capacity;
        this.size          = 0;
        this.maxIterations = capacity;

        table1 = (Entry<K, V>[]) new Entry[capacity];
        table2 = (Entry<K, V>[]) new Entry[capacity];
    }

    // -------------------------------------------------------------------------
    // Hash functions
    // -------------------------------------------------------------------------

    /**
     * First hash function -- maps a key to an index in table1.
     * Uses Java's built-in hashCode with modulo. Time complexity: O(1).
     */
    private int hash1(K key) {
        return Math.abs(key.hashCode()) % capacity;
    }

    /**
     * Second hash function -- maps a key to an index in table2.
     *
     * Uses a bit-mixing step (avalanche effect) to ensure that hash1 and hash2
     * are sufficiently independent even for keys with structured hash codes.
     * Without independence, both functions might map many keys to the same
     * slots, causing frequent cycles.
     *
     * Time complexity: O(1).
     */
    private int hash2(K key) {
        int h = key.hashCode();
        h = ((h >>> 16) ^ h) * 0x45d9f3b;
        h = ((h >>> 16) ^ h) * 0x45d9f3b;
        h = (h >>> 16) ^ h;
        return Math.abs(h) % capacity;
    }

    // -------------------------------------------------------------------------
    // Public API -- INSERT / UPDATE
    // -------------------------------------------------------------------------

    /**
     * Inserts a new key-value pair or updates the value for an existing key.
     *
     * Algorithm:
     *   1. Check table1[hash1(key)] and table2[hash2(key)] for an existing
     *      mapping; update in place if found.
     *   2. If the load factor would be exceeded, resize first.
     *   3. Begin the eviction chain starting from table1.
     *   4. If maxIterations evictions occur without finding a free slot,
     *      a cycle is assumed; resize and retry.
     *
     * Time complexity: O(1) amortised, O(n) when resize triggers.
     *
     * @param key   must not be null
     * @param value value to associate with the key
     * @return true if the operation succeeded
     * @throws IllegalArgumentException if key is null
     */
    public boolean put(K key, V value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");

        // --- Check for existing key and update in place ---
        int index1 = hash1(key);
        int index2 = hash2(key);

        if (table1[index1] != null && table1[index1].key.equals(key)) {
            table1[index1].value = value;
            return true;
        }
        if (table2[index2] != null && table2[index2].key.equals(key)) {
            table2[index2].value = value;
            return true;
        }

        // --- Eager resize to stay within load factor ---
        if (getLoadFactor() > LOAD_FACTOR_THRESHOLD) resize();

        // --- Attempt cuckoo insertion ---
        Entry<K, V> newEntry = new Entry<>(key, value);
        if (insertEntry(newEntry)) {
            size++;
            return true;
        }

        // --- Cycle detected: resize and retry ---
        resize();
        return put(key, value);
    }

    /**
     * Executes the cuckoo eviction chain.
     *
     * Starting from table1, the new entry attempts to claim slot hash1(key).
     * If that slot is occupied, the sitting entry is displaced to its alternate
     * position in table2 (slot hash2(displaced.key)), and so on, alternating
     * between the two tables.  The chain terminates when an empty slot is
     * found or after maxIterations steps (cycle detected).
     *
     * @param entry entry to insert
     * @return true if a free slot was found; false if a cycle was detected
     */
    private boolean insertEntry(Entry<K, V> entry) {
        Entry<K, V> current   = entry;
        boolean     useTable1 = true;

        for (int i = 0; i < maxIterations; i++) {
            int             index;
            Entry<K, V>[]   currentTable;

            if (useTable1) {
                index        = hash1(current.key);
                currentTable = table1;
            } else {
                index        = hash2(current.key);
                currentTable = table2;
            }

            if (currentTable[index] == null) {
                // Empty slot found -- place entry and done
                currentTable[index] = current;
                return true;
            }

            // Evict the sitting tenant and continue the chain
            Entry<K, V> displaced = currentTable[index];
            currentTable[index]   = current;
            current               = displaced;
            useTable1             = !useTable1; // switch tables
        }

        // maxIterations reached without an empty slot -- cycle detected
        return false;
    }

    // -------------------------------------------------------------------------
    // Public API -- SEARCH
    // -------------------------------------------------------------------------

    /**
     * Returns the value mapped to {@code key}, or null if absent.
     *
     * Only two array positions are ever inspected: table1[hash1(key)] and
     * table2[hash2(key)].  This gives a strict worst-case O(1) guarantee.
     *
     * @param key key to look up; null returns null
     */
    public V get(K key) {
        if (key == null) return null;

        int index1 = hash1(key);
        if (table1[index1] != null && table1[index1].key.equals(key))
            return table1[index1].value;

        int index2 = hash2(key);
        if (table2[index2] != null && table2[index2].key.equals(key))
            return table2[index2].value;

        return null;
    }

    // -------------------------------------------------------------------------
    // Public API -- DELETE
    // -------------------------------------------------------------------------

    /**
     * Removes the entry for {@code key} and returns its value, or null if absent.
     *
     * Unlike open-addressing schemes, cuckoo hashing does NOT need tombstones:
     * the two-slot invariant is self-contained, so a simple null-out is safe.
     *
     * Time complexity: O(1) worst-case.
     *
     * @param key key to remove
     */
    public V remove(K key) {
        if (key == null) return null;

        int index1 = hash1(key);
        if (table1[index1] != null && table1[index1].key.equals(key)) {
            V val      = table1[index1].value;
            table1[index1] = null;
            size--;
            return val;
        }

        int index2 = hash2(key);
        if (table2[index2] != null && table2[index2].key.equals(key)) {
            V val      = table2[index2].value;
            table2[index2] = null;
            size--;
            return val;
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

    /**
     * Returns the current load factor.
     * Computed as size / (2 * capacity) because there are two tables.
     */
    public double getLoadFactor() { return (double) size / (2 * capacity); }

    /** Removes all key-value mappings from this table. */
    @SuppressWarnings("unchecked")
    public void clear() {
        table1 = (Entry<K, V>[]) new Entry[capacity];
        table2 = (Entry<K, V>[]) new Entry[capacity];
        size   = 0;
    }

    /**
     * Returns a human-readable distribution summary showing how many entries
     * are in each sub-table versus the per-table capacity.
     */
    public String getStatistics() {
        int t1 = 0, t2 = 0;
        for (int i = 0; i < capacity; i++) {
            if (table1[i] != null) t1++;
            if (table2[i] != null) t2++;
        }
        return String.format(
            "Table1: %d/%d slots used  |  Table2: %d/%d slots used  |  "
          + "Total size: %d  |  Load factor: %.2f",
            t1, capacity, t2, capacity, size, getLoadFactor());
    }

    // -------------------------------------------------------------------------
    // Internal -- RESIZE / REHASH
    // -------------------------------------------------------------------------

    /**
     * Doubles the per-table capacity and reinserts all live entries.
     *
     * A larger table reduces the probability that the eviction chain will
     * cycle before finding a free slot -- the primary failure mode of cuckoo
     * hashing at high load or with poorly separated hash functions.
     *
     * Time complexity: O(n).
     */
    @SuppressWarnings("unchecked")
    private void resize() {
        Entry<K, V>[] old1      = table1;
        Entry<K, V>[] old2      = table2;
        int           oldCap    = capacity;

        capacity      = capacity * 2;
        maxIterations = capacity;

        table1 = (Entry<K, V>[]) new Entry[capacity];
        table2 = (Entry<K, V>[]) new Entry[capacity];
        size   = 0;

        for (int i = 0; i < oldCap; i++) {
            if (old1[i] != null) put(old1[i].key, old1[i].value);
            if (old2[i] != null) put(old2[i].key, old2[i].value);
        }
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CuckooHashTable [size=").append(size)
          .append(", capacity per table=").append(capacity)
          .append(", total slots=").append(2 * capacity)
          .append(", loadFactor=").append(String.format("%.2f", getLoadFactor()))
          .append("]\n");

        sb.append("  -- Table 1 --\n");
        for (int i = 0; i < capacity; i++)
            if (table1[i] != null)
                sb.append("    [").append(i).append("]: ").append(table1[i]).append("\n");

        sb.append("  -- Table 2 --\n");
        for (int i = 0; i < capacity; i++)
            if (table2[i] != null)
                sb.append("    [").append(i).append("]: ").append(table2[i]).append("\n");

        return sb.toString();
    }
}
