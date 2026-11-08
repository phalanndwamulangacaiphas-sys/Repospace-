package com.repospace.controller;

import com.repospace.util.Markdown;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

final class MarkdownView {

    private MarkdownView() {
    }

    static VBox render(String markdown, Consumer<String> onLink) {
        VBox box = new VBox(8);
        for (Markdown.Block block : Markdown.parse(markdown)) {
            box.getChildren().add(renderBlock(block, onLink));
        }
        return box;
    }

    private static Node renderBlock(Markdown.Block block, Consumer<String> onLink) {
        switch (block.getType()) {
            case HEADING: {
                int size = Math.max(14, 28 - 3 * block.getLevel());
                return flow(block.getText(), onLink, "-fx-font-size: " + size + "px; -fx-font-weight: bold;", "");
            }
            case BULLET:
                return flow("•  " + block.getText(), onLink, "", "-fx-padding: 0 0 0 14;");
            case NUMBERED:
                return flow(block.getLevel() + ".  " + block.getText(), onLink, "", "-fx-padding: 0 0 0 14;");
            case QUOTE:
                return flow(block.getText(), onLink, "-fx-font-style: italic;",
                        "-fx-border-color: #149dfd; -fx-border-width: 0 0 0 3; -fx-padding: 0 0 0 10;");
            case CODE: {
                Label code = new Label(block.getText());
                code.getStyleClass().add("md-code");
                return code;
            }
            case RULE:
                return new Separator();
            case PARAGRAPH:
            default:
                return flow(block.getText(), onLink, "", "");
        }
    }

    private static TextFlow flow(String text, Consumer<String> onLink, String extraTextStyle, String flowStyle) {
        TextFlow flow = new TextFlow();
        if (!flowStyle.isEmpty()) {
            flow.setStyle(flowStyle);
        }
        for (Markdown.Span span : Markdown.inline(text)) {
            if (span.isLink()) {
                Hyperlink link = new Hyperlink(span.getText());
                String url = span.getUrl();
                link.setOnAction(e -> onLink.accept(url));
                flow.getChildren().add(link);
                continue;
            }
            Text piece = new Text(span.getText());
            piece.getStyleClass().add("md-text");
            StringBuilder style = new StringBuilder(extraTextStyle);
            if (span.isBold()) {
                style.append(" -fx-font-weight: bold;");
            }
            if (span.isItalic()) {
                style.append(" -fx-font-style: italic;");
            }
            if (span.isCode()) {
                style.append(" -fx-font-family: monospace;");
            }
            if (style.length() > 0) {
                piece.setStyle(style.toString());
            }
            flow.getChildren().add(piece);
        }
        return flow;
    }
}
