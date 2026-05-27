package org.example;

import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class LetterPreview {

    private final VBox pagesBox;
    private final ScrollPane scrollPane;
    private final PauseTransition debounce;
    private final ExecutorService executor;
    private final LetterDocx templateService;
    private final Path templatePath;

    private LetterData currentData;

    public LetterPreview(Path templatePath, LetterDocx templateService) {
        this.templatePath = templatePath;
        this.templateService = templateService;

        this.pagesBox = new VBox(15);
        this.pagesBox.setPadding(new Insets(20));
        this.pagesBox.setAlignment(Pos.TOP_CENTER);
        this.pagesBox.setStyle("-fx-background-color: #2a2448;");

        this.scrollPane = new ScrollPane(pagesBox);
        this.scrollPane.setFitToWidth(true);
        this.scrollPane.setStyle("-fx-background: #2a2448; -fx-background-color: #2a2448;");
        this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Preview-Generator");
            t.setDaemon(true);
            return t;
        });

        this.debounce = new PauseTransition(Duration.millis(600));
        this.debounce.setOnFinished(e -> {
            if (currentData != null) {
                executeGeneration(currentData);
            }
        });

        showPlaceholder("Заполните поля слева,\nчтобы увидеть предпросмотр...");
    }

    public ScrollPane getView() {
        return scrollPane;
    }

    public void updatePreview(LetterData data) {
        this.currentData = data;
        debounce.playFromStart();
    }

    private void showPlaceholder(String text) {
        Platform.runLater(() -> {
            pagesBox.getChildren().clear();
            Label label = new Label(text);
            label.setStyle("-fx-text-fill: #c3b7ff; -fx-font-size: 16px;");
            label.setAlignment(Pos.CENTER);
            pagesBox.getChildren().add(label);
        });
    }

    private void executeGeneration(LetterData data) {
        executor.submit(() -> {
            try {
                Path tempDocx = Files.createTempFile("letter_preview_", ".docx");
                try {
                    templateService.generateFromTemplate(templatePath, tempDocx, data);

                    ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
                    try (InputStream docxIn = Files.newInputStream(tempDocx);
                         XWPFDocument document = new XWPFDocument(docxIn)) {

                        PdfOptions options = PdfOptions.create();
                        PdfConverter.getInstance().convert(document, pdfOut, options);
                    }

                    byte[] pdfBytes = pdfOut.toByteArray();
                    List<Image> fxImages = new ArrayList<>();

                    try (PDDocument pdfDoc = PDDocument.load(pdfBytes)) {
                        PDFRenderer renderer = new PDFRenderer(pdfDoc);
                        int pages = pdfDoc.getNumberOfPages();
                        for (int i = 0; i < pages; i++) {
                            BufferedImage bim = renderer.renderImageWithDPI(i, 110);
                            fxImages.add(SwingFXUtils.toFXImage(bim, null));
                        }
                    }

                    Platform.runLater(() -> {
                        pagesBox.getChildren().clear();
                        for (Image img : fxImages) {
                            ImageView iv = new ImageView(img);
                            iv.setPreserveRatio(true);

                            iv.fitWidthProperty().bind(
                                    Bindings.max(100.0, scrollPane.widthProperty().subtract(60.0))
                            );

                            DropShadow shadow = new DropShadow();
                            shadow.setRadius(15);
                            shadow.setOffsetX(0);
                            shadow.setOffsetY(5);
                            shadow.setColor(Color.color(0, 0, 0, 0.6));
                            iv.setEffect(shadow);

                            pagesBox.getChildren().add(iv);
                        }
                    });

                } finally {
                    Files.deleteIfExists(tempDocx);
                }
            } catch (Exception e) {
                e.printStackTrace();
                showPlaceholder("Ошибка генерации:\n" + e.getMessage());
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}