package uskoag.gservices.docs;

import com.google.api.services.docs.v1.model.TextStyle;

/** A stretch of one effective text style, as offsets into the paragraph text (the paragraph mark is at text.length()). */
record SrcRun(int from, int to, TextStyle style) {}
