# The archive slot

Put the latest export here as **`archive.db`**. The Reader loads whatever file is in this place - replace it and
reload, nothing else to change. (To use another name or location, edit `db` in `../config.js`.)

The file is the `codex.db` written by the gatherer's `export` command: SQLite with the tables `entries`,
`entries_fts` and `info`. Licensed CC BY-SA 4.0, see `../../DATA_LICENSE.md`.
