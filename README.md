# Advanced Hash-Based Data Structures
**UCD COMP47500 — Group Project**

| Student | ID | Contribution |
|---|---|---|
| Geet Bhute | 25202112 | 33.33% |
| Muskaan Pahilajani | 25200738 | 33.33% |
| Shridhar Joshi | 25200572 | 33.33% |

---

## Structures Implemented

| File | Description |
|---|---|
| `CustomHashTable.java` | Single class supporting Separate Chaining, Linear Probing, and Quadratic Probing |
| `CuckooHashTable.java` | Two-table scheme with O(1) worst-case lookup |
| `HopscotchHashTable.java` | Neighbourhood-bitmap open addressing for cache-friendly lookups |
| `HashTableTestHarness.java` | 106 correctness assertions + performance benchmark |

---

## Time Complexity

| Operation | Separate Chaining / Probing | Cuckoo / Hopscotch |
|---|---|---|
| `put` | O(1) avg, O(n) worst | O(1) amortised |
| `get` | O(1) avg, O(n) worst | O(1) worst-case |
| `remove` | O(1) avg, O(n) worst | O(1) worst-case |
| `resize` | O(n) | O(n) |

---

## How to Run

```bash
javac CustomHashTable.java CuckooHashTable.java HopscotchHashTable.java HashTableTestHarness.java
java HashTableTestHarness
```

---

## GenAI Usage
ChatGPT, GitHub Copilot, and Tabnine were used for conceptual guidance and boilerplate generation.
All output was reviewed, tested, and verified by the group. Final implementation and report reflect collective team effort.
