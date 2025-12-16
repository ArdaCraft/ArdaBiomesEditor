package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.services.ColorEditorService;
import com.duom.ardabiomeseditor.services.IconResourceService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.converter.NumberStringConverter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for managing the color editor functionality in the application.
 * Handles user interactions with the color picker, HSL sliders, and table selections.
 */
public class ColorEditorController {

    /* UI Elements */

    @FXML private Slider hueSlider;
    @FXML private Slider saturationSlider;
    @FXML private Slider brightnessSlider;
    @FXML private Slider opacitySlider;
    @FXML private TextField hueField;
    @FXML private TextField saturationField;
    @FXML private TextField brightnessField;
    @FXML private TextField opacityField;
    @FXML private ListView<ColumnItem> columnsList;
    @FXML private TitledPane hsbPane;

    /* Listeners */

    private ChangeListener<Number> hueListener;
    private ChangeListener<Number> saturationListener;
    private ChangeListener<Number> brightnessListener;
    private ChangeListener<Number> opacityListener;

    /* Callbacks */

    private Consumer<ColorEditorService.HSB> hsbAdjustmentConsumer;
    private Consumer<Double> opacityAdjustmentConsumer;

    /**
     * Initializes the controller. Sets up bindings and listeners for UI components.
     */
    @FXML
    public void initialize() {

        hueListener = (obs, old, val) -> {
            hueField.setText(String.format("%.0f°", val.doubleValue()));
            propagateHslAdjustments();
        };
        bindHsbTextFormatter(hueField, hueSlider);

        saturationListener = (obs, old, val) -> {
            saturationField.setText(String.format("%.0f%%", val.doubleValue()));
            propagateHslAdjustments();
        };
        bindHsbTextFormatter(saturationField, saturationSlider);

        brightnessListener = (obs, old, val) -> {
            brightnessField.setText(String.format("%.0f%%", val.doubleValue()));
            propagateHslAdjustments();
        };
        bindHsbTextFormatter(brightnessField, brightnessSlider);

        opacityListener = (obs, old, val) -> {
            opacityField.setText(String.format("%.0f%%", val.doubleValue()));
            propagateOpacityAdjustment();
        };
        bindHsbTextFormatter(opacityField, opacitySlider);

        initHsbSliderListeners();
        initColumnsList();
        toggleHsbEditorVisibility(false);
    }

    /**
     * Propagates the current HSL adjustments to the registered consumer.
     */
    private void propagateHslAdjustments() {

        double hueShift = hueSlider.getValue();
        double satShift = saturationSlider.getValue() / 100.0;
        double lightShift = brightnessSlider.getValue() / 100.0;

        this.hsbAdjustmentConsumer.accept(new ColorEditorService.HSB(hueShift, satShift, lightShift));
    }

    /**
     * Binds a TextField to a Slider using a TextFormatter for HSB values.
     *
     * @param textField The TextField to bind.
     * @param slider    The Slider to bind.
     */
    private void bindHsbTextFormatter(TextField textField, Slider slider) {
        TextFormatter<Number> formatter = new TextFormatter<Number>(
                new NumberStringConverter(), 0d,
                change -> {
                    String newText = change.getControlNewText();
                    // Allow empty (user deleting)
                    if (newText.isEmpty())
                        return change;

                    // Allow digits with optional %
                    if (newText.matches("[-+]?\\d{0,3}[°%]?"))
                        return change;

                    return null;
                }
        );
        slider.valueProperty().bindBidirectional(formatter.valueProperty());

        textField.setTextFormatter(formatter);
    }

    private void propagateOpacityAdjustment() {

        double opacityShift = opacitySlider.getValue() / 100.0;

        this.opacityAdjustmentConsumer.accept(opacityShift);
    }

    /**
     * Initializes listeners for the HSL sliders.
     */
    private void initHsbSliderListeners() {

        hueSlider.valueProperty().addListener(hueListener);
        saturationSlider.valueProperty().addListener(saturationListener);
        brightnessSlider.valueProperty().addListener(brightnessListener);
        opacitySlider.valueProperty().addListener(opacityListener);

        addSliderResetListener(hueSlider);
        addSliderResetListener(saturationSlider);
        addSliderResetListener(brightnessSlider);
        addSliderResetListener(opacitySlider);
    }

    /**
     * Initializes the columns list with custom cell factory and context menu.
     */
    private void initColumnsList() {

        columnsList.setCellFactory(lv -> new ColumnCell());
        columnsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        ContextMenu contextMenu = new ContextMenu();
        MenuItem selectionToggle = new MenuItem("Toggle Selection");
        MenuItem visibilityToggle = new MenuItem("Toggle Visibility");
        MenuItem selectAll = new MenuItem("Select All");
        MenuItem deselectAll = new MenuItem("Deselect All");
        MenuItem showAll = new MenuItem("Show All");
        MenuItem hideAll = new MenuItem("Hide All");

        selectionToggle.setOnAction(e -> {
            ObservableList<ColumnItem> selected = columnsList.getSelectionModel().getSelectedItems();
            selected.forEach(item -> item.selected.set(!item.selected.get()));
        });

        visibilityToggle.setOnAction(e -> {
            ObservableList<ColumnItem> selected = columnsList.getSelectionModel().getSelectedItems();
            selected.forEach(item -> item.visible.set(!item.visible.get()));
        });

        selectAll.setOnAction(e -> {
            columnsList.getItems().forEach(item -> item.selected.set(true));
        });

        deselectAll.setOnAction(e -> {
            columnsList.getItems().forEach(item -> item.selected.set(false));
        });

        showAll.setOnAction(e -> {
            columnsList.getItems().forEach(item -> item.visible.set(true));
        });

        hideAll.setOnAction(e -> {
            columnsList.getItems().forEach(item -> item.visible.set(false));
        });

        contextMenu.getItems().addAll(visibilityToggle,
                selectionToggle,
                new SeparatorMenuItem(),
                selectAll,
                deselectAll,
                new SeparatorMenuItem(),
                showAll,
                hideAll);

        columnsList.setContextMenu(contextMenu);
    }

    /**
     * Toggles the visibility of the HSB editor pane.
     *
     * @param isSelected True to show the HSB editor, false to hide it.
     */
    public void toggleHsbEditorVisibility(Boolean isSelected) {

        hsbPane.setExpanded(isSelected);
        hsbPane.setDisable(!isSelected);
    }

    private void addSliderResetListener(Slider slider) {

        // Reset to default on double-click
        Platform.runLater(() -> {
            // Look up the thumb node
            var thumb = slider.lookup(".thumb");

            if (thumb != null) {
                thumb.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                    if (event.getClickCount() == 2) {
                        slider.setValue((Math.abs(slider.getMax()) - Math.abs(slider.getMin())) / 2);
                        event.consume();
                    }
                });
            }
        });
    }

    /**
     * Sets the HSB slider values based on the provided HSL object.
     *
     * @param hsb The HSL values to set on the sliders.
     */
    public void setHsbSliders(ColorEditorService.HSB hsb) {

        hueSlider.valueProperty().removeListener(hueListener);
        saturationSlider.valueProperty().removeListener(saturationListener);
        brightnessSlider.valueProperty().removeListener(brightnessListener);

        hueSlider.setValue(Math.round(hsb.hue()));
        saturationSlider.setValue(Math.round(hsb.saturation() * 100));
        brightnessSlider.setValue(Math.round(hsb.brightness() * 100));

        initHsbSliderListeners();
    }

    public void setOpacitySlider(double opacity) {

        opacitySlider.valueProperty().removeListener(opacityListener);
        opacitySlider.setValue(Math.round(opacity * 100));
        opacitySlider.valueProperty().addListener(opacityListener);
    }

    /**
     * Sets the consumer that will handle HSB adjustments.
     *
     * @param hsbAdjustmentConsumer The consumer to handle HSB adjustments.
     */
    public void setHsbAdjustmentConsumer(Consumer<ColorEditorService.HSB> hsbAdjustmentConsumer) {
        this.hsbAdjustmentConsumer = hsbAdjustmentConsumer;
    }

    /**
     * Sets the consumer that will handle opacity adjustments.
     *
     * @param opacityAdjustmentConsumer The consumer to handle opacity adjustments.
     */
    public void setOpacityAdjustmentConsumer(Consumer<Double> opacityAdjustmentConsumer) {
        this.opacityAdjustmentConsumer = opacityAdjustmentConsumer;
    }

    /**
     * Sets the list of columns to be displayed in the columns list view.
     *
     * @param columns The list of column items to display.
     */
    public void setColumnsList(List<ColumnItem> columns) {
        columnsList.getItems().clear();
        columnsList.getItems().setAll(columns);
        columnsList.refresh();
    }

    /* Data elements */

    /**
     * Data class representing a column item with selection and visibility properties.
     *
     * @param itemName item name
     * @param index    item index
     * @param selected selection property
     * @param visible  visibility property
     */
    public record ColumnItem(String itemName, int index, BooleanProperty selected, BooleanProperty visible) {
    }

    /**
     * List cell for displaying column items with selection and visibility toggles.
     */
    private static class ColumnCell extends ListCell<ColumnItem> {

        private final Label nameLabel = new Label();
        private final ToggleButton selectBtn = new ToggleButton();
        private final ToggleButton visibilityBtn = new ToggleButton();
        private final Region spacer = new Region();
        private final HBox root = new HBox(10, nameLabel, spacer, selectBtn, visibilityBtn);
        private ColumnItem previousItem;

        public ColumnCell() {

            HBox.setHgrow(spacer, Priority.ALWAYS);
            root.setAlignment(Pos.CENTER_LEFT);

            selectBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            selectBtn.graphicProperty().bind(
                    Bindings.when(selectBtn.selectedProperty())
                            .then(IconResourceService.getIcon(IconResourceService.IconType.CHECKBOX_CHECKED))
                            .otherwise(IconResourceService.getIcon(IconResourceService.IconType.CHECKBOX_BLANK)));

            visibilityBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            visibilityBtn.graphicProperty().bind(
                    Bindings.when(visibilityBtn.selectedProperty())
                            .then(IconResourceService.getIcon(IconResourceService.IconType.VISIBILITY_ON))
                            .otherwise(IconResourceService.getIcon(IconResourceService.IconType.VISIBILITY_OFF)));

            // Ensure name label fits with extra padding
            nameLabel.maxWidthProperty().bind(
                    widthProperty().subtract(selectBtn.widthProperty())
                            .subtract(visibilityBtn.widthProperty())
                            .subtract(spacer.widthProperty())
                            .subtract(10));
        }

        @Override
        protected void updateItem(ColumnItem item, boolean empty) {
            super.updateItem(item, empty);


            // Unbind previous item properties - list cells are reused
            if (previousItem != null) {

                selectBtn.selectedProperty().unbindBidirectional(previousItem.selected);
                visibilityBtn.selectedProperty().unbindBidirectional(previousItem.visible);
                root.setOpacity(1d);
            }

            if (empty || item == null) {

                root.setOpacity(1d);
                setGraphic(null);
                previousItem = null;

            } else {

                nameLabel.setText(item.itemName);

                selectBtn.selectedProperty().bindBidirectional(item.selected);
                visibilityBtn.selectedProperty().bindBidirectional(item.visible);
                visibilityBtn.selectedProperty().addListener((observableValue, oldVal, newVal) -> {
                    if (newVal != null && newVal) root.setOpacity(1d);
                    else root.setOpacity(0.5d);
                });
                previousItem = item;
                setGraphic(root);
            }
        }
    }
}