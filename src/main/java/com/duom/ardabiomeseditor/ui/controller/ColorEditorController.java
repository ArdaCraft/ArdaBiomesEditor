package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.services.I18nService;
import com.duom.ardabiomeseditor.ui.views.BiomeTableView;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

/**
 * Controller for managing the color editor functionality in the application.
 * Handles user interactions with the color picker, HSL sliders, and table selections.
 */
public class ColorEditorController {

    @FXML private ColorPicker colorPicker;
    @FXML private Slider hueSlider;
    @FXML private Slider saturationSlider;
    @FXML private Slider lightnessSlider;
    @FXML private Label hueLabel;
    @FXML private Label saturationLabel;
    @FXML private Label lightnessLabel;
    @FXML private VBox cellColorSettings;
    @FXML private Label cellColorSettingsHeader;
    @FXML private Label cellColorSettingsSubtitle;
    @FXML private VBox columnHsvSettings;
    @FXML private Label columnHsvSettingsHeader;
    @FXML private Label columnHsvSettingsSubtitle;

    private BiomeTableView biomeTableView;
    private Consumer<Runnable> saveCallback;

    /**
     * Initializes the controller. Sets up bindings and listeners for UI components.
     */
    @FXML
    public void initialize() {

        cellColorSettings.managedProperty().bind(cellColorSettings.visibleProperty());
        columnHsvSettings.managedProperty().bind(columnHsvSettings.visibleProperty());

        hueSlider.valueProperty().addListener((obs, old, val) -> {
            hueLabel.setText(String.format("%.0f°", val.doubleValue()));
            applyLiveHSLAdjustments();
        });

        saturationSlider.valueProperty().addListener((obs, old, val) -> {
            saturationLabel.setText(String.format("%.0f%%", val.doubleValue()));
            applyLiveHSLAdjustments();
        });

        lightnessSlider.valueProperty().addListener((obs, old, val) -> {
            lightnessLabel.setText(String.format("%.0f%%", val.doubleValue()));
            applyLiveHSLAdjustments();
        });
    }

    /**
     * Sets the BiomeTableView instance for this controller.
     *
     * @param biomeTableView The BiomeTableView instance.
     */
    public void setBiomeTableView(BiomeTableView biomeTableView) {
        this.biomeTableView = biomeTableView;
    }

    /**
     * Resets the HSL sliders to their default values.
     */
    @FXML
    public void onResetHSL() {
        hueSlider.setValue(0);
        saturationSlider.setValue(0);
        lightnessSlider.setValue(0);
    }

    /**
     * Handles changes in the ColorPicker. Updates the selected cell's color.
     */
    @FXML
    public void onColorPickerChange() {
        Color newColor = colorPicker.getValue();
        var selectedCells = biomeTableView.getBiomeTableViewSelectionModel().getSelectedCells();

        if (!selectedCells.isEmpty() && newColor != null) {
            var cellPosition = selectedCells.getFirst();
            ColorData colorData = biomeTableView.getCellValue(cellPosition.getRow(), cellPosition.getColumn());

            String hexColor = String.format("#%02X%02X%02X",
                    (int)(newColor.getRed() * 255),
                    (int)(newColor.getGreen() * 255),
                    (int)(newColor.getBlue() * 255));

            colorData.setCurrentColor(hexColor);
            biomeTableView.refresh();
        }
    }

    /**
     * Handles table click events. Updates the UI based on the selected cell or column.
     *
     * @param event The BiomeTableClickEvent triggered by the table click.
     */
    public void handleTableClick(BiomeTableView.BiomeTableClickEvent event) {
        onResetHSL();

        if (BiomeTableView.BiomeTableClickEventType.CELL == event.type()) {
            var selectedCells = biomeTableView.getBiomeTableViewSelectionModel().getSelectedCells();

            if (selectedCells.size() == 1) {
                var cellPosition = selectedCells.getFirst();
                cellColorSettingsHeader.setText(I18nService.get("ardabiomeseditor.biometableview.single.cell.select.label"));
                ColorData color = biomeTableView.getCellValue(cellPosition.getRow(), cellPosition.getColumn());
                colorPicker.setValue(Color.web(color.getCurrentColor()));

                cellColorSettingsSubtitle.setText(event.eventData());
                cellColorSettings.setVisible(true);
                cellColorSettingsHeader.setVisible(true);
                columnHsvSettings.setVisible(false);
                columnHsvSettingsHeader.setVisible(false);
            }
        } else if (BiomeTableView.BiomeTableClickEventType.HEADER == event.type()) {
            var selectedColumns = biomeTableView.getBiomeTableViewSelectionModel().getSelectedColumns();

            columnHsvSettingsHeader.setText(I18nService.get("ardabiomeseditor.biometableview.column.adjustments.header"));
            columnHsvSettingsSubtitle.setText(event.eventData());

            columnHsvSettings.setVisible(!selectedColumns.isEmpty());
            columnHsvSettingsHeader.setVisible(!selectedColumns.isEmpty());
            cellColorSettings.setVisible(false);
            cellColorSettingsHeader.setVisible(false);
        } else {
            columnHsvSettings.setVisible(false);
            columnHsvSettingsHeader.setVisible(false);
            cellColorSettings.setVisible(false);
            cellColorSettingsHeader.setVisible(false);
        }

        biomeTableView.refresh();
    }

    /**
     * Applies live HSL adjustments to the selected columns in the table.
     */
    private void applyLiveHSLAdjustments() {
        double hueShift = hueSlider.getValue();
        double satShift = saturationSlider.getValue() / 100.0;
        double lightShift = lightnessSlider.getValue() / 100.0;

        var selectedColumns = biomeTableView.getBiomeTableViewSelectionModel().getSelectedColumns();

        selectedColumns.forEach(pos -> biomeTableView.getItems().get(pos.getRow()).get(pos.getColumn()).adjustHSL(hueShift,satShift,lightShift));

        biomeTableView.refresh();
    }

    /**
     * Clears the current selection in the table.
     */
    @FXML
    private void onClearSelection() {
        biomeTableView.getSelectionModel().clearSelection();
        biomeTableView.refresh();
    }

    /**
     * Saves the color edits by invoking the save callback.
     */
    @FXML
    private void onSaveColorEdits() {
        saveCallback.accept(null);
    }

    /**
     * Sets the callback to be executed when saving changes.
     *
     * @param callback The save callback function.
     */
    public void setSaveCallback(Consumer<Runnable> callback) {
        this.saveCallback = callback;
    }
}