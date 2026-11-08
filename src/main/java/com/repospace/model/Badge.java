package com.repospace.model;

import com.repospace.util.Markdown;

public final class Badge {

    private final String key;
    private final String label;
    private final String value;
    private final String color;

    public Badge(String key, String label, String value, String color) {
        this.key = key;
        this.label = label;
        this.value = value;
        this.color = color;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getColor() {
        return color;
    }

    public String getText() {
        return label + ": " + value;
    }

    public String toSvg() {
        int labelWidth = width(label);
        int valueWidth = width(value);
        int total = labelWidth + valueWidth;
        String l = Markdown.escape(label);
        String v = Markdown.escape(value);
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + total + "\" height=\"22\" role=\"img\" aria-label=\"" + l + ": " + v + "\">"
                + "<rect width=\"" + labelWidth + "\" height=\"22\" rx=\"4\" fill=\"#24366b\"/>"
                + "<rect x=\"" + labelWidth + "\" width=\"" + valueWidth + "\" height=\"22\" rx=\"4\" fill=\"" + color + "\"/>"
                + "<rect x=\"" + (labelWidth - 4) + "\" width=\"8\" height=\"22\" fill=\"" + color + "\"/>"
                + "<g fill=\"#ffffff\" font-family=\"Verdana,Arial,sans-serif\" font-size=\"11\" text-anchor=\"middle\">"
                + "<text x=\"" + (labelWidth / 2) + "\" y=\"15\">" + l + "</text>"
                + "<text x=\"" + (labelWidth + valueWidth / 2) + "\" y=\"15\">" + v + "</text></g></svg>";
    }

    private static int width(String text) {
        return 16 + (int) Math.ceil(text.length() * 6.6);
    }
}
