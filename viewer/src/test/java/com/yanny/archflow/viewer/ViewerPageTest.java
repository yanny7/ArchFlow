package com.yanny.archflow.viewer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerPageTest {
    @Test
    void pageInlinesDataWithoutClosingItsScriptEarly() {
        String html = ViewerPage.html("{\"title\":\"</script><b>x</b>\"}");

        assertTrue(html.contains("window.ARCHFLOW_DATA = {\"title\":\"<\\/script><b>x</b>\"};"));
        assertFalse(html.contains("<!--"));
        assertEquals(5, html.split("<script>", -1).length - 1);
    }
}
