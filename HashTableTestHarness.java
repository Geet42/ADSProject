/**
 * HashTableTestHarness
 *
 * A self-contained test and performance driver for the three hash table
 * implementations produced in this group assignment:
 *
 *   - CustomHashTable  (separate chaining, linear probing, quadratic probing)
 *   - CuckooHashTable  (two-table cuckoo hashing)
 *   - HopscotchHashTable (neighbourhood-bitmap open addressing)
 *
 * Structure
 * ---------
 * Section 1  -- Functional correctness tests (put, get, update, remove,
 *               contains, clear, edge cases).  Each test prints PASS or FAIL.
 *
 * Section 2  -- Behavioural / scenario tests (mixed workload, hash collisions,
 *               stress beyond initial capacity, delete-then-reinsert).
 *
 * Section 3  -- Performance microbenchmark: measures throughput (ops/sec) for
 *               bulk put, get, and remove on each implementation.  Results are
 *               printed in a formatted table for easy comparison.
 *
 * How to compile and run
 * ----------------------
 *   javac CustomHashTable.java CuckooHashTable.java HopscotchHashTable.java \
 *         HashTableTestHarness.java
 *   java  HashTableTestHarness
 *
 * @author Group Assignment
 * @version 1.0
 */
public class HashTableTestHarness {

    // -------------------------------------------------------------------------
    // Test counters (global for simplicity in a test harness)
    // -------------------------------------------------------------------------
    private static int totalTests  = 0;
    private static int passedTests = 0;

    
    //  Entry point
    

    public static void main(String[] args) {
        printBanner("Hash Table Test Harness");

        // ----- Section 1: Correctness ----------------------------------------
        printSection("1. FUNCTIONAL CORRECTNESS TESTS");

        printSubSection("1a. CustomHashTable -- Separate Chaining");
        testCustom(CustomHashTable.CollisionStrategy.SEPARATE_CHAINING);

        printSubSection("1b. CustomHashTable -- Linear Probing");
        testCustom(CustomHashTable.CollisionStrategy.LINEAR_PROBING);

        printSubSection("1c. CustomHashTable -- Quadratic Probing");
        testCustom(CustomHashTable.CollisionStrategy.QUADRATIC_PROBING);

        printSubSection("1d. CuckooHashTable");
        testCuckoo();

        printSubSection("1e. HopscotchHashTable");
        testHopscotch();

        // ----- Section 2: Scenarios ------------------------------------------
        printSection("2. SCENARIO / BEHAVIOURAL TESTS");
        testCollisionScenario();
        testStressExpansion();
        testDeleteThenReinsert();
        testMixedWorkload();
        testNullKeyHandling();

        // ----- Section 3: Performance ----------------------------------------
        printSection("3. PERFORMANCE MICROBENCHMARK");
        runPerformanceBenchmark(10_000);

        // ----- Summary -------------------------------------------------------
        printSection("SUMMARY");
        System.out.printf("  Tests passed: %d / %d%n%n", passedTests, totalTests);
    }

    
    //  Section 1: Functional correctness
    

    // ---- CustomHashTable correctness ----------------------------------------

    private static void testCustom(CustomHashTable.CollisionStrategy strategy) {
        CustomHashTable<String, Integer> map =
                new CustomHashTable<>(16, strategy);

        // Basic put + get
        map.put("alpha", 1);
        map.put("beta", 2);
        map.put("gamma", 3);
        check("put/get alpha",       map.get("alpha") == 1);
        check("put/get beta",        map.get("beta")  == 2);
        check("put/get gamma",       map.get("gamma") == 3);
        check("size after 3 puts",   map.size() == 3);

        // Update existing key
        map.put("alpha", 99);
        check("update alpha",        map.get("alpha") == 99);
        check("size unchanged",      map.size() == 3);

        // Contains
        check("containsKey alpha",   map.containsKey("alpha"));
        check("containsKey missing", !map.containsKey("delta"));

        // Absent key
        check("get absent key",      map.get("delta") == null);

        // Remove
        int removed = map.remove("beta");
        check("remove beta value",   removed == 2);
        check("get after remove",    map.get("beta") == null);
        check("size after remove",   map.size() == 2);

        // Remove absent key
        check("remove absent",       map.remove("zzz") == null);

        // isEmpty + clear
        check("not empty",           !map.isEmpty());
        map.clear();
        check("empty after clear",   map.isEmpty());
        check("size 0 after clear",  map.size() == 0);

        // Re-insert after clear
        map.put("new", 42);
        check("put after clear",     map.get("new") == 42);
    }

    // ---- CuckooHashTable correctness ----------------------------------------

    private static void testCuckoo() {
        CuckooHashTable<String, Integer> map = new CuckooHashTable<>();

        map.put("x", 10);
        map.put("y", 20);
        map.put("z", 30);
        check("cuckoo put/get x",     map.get("x") == 10);
        check("cuckoo put/get y",     map.get("y") == 20);
        check("cuckoo size",          map.size() == 3);

        map.put("x", 100);
        check("cuckoo update x",      map.get("x") == 100);
        check("cuckoo size unchanged",map.size() == 3);

        check("cuckoo contains x",    map.containsKey("x"));
        check("cuckoo no w",          !map.containsKey("w"));
        check("cuckoo get absent",    map.get("w") == null);

        int rv = map.remove("y");
        check("cuckoo remove y val",  rv == 20);
        check("cuckoo get y gone",    map.get("y") == null);
        check("cuckoo size 2",        map.size() == 2);

        check("cuckoo remove absent", map.remove("nope") == null);

        map.clear();
        check("cuckoo empty",         map.isEmpty());
        map.put("after", 1);
        check("cuckoo put after clear", map.get("after") == 1);
    }

    // ---- HopscotchHashTable correctness ------------------------------------

    private static void testHopscotch() {
        HopscotchHashTable<String, Integer> map = new HopscotchHashTable<>();

        map.put("p", 7);
        map.put("q", 8);
        map.put("r", 9);
        check("hop put/get p",        map.get("p") == 7);
        check("hop put/get q",        map.get("q") == 8);
        check("hop size 3",           map.size() == 3);

        map.put("q", 88);
        check("hop update q",         map.get("q") == 88);
        check("hop size still 3",     map.size() == 3);

        check("hop containsKey p",    map.containsKey("p"));
        check("hop missing key",      !map.containsKey("zz"));

        int rv = map.remove("p");
        check("hop remove p val",     rv == 7);
        check("hop get p gone",       map.get("p") == null);
        check("hop size 2",           map.size() == 2);

        check("hop remove absent",    map.remove("none") == null);

        map.clear();
        check("hop empty after clear",map.isEmpty());
        map.put("fresh", 55);
        check("hop put after clear",  map.get("fresh") == 55);
    }

    
    //  Section 2: Scenario / behavioural tests
    

    /**
     * Collision scenario: insert keys designed to collide under Java's default
     * String.hashCode() modulo a small capacity.  All three CustomHashTable
     * strategies must still retrieve every key correctly.
     *
     * We achieve controlled collisions by choosing strings whose hashCode() %
     * 16 all map to the same bucket (bucket 0): any string whose hash is a
     * multiple of 16 will do.  The string "a" has hashCode 97; we use numeric
     * keys 0, 16, 32, 48, 64 which all have hashCode equal to themselves and
     * hash to bucket 0 in a capacity-16 table.
     */
    private static void testCollisionScenario() {
        printSubSection("2a. Forced collision scenario (integer keys, small table)");

        for (CustomHashTable.CollisionStrategy s : CustomHashTable.CollisionStrategy.values()) {
            CustomHashTable<Integer, String> map = new CustomHashTable<>(16, s);
            // Insert 5 keys that all map to bucket 0 (0 % 16 == 0)
            for (int k : new int[]{0, 16, 32, 48, 64}) map.put(k, "v" + k);

            boolean ok = true;
            for (int k : new int[]{0, 16, 32, 48, 64}) {
                if (!("v" + k).equals(map.get(k))) { ok = false; break; }
            }
            check("collision retrieval [" + s + "]", ok);
        }

        // Same for cuckoo and hopscotch using integer keys
        CuckooHashTable<Integer, String> cuckoo = new CuckooHashTable<>(16);
        for (int k : new int[]{0, 16, 32, 48, 64}) cuckoo.put(k, "v" + k);
        boolean cOk = true;
        for (int k : new int[]{0, 16, 32, 48, 64})
            if (!("v" + k).equals(cuckoo.get(k))) { cOk = false; break; }
        check("collision retrieval [CuckooHashTable]", cOk);

        HopscotchHashTable<Integer, String> hop = new HopscotchHashTable<>(16);
        for (int k : new int[]{0, 16, 32, 48, 64}) hop.put(k, "v" + k);
        boolean hOk = true;
        for (int k : new int[]{0, 16, 32, 48, 64})
            if (!("v" + k).equals(hop.get(k))) { hOk = false; break; }
        check("collision retrieval [HopscotchHashTable]", hOk);
    }

    /**
     * Stress expansion: insert far more entries than the initial capacity so
     * that every implementation must resize at least once.
     */
    private static void testStressExpansion() {
        printSubSection("2b. Stress expansion (500 entries, initial capacity 8)");

        int N = 500;

        // CustomHashTable -- all three strategies
        for (CustomHashTable.CollisionStrategy s : CustomHashTable.CollisionStrategy.values()) {
            CustomHashTable<Integer, Integer> map = new CustomHashTable<>(8, s);
            for (int i = 0; i < N; i++) map.put(i, i * 2);
            boolean ok = true;
            for (int i = 0; i < N; i++)
                if (!Integer.valueOf(i * 2).equals(map.get(i))) { ok = false; break; }
            check("stress retrieval [" + s + "]", ok);
        }

        CuckooHashTable<Integer, Integer> cuckoo = new CuckooHashTable<>(8);
        for (int i = 0; i < N; i++) cuckoo.put(i, i * 2);
        boolean cOk = true;
        for (int i = 0; i < N; i++)
            if (!Integer.valueOf(i * 2).equals(cuckoo.get(i))) { cOk = false; break; }
        check("stress retrieval [CuckooHashTable]", cOk);

        HopscotchHashTable<Integer, Integer> hop = new HopscotchHashTable<>(8);
        for (int i = 0; i < N; i++) hop.put(i, i * 2);
        boolean hOk = true;
        for (int i = 0; i < N; i++)
            if (!Integer.valueOf(i * 2).equals(hop.get(i))) { hOk = false; break; }
        check("stress retrieval [HopscotchHashTable]", hOk);
    }

    /**
     * Delete-then-reinsert: remove every key from a populated table, verify
     * all are gone, then reinsert half and verify only those are present.
     * This is important for open-addressing schemes where tombstones can
     * cause correctness issues.
     */
    private static void testDeleteThenReinsert() {
        printSubSection("2c. Delete-then-reinsert");

        int N = 50;

        for (CustomHashTable.CollisionStrategy s : CustomHashTable.CollisionStrategy.values()) {
            CustomHashTable<Integer, Integer> map = new CustomHashTable<>(16, s);
            for (int i = 0; i < N; i++) map.put(i, i);
            for (int i = 0; i < N; i++) map.remove(i);
            check("all removed [" + s + "]", map.size() == 0);

            for (int i = 0; i < N / 2; i++) map.put(i, i * 10);
            boolean ok = true;
            for (int i = 0; i < N / 2; i++)
                if (!Integer.valueOf(i * 10).equals(map.get(i))) { ok = false; break; }
            for (int i = N / 2; i < N; i++)
                if (map.get(i) != null) { ok = false; break; }
            check("reinsert correct [" + s + "]", ok);
        }

        // Cuckoo
        CuckooHashTable<Integer, Integer> c = new CuckooHashTable<>();
        for (int i = 0; i < N; i++) c.put(i, i);
        for (int i = 0; i < N; i++) c.remove(i);
        check("cuckoo all removed", c.size() == 0);
        for (int i = 0; i < N / 2; i++) c.put(i, i * 10);
        boolean cOk = true;
        for (int i = 0; i < N / 2; i++)
            if (!Integer.valueOf(i * 10).equals(c.get(i))) { cOk = false; break; }
        check("cuckoo reinsert correct", cOk);

        // Hopscotch
        HopscotchHashTable<Integer, Integer> h = new HopscotchHashTable<>();
        for (int i = 0; i < N; i++) h.put(i, i);
        for (int i = 0; i < N; i++) h.remove(i);
        check("hopscotch all removed", h.size() == 0);
        for (int i = 0; i < N / 2; i++) h.put(i, i * 10);
        boolean hOk = true;
        for (int i = 0; i < N / 2; i++)
            if (!Integer.valueOf(i * 10).equals(h.get(i))) { hOk = false; break; }
        check("hopscotch reinsert correct", hOk);
    }

    /**
     * Mixed workload: interleave puts, gets, updates, and removes in a pattern
     * that mimics a real-world usage scenario.
     */
    private static void testMixedWorkload() {
        printSubSection("2d. Mixed workload (interleaved put/get/update/remove)");

        CustomHashTable<String, Integer> map =
                new CustomHashTable<>(16, CustomHashTable.CollisionStrategy.LINEAR_PROBING);

        // Phase 1: put 20 entries
        for (int i = 0; i < 20; i++) map.put("key" + i, i);

        // Phase 2: update even keys
        for (int i = 0; i < 20; i += 2) map.put("key" + i, i * 100);

        // Phase 3: remove multiples of 3
        for (int i = 0; i < 20; i += 3) map.remove("key" + i);

        // Phase 4: verify
        boolean ok = true;
        for (int i = 0; i < 20; i++) {
            Integer val = map.get("key" + i);
            if (i % 3 == 0) {
                // Should be absent
                if (val != null) { ok = false; break; }
            } else if (i % 2 == 0) {
                // Should be updated value
                if (!Integer.valueOf(i * 100).equals(val)) { ok = false; break; }
            } else {
                // Should be original value
                if (!Integer.valueOf(i).equals(val)) { ok = false; break; }
            }
        }
        check("mixed workload correctness", ok);
    }

    /**
     * Null key handling: all three implementations must throw
     * IllegalArgumentException (or handle gracefully) when a null key is used.
     */
    private static void testNullKeyHandling() {
        printSubSection("2e. Null key edge cases");

        // CustomHashTable -- put null key should throw
        boolean customThrew = false;
        try {
            new CustomHashTable<String, Integer>().put(null, 1);
        } catch (IllegalArgumentException e) {
            customThrew = true;
        }
        check("CustomHashTable put(null) throws", customThrew);

        // get(null) should return null gracefully
        CustomHashTable<String, Integer> ct = new CustomHashTable<>();
        check("CustomHashTable get(null) == null", ct.get(null) == null);
        check("CustomHashTable remove(null) == null", ct.remove(null) == null);

        // CuckooHashTable
        boolean cuckooThrew = false;
        try {
            new CuckooHashTable<String, Integer>().put(null, 1);
        } catch (IllegalArgumentException e) {
            cuckooThrew = true;
        }
        check("CuckooHashTable put(null) throws", cuckooThrew);
        check("CuckooHashTable get(null) == null",
              new CuckooHashTable<String, Integer>().get(null) == null);

        // HopscotchHashTable
        boolean hopThrew = false;
        try {
            new HopscotchHashTable<String, Integer>().put(null, 1);
        } catch (IllegalArgumentException e) {
            hopThrew = true;
        }
        check("HopscotchHashTable put(null) throws", hopThrew);
        check("HopscotchHashTable get(null) == null",
              new HopscotchHashTable<String, Integer>().get(null) == null);
    }

    
    //  Section 3: Performance microbenchmark
    

    /**
     * Measures and compares throughput of put, get, and remove across all
     * five table configurations for N operations each.
     *
     * Warm-up runs precede measurement to avoid JIT startup bias.
     * Results are reported as millions of operations per second (Mops/s).
     *
     * @param N number of operations per measurement
     */
    private static void runPerformanceBenchmark(int N) {
        System.out.println();
        System.out.println("  Benchmarking " + N + " ops per operation type.");
        System.out.println("  (Each table starts empty; JVM warm-up included.)");
        System.out.println();
        System.out.printf("  %-38s %10s %10s %10s%n",
                "Implementation", "PUT(ms)", "GET(ms)", "REM(ms)");
        System.out.println("  " + "-".repeat(72));

        // Warm-up JVM with the first configuration
        warmUp(N);

        // --- CustomHashTable: all three strategies ---
        for (CustomHashTable.CollisionStrategy s : CustomHashTable.CollisionStrategy.values()) {
            long[] times = benchmarkCustom(s, N);
            System.out.printf("  %-38s %10d %10d %10d%n",
                    "CustomHashTable [" + s + "]", times[0], times[1], times[2]);
        }

        // --- CuckooHashTable ---
        long[] cTimes = benchmarkCuckoo(N);
        System.out.printf("  %-38s %10d %10d %10d%n",
                "CuckooHashTable", cTimes[0], cTimes[1], cTimes[2]);

        // --- HopscotchHashTable ---
        long[] hTimes = benchmarkHopscotch(N);
        System.out.printf("  %-38s %10d %10d %10d%n",
                "HopscotchHashTable", hTimes[0], hTimes[1], hTimes[2]);

        System.out.println();
        System.out.println("  Times are wall-clock milliseconds for " + N + " operations.");
        System.out.println("  Lower is better. Resize events are included in PUT time.");
        System.out.println();
        printComplexityTable();
    }

    /** Warm-up: runs a quick round of all operations to let the JIT compile hot paths. */
    private static void warmUp(int N) {
        CustomHashTable<Integer, Integer> map =
                new CustomHashTable<>(16, CustomHashTable.CollisionStrategy.SEPARATE_CHAINING);
        for (int i = 0; i < N; i++) map.put(i, i);
        for (int i = 0; i < N; i++) map.get(i);
        for (int i = 0; i < N; i++) map.remove(i);
    }

    private static long[] benchmarkCustom(CustomHashTable.CollisionStrategy s, int N) {
        CustomHashTable<Integer, Integer> map = new CustomHashTable<>(16, s);

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.put(i, i);
        long putMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.get(i);
        long getMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.remove(i);
        long remMs = System.currentTimeMillis() - t0;

        return new long[]{putMs, getMs, remMs};
    }

    private static long[] benchmarkCuckoo(int N) {
        CuckooHashTable<Integer, Integer> map = new CuckooHashTable<>();

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.put(i, i);
        long putMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.get(i);
        long getMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.remove(i);
        long remMs = System.currentTimeMillis() - t0;

        return new long[]{putMs, getMs, remMs};
    }

    private static long[] benchmarkHopscotch(int N) {
        HopscotchHashTable<Integer, Integer> map = new HopscotchHashTable<>();

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.put(i, i);
        long putMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.get(i);
        long getMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        for (int i = 0; i < N; i++) map.remove(i);
        long remMs = System.currentTimeMillis() - t0;

        return new long[]{putMs, getMs, remMs};
    }

    /**
     * Prints a reference table of theoretical time complexities for all
     * implemented structures.
     *
     * Discussion
     * ----------
     * Separate chaining achieves O(1) average by distributing entries across
     * independent linked lists.  The worst case O(n) occurs only when all
     * keys hash to the same bucket.
     *
     * Linear probing achieves excellent cache locality because the probe
     * sequence is contiguous in memory.  It suffers from primary clustering:
     * filled slots tend to clump together, raising the average probe length
     * as the load factor grows.
     *
     * Quadratic probing reduces primary clustering (fewer consecutive-slot
     * chains) but introduces secondary clustering and may fail to visit all
     * slots if the table capacity is not a prime or power of two.
     *
     * Cuckoo hashing guarantees O(1) worst-case lookup because each key
     * can reside in exactly one of two positions.  Insertion is O(1)
     * amortised but can trigger a cascade of evictions and, rarely, a resize.
     *
     * Hopscotch hashing guarantees O(H) worst-case lookup (H = 8 here),
     * which is practically O(1), while maintaining better cache locality than
     * cuckoo hashing because all candidate positions are within H consecutive
     * slots of the home bucket.
     */
    private static void printComplexityTable() {
        System.out.println("  Theoretical time complexities:");
        System.out.println();
        System.out.printf("  %-34s %-14s %-14s %-14s%n",
                "Structure", "get", "put (amrt)", "remove");
        System.out.println("  " + "-".repeat(78));
        row("Separate Chaining",   "O(1) avg",  "O(1) avg",  "O(1) avg");
        row("Linear Probing",      "O(1) avg",  "O(1) avg",  "O(1) avg");
        row("Quadratic Probing",   "O(1) avg",  "O(1) avg",  "O(1) avg");
        row("Cuckoo Hashing",      "O(1) WC",   "O(1) amrt", "O(1) WC");
        row("Hopscotch Hashing",   "O(H)=O(1)", "O(1) amrt", "O(H)=O(1)");
        System.out.println();
        System.out.println("  WC = worst-case  |  amrt = amortised  |  avg = average case");
        System.out.println("  All structures resize in O(n) when the load factor is exceeded.");
    }

    private static void row(String name, String g, String p, String r) {
        System.out.printf("  %-34s %-14s %-14s %-14s%n", name, g, p, r);
    }

    
    //  Test assertion helper
    

    /**
     * Records a single test result.
     *
     * @param name    human-readable description of what is being checked
     * @param passed  true if the assertion holds
     */
    private static void check(String name, boolean passed) {
        totalTests++;
        if (passed) {
            passedTests++;
            System.out.println("  [PASS] " + name);
        } else {
            System.out.println("  [FAIL] " + name);
        }
    }

    
    //  Formatting helpers
    

    private static void printBanner(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  " + title);
        System.out.println("=".repeat(70));
    }

    private static void printSection(String title) {
        System.out.println();
        System.out.println("  " + "-".repeat(66));
        System.out.println("  " + title);
        System.out.println("  " + "-".repeat(66));
    }

    private static void printSubSection(String title) {
        System.out.println();
        System.out.println("  >> " + title);
    }
}
