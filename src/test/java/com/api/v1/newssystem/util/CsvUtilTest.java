package com.api.v1.newssystem.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUtilTest {

    @Test
    void dangerousStarts_getALeadingQuote() {
        assertThat(CsvUtil.cell("=HYPERLINK(\"http://evil\")")).isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(CsvUtil.cell("+1+1")).isEqualTo("'+1+1");
        assertThat(CsvUtil.cell("-2+3")).isEqualTo("'-2+3");
        assertThat(CsvUtil.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvUtil.cell("\t=1")).isEqualTo("'\t=1");
        assertThat(CsvUtil.cell("\r=1")).startsWith("\"'\r");
    }

    @Test
    void safeValues_areUnchanged() {
        assertThat(CsvUtil.cell("Cup final")).isEqualTo("Cup final");
        assertThat(CsvUtil.cell(42)).isEqualTo("42");
        assertThat(CsvUtil.cell(null)).isEmpty();
        assertThat(CsvUtil.cell("ព័ត៌មាន")).isEqualTo("ព័ត៌មាន");
        assertThat(CsvUtil.cell("a=b")).isEqualTo("a=b"); // only the first character matters
    }

    @Test
    void commasQuotesAndLineBreaks_areQuoted() {
        assertThat(CsvUtil.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvUtil.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvUtil.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void row_joinsTheCells_andEndsWithCrlf() {
        assertThat(CsvUtil.row(Arrays.asList("a", null, 3, "=x"))).isEqualTo("a,,3,'=x\r\n");
        assertThat(CsvUtil.row(List.of("only"))).isEqualTo("only\r\n");
    }
}
