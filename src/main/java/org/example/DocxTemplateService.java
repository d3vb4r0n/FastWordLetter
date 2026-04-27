package org.example;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DocxTemplateService {

    private static final String LOGO_PLACEHOLDER = "{{COMPANY_LOGO}}";
    private static final int DEFAULT_LOGO_WIDTH = 140;
    private static final int DEFAULT_LOGO_HEIGHT = 80;
    private static final Set<String> UNDERLINED_PLACEHOLDERS = Set.of(
            "{{SIGNATURE}}",
            "{{OUR_LETTER_NUMBER}}",
            "{{REPLY_LETTER_NUMBER}}",
            "{{REPLY_LETTER_DATE}}"
    );

    public void generateFromTemplate(Path templatePath, Path outputPath, LetterData data) throws IOException {
        Map<String, String> placeholders = data.toPlaceholderMap();
        Path logoPath = toLogoPath(data.companyLogoPath());

        try (InputStream in = Files.newInputStream(templatePath);
             XWPFDocument document = new XWPFDocument(in)) {

            processParagraphs(document.getParagraphs(), placeholders, logoPath);

            for (XWPFTable table : document.getTables()) {
                processTable(table, placeholders, logoPath);
            }

            for (XWPFHeader header : document.getHeaderList()) {
                processParagraphs(header.getParagraphs(), placeholders, logoPath);
                for (XWPFTable table : header.getTables()) {
                    processTable(table, placeholders, logoPath);
                }
            }

            for (XWPFFooter footer : document.getFooterList()) {
                processParagraphs(footer.getParagraphs(), placeholders, logoPath);
                for (XWPFTable table : footer.getTables()) {
                    processTable(table, placeholders, logoPath);
                }
            }

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                document.write(out);
            }
        }
    }

    private void processTable(XWPFTable table, Map<String, String> placeholders, Path logoPath) throws IOException {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                processParagraphs(cell.getParagraphs(), placeholders, logoPath);
                for (XWPFTable nestedTable : cell.getTables()) {
                    processTable(nestedTable, placeholders, logoPath);
                }
            }
        }
    }

    private void processParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> placeholders, Path logoPath) throws IOException {
        for (XWPFParagraph paragraph : paragraphs) {
            renderParagraph(paragraph, placeholders, logoPath);
        }
    }

    private void renderParagraph(XWPFParagraph paragraph, Map<String, String> placeholders, Path logoPath) throws IOException {
        String text = paragraph.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        List<String> orderedPlaceholders = new ArrayList<>(placeholders.keySet());
        orderedPlaceholders.sort((a, b) -> Integer.compare(b.length(), a.length()));

        boolean hasAnyMarker = text.contains(LOGO_PLACEHOLDER);
        if (!hasAnyMarker) {
            for (String marker : orderedPlaceholders) {
                if (text.contains(marker)) {
                    hasAnyMarker = true;
                    break;
                }
            }
        }

        if (!hasAnyMarker) {
            return;
        }

        clearParagraphRuns(paragraph);

        int index = 0;
        while (index < text.length()) {
            Match nextMatch = findNextMatch(text, index, orderedPlaceholders);
            if (nextMatch == null) {
                addTextRun(paragraph, text.substring(index), false);
                break;
            }

            if (nextMatch.start > index) {
                addTextRun(paragraph, text.substring(index, nextMatch.start), false);
            }

            if (LOGO_PLACEHOLDER.equals(nextMatch.marker)) {
                addLogoRun(paragraph, logoPath);
            } else {
                String value = placeholders.getOrDefault(nextMatch.marker, "");
                addTextRun(paragraph, value, UNDERLINED_PLACEHOLDERS.contains(nextMatch.marker));
            }
            index = nextMatch.start + nextMatch.marker.length();
        }
    }

    private void clearParagraphRuns(XWPFParagraph paragraph) {
        List<XWPFRun> runs = paragraph.getRuns();
        for (int i = runs.size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
    }

    private void addTextRun(XWPFParagraph paragraph, String text, boolean underlined) {
        if (text == null || text.isEmpty()) {
            return;
        }
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        if (underlined) {
            run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
        }
    }

    private void addLogoRun(XWPFParagraph paragraph, Path logoPath) throws IOException {
        if (logoPath == null) {
            return;
        }
        try (InputStream imageStream = Files.newInputStream(logoPath)) {
            XWPFRun imageRun = paragraph.createRun();
            imageRun.addPicture(
                    imageStream,
                    detectPictureType(logoPath),
                    logoPath.getFileName().toString(),
                    Units.toEMU(DEFAULT_LOGO_WIDTH),
                    Units.toEMU(DEFAULT_LOGO_HEIGHT)
            );
        } catch (InvalidFormatException e) {
            throw new IOException("Не удалось вставить логотип в документ.", e);
        }
    }

    private Match findNextMatch(String text, int fromIndex, List<String> orderedPlaceholders) {
        int closestIndex = -1;
        String closestMarker = null;

        int logoIndex = text.indexOf(LOGO_PLACEHOLDER, fromIndex);
        if (logoIndex >= 0) {
            closestIndex = logoIndex;
            closestMarker = LOGO_PLACEHOLDER;
        }

        for (String marker : orderedPlaceholders) {
            int index = text.indexOf(marker, fromIndex);
            if (index < 0) {
                continue;
            }
            if (closestIndex < 0 || index < closestIndex) {
                closestIndex = index;
                closestMarker = marker;
            }
        }

        return closestIndex < 0 ? null : new Match(closestIndex, closestMarker);
    }

    private record Match(int start, String marker) {
    }

    private Path toLogoPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path path = Path.of(rawPath.trim());
        return Files.exists(path) ? path : null;
    }

    private int detectPictureType(Path imagePath) {
        String fileName = imagePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return XWPFDocument.PICTURE_TYPE_JPEG;
        }
        if (fileName.endsWith(".gif")) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        }
        if (fileName.endsWith(".bmp")) {
            return XWPFDocument.PICTURE_TYPE_BMP;
        }
        return XWPFDocument.PICTURE_TYPE_PNG;
    }
}

