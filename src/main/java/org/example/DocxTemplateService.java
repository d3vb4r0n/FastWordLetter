package org.example;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;

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
    private static final String ATTACHMENTS_SECTION_PLACEHOLDER = "{{ATTACHMENTS_SECTION}}";
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
        List<Attachment> attachments = data.attachments();

        try (InputStream in = Files.newInputStream(templatePath);
             XWPFDocument document = new XWPFDocument(in)) {

            processParagraphs(document.getParagraphs(), placeholders, logoPath, attachments, true);

            for (XWPFTable table : document.getTables()) {
                processTable(table, placeholders, logoPath, attachments);
            }

            for (XWPFHeader header : document.getHeaderList()) {
                processParagraphs(header.getParagraphs(), placeholders, logoPath, attachments, false);
                for (XWPFTable table : header.getTables()) {
                    processTable(table, placeholders, logoPath, attachments);
                }
            }

            for (XWPFFooter footer : document.getFooterList()) {
                processParagraphs(footer.getParagraphs(), placeholders, logoPath, attachments, false);
                for (XWPFTable table : footer.getTables()) {
                    processTable(table, placeholders, logoPath, attachments);
                }
            }

            if (attachments == null || attachments.isEmpty()) {
                trimTrailingEmptyParagraphs(document);
            }

            try (OutputStream out = Files.newOutputStream(outputPath)) {
                document.write(out);
            }
        }
    }

    private void processTable(XWPFTable table, Map<String, String> placeholders, Path logoPath, List<Attachment> attachments) throws IOException {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                processParagraphs(cell.getParagraphs(), placeholders, logoPath, attachments, false);
                for (XWPFTable nestedTable : cell.getTables()) {
                    processTable(nestedTable, placeholders, logoPath, attachments);
                }
            }
        }
    }

    private void processParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> placeholders, Path logoPath, List<Attachment> attachments, boolean allowAttachments) throws IOException {
        List<XWPFParagraph> snapshot = new ArrayList<>(paragraphs);
        for (XWPFParagraph paragraph : snapshot) {
            renderParagraph(paragraph, placeholders, logoPath, attachments, allowAttachments);
        }
    }

    private void renderParagraph(XWPFParagraph paragraph, Map<String, String> placeholders, Path logoPath, List<Attachment> attachments, boolean allowAttachments) throws IOException {
        String text = paragraph.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        if (allowAttachments && text.contains(ATTACHMENTS_SECTION_PLACEHOLDER)) {
            renderAttachmentsSection(paragraph, attachments);
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

    private void addTextRun(XWPFParagraph paragraph, String text, boolean underlined) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            run.setText(lines[i]);
            if (underlined) {
                run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
            }
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    private void renderAttachmentsSection(XWPFParagraph paragraph, List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            if (!removeParagraph(paragraph)) {
                clearParagraphRuns(paragraph);
            }
            return;
        }

        boolean multiple = attachments.size() > 1;
        XWPFParagraph current = paragraph;
        for (int i = 0; i < attachments.size(); i++) {
            Attachment attachment = attachments.get(i);
            if (i > 0) {
                current = insertParagraphAfter(current);
            }
            clearParagraphRuns(current);
            current.setAlignment(ParagraphAlignment.RIGHT);

            XWPFRun headerRun = current.createRun();
            if (i > 0) {
                headerRun.addBreak(BreakType.PAGE);
            }
            headerRun.setText(multiple ? "Приложение " + (i + 1) : "Приложение");

            XWPFParagraph titleParagraph = insertParagraphAfter(current);
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            addTextRun(titleParagraph, safe(attachment.title()), false);

            List<String> bodyLines = splitLines(safe(attachment.body()));
            XWPFParagraph bodyParagraph = insertParagraphAfter(titleParagraph);
            bodyParagraph.setAlignment(ParagraphAlignment.LEFT);
            if (bodyLines.isEmpty()) {
                addTextRun(bodyParagraph, "", false);
            } else {
                addTextRun(bodyParagraph, bodyLines.get(0), false);
                for (int lineIndex = 1; lineIndex < bodyLines.size(); lineIndex++) {
                    XWPFParagraph nextBody = insertParagraphAfter(bodyParagraph);
                    nextBody.setAlignment(ParagraphAlignment.LEFT);
                    addTextRun(nextBody, bodyLines.get(lineIndex), false);
                    bodyParagraph = nextBody;
                }
            }
            current = bodyParagraph;
        }
    }

    private XWPFParagraph insertParagraphAfter(XWPFParagraph paragraph) {
        XmlCursor cursor = paragraph.getCTP().newCursor();
        cursor.toEndToken();
        Object body = paragraph.getBody();
        try {
            if (body instanceof XWPFDocument document) {
                return document.insertNewParagraph(cursor);
            }
            if (body instanceof XWPFTableCell cell) {
                return cell.insertNewParagraph(cursor);
            }
            if (body instanceof XWPFHeader header) {
                return header.insertNewParagraph(cursor);
            }
            if (body instanceof XWPFFooter footer) {
                return footer.insertNewParagraph(cursor);
            }
        } catch (ClassCastException ignored) {
        }
        if (body instanceof XWPFDocument document) {
            return document.createParagraph();
        }
        if (body instanceof XWPFTableCell cell) {
            return cell.addParagraph();
        }
        if (body instanceof XWPFHeader header) {
            return header.createParagraph();
        }
        if (body instanceof XWPFFooter footer) {
            return footer.createParagraph();
        }
        throw new IllegalStateException("Не удалось вставить новый абзац для приложений.");
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] lines = text.split("\\R", -1);
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.isEmpty() || lines.length == 1) {
                result.add(line);
            }
        }
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
        return XWPFDocument.PICTURE_TYPE_PNG;
    }


    private boolean removeParagraph(XWPFParagraph paragraph) {
        Object body = paragraph.getBody();
        if (body instanceof XWPFDocument document) {
            int index = document.getBodyElements().indexOf(paragraph);
            return index >= 0 && document.removeBodyElement(index);
        }
        if (body instanceof XWPFTableCell cell) {
            int index = cell.getParagraphs().indexOf(paragraph);
            if (index >= 0) {
                cell.removeParagraph(index);
                return true;
            }
            return false;
        }
        if (body instanceof XWPFHeader header) {
            int index = header.getParagraphs().indexOf(paragraph);
            if (index >= 0) {
                header.removeParagraph(paragraph);
                return true;
            }
            return false;
        }
        if (body instanceof XWPFFooter footer) {
            int index = footer.getParagraphs().indexOf(paragraph);
            if (index >= 0) {
                footer.removeParagraph(paragraph);
                return true;
            }
            return false;
        }
        return false;
    }

    private void trimTrailingEmptyParagraphs(XWPFDocument document) {
        List<org.apache.poi.xwpf.usermodel.IBodyElement> elements = document.getBodyElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            org.apache.poi.xwpf.usermodel.IBodyElement element = elements.get(i);
            if (element instanceof XWPFParagraph paragraph) {
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    document.removeBodyElement(i);
                    continue;
                }
            }
            break;
        }
    }
}
