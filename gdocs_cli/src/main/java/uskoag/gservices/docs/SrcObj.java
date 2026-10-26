package uskoag.gservices.docs;

/** A one-character object inside a paragraph (an image or a page break), at an offset into the paragraph's text. */
record SrcObj(int at, String kind, String ref, int seq) {}
