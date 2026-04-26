/**
 * Hopscotch Hashing Implementation
 *
 * Hopscotch hashing is an open-addressing scheme introduced by Herlihy,
 * Shavit, and Tzafrir (2008).  It combines the cache-friendliness of linear
 * probing with the bounded lookup time of cuckoo hashing.
 *
 * Core idea
 * ---------
 * Each slot i has a "neighbourhood" of H consecutive slots starting at i.
 * A key that hashes to i is guaranteed to reside somewhere in that window.
 * This means lookup always inspects at most H slots -- a constant -- giving a
 * worst-case O(H) = O(1) lookup.
 *
 * Neighbourhood bitmap
 * --------------------
 * Each slot carries an integer "hop" bitmap.  Bit j (0-indexed from the LSB)
 * is set when the slot at position (i + j) % capacity holds a key that
 * originally hashed to i.  An entry's "home" bucket is the slot it hashed to.
 *
 * Insert algorithm
 * ----------------
 * 1. Hash the key to home index h.
 * 2. If any slot in [h, h+H) is free, place the key there and update the
 *    hop bitmap at h.
 * 3. If no slot in the neighbourhood is free, find the nearest free slot
 *    beyond h and "hop" it closer by swapping it backwards through the table
 *    until it falls within [h, h+H).
 * 4. If hopping fails (no displacement found), resize and retry.
 *
 * Why no tombstones?
 * ------------------
 * The hop bitmap tracks exactly which neighbourhood positions are occupied.
 * Clearing the slot and unsetting the corresponding bit is fully sufficient
 * for deletion -- there is no ambiguity about the probe sequence because
 * lookup uses the bitmap rather than a sequential scan.
 *
 * Time complexity summary
 * -----------------------
 * Operation  | Average      | Worst-case
 * -----------+--------------+------------------------------------
 * get        | O(H)         | O(H) = O(1)  (H is a small constant)
 * remove     | O(H)         | O(H) = O(1)
 * put        | O(1) amrt    | O(n) when resize triggers
 * resize     |    --        | O(n)
 *
 * H defaults to 8 in this implementation.
 *
 * @author Group Assignment
 * @version 2.0
 */
public class HopscotchHashTable<K, V> {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Default initial capacity (number of slots in the table). */
    private static final int DEFAULT_CAPACITY = 16;

    /**
     * Neighbourhood size H.
     * Every key hashing to slot i must reside in the window [i, i+H).
     * Using a power of two is convenient because the bitmap fits in an int
     * and offset arithmetic simplifies to bitwise operations.
     */
    private static final int HOP_RANGE = 8;

    /** Resize when load factor exceeds this threshold. */
    private static final double LOAD_FACTOR_THRESHOLD = 0.75;

    // -------------------------------------------------------------------------
    // Inner class: Bucket
    // -------------------------------------------------------------------------

    /**
     * A single slot in the hopscotch table.
     *
     * hop is a bitmask of length HOP_RANGE.  Bit j is set when the slot at
     * position (thisIndex + j) % capacity holds a key whose home bucket is
     * this slot.  Bit 0 always refers to the slot itself.
     */
    private static class Bucket<K, V> {
        K   key;
        V   value;
        /**
         * Neighbourhood membership bitmap.
         * Bit j set means: the entry at offset +j from this bucket's index
         * originally hashed to this bucket.
         */
        int hop;

        Bucket() { this.hop = 0; }

        /** Returns true if this slot holds no live entry. */
        boolean isEmpty() { return key == null; }

        @Override
        public String toString() {
            return isEmpty() ? "[empty]"
                             : key + "=" + value + " (hop=" + Integer.toBinaryString(hop) + ")";
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private Bucket<K, V>[] table;
    private int capacity;
    private int size;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Creates a hopscotch table with default capacity. */
    public HopscotchHashTable() { this(DEFAULT_CAPACITY); }

    /**
     * Creates a hopscotch table with the specified capacity.
     *
     * The actual capacity is at least HOP_RANGE * 2 to avoid degenerate
     * layouts where a neighbourhood wraps completely around a tiny table.
     *
     * @param capacity desired number of slots
     */
    @SuppressWarnings("unchecked")
    public HopscotchHashTable(int capacity) {
        this.capacity = Math.max(capacity, HOP_RANGE * 2);
        this.size     = 0;

        table = (Bucket<K, V>[]) new Bucket[this.capacity];
        for (int i = 0; i < this.capacity; i++) table[i] = new Bucket<>();
    }

    // -------------------------------------------------------------------------
    // Hash function
    // -------------------------------------------------------------------------

    /**
     * Maps a key to its home bucket index.
     * Time complexity: O(1).
     */
    private int hash(K key) {
        return Math.abs(key.hashCode()) % capacity;
    }

    // -------------------------------------------------------------------------
    // Public API -- INSERT / UPDATE
    // -------------------------------------------------------------------------

    /**
     * Inserts a new key-value pair, or updates the value for an existing key.
     *
     * Steps:
     *   1. If the key already lives in its neighbourhood, update in place.
     *   2. Find the first free slot anywhere beyond the home bucket.
     *   3. Hop that free slot into the neighbourhood using displacement swaps.
     *   4. If hopping cannot bring the free slot close enough, resize and retry.
     *
     * Time complexity: O(1) amortised, O(n) on resize.
     *
     * @param key   must not be null
     * @param value value to associate with the key
     * @throws IllegalArgumentException if key is null
     */
    public void put(K key, V value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");

        if (getLoadFactor() > LOAD_FACTOR_THRESHOLD) resize();

        int home = hash(key);

        // --- Check for existing key in this home's neighbourhood ---
        int hop = table[home].hop;
        for (int j = 0; j < HOP_RANGE; j++) {
            if ((hop & (1 << j)) != 0) {
                int slot = (home + j) % capacity;
                if (table[slot].key != null && table[slot].key.equals(key)) {
                    table[slot].value = value; // update in place
                    return;
                }
            }
        }

        // --- Find the nearest empty slot anywhere forward from home ---
        int freeSlot = findFreeSlot(home);
        if (freeSlot == -1) {
            resize();
            put(key, value);
            return;
        }

        // --- Try to hop the free slot into the neighbourhood ---
        freeSlot = hopToNeighbourhood(home, freeSlot);
        if (freeSlot == -1) {
            resize();
            put(key, value);
            return;
        }

        // --- Place the entry ---
        table[freeSlot].key   = key;
        table[freeSlot].value = value;
        // Set the bit corresponding to this offset in home's bitmap
        int offset = (freeSlot - home + capacity) % capacity;
        table[home].hop |= (1 << offset);
        size++;
    }

    /**
     * Scans forward from {@code start} (wrapping around) to find an empty slot.
     *
     * @return slot index, or -1 if the table is completely full
     */
    private int findFreeSlot(int start) {
        for (int i = 0; i < capacity; i++) {
            int idx = (start + i) % capacity;
            if (table[idx].isEmpty()) return idx;
        }
        return -1;
    }

    /**
     * Moves the free slot backwards (closer to home) by repeatedly swapping it
     * with an occupied entry that can legally reside at the current free slot.
     *
     * An entry at slot s can be moved to freeSlot if freeSlot is still within
     * the neighbourhood of s's home bucket, i.e. (freeSlot - home(s)) < H.
     *
     * The loop scans the HOP_RANGE-1 slots immediately before freeSlot to
     * find such a candidate.  Each successful swap moves freeSlot one step
     * closer to home.  If no valid swap exists, -1 is returned and the caller
     * must resize.
     *
     * @param home     home bucket of the key being inserted
     * @param freeSlot initially-found empty slot
     * @return new position of the free slot (inside neighbourhood), or -1
     */
    private int hopToNeighbourhood(int home, int freeSlot) {
        while (true) {
            int dist = (freeSlot - home + capacity) % capacity;
            if (dist < HOP_RANGE) return freeSlot; // already within range

            // Scan [freeSlot - (HOP_RANGE-1), freeSlot - 1] for a swap candidate
            boolean swapped = false;
            for (int gap = HOP_RANGE - 1; gap >= 1; gap--) {
                int candidate      = (freeSlot - gap + capacity) % capacity;
                int candidateHome  = hash(
                        table[candidate].isEmpty() ? (K) "" : table[candidate].key);

                int distCandToFree = (freeSlot - candidateHome + capacity) % capacity;

                // Candidate can be moved to freeSlot only if freeSlot is within
                // candidateHome's neighbourhood
                if (!table[candidate].isEmpty() && distCandToFree < HOP_RANGE) {

                    int bitFrom = (candidate - candidateHome + capacity) % capacity;
                    int bitTo   = (freeSlot  - candidateHome + capacity) % capacity;

                    // Update candidateHome's bitmap: clear old bit, set new bit
                    table[candidateHome].hop &= ~(1 << bitFrom);
                    table[candidateHome].hop |=  (1 << bitTo);

                    // Physically move the data
                    table[freeSlot].key   = table[candidate].key;
                    table[freeSlot].value = table[candidate].value;
                    table[candidate].key   = null;
                    table[candidate].value = null;

                    freeSlot = candidate; // the vacated slot is now our new free slot
                    swapped  = true;
                    break;
                }
            }

            if (!swapped) return -1; // no valid displacement -- resize required
        }
    }

    // -------------------------------------------------------------------------
    // Public API -- SEARCH
    // -------------------------------------------------------------------------

    /**
     * Returns the value mapped to {@code key}, or null if absent.
     *
     * Only the HOP_RANGE slots of the home neighbourhood are inspected,
     * giving a worst-case O(H) = O(1) guarantee.
     *
     * @param key key to look up; null returns null
     */
    public V get(K key) {
        if (key == null) return null;

        int home = hash(key);
        int hop  = table[home].hop;

        for (int j = 0; j < HOP_RANGE; j++) {
            if ((hop & (1 << j)) != 0) {
                int slot = (home + j) % capacity;
                if (table[slot].key != null && table[slot].key.equals(key))
                    return table[slot].value;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Public API -- DELETE
    // -------------------------------------------------------------------------

    /**
     * Removes the entry for {@code key} and returns its value, or null if absent.
     *
     * Unlike open-addressing schemes with linear probing, hopscotch hashing
     * does NOT require tombstones.  The hop bitmap at the home bucket precisely
     * tracks which neighbourhood slots are occupied; clearing the physical slot
     * and unsetting the corresponding bitmap bit is fully sufficient.
     *
     * Time complexity: O(H) = O(1) worst-case.
     *
     * @param key key to remove
     */
    public V remove(K key) {
        if (key == null) return null;

        int home = hash(key);
        int hop  = table[home].hop;

        for (int j = 0; j < HOP_RANGE; j++) {
            if ((hop & (1 << j)) != 0) {
                int slot = (home + j) % capacity;
                if (table[slot].key != null && table[slot].key.equals(key)) {
                    V val = table[slot].value;
                    table[slot].key   = null;
                    table[slot].value = null;
                    table[home].hop  &= ~(1 << j); // clear the bit
                    size--;
                    return val;
                }
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

    /**
     * Returns the current load factor (size / capacity).
     * This table uses a single flat array, so capacity equals the total
     * number of available slots.
     */
    public double getLoadFactor() { return (double) size / capacity; }

    /** Removes all key-value mappings from this table. */
    @SuppressWarnings("unchecked")
    public void clear() {
        for (int i = 0; i < capacity; i++) table[i] = new Bucket<>();
        size = 0;
    }

    // -------------------------------------------------------------------------
    // Internal -- RESIZE / REHASH
    // -------------------------------------------------------------------------

    /**
     * Doubles the capacity and reinserts all live entries.
     *
     * A larger table reduces the probability that hopping will fail to move a
     * free slot into a neighbourhood -- the main failure mode at high load or
     * with adversarial key distributions.
     *
     * Time complexity: O(n).
     */
    @SuppressWarnings("unchecked")
    private void resize() {
        Bucket<K, V>[] old    = table;
        int            oldCap = capacity;

        capacity = capacity * 2;
        table    = (Bucket<K, V>[]) new Bucket[capacity];
        for (int i = 0; i < capacity; i++) table[i] = new Bucket<>();
        size = 0;

        for (int i = 0; i < oldCap; i++)
            if (!old[i].isEmpty()) put(old[i].key, old[i].value);
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("HopscotchHashTable [size=").append(size)
          .append(", capacity=").append(capacity)
          .append(", hopRange=").append(HOP_RANGE)
          .append(", loadFactor=").append(String.format("%.2f", getLoadFactor()))
          .append("]\n");

        for (int i = 0; i < capacity; i++) {
            if (!table[i].isEmpty()) {
                sb.append("  [").append(i).append("]: ")
                  .append(table[i].key).append("=").append(table[i].value)
                  .append("  hop=")
                  .append(String.format("%8s", Integer.toBinaryString(table[i].hop))
                                 .replace(' ', '0'))
                  .append("\n");
            }
        }
        return sb.toString();
    }
}
