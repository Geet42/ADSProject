# Advanced Hash-Based Data Structures
### UCD COMP47500 — Advanced Data Structures | Group Project

> Implementation of three advanced hash table variants in Java from scratch, covering multiple collision resolution strategies, worst-case performance guarantees, and a comprehensive test harness.

## Project Overview

This project implements and compares three hash-based data structures, each using a different collision resolution strategy. The goal was to analyse algorithmic trade-offs in terms of lookup guarantees, cache behaviour, memory usage, and insertion complexity — and to validate correctness and performance through a dedicated test harness.

All implementations are written from scratch in Java. No built-in `HashMap`, `HashSet`, or any Java Collections hashing utility was used.

---

## Repository Structure

```
ADSProject/
├── CustomHashTable.java        # Three collision strategies in one class
├── CuckooHashTable.java        # Two-table cuckoo hashing
├── HopscotchHashTable.java     # Neighbourhood-bitmap open addressing
└── HashTableTestHarness.java   # Correctness tests + performance benchmark
```

---

## Structures Implemented

### 1. CustomHashTable
A single generic class `CustomHashTable<K, V>` supporting three interchangeable collision strategies selected at construction time via the `CollisionStrategy` enum.

- **Separate Chaining** — Each bucket holds a `LinkedList` of entries. Handles high load gracefully; physical removal on delete, no tombstones needed.
- **Linear Probing** — Flat array with probe sequence `h, h+1, h+2, ...`. Lazy deletion via `isDeleted` tombstone flag. Best cache locality of the three probing methods.
- **Quadratic Probing** — Probe sequence `h + i²`. Reduces primary clustering vs linear probing. Includes a resize-and-retry fallback for cases where the sequence cycles without visiting all slots.

All three strategies share the same public API: `put`, `get`, `remove`, `containsKey`, `size`, `isEmpty`, `clear`, `keys`, `getLoadFactor`. Auto-resize triggers at load factor > 0.75.

---

### 2. CuckooHashTable
A generic class `CuckooHashTable<K, V>` using two independent backing arrays (`table1`, `table2`).

- Every key has exactly **two candidate positions**: `table1[hash1(key)]` and `table2[hash2(key)]`
- On collision, the sitting entry is evicted to its alternate position — the "cuckoo" eviction chain
- `hash2` applies a **bit-mixing (avalanche) transformation** to ensure independence from `hash1`
- Cycle detection: if eviction chain exceeds `maxIterations`, a resize is triggered and insertion retried
- **No tombstones needed** on delete — a direct null-out is safe due to the two-slot invariant
- Load factor threshold: **0.5** (lower than others to keep cycle probability low)
- Guaranteed **O(1) worst-case** `get` and `remove` — exactly two array accesses always

---

### 3. HopscotchHashTable
A generic class `HopscotchHashTable<K, V>` using a single flat `Bucket[]` array with a neighbourhood size `HOP_RANGE = 8`.

- Every key hashing to slot `i` is guaranteed to reside in window `[i, i+8)`
- Each `Bucket` carries an integer **hop bitmap**: bit `j` set means slot `(i+j) % capacity` holds a key homed at `i`
- **Insert**: find nearest free slot, then `hopToNeighbourhood()` swaps it backwards into range via valid displacement swaps; resize if no swap is possible
- **Delete**: no tombstones — clear the slot and unset the bitmap bit; lookup uses the bitmap, not a sequential scan
- Combines **cache locality** of linear probing with **bounded lookup** of cuckoo hashing
- Guaranteed **O(H) = O(1) worst-case** lookup and delete

---

### 4. HashTableTestHarness
A self-contained test and benchmarking driver covering three sections.

**Section 1 — Functional Correctness (106 assertions)**
Run against all five table configurations:
- Basic `put` / `get`
- In-place update (size must not change)
- `get` on absent key returns `null`
- `remove` with correct return value, `get` after remove
- `containsKey` for present and absent keys
- `isEmpty`, `clear`, put-after-clear

**Section 2 — Scenario / Behavioural Tests**
- Forced collision: keys `0, 16, 32, 48, 64` all map to bucket 0 in a capacity-16 table
- Stress expansion: 500 entries from initial capacity 8 (multiple forced resizes)
- Delete-then-reinsert: catches tombstone correctness bugs in open-addressing schemes
- Mixed workload: interleaved put / update / remove with phased verification
- Null key edge cases: `put(null)` must throw `IllegalArgumentException`; `get(null)` and `remove(null)` must return `null` gracefully

**Section 3 — Performance Microbenchmark**
- Wall-clock timing for 10,000 bulk `put`, `get`, and `remove` per implementation
- JVM warm-up round before measurement to reduce JIT startup bias
- Formatted results table printed to console
- Theoretical complexity reference table with written trade-off discussion

---

## Time Complexity Reference

| Operation | Separate Chaining | Linear / Quad Probing | Cuckoo Hashing | Hopscotch Hashing |
|---|---|---|---|---|
| `get` | O(1) avg, O(n) wc | O(1) avg, O(n) wc | **O(1) worst-case** | **O(H)=O(1) worst-case** |
| `put` | O(1) avg, O(n) wc | O(1) avg, O(n) wc | O(1) amortised | O(1) amortised |
| `remove` | O(1) avg, O(n) wc | O(1) avg, O(n) wc | **O(1) worst-case** | **O(H)=O(1) worst-case** |
| `resize` | O(n) | O(n) | O(n) | O(n) |

> wc = worst-case | avg = average case | H = neighbourhood size (constant = 8)

---

## Trade-off Summary

| Structure | Strength | Weakness |
|---|---|---|
| Separate Chaining | Robust at high load; simple logic | Heap allocations per entry; poor cache locality |
| Linear Probing | Best cache performance | Primary clustering degrades at high load |
| Quadratic Probing | Reduces primary clustering | Secondary clustering; probe coverage gaps |
| Cuckoo Hashing | Strictest O(1) lookup guarantee | Complex eviction logic; frequent resizes at high load |
| Hopscotch Hashing | Cache-friendly + bounded lookup; no tombstones | Complex insert displacement logic |

---

## How to Compile and Run

```bash
# Compile all files
javac CustomHashTable.java CuckooHashTable.java HopscotchHashTable.java HashTableTestHarness.java

# Run the test harness
java HashTableTestHarness
```

Expected output:
```
Tests passed: 106 / 106
```

---

## Key Bugs Fixed During Development

| Bug | File | Fix |
|---|---|---|
| Linear probing reused tombstone slot without checking for duplicate key further along probe chain | `CustomHashTable.java` | Scan full sequence first; record first tombstone index; commit only after confirming no live duplicate |
| Quadratic probing threw hard exception when probe sequence cycled without visiting all slots | `CustomHashTable.java` | Added resize-and-retry fallback instead of `RuntimeException` |
