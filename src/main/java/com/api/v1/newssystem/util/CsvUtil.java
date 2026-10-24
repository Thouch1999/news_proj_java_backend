package com.api.v1.newssystem.util;

import java.util.List;

/** Writes CSV (RFC 4180) that is safe to open in a spreadsheet: a cell is never read as a formula. */
public final class CsvUtil {
    private static final String FORMULA_START = "=+-@\t\r";

    private CsvUtil() {
    }

    /**
     * One cell. A text that starts with = + - @ (or a tab or a carriage return) gets a leading quote ('), so a spreadsheet
     * shows it as text and never runs it (CSV injection). A cell with a comma, a quote or a line break is put in quotes.
     */
    public static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && FORMULA_START.indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /** One line, ended by CRLF. */
    public static String row(List<?> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(cell(cells.get(i)));
        }
        return line.append("\r\n").toString();
    }
}
