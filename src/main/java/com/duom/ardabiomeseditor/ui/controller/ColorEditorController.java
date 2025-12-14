package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.services.GuiResourceService;
import com.duom.ardabiomeseditor.services.I18nService;
import com.duom.ardabiomeseditor.ui.views.BiomeTableView;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

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
    @FXML private Button saveButton;

    private BiomeTableView biomeTableView;
    private Consumer<Runnable> saveCallback;
    private ChangeListener<Number> hueListener;
    private ChangeListener<Number> saturationListener;
    private ChangeListener<Number> lightnessListener;
    private PauseTransition hslDebouncer;

    /**
     * Initializes the controller. Sets up bindings and listeners for UI components.
     */
    @FXML
    public void initialize() {

        cellColorSettings.managedProperty().bind(cellColorSettings.visibleProperty());
        columnHsvSettings.managedProperty().bind(columnHsvSettings.visibleProperty());

        hslDebouncer = new PauseTransition(Duration.millis(500));
        hslDebouncer.setOnFinished(event -> applyLiveHSLAdjustments());

        hueListener = (obs, old, val) -> {
            hueLabel.setText(String.format("%.0f°", val.doubleValue()));
            applyLiveHSLAdjustmentsImmediate();
            hslDebouncer.playFromStart();
        };

        saturationListener = (obs, old, val) -> {
            saturationLabel.setText(String.format("%.0f%%", val.doubleValue()));
            applyLiveHSLAdjustmentsImmediate();
            hslDebouncer.playFromStart();
        };

        lightnessListener = (obs, old, val) -> {
            lightnessLabel.setText(String.format("%.0f%%", val.doubleValue()));
            applyLiveHSLAdjustmentsImmediate();
            hslDebouncer.playFromStart();
        };

        saveButton.setGraphic(GuiResourceService.getIcon(GuiResourceService.IconType.SAVE));

        initHslSliderListeners();
    }

    private void initHslSliderListeners() {

        hueSlider.valueProperty().addListener(hueListener);
        saturationSlider.valueProperty().addListener(saturationListener);
        lightnessSlider.valueProperty().addListener(lightnessListener);
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
        hueLabel.setText("0");
        saturationLabel.setText("0");
        lightnessLabel.setText("0");
    }

    public void silentResetHsl(){

        hueSlider.valueProperty().removeListener(hueListener);
        saturationSlider.valueProperty().removeListener(saturationListener);
        lightnessSlider.valueProperty().removeListener(lightnessListener);

        onResetHSL();

        initHslSliderListeners();
    }

    public void resetAndHideUi() {
        onResetHSL();
        hideUi();
    }

    /**
     * Handles changes in the ColorPicker. Updates the selected cell's color.
     */
    @FXML
    public void onColorPickerChange() {
        Color newColor = colorPicker.getValue();
        var selectedCells = biomeTableView.getSelectedCells();

        if (!selectedCells.isEmpty() && newColor != null) {
            var cell = selectedCells.getFirst();

            String hexColor = String.format("#%02X%02X%02X",
                    (int)(newColor.getRed() * 255),
                    (int)(newColor.getGreen() * 255),
                    (int)(newColor.getBlue() * 255));

            cell.setCurrentColor(hexColor);
            biomeTableView.refresh();
        }
    }

    /**
     * Handles table click events. Updates the UI based on the selected cell or column.
     *
     * @param event The BiomeTableClickEvent triggered by the table click.
     */
    public void handleTableClick(BiomeTableView.BiomeTableClickEvent event) {

        silentResetHsl();

        if (BiomeTableView.BiomeTableClickEventType.CELL == event.type()) {
            var selectedCells = biomeTableView.getSelectedCells();

            if (selectedCells.size() == 1) {
                var cell = selectedCells.getFirst();
                cellColorSettingsHeader.setText(I18nService.get("ardabiomeseditor.biometableview.single.cell.select.label"));
                colorPicker.setValue(Color.web(cell.getCurrentColor()));

                cellColorSettingsSubtitle.setText(event.eventData());
                cellColorSettings.setVisible(true);
                cellColorSettingsHeader.setVisible(true);
                columnHsvSettings.setVisible(false);
                columnHsvSettingsHeader.setVisible(false);
            }
        } else if (BiomeTableView.BiomeTableClickEventType.HEADER == event.type()) {
            var selectedColumns = biomeTableView.getSelectedColumns();

            columnHsvSettingsHeader.setText(I18nService.get("ardabiomeseditor.biometableview.column.adjustments.header"));
            columnHsvSettingsSubtitle.setText(event.eventData());

            columnHsvSettings.setVisible(!selectedColumns.isEmpty());
            columnHsvSettingsHeader.setVisible(!selectedColumns.isEmpty());
            cellColorSettings.setVisible(false);
            cellColorSettingsHeader.setVisible(false);
        } else {
            hideUi();
        }

        biomeTableView.refresh();
    }

    /**
     * Hides the color and HSL adjustment UI components.
     */
    private void hideUi() {
        columnHsvSettings.setVisible(false);
        columnHsvSettingsHeader.setVisible(false);
        cellColorSettings.setVisible(false);
        cellColorSettingsHeader.setVisible(false);
    }

    /**
     * Immediately applies HSL adjustments without triggering a full table refresh.
     * This updates the color data instantly while allowing the debouncer to handle the visual refresh.
     */
    private void applyLiveHSLAdjustmentsImmediate() {
        double hueShift = hueSlider.getValue();
        double satShift = saturationSlider.getValue() / 100.0;
        double lightShift = lightnessSlider.getValue() / 100.0;

        var selectedCells = biomeTableView.getSelectedCells();
        selectedCells.forEach(cell -> cell.adjustHSL(hueShift, satShift, lightShift));
    }

    /**
     * Applies live HSL adjustments to the selected columns in the table and refresh.
     */
    private void applyLiveHSLAdjustments() {

        applyLiveHSLAdjustmentsImmediate();
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