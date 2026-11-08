# Sandbox (sample input for demos and tests)

| Folder | Purpose |
|---|---|
| `sample-project/` | A small, normal project to track in the demo: edit `notes.txt`, add or delete a file, commit again. |
| `edge-cases/` | Unusual input the system must handle: an empty file, a binary file, a file name with spaces, a file buried 5 folders deep, and an empty folder. |

Edge-case behaviour:

* An **empty file** is tracked (its SHA-256 is e3b0c442...).
* A **binary file** is restored byte-for-byte identical.
* An **empty folder** is not tracked (same as Git: only files are stored).
* Committing with **no changes** is refused, and so is committing a folder with **no files**.

Copy a folder out of `sandbox/` before tracking it with the real application, so the original sample
files stay unchanged for the next demo.
