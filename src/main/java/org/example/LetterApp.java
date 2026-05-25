package org.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LetterApp extends Application {

    private static final Path TEMPLATE_PATH = Path.of("materials", "Шаблон.docx").toAbsolutePath();
    private static final String DEFAULT_LOGO_PATH = Path.of("materials", "Logo.jpg").toAbsolutePath().toString();
    private static final String ERROR_CLASS = "field-error";

    private final LetterDocx templateService = new LetterDocx();

    private final TextField companyNameField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField faxField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField okpoField = new TextField();
    private final TextField ogrnField = new TextField();
    private final TextField innField = new TextField();
    private final TextField kppField = new TextField();
    private final DatePicker fillDatePicker = new DatePicker(LocalDate.now());
    private final TextField recipientField = new TextField();
    private final TextField ourLetterNumberField = new TextField();
    private final TextField replyLetterNumberField = new TextField();
    private final DatePicker replyLetterDatePicker = new DatePicker();
    private final TextField letterTitleField = new TextField();
    private final TextArea letterBodyArea = new TextArea();
    private final TextField writerNameField = new TextField();
    private final TextField writerPositionField = new TextField();
    private final TextField signatureField = new TextField();

    private final List<ExampleBinding> exampleBindings = new ArrayList<>();

    private final VBox attachmentsBox = new VBox(10);
    private final List<AttachmentRow> attachmentRows = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        stage.setTitle("Несчастный генератор писем DOCX");

        letterBodyArea.setPrefRowCount(8);
        letterBodyArea.setWrapText(true);

        GridPane form = new GridPane();
        form.setPadding(new Insets(16));
        form.setHgap(16);
        form.setVgap(10);
        form.setPrefWidth(980);

        int row = 0;
        addRow(form, row++, "Название компании", companyNameField, "ООО \"Рога и Копыта\"");
        addRow(form, row++, "Телефон", phoneField, "+7 123 456 78 90");
        addRow(form, row++, "Факс", faxField, "+8 123 456 78 90");
        addRow(form, row++, "Почта", emailField, "rogacopita@gmail.com");
        addRow(form, row++, "ОКПО", okpoField, "64609789");
        addRow(form, row++, "ОГРН", ogrnField, "1097654389076");
        addRow(form, row++, "ИНН", innField, "7701234567");
        addRow(form, row++, "КПП", kppField, "770605405");
        addRow(form, row++, "Дата", fillDatePicker, "");
        addRow(form, row++, "Кому пишем", recipientField, "Генеральному директору ООО \"Рога и Копыта\" Остапу Бендеру");
        addRow(form, row++, "Регистрационный номер нашего письма", ourLetterNumberField, "6543-21");
        addRow(form, row++, "Номер письма, на которое отвечаем", replyLetterNumberField, "1234-56");
        addRow(form, row++, "Дата письма, на которое отвечаем", replyLetterDatePicker, "");
        addRow(form, row++, "Заголовок письма", letterTitleField, "Уважаемый Остап Бендер,");
        addRow(form, row++, "Тело письма", letterBodyArea, "Верните, пожалуйста, стул!");
        addRow(form, row++, "ФИО автора", writerNameField, "Зюганов Геннадий Андреевич");
        addRow(form, row++, "Должность автора", writerPositionField, "Руководитель партии КПРФ");
        addRow(form, row++, "Подпись", signatureField, "Зюганов Г. А.");
        Separator attachmentsSeparator = new Separator();
        form.add(attachmentsSeparator, 0, row++);
        GridPane.setColumnSpan(attachmentsSeparator, 3);
        addRow(form, row, "Приложения", createAttachmentsSection(), "");

        Button fillExamplesButton = new Button("Заполнить по примеру");
        fillExamplesButton.setOnAction(event -> fillFromExamples());

        Button generateButton = new Button("Сформировать документ");
        generateButton.setOnAction(event -> generateDocument(stage));

        HBox actions = new HBox(10, fillExamplesButton, generateButton);
        actions.setPadding(new Insets(0, 16, 16, 16));

        ScrollPane scrollPane = new ScrollPane(form);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        BorderPane root = new BorderPane();
        root.setCenter(scrollPane);
        root.setBottom(actions);

        Scene scene = new Scene(root, 980, 680);
        var stylesheet = getClass().getResource("/styles.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        registerValidationListeners();
        stage.setScene(scene);
        stage.show();
    }

    private void addRow(GridPane form, int row, String label, javafx.scene.Node input, String example) {
        form.add(new Label(label + ":"), 0, row);
        form.add(input, 1, row);
        Label exampleLabel = createExampleLabel(example);
        form.add(exampleLabel, 2, row);
        registerExampleBinding(input, exampleLabel);
    }

    private Label createExampleLabel(String example) {
        Label label = new Label(example == null ? "" : example);
        label.setStyle("-fx-text-fill: #c3b7ff; -fx-font-size: 11px;");
        label.setWrapText(true);
        label.setMaxWidth(330);
        return label;
    }

    private void registerExampleBinding(javafx.scene.Node input, Label exampleLabel) {
        if (input instanceof TextInputControl textInput) {
            exampleBindings.add(new ExampleBinding(textInput, exampleLabel));
        }
    }

    private void fillFromExamples() {
        for (ExampleBinding binding : exampleBindings) {
            String example = binding.exampleLabel.getText();
            if (example != null && !example.isBlank()) {
                binding.input.setText(example);
            }
        }
        fillDatePicker.setValue(LocalDate.now());
        replyLetterDatePicker.setValue(LocalDate.now().minusDays(1));
        fillAttachmentExample();
    }

    private void fillAttachmentExample() {
        if (attachmentRows.isEmpty()) {
            addAttachmentRow("", "", "");
        }
        AttachmentRow firstRow = attachmentRows.get(0);
        firstRow.titleField.setText("Учеба");
        firstRow.pagesField.setText("2");
        firstRow.bodyArea.setText("Сессия близка...");
    }

    private static class ExampleBinding {
        private final TextInputControl input;
        private final Label exampleLabel;

        private ExampleBinding(TextInputControl input, Label exampleLabel) {
            this.input = input;
            this.exampleLabel = exampleLabel;
        }
    }

    private void generateDocument(Stage stage) {
        if (!validateRequiredFields()) {
            return;
        }
        if (!TEMPLATE_PATH.toFile().exists()) {
            showError("Не найден шаблон DOCX: " + TEMPLATE_PATH);
            return;
        }

        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Сохранить итоговый документ");
        saveChooser.setInitialFileName("Письмо.docx");
        saveChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DOCX", "*.docx"));

        File saveFile = saveChooser.showSaveDialog(stage);
        if (saveFile == null) {
            return;
        }

        LetterData data = new LetterData(
                companyNameField.getText(),
                DEFAULT_LOGO_PATH,
                phoneField.getText(),
                faxField.getText(),
                emailField.getText(),
                okpoField.getText(),
                ogrnField.getText(),
                innField.getText(),
                kppField.getText(),
                fillDatePicker.getValue(),
                recipientField.getText(),
                ourLetterNumberField.getText(),
                replyLetterNumberField.getText(),
                replyLetterDatePicker.getValue(),
                letterTitleField.getText(),
                letterBodyArea.getText(),
                writerNameField.getText(),
                writerPositionField.getText(),
                signatureField.getText(),
                buildAttachments()
        );

        Path outputPath = ensureDocxExtension(saveFile.toPath());

        try {
            templateService.generateFromTemplate(TEMPLATE_PATH, outputPath, data);
            showInfo("Документ успешно создан:\n" + outputPath);
        } catch (Exception e) {
            showError("Ошибка при генерации документа:\n" + e);
        }
    }

    private Path ensureDocxExtension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".docx")) {
            return path;
        }

        Path parent = path.getParent();
        String fixedName = path.getFileName() + ".docx";
        return parent == null ? Path.of(fixedName) : parent.resolve(fixedName);
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText("Готово");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Ошибка");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean validateRequiredFields() {
        List<Node> invalid = new ArrayList<>();

        for (Node node : requiredNodes()) {
            if (node instanceof TextInputControl textInput) {
                if (textInput.getText() == null || textInput.getText().isBlank()) {
                    invalid.add(node);
                }
            } else if (node instanceof DatePicker datePicker) {
                if (datePicker.getValue() == null) {
                    invalid.add(node);
                }
            }
        }

        clearValidationStyles();
        for (Node node : invalid) {
            if (!node.getStyleClass().contains(ERROR_CLASS)) {
                node.getStyleClass().add(ERROR_CLASS);
            }
        }

        if (!invalid.isEmpty()) {
            invalid.get(0).requestFocus();
            showError("Введены не все необходимые данные.");
            return false;
        }
        return true;
    }

    private List<Node> requiredNodes() {
        return List.of(
                companyNameField,
                phoneField,
                faxField,
                emailField,
                okpoField,
                ogrnField,
                innField,
                kppField,
                fillDatePicker,
                recipientField,
                ourLetterNumberField,
                replyLetterNumberField,
                replyLetterDatePicker,
                letterTitleField,
                letterBodyArea,
                writerNameField,
                writerPositionField,
                signatureField
        );
    }

    private void clearValidationStyles() {
        for (Node node : requiredNodes()) {
            node.getStyleClass().remove(ERROR_CLASS);
        }
    }

    private void registerValidationListeners() {
        for (Node node : requiredNodes()) {
            if (node instanceof TextInputControl textInput) {
                textInput.textProperty().addListener((obs, oldValue, newValue) -> {
                    if (newValue != null && !newValue.isBlank()) {
                        node.getStyleClass().remove(ERROR_CLASS);
                    }
                });
            } else if (node instanceof DatePicker datePicker) {
                datePicker.valueProperty().addListener((obs, oldValue, newValue) -> {
                    if (newValue != null) {
                        node.getStyleClass().remove(ERROR_CLASS);
                    }
                });
            }
        }
    }

    private Node createAttachmentsSection() {
        Button addAttachmentButton = new Button("Добавить приложение");
        addAttachmentButton.setOnAction(event -> addAttachmentRow("", "", ""));

        VBox container = new VBox(8, attachmentsBox, addAttachmentButton);
        if (attachmentRows.isEmpty()) {
            addAttachmentRow("", "", "");
        }
        return container;
    }

    private void addAttachmentRow(String title, String body, String pages) {
        AttachmentRow row = new AttachmentRow(title, body, pages);
        attachmentRows.add(row);
        attachmentsBox.getChildren().add(row.container);
    }

    private List<LetterAttachment> buildAttachments() {
        List<LetterAttachment> attachments = new ArrayList<>();
        for (AttachmentRow row : attachmentRows) {
            String title = safeTrim(row.titleField.getText());
            String body = safeTrim(row.bodyArea.getText());
            String pages = safeTrim(row.pagesField.getText());
            if (title.isBlank() && body.isBlank()) {
                continue;
            }
            attachments.add(new LetterAttachment(title, body, pages));
        }
        return attachments;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private class AttachmentRow {
        private final VBox container = new VBox(6);
        private final TextField titleField = new TextField();
        private final TextField pagesField = new TextField();
        private final TextArea bodyArea = new TextArea();

        private AttachmentRow(String title, String body, String pages) {
            titleField.setText(title);
            pagesField.setText(pages);
            bodyArea.setText(body);
            bodyArea.setPrefRowCount(4);
            bodyArea.setWrapText(true);

            Label titleExample = createExampleLabel("Учеба");
            Label pagesExample = createExampleLabel("2");
            Label bodyExample = createExampleLabel("Сессия близка...");

            VBox titleBox = new VBox(2, new Label("Заголовок"), titleField, titleExample);
            VBox pagesBox = new VBox(2, new Label("Листов"), pagesField, pagesExample);
            HBox header = new HBox(12, titleBox, pagesBox);

            Button removeButton = new Button("Удалить");
            removeButton.setOnAction(event -> {
                attachmentRows.remove(this);
                attachmentsBox.getChildren().remove(container);
            });

            container.getChildren().addAll(
                    header,
                    new Label("Текст приложения"),
                    bodyArea,
                    bodyExample,
                    removeButton,
                    new Separator()
            );
        }
    }
}

