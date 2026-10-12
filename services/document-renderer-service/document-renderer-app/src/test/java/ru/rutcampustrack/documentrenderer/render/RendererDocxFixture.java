package ru.rutcampustrack.documentrenderer.render;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministic two-page OOXML with Cyrillic text and page-specific visible color patches. */
final class RendererDocxFixture {
    private RendererDocxFixture() {}

    static byte[] docx() {
        try {
            return twoPageDocument();
        } catch (Exception error) {
            throw new IllegalStateException("Could not prepare synthetic DOCX fixture", error);
        }
    }

    private static byte[] twoPageDocument() throws Exception {
        String doc =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body><w:p><w:r><w:t>Синтетический отчёт FIRST PAGE</w:t></w:r></w:p>
                %s
                <w:p><w:r><w:br w:type="page"/></w:r></w:p>
                <w:p><w:r><w:t>Синтетический отчёт SECOND PAGE</w:t></w:r></w:p>
                %s
                <w:sectPr><w:pgSz w:w="11906" w:h="16838"/>
                <w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720"/>
                </w:sectPr></w:body></w:document>
                """
                        .formatted(colorTable("FF0000"), colorTable("0000FF"));
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(
                    zip,
                    "[Content_Types].xml",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/word/document.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            entry(
                    zip,
                    "_rels/.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="word/document.xml"/></Relationships>
                    """);
            entry(zip, "word/document.xml", doc);
            zip.finish();
            return bytes.toByteArray();
        }
    }

    private static String colorTable(String color) {
        return """
                <w:tbl><w:tblPr><w:tblW w:w="6000" w:type="dxa"/></w:tblPr>
                <w:tblGrid><w:gridCol w:w="6000"/></w:tblGrid>
                <w:tr><w:trPr><w:trHeight w:val="1440" w:hRule="exact"/></w:trPr>
                <w:tc><w:tcPr><w:tcW w:w="6000" w:type="dxa"/>
                <w:shd w:fill="%s"/></w:tcPr><w:p/></w:tc></w:tr></w:tbl>
                """
                .formatted(color);
    }

    private static void entry(ZipOutputStream zip, String name, String xml) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
