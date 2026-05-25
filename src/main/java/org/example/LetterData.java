package org.example;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LetterData(
        String companyName,
        String companyLogoPath,
        String phone,
        String fax,
        String email,
        String okpo,
        String ogrn,
        String inn,
        String kpp,
        LocalDate fillDate,
        String recipient,
        String ourLetterNumber,
        String replyLetterNumber,
        LocalDate replyLetterDate,
        String letterTitle,
        String letterBody,
        String writerName,
        String writerPosition,
        String signature,
        List<LetterAttachment> attachments
) {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public Map<String, String> toPlaceholderMap() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("{{COMPANY_NAME}}", safe(companyName));
        values.put("{{PHONE}}", safe(phone));
        values.put("{{FAX}}", safe(fax));
        values.put("{{EMAIL}}", safe(email));
        values.put("{{OKPO}}", safe(okpo));
        values.put("{{OGRN}}", safe(ogrn));
        values.put("{{INN}}", safe(inn));
        values.put("{{KPP}}", safe(kpp));
        values.put("{{FILL_DATE}}", fillDate == null ? "" : fillDate.format(DATE_FORMATTER));
        values.put("{{RECIPIENT}}", safe(recipient));
        values.put("{{OUR_LETTER_NUMBER}}", safe(ourLetterNumber));
        values.put("{{REPLY_LETTER_NUMBER}}", safe(replyLetterNumber));
        values.put("{{REPLY_LETTER_DATE}}", replyLetterDate == null ? "" : replyLetterDate.format(DATE_FORMATTER));
        values.put("{{LETTER_TITLE}}", safe(letterTitle));
        values.put("{{LETTER_BODY}}", safe(letterBody));
        values.put("{{WRITER_NAME}}", safe(writerName));
        values.put("{{WRITER_POSITION}}", safe(writerPosition));
        values.put("{{SIGNATURE}}", safe(signature));
        values.put("{{ATTACHMENTS_LIST}}", buildAttachmentsList());
        return values;
    }

    private String buildAttachmentsList() {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(attachments.size() == 1 ? "Приложение:" : "Приложения:");
        for (int i = 0; i < attachments.size(); i++) {
            LetterAttachment attachment = attachments.get(i);
            String title = safe(attachment.title());
            builder.append("\n").append(i + 1).append(". ").append(title);
            String pages = safe(attachment.pages());
            if (!pages.isBlank()) {
                builder.append(" на ").append(pages).append(" л.");
            }
        }
        return builder.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
