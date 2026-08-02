package uskoag.gservices.sheetsdb;

/**
 * A1 notation: column letters, and quoting a tab name so a range survives contact with a title that
 * has a space or an apostrophe in it.
 */
final class A1 {

    private A1() {}

    /** 1-based column index to letters: 1 -&gt; A, 26 -&gt; Z, 27 -&gt; AA. */
    static String col(int index) {
        if (index < 1) throw new IllegalArgumentException("column index is 1-based, got " + index);
        var sb = new StringBuilder();
        int n = index;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /**
     * A tab name as the leading part of a range.
     *
     * <p>Always quoted, never conditionally. A title of {@code Treaty} needs no quotes and a title of
     * {@code Treaties (2026)} does; quoting unconditionally removes the branch and the class of bug
     * where it was decided wrongly. An apostrophe inside the title is doubled, which is the escape
     * the API expects — {@code Bob's data} becomes {@code 'Bob''s data'}.
     */
    static String sheet(String title) {
        return "'" + title.replace("'", "''") + "'";
    }

    /** The whole of one tab, as a range. */
    static String wholeSheet(String title) {
        return sheet(title);
    }

    /** One row of a tab, from column A to {@code lastCol} inclusive. */
    static String row(String title, int row, int lastCol) {
        return sheet(title) + "!A" + row + ":" + col(lastCol) + row;
    }

    /** A contiguous block of rows, from column A to {@code lastCol} inclusive. */
    static String rows(String title, int firstRow, int lastRow, int lastCol) {
        return sheet(title) + "!A" + firstRow + ":" + col(lastCol) + lastRow;
    }

    /** An arbitrary rectangle, all bounds 1-based and inclusive. */
    static String rect(String title, int firstRow, int firstCol, int lastRow, int lastCol) {
        return sheet(title) + "!" + col(firstCol) + firstRow + ":" + col(lastCol) + lastRow;
    }

    /** One whole column from {@code firstRow} down, open-ended. */
    static String column(String title, int column, int firstRow) {
        return sheet(title) + "!" + col(column) + firstRow + ":" + col(column);
    }

    /** One whole row, open-ended to the right. */
    static String wholeRow(String title, int row) {
        return sheet(title) + "!" + row + ":" + row;
    }
}
