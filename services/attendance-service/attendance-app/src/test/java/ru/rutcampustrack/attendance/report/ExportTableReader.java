package ru.rutcampustrack.attendance.report;

import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import javax.xml.parsers.DocumentBuilderFactory;

/** Reads exported logical cells without depending on styles, coordinates, or text runs. */
public final class ExportTableReader {
    private ExportTableReader() {}

    public static org.w3c.dom.Document xml(String value) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static List<List<String>> wordRows(String value) throws Exception {
        List<List<String>> result = new ArrayList<>();
        var rows = xml(value).getElementsByTagNameNS("*", "tr");
        for (int i = 0; i < rows.getLength(); i++) {
            List<String> cells = new ArrayList<>();
            var nodes = ((Element) rows.item(i)).getElementsByTagNameNS("*", "tc");
            for (int j = 0; j < nodes.getLength(); j++) {
                var texts = ((Element) nodes.item(j)).getElementsByTagNameNS("*", "t");
                StringBuilder text = new StringBuilder();
                for (int k = 0; k < texts.getLength(); k++)
                    text.append(texts.item(k).getTextContent());
                cells.add(text.toString());
            }
            result.add(cells);
        }
        return result;
    }

    public static List<List<String>> sheetRows(String value) throws Exception {
        List<List<String>> result = new ArrayList<>();
        var rows = xml(value).getElementsByTagNameNS("*", "row");
        for (int i = 0; i < rows.getLength(); i++) {
            List<String> cells = new ArrayList<>();
            var nodes = ((Element) rows.item(i)).getElementsByTagNameNS("*", "c");
            for (int j = 0; j < nodes.getLength(); j++) cells.add(nodes.item(j).getTextContent());
            result.add(cells);
        }
        return result;
    }

    public static String cell(List<List<String>> rows, String rowName, String headerPart) {
        var header =
                rows.stream()
                        .filter(
                                cells ->
                                        cells.contains("Студент")
                                                && cells.stream()
                                                        .anyMatch(
                                                                value ->
                                                                        value.contains(headerPart)))
                        .findFirst()
                        .orElseThrow();
        int column = -1;
        for (int index = 0; index < header.size(); index++)
            if (header.get(index).contains(headerPart)) column = index;
        var row = rows.stream().filter(cells -> cells.contains(rowName)).findFirst().orElseThrow();
        return row.get(column);
    }

    public static List<Element> sheetCells(String xml, String rowName) throws Exception {
        var rows = xml(xml).getElementsByTagNameNS("*", "row");
        for (int index = 0; index < rows.getLength(); index++) {
            var cells = ((Element) rows.item(index)).getElementsByTagNameNS("*", "c");
            List<Element> found = new ArrayList<>();
            for (int cell = 0; cell < cells.getLength(); cell++)
                found.add((Element) cells.item(cell));
            if (!found.isEmpty() && found.getFirst().getTextContent().equals(rowName)) return found;
        }
        throw new IllegalArgumentException("Missing exported row: " + rowName);
    }

    public static List<List<String>> htmlRows(String value) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        new ParserDelegator()
                .parse(
                        new StringReader(value),
                        new HTMLEditorKit.ParserCallback() {
                            private List<String> row;
                            private StringBuilder cell;

                            @Override
                            public void handleStartTag(
                                    HTML.Tag tag, MutableAttributeSet attrs, int position) {
                                if (tag == HTML.Tag.TR) row = new ArrayList<>();
                                if (row != null && (tag == HTML.Tag.TD || tag == HTML.Tag.TH))
                                    cell = new StringBuilder();
                            }

                            @Override
                            public void handleText(char[] text, int position) {
                                if (cell != null) cell.append(text);
                            }

                            @Override
                            public void handleEndTag(HTML.Tag tag, int position) {
                                if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && cell != null) {
                                    row.add(cell.toString());
                                    cell = null;
                                }
                                if (tag == HTML.Tag.TR && row != null) {
                                    rows.add(row);
                                    row = null;
                                }
                            }
                        },
                        true);
        return rows;
    }
}
