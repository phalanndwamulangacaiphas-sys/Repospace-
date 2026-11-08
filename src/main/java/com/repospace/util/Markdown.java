package com.repospace.util;

import java.util.ArrayList;
import java.util.List;

public final class Markdown {

    public enum BlockType { HEADING, PARAGRAPH, BULLET, NUMBERED, QUOTE, CODE, RULE }

    public static final class Block {
        private final BlockType type;
        private final int level;
        private final String text;

        Block(BlockType type, int level, String text) {
            this.type = type;
            this.level = level;
            this.text = text;
        }

        public BlockType getType() {
            return type;
        }

        public int getLevel() {
            return level;
        }

        public String getText() {
            return text;
        }
    }

    public static final class Span {
        private final String text;
        private final boolean bold;
        private final boolean italic;
        private final boolean code;
        private final String url;

        Span(String text, boolean bold, boolean italic, boolean code, String url) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
            this.code = code;
            this.url = url;
        }

        public String getText() {
            return text;
        }

        public boolean isBold() {
            return bold;
        }

        public boolean isItalic() {
            return italic;
        }

        public boolean isCode() {
            return code;
        }

        public String getUrl() {
            return url;
        }

        public boolean isLink() {
            return url != null;
        }

        Span with(boolean makeBold, boolean makeItalic) {
            return new Span(text, bold || makeBold, italic || makeItalic, code, url);
        }
    }

    private Markdown() {
    }

    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (markdown == null) {
            return blocks;
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder paragraph = new StringBuilder();
        StringBuilder code = null;
        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (code != null) {
                if (line.strip().startsWith("```")) {
                    blocks.add(new Block(BlockType.CODE, 0, code.toString()));
                    code = null;
                } else {
                    if (code.length() > 0) {
                        code.append('\n');
                    }
                    code.append(raw);
                }
                continue;
            }
            String trimmed = line.strip();
            if (trimmed.startsWith("```")) {
                flush(blocks, paragraph);
                code = new StringBuilder();
            } else if (trimmed.isEmpty()) {
                flush(blocks, paragraph);
            } else if (trimmed.matches("(-{3,}|\\*{3,}|_{3,})")) {
                flush(blocks, paragraph);
                blocks.add(new Block(BlockType.RULE, 0, ""));
            } else if (trimmed.matches("#{1,6}\\s+.*")) {
                flush(blocks, paragraph);
                int level = 0;
                while (trimmed.charAt(level) == '#') {
                    level++;
                }
                blocks.add(new Block(BlockType.HEADING, level, trimmed.substring(level).strip()));
            } else if (trimmed.startsWith(">")) {
                flush(blocks, paragraph);
                blocks.add(new Block(BlockType.QUOTE, 0, trimmed.substring(1).strip()));
            } else if (trimmed.matches("[-*+]\\s+.*")) {
                flush(blocks, paragraph);
                blocks.add(new Block(BlockType.BULLET, 0, trimmed.substring(1).strip()));
            } else if (trimmed.matches("\\d{1,4}[.)]\\s+.*")) {
                flush(blocks, paragraph);
                int dot = 0;
                while (Character.isDigit(trimmed.charAt(dot))) {
                    dot++;
                }
                blocks.add(new Block(BlockType.NUMBERED, Integer.parseInt(trimmed.substring(0, dot)), trimmed.substring(dot + 1).strip()));
            } else {
                if (paragraph.length() > 0) {
                    paragraph.append(' ');
                }
                paragraph.append(trimmed);
            }
        }
        if (code != null) {
            blocks.add(new Block(BlockType.CODE, 0, code.toString()));
        }
        flush(blocks, paragraph);
        return blocks;
    }

    private static void flush(List<Block> blocks, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            blocks.add(new Block(BlockType.PARAGRAPH, 0, paragraph.toString()));
            paragraph.setLength(0);
        }
    }

    public static List<Span> inline(String text) {
        List<Span> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return spans;
        }
        StringBuilder plain = new StringBuilder();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (ch == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i + 1) {
                    addPlain(spans, plain);
                    spans.add(new Span(text.substring(i + 1, end), false, false, true, null));
                    i = end + 1;
                    continue;
                }
            } else if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end > i + 2) {
                    addPlain(spans, plain);
                    for (Span inner : inline(text.substring(i + 2, end))) {
                        spans.add(inner.with(true, false));
                    }
                    i = end + 2;
                    continue;
                }
            } else if (ch == '*' || (ch == '_' && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1))))) {
                int end = text.indexOf(ch, i + 1);
                if (end > i + 1 && !Character.isWhitespace(text.charAt(i + 1))
                        && (ch == '*' || end + 1 >= n || !Character.isLetterOrDigit(text.charAt(end + 1)))) {
                    addPlain(spans, plain);
                    for (Span inner : inline(text.substring(i + 1, end))) {
                        spans.add(inner.with(false, true));
                    }
                    i = end + 1;
                    continue;
                }
            } else if (text.startsWith("![", i)) {
                int close = text.indexOf("](", i + 2);
                int end = close < 0 ? -1 : text.indexOf(')', close + 2);
                if (close > 0 && end > 0) {
                    plain.append("[image: ").append(text, i + 2, close).append(']');
                    i = end + 1;
                    continue;
                }
            } else if (text.startsWith("[[", i)) {
                int end = text.indexOf("]]", i + 2);
                if (end > i + 2) {
                    String title = text.substring(i + 2, end).strip();
                    if (!title.isEmpty()) {
                        addPlain(spans, plain);
                        spans.add(new Span(title, false, false, false, "wiki:" + title));
                        i = end + 2;
                        continue;
                    }
                }
            } else if (ch == '[') {
                int close = text.indexOf("](", i + 1);
                int end = close < 0 ? -1 : text.indexOf(')', close + 2);
                if (close > i && end > 0) {
                    String label = text.substring(i + 1, close);
                    String url = text.substring(close + 2, end).strip();
                    if (isSafeUrl(url) && !label.isBlank()) {
                        addPlain(spans, plain);
                        spans.add(new Span(label, false, false, false, url));
                    } else {
                        plain.append(label);
                    }
                    i = end + 1;
                    continue;
                }
            }
            plain.append(ch);
            i++;
        }
        addPlain(spans, plain);
        return spans;
    }

    private static void addPlain(List<Span> spans, StringBuilder plain) {
        if (plain.length() > 0) {
            spans.add(new Span(plain.toString(), false, false, false, null));
            plain.setLength(0);
        }
    }

    public static boolean isSafeUrl(String url) {
        if (url == null) {
            return false;
        }
        String u = url.strip().toLowerCase();
        return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("mailto:");
    }

    public static String toHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        String openList = null;
        for (Block block : parse(markdown)) {
            BlockType t = block.getType();
            String wanted = t == BlockType.BULLET ? "ul" : t == BlockType.NUMBERED ? "ol" : null;
            if (openList != null && !openList.equals(wanted)) {
                html.append("</").append(openList).append(">\n");
                openList = null;
            }
            if (wanted != null && openList == null) {
                html.append('<').append(wanted).append(">\n");
                openList = wanted;
            }
            switch (t) {
                case HEADING -> html.append("<h").append(block.getLevel()).append('>')
                        .append(inlineHtml(block.getText())).append("</h").append(block.getLevel()).append(">\n");
                case PARAGRAPH -> html.append("<p>").append(inlineHtml(block.getText())).append("</p>\n");
                case BULLET, NUMBERED -> html.append("<li>").append(inlineHtml(block.getText())).append("</li>\n");
                case QUOTE -> html.append("<blockquote>").append(inlineHtml(block.getText())).append("</blockquote>\n");
                case CODE -> html.append("<pre><code>").append(escape(block.getText())).append("</code></pre>\n");
                case RULE -> html.append("<hr>\n");
                default -> throw new IllegalStateException("Unknown block " + t);
            }
        }
        if (openList != null) {
            html.append("</").append(openList).append(">\n");
        }
        return html.toString();
    }

    private static String inlineHtml(String text) {
        StringBuilder sb = new StringBuilder();
        for (Span s : inline(text)) {
            String body = escape(s.getText());
            if (s.isCode()) {
                body = "<code>" + body + "</code>";
            }
            if (s.isBold()) {
                body = "<strong>" + body + "</strong>";
            }
            if (s.isItalic()) {
                body = "<em>" + body + "</em>";
            }
            if (s.isLink()) {
                String href = s.getUrl().startsWith("wiki:") ? "#" : s.getUrl();
                body = "<a href=\"" + escape(href) + "\" rel=\"noopener noreferrer\">" + body + "</a>";
            }
            sb.append(body);
        }
        return sb.toString();
    }

    public static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
