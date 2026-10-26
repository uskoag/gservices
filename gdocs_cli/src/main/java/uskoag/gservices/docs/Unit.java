package uskoag.gservices.docs;

/** One top-level thing the import replays, in document order: a paragraph or a table. */
sealed interface Unit permits SrcPara, SrcTable {}
