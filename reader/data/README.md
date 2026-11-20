# The archive slot

The archive is too large for git, so it lives as `archive.db.gz` in the repository's **archive-data** release.
The Pages build downloads it and splits it into `data/archive/` (chunks + `config.json`), which the Reader reads
on demand. The Android build bakes it into the app.

To run the Reader locally with the whole file instead, unpack it here as **`archive.db`** (or open it with the
file picker). The file is the `codex.db` written by the gatherer's `export` command plus a small `light` table
(no article bodies) for fast lists: SQLite with `entries`, `entries_fts` (FTS4), `light` and `info`.
Licensed CC BY-SA 4.0, see `../../DATA_LICENSE.md`.
