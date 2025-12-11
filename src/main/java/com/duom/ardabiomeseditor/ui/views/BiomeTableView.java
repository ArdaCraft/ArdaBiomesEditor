package com.duom.ardabiomeseditor.ui.views;

import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.services.I18nService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.*;
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
    private List<String> currentColumnOrder = new ArrayList<>();
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

        getColumns().addListener((javafx.collections.ListChangeListener.Change<? extends TableColumn<ObservableList<ColorData>, ?>> change) -> {
            while (change.next())
                reorderRowData();
        });
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

        currentColumnOrder= new ArrayList<>(headers);

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
        column.setReorderable(true);
        column.setUserData(header);

        Text headerText = new Text(formatVerticalHeaderString(header));
        headerText.setTextAlignment(TextAlignment.CENTER);

        StackPane headerGroup = new StackPane(headerText);
        updateHeaderStyle(headerGroup, column);

        headerGroup.setOnMouseClicked(mouseEvent -> {
            String modifierName = (String) column.getUserData();
            handleTableHeaderMouseEvent(modifierName, mouseEvent);
        });
        column.setGraphic(headerGroup);

        column.setPrefWidth(CELL_WIDTH);
        column.setMinWidth(CELL_WIDTH);
        column.setSortable(false);

        column.setCellValueFactory(param -> {
            String modifierName = (String) column.getUserData();

            // Access the entire row data
            ObservableList<ColorData> row = param.getValue();

            // Find the correct data index based on modifier name
            int dataIndex = getColumns().stream()
                    .skip(1) // Skip the index column
                    .map(col -> (String) col.getUserData())
                    .filter(Objects::nonNull)
                    .toList()
                    .indexOf(modifierName);

            if (dataIndex >= 0 && dataIndex < row.size()) {
                return row.get(dataIndex).currentColorProperty();
            }

            return new SimpleStringProperty("");
        });

        column.setCellFactory(col -> createColorCell(column));
        return column;
    }

    private void handleTableHeaderMouseEvent(String modifierName, MouseEvent mouseEvent) {

        if (mouseEvent.isControlDown()) {

            if (biomeTableViewSelectionModel.isColumnSelected(modifierName)) {

                biomeTableViewSelectionModel.deselectColumn(modifierName);
                updateAllHeaders();

                if (clickHandler != null)
                    clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, ""));
            } else {

                selectColumn(modifierName);
                updateAllHeaders();
            }
        } else if (mouseEvent.isShiftDown()) {

            Set<String> selectedColumnNames = biomeTableViewSelectionModel.getSelectedColumns();

            if (!selectedColumnNames.isEmpty()) {

                // Find the anchor column name (first selected column)
                String anchorColumnName = selectedColumnNames.stream()
                        .findFirst()
                        .orElse(null);

                // Get column indices for anchor and clicked column
                int anchorIndex = getColumnIndex(anchorColumnName);
                int clickedIndex = getColumnIndex(modifierName);

                // Select range between anchor and clicked column
                int start = Math.min(anchorIndex, clickedIndex);
                int end = Math.max(anchorIndex, clickedIndex);

                for (int i = start; i <= end; i++) {
                    String columnName = (String) getColumns().get(i + 1).getUserData();
                    if (!biomeTableViewSelectionModel.isColumnSelected(columnName)) {
                        biomeTableViewSelectionModel.selectColumn(columnName);
                    }
                }

                String formattedHeaders = biomeTableViewSelectionModel.getSelectedColumns().stream()
                        .map(this::formatHeaderString)
                        .collect(Collectors.joining(", "));

                if (clickHandler != null)
                    clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, formattedHeaders));
            }

        } else {
            // Normal selection
            biomeTableViewSelectionModel.clearSelection();
            selectColumn(modifierName);
        }
        updateAllHeaders();
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
     * @param column          The column.
     */
    private void updateHeaderStyle(StackPane headerContainer, TableColumn<ObservableList<ColorData>, ?> column) {

        var parentNode = headerContainer.getParent();

        if (parentNode != null) {

            if (biomeTableViewSelectionModel.isColumnSelected((String) column.getUserData())) {
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
        for (int i = 1; i < getColumns().size(); i++) {

            TableColumn<ObservableList<ColorData>, ?> column = getColumns().get(i);

            if (column.getGraphic() instanceof StackPane headerContainer) {
                updateHeaderStyle(headerContainer, column);
            }
        }
    }

    /**
     * Creates a custom TableCell for displaying color data.
     *
     * @param column The column for which the cell is created.
     * @return The created TableCell.
     */
    private TableCell<ObservableList<ColorData>, String> createColorCell(TableColumn<ObservableList<ColorData>, String> column) {

        TableCell<ObservableList<ColorData>, String> cell = new TableCell<>() {
            private final Button colorBox = new Button();
            {
                colorBox.setPrefSize(CELL_WIDTH - 5, CELL_WIDTH - 5);
                colorBox.setOnAction(e -> {

                    String value = getItem();

                    if (value != null && clickHandler != null) {


                        if (biomeTableViewSelectionModel.isSelected(getIndex(), column)) {
                            biomeTableViewSelectionModel.clearSelection();

                        } else {

                            biomeTableViewSelectionModel.selectCell(getIndex(), column);

                            var formatedHeader = formatHeaderString((String) column.getUserData());
                            clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.CELL, formatedHeader));
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

                    if (biomeTableViewSelectionModel.isSelected(getIndex(), column)) {
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
     * @param modifierName The modifier represented by the column.
     */
    private void selectColumn(String modifierName) {

        biomeTableViewSelectionModel.selectColumn(modifierName);

        refresh();

        String formattedHeaders = biomeTableViewSelectionModel.getSelectedColumns().stream()
                .map(this::formatHeaderString)
                .collect(Collectors.joining(", "));

        if (clickHandler != null) clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, formattedHeaders));
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
                int dataColumnIndex = columnIndex - 1;
                List<ColorData> modifiedColors = getItems().stream()
                        .map(row -> row.get(dataColumnIndex))
                        .map(colorData -> colorData.isModified() ? colorData : null)
                        .toList();

                boolean hasAnyModified = modifiedColors.stream().anyMatch(Objects::nonNull);

                if (!modifiedColors.isEmpty() && hasAnyModified)
                    changes.put(columnName, modifiedColors);

            }
        }

        return changes;
    }

    /**
     * Reorders the data in each row to match the current visual column order.
     * This ensures data stays aligned with columns after drag-and-drop reordering.
     */
    private void reorderRowData() {
        // Returns the column order as it is currently displayed
        List<String> newColumnOrder = getColumns().stream()
                .skip(1) // Skip index column
                .map(col -> (String) col.getUserData())
                .filter(Objects::nonNull)
                .toList();

        // If order hasn't changed or we don't have original order, skip
        if (currentColumnOrder.isEmpty() || currentColumnOrder.equals(newColumnOrder)) {
            return;
        }

        // Reorder each row's data to match the new column order
        for (ObservableList<ColorData> row : getItems()) {

            Map<String, ColorData> dataMap = new HashMap<>();

            for (int i = 0; i < row.size() && i < currentColumnOrder.size(); i++) {
                dataMap.put(currentColumnOrder.get(i), row.get(i));
            }

            // Rebuild the row using the new column order
            ObservableList<ColorData> reorderedRow = FXCollections.observableArrayList();
            for (String modifierName : newColumnOrder) {
                ColorData colorData = dataMap.get(modifierName);
                reorderedRow.add(colorData != null ? colorData : new ColorData(""));
            }

            row.setAll(reorderedRow);
        }

        currentColumnOrder = new ArrayList<>(newColumnOrder);
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

    /**
     * @return the list of selected ColorData cells
     */
    public List<ColorData> getSelectedCells(){

        List<ColorData> selectedColors = new ArrayList<>();
        var selectedCells = biomeTableViewSelectionModel.getSelectedCells();

        for (TablePosition position : selectedCells) {

            var rowData = getItems().get(position.getRow());
            var cellData = rowData.get(position.getColumn() - 1 );

            selectedColors.add(cellData);
        }

        return selectedColors;
    }

    /**
     * @return the set of selected column modifier names
     */
    public Set<String> getSelectedColumns(){

        return biomeTableViewSelectionModel.getSelectedColumns();
    }

    /**
     * Gets the column index for a given modifier name.
     * @param modifierName the modifier name to find
     * @return the column index (excluding the index column), or -1 if not found
     */
    private int getColumnIndex(String modifierName) {
        for (int i = 1; i < getColumns().size(); i++) {
            if (modifierName.equals(getColumns().get(i).getUserData())) {
                return i - 1; // Subtract 1 to exclude index column
            }
        }
        return -1;
    }
}