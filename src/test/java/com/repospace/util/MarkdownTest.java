package com.repospace.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

public class MarkdownTest {

    @Test
    public void blocksAreRecognised() {
        List<Markdown.Block> blocks = Markdown.parse("# Title\n\nSome text\nover two lines.\n\n- one\n- two\n\n1. first\n2. second\n\n> quoted\n\n---\n\n```\ncode line\n```\n");
        assertEquals(Markdown.BlockType.HEADING, blocks.get(0).getType());
        assertEquals(1, blocks.get(0).getLevel());
        assertEquals("Title", blocks.get(0).getText());
        assertEquals(Markdown.BlockType.PARAGRAPH, blocks.get(1).getType());
        assertEquals("Some text over two lines.", blocks.get(1).getText());
        assertEquals(Markdown.BlockType.BULLET, blocks.get(2).getType());
        assertEquals(Markdown.BlockType.BULLET, blocks.get(3).getType());
        assertEquals(Markdown.BlockType.NUMBERED, blocks.get(4).getType());
        assertEquals(2, blocks.get(5).getLevel());
        assertEquals(Markdown.BlockType.QUOTE, blocks.get(6).getType());
        assertEquals(Markdown.BlockType.RULE, blocks.get(7).getType());
        assertEquals(Markdown.BlockType.CODE, blocks.get(8).getType());
        assertEquals("code line", blocks.get(8).getText());
        assertTrue(Markdown.parse(null).isEmpty());
        assertTrue(Markdown.parse("").isEmpty());
    }

    @Test
    public void inlineStylesLinksAndWikiLinks() {
        List<Markdown.Span> spans = Markdown.inline("a **bold** and *italic* with `code` and [site](https://example.com) and [[Install]]");
        assertEquals("a ", spans.get(0).getText());
        assertTrue(spans.get(1).isBold());
        assertTrue(spans.get(3).isItalic());
        assertTrue(spans.get(5).isCode());
        assertEquals("https://example.com", spans.get(7).getUrl());
        assertEquals("wiki:Install", spans.get(9).getUrl());
        assertEquals("Install", spans.get(9).getText());
    }

    @Test
    public void snakeCaseAndStrayMarksStayPlainText() {
        List<Markdown.Span> spans = Markdown.inline("use my_variable_name and 2 * 3 = 6");
        assertEquals(1, spans.size());
        assertEquals("use my_variable_name and 2 * 3 = 6", spans.get(0).getText());
    }

    @Test
    public void dangerousLinksAndScriptsAreNeverKept() {
        List<Markdown.Span> spans = Markdown.inline("[click](javascript:alert(1))");
        for (Markdown.Span s : spans) {
            assertNull(s.getUrl());
        }
        assertFalse(Markdown.isSafeUrl("javascript:alert(1)"));
        assertFalse(Markdown.isSafeUrl("file:///etc/passwd"));
        assertFalse(Markdown.isSafeUrl("data:text/html,x"));
        assertTrue(Markdown.isSafeUrl("https://example.com"));
        String html = Markdown.toHtml("<script>alert('x')</script>\n\n[a](javascript:evil)");
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("javascript:"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    public void htmlOutputHasListsHeadingsAndCode() {
        String html = Markdown.toHtml("## Hi\n\n* a\n* b\n\n1. x\n\n```\n<b>\n```");
        assertTrue(html.contains("<h2>Hi</h2>"));
        assertTrue(html.contains("<ul>\n<li>a</li>\n<li>b</li>\n</ul>"));
        assertTrue(html.contains("<ol>\n<li>x</li>\n</ol>"));
        assertTrue(html.contains("<pre><code>&lt;b&gt;</code></pre>"));
    }

    @Test
    public void imagesAreShownAsText() {
        List<Markdown.Span> spans = Markdown.inline("![logo](https://example.com/logo.png)");
        assertEquals("[image: logo]", spans.get(0).getText());
    }
}
