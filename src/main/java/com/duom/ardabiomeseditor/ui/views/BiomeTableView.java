package com.duom.ardabiomeseditor.ui.views;

import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.services.I18nService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.control.Label;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Custom TableView implementation for displaying and managing biome color data.
 * This class provides advanced features such as column selection, custom cell rendering,
 * and handling unsaved changes.
 */
public class BiomeTableView extends TableView<ObservableList<ColorData>> {

    public static final String HIGHLIGHT_COLOR = "#fcb620;";
    public static final String HIGHLIGHT_TEXT_COLOR = "#161b22;";
    public static final int CELL_WIDTH = 40;

    // The key representing the current biome being displayed
    private int biomeKey;

    private BiConsumer<Integer, BiomeTableClickEvent> clickHandler;

    private final BiomeTableViewSelectionModel<ObservableList<ColorData>> biomeTableViewSelectionModel;

    /**
     * Record representing a click event in the biome table.
     *
     * @param type      The type of the click event (e.g., HEADER, CELL).
     * @param eventData Additional data related to the event.
     */
    public record BiomeTableClickEvent(BiomeTableClickEventType type, String eventData) {}

    /**
     * Enum representing the types of click events in the biome table.
     */
    public enum BiomeTableClickEventType {
        HEADER,
        CELL
    }

    /**
     * Constructor for the BiomeTableView.
     * Initializes the table, sets up the selection model, and loads the CSS stylesheet.
     */
    public BiomeTableView(){

        super();

        biomeTableViewSelectionModel =new BiomeTableViewSelectionModel<>(this);
        setSelectionModel(biomeTableViewSelectionModel);
        loadCss();

        Label placeholder = new Label(I18nService.get("ardabiomeseditor.biometableview.no_data"));
        placeholder.getStyleClass().add("text-muted");
        setPlaceholder(placeholder);
    }

    /**
     * Loads the CSS stylesheet for the biome table.
     */
    private void loadCss(){

        var cssResource = getClass().getResource("/css/biomes-table-view.css");

        if (cssResource != null)
            getStylesheets().add(cssResource.toExternalForm());
    }

    /**
     * Refreshes the table and updates all column headers.
     */
    @Override
    public void refresh() {
        updateAllHeaders();
        super.refresh();
    }

    /**
     * Configures the table with the given biome key and color mappings.
     *
     * @param biomeKey      The key representing the biome.
     * @param colorMappings A map of modifier names to their corresponding color lists.
     */
    public void configure(int biomeKey, Map<String, List<String>> colorMappings) {

        this.biomeKey = biomeKey;
        this.biomeTableViewSelectionModel.clearSelection();
        List<String> modifierNames = colorMappings.keySet().stream().sorted().toList();

        getColumns().clear();
        getItems().clear();

        createColumns(modifierNames);
        populateRows(colorMappings, modifierNames);
    }

    /**
     * Creates columns for the table based on the given headers.
     *
     * @param headers A list of column headers.
     */
    private void createColumns(List<String> headers) {

        getColumns().add(createIndexColumn());

        setFixedCellSize(-1);

        for (int idx = 0; idx < headers.size(); idx++) {

            getColumns().add(createColumn(headers.get(idx), idx));
        }
    }

    /**
     * Creates the index column for the table.
     *
     * @return The index column.
     */
    private TableColumn<ObservableList<ColorData>, String> createIndexColumn() {

        TableColumn<ObservableList<ColorData>, String> indexColumn = new TableColumn<>("#");
        indexColumn.setPrefWidth(CELL_WIDTH);
        indexColumn.setMinWidth(CELL_WIDTH);
        indexColumn.setSortable(false);

        indexColumn.setCellValueFactory(param ->
                new SimpleStringProperty(String.valueOf(getItems().indexOf(param.getValue())))
        );

        indexColumn.setStyle("-fx-alignment: CENTER;");
        return indexColumn;
    }

    /**
     * Creates a column for the table with the given header and index.
     *
     * @param header      The header text for the column.
     * @param columnIndex The index of the column.
     * @return The created column.
     */
    private TableColumn<ObservableList<ColorData>, String> createColumn(String header, int columnIndex) {

        TableColumn<ObservableList<ColorData>, String> column = new TableColumn<>();
        column.setReorderable(false);

        column.setUserData(header);

        Text headerText = new Text(formatVerticalHeaderString(header));
        headerText.setTextAlignment(TextAlignment.CENTER);

        StackPane headerGroup = new StackPane(headerText);
        updateHeaderStyle(headerGroup, columnIndex);

        headerGroup.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.isShiftDown()) {

                 if (biomeTableViewSelectionModel.isColumnSelected(columnIndex)) {

                    biomeTableViewSelectionModel.deselectColumn(columnIndex);
                    updateAllHeaders();

                    if (clickHandler != null)
                        clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, ""));
                } else {

                     selectColumn(columnIndex);
                     updateAllHeaders();
                 }
            } else {
                // Normal selection
                biomeTableViewSelectionModel.clearSelection();
                selectColumn(columnIndex);
            }
            updateAllHeaders();
        });
        column.setGraphic(headerGroup);

        column.setPrefWidth(CELL_WIDTH);
        column.setMinWidth(CELL_WIDTH);
        column.setSortable(false);

        column.setCellValueFactory(param -> param.getValue().get(columnIndex).currentColorProperty());
        column.setCellFactory(col -> createColorCell(columnIndex));
        return column;
    }

    /**
     * Formats header string by replacing underscores with spaces, capitalizing each word,
     * and inserting line breaks between characters. Allowing for vertical text display.
     * @param header the header string to format
     * @return the formatted header
     */
    private String formatVerticalHeaderString(String header) {

        return formatHeaderString(header).replaceAll("(.)", "$1\n");
    }

    /**
     * Formats a header string by replacing underscores with spaces and capitalizing each word.
     *
     * @param header The header string to format.
     * @return The formatted header string.
     */
    private String formatHeaderString(String header) {

        if (header == null) return "";

        String[] words = header.replaceAll("_", " ").split(" ");
        return Arrays.stream(words)
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    /**
     * Updates the style of a column header based on its selection state.
     *
     * @param headerContainer The container for the header.
     * @param columnIndex     The index of the column.
     */
    private void updateHeaderStyle(StackPane headerContainer, int columnIndex) {

        var parentNode = headerContainer.getParent();

        if (parentNode != null) {
            if (biomeTableViewSelectionModel.isColumnSelected(columnIndex)) {
                parentNode.setStyle("-fx-cursor: hand; -fx-background-color: " + HIGHLIGHT_COLOR + ";");

                if (!headerContainer.getChildren().isEmpty() && headerContainer.getChildren().getFirst() instanceof Text text) {
                    text.setStyle("-fx-fill: " + HIGHLIGHT_TEXT_COLOR + ";");
                }

            } else {
                parentNode.setStyle("-fx-cursor: hand;");

                if (!headerContainer.getChildren().isEmpty() && headerContainer.getChildren().getFirst() instanceof Text text) {
                    text.setStyle("");
                }
            }
        }
    }

    /**
     * Updates the styles of all column headers in the table.
     */
    private void updateAllHeaders() {
        for (int i = 1; i < getColumns().size(); i++) { // Start at 1 to skip index column

            TableColumn<ObservableList<ColorData>, ?> column = getColumns().get(i);

            if (column.getGraphic() instanceof StackPane headerContainer) {
                updateHeaderStyle(headerContainer, i - 1);
            }
        }
    }

    /**
     * Creates a custom TableCell for displaying color data.
     *
     * @param columnIndex The index of the column for which the cell is created.
     * @return The created TableCell.
     */
    private TableCell<ObservableList<ColorData>, String> createColorCell(int columnIndex) {

        TableCell<ObservableList<ColorData>, String> cell = new TableCell<>() {
            private final Button colorBox = new Button();
            {
                colorBox.setPrefSize(CELL_WIDTH - 5, CELL_WIDTH - 5);
                colorBox.setOnAction(e -> {

                    String value = getItem();

                    if (value != null && clickHandler != null) {

                        if (biomeTableViewSelectionModel.isSelected(getIndex(), getColumns().get(columnIndex))) {
                            biomeTableViewSelectionModel.clearSelection();

                        } else {

                            biomeTableViewSelectionModel.selectCell(getIndex(), columnIndex);

                            var formatedHeader = formatHeaderString((String) getColumns().get(columnIndex + 1).getUserData());
                            clickHandler.accept(columnIndex, new BiomeTableClickEvent(BiomeTableClickEventType.CELL, formatedHeader));
                        }

                        updateAllHeaders();
                        refresh();
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {

                    setGraphic(null);
                    setStyle("");

                } else {

                    colorBox.setStyle("-fx-background-color: " + item + ";");
                    setGraphic(colorBox);

                    var rowIndex = getIndex();

                    if (biomeTableViewSelectionModel.isSelected(rowIndex, getColumns().get(columnIndex))) {

                        setStyle("-fx-background-color: " + HIGHLIGHT_COLOR + "; -fx-text-fill: " + HIGHLIGHT_TEXT_COLOR + ";");
                    } else {
                        setStyle("");
                    }
                }
            }
        };

        cell.setPadding(Insets.EMPTY);
        cell.setAlignment(Pos.CENTER);
        return cell;
    }

    /**
     * Populates the table rows with color data based on the provided color mappings.
     *
     * @param colorMappings A map of modifier names to their corresponding color lists.
     * @param modifierNames A list of modifier names to determine column order.
     */
    private void populateRows(Map<String, List<String>> colorMappings, List<String> modifierNames) {

        int maxRows = colorMappings.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        for (int rowIndex = 0; rowIndex < maxRows; rowIndex++) {
            ObservableList<ColorData> row = FXCollections.observableArrayList();

            for (String header : modifierNames) {
                List<String> colors = colorMappings.get(header);
                String color = (rowIndex < colors.size()) ? colors.get(rowIndex) : "";
                row.add(new ColorData(color));
            }

            getItems().add(row);
        }
    }

    /**
     * Selects a column in the table and triggers the click handler.
     *
     * @param columnIndex The index of the column to select.
     */
    private void selectColumn(int columnIndex) {

        biomeTableViewSelectionModel.selectColumn(columnIndex);

        refresh();

        String formattedHeaders = biomeTableViewSelectionModel.getSelectedColumnIndices().stream()
                .map(colIdx -> formatHeaderString((String) getColumns().get(colIdx + 1).getUserData()))
                .collect(Collectors.joining(", "));

        if (clickHandler != null) clickHandler.accept(columnIndex, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, formattedHeaders));
    }

    /**
     * Sets the click handler for the biome table.
     *
     * @param handler A BiConsumer that handles click events, receiving the column index and event details.
     */
    public void setClickHandler(BiConsumer<Integer, BiomeTableClickEvent> handler) {
        this.clickHandler = handler;
    }

    /**
     * Checks if there are any unsaved changes in the table.
     *
     * @return True if there are unsaved changes, false otherwise.
     */
    public boolean hasUnsavedChanges() {

        for (int idx = 0; idx < getItems().size(); idx++) {

            var rowData = getItems().get(idx);

            for (int columnIndex = 0 ; columnIndex < rowData.size() - 1; columnIndex++) {

                if (rowData.get(columnIndex).isModified()) return true;
            }
        }

        return false;
    }

    /**
     * Retrieves the custom selection model for the biome table view.
     *
     * @return The BiomeTableViewSelectionModel instance.
     */
    public BiomeTableViewSelectionModel<ObservableList<ColorData>> getBiomeTableViewSelectionModel() {
        return biomeTableViewSelectionModel;
    }

    /**
     * Persists all color changes made in the currently displayed biome table.
     * This method updates the original color values to match the current colors.
     */
    public void persistColorChanges(){

        for (int idx = 0; idx < getItems().size(); idx++) {

            var rowData = getItems().get(idx);

            for (int columnIndex = 0 ; columnIndex < rowData.size() - 1; columnIndex++) {

                rowData.set(columnIndex, new ColorData(rowData.get(columnIndex).getCurrentColor()));
            }
        }
    }

    /**
     * Retrieve all the color changes made in the currently displayed biome table.
     * @return a map of modified modifier names to their corresponding color list
     */
    public Map<String, List<ColorData>> getAllColorChanges(){

        Map<String, List<ColorData>> changes = new HashMap<>();

        if (hasUnsavedChanges()) {

            // Iterate through columns (skip index column at position 0)
            for (int columnIndex = 1; columnIndex < getColumns().size() - 1; columnIndex++) {

                // Get modifier name
                String columnName = (String) getColumns().get(columnIndex).getUserData();

                // Collect all modified ColorData in this column
                // Adjust index for items list (which does not include index column)
                int finalColumnIndex = columnIndex - 1;
                List<ColorData> modifiedColors = getItems().stream()
                        .map(row -> row.get(finalColumnIndex))
                        .filter(ColorData::isModified)
                        .toList();

                if (!modifiedColors.isEmpty())
                    changes.put(columnName, modifiedColors);

            }
        }

        return changes;
    }

    /**
     * Retrieves the ColorData at the specified row and column indices.
     *
     * @param rowIndex    The row index of the cell.
     * @param columnIndex The column index of the cell.
     * @return The ColorData at the specified cell, or null if indices are out of bounds.
     */
    public ColorData getCellValue(int rowIndex, int columnIndex) {

        if (rowIndex >= 0 && rowIndex < getItems().size()) {

            ObservableList<ColorData> row = getItems().get(rowIndex);

            if (columnIndex >= 0 && columnIndex < row.size()) {

                return row.get(columnIndex);
            }
        }
        return null;
    }

    /**
     * Resets all changes made in the table to their original values.
     */
    public void resetChanges() {

        for (int idx = 0; idx < getItems().size(); idx++) {

            var rowData = getItems().get(idx);

            for (int columnIndex = 0 ; columnIndex < rowData.size() - 1; columnIndex++) {

                rowData.get(columnIndex).reset();
            }
        }
    }

    /**
     * Retrieves the biome key associated with this table.
     *
     * @return The biome key.
     */
    public int getBiomeKey() {
        return biomeKey;
    }
}