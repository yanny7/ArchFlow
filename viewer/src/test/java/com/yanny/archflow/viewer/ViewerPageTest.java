package com.yanny.archflow.viewer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerPageTest {
    @Test
    void pageInlinesDataWithoutClosingItsScriptEarly() {
        String html = ViewerPage.html("{\"title\":\"</script><b>x</b>\"}");

        assertTrue(html.contains("window.ARCHFLOW_DATA = JSON.parse('{\"title\":\"<\\/script><b>x</b>\"}');"));
        assertFalse(html.contains("<!--"));
        assertEquals(4, html.split("<script>", -1).length - 1);
        assertTrue(html.contains("<script id=\"elk-source\">"));
    }

    @Test
    void dataIsParsedFromAStringLiteralThatKeepsQuotesBackslashesAndLineSeparators() {
        assertEquals("JSON.parse('{\"a\":\"it\\'s \\\\\"x\\\\\" \\u2028\"}')", ViewerPage.parse("{\"a\":\"it's \\\"x\\\" \u2028\"}"));
    }
}
