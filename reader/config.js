// The only thing the reader needs to know.
//  lazy: the archive split into chunks (made by the Pages build) - the browser reads only what a search needs.
//  db:   a whole archive file - used when there is no lazy config (e.g. opening the reader from a folder).
window.ARCHIVE_CONFIG = {
  lazy: "data/archive/config.json",
  db: "data/archive.db",
  defaultLang: "en",         // "en" or "de"; the reader remembers the last choice
  resultsPerPage: 40,
};
