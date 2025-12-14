package com.duom.ardabiomeseditor.ui.views;

import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.services.GuiResourceService;
import com.duom.ardabiomeseditor.services.I18nService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

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
    private int biomeKey = -1;

    private BiConsumer<Integer, BiomeTableClickEvent> clickHandler;
    private List<String> currentColumnOrder = new ArrayList<>();
    private final BiomeTableViewSelectionModel<ObservableList<ColorData>> biomeTableViewSelectionModel;
    private ContextMenu columnContextMenu;
    private VBox indexDisplayContainer;
    private double zoomFactor = 1.0;
    private PauseTransition debounceTimeline;

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
        setupContextMenu();

        Label placeholder = new Label(I18nService.get("ardabiomeseditor.biometableview.no_data"));
        placeholder.getStyleClass().add("text-muted");
        setPlaceholder(placeholder);
        initKeyboardListener();
        initZoomListener();
    }

    /**
     * Loads the CSS stylesheet for the biome table.
     */
    private void loadCss(){

        var cssResource = getClass().getResource("/css/biomes-table-view.css");

        if (cssResource != null)
            getStylesheets().add(cssResource.toExternalForm());
    }

    private void initKeyboardListener() {
        setOnKeyPressed(event -> {
            if (event.isControlDown()) {

                switch (event.getCode()) {
                    case A:
                        event.consume();
                        selectAllColumns();
                        break;
                    case D:
                        event.consume();
                        deselectAllColumns();
                        break;
                }
            }
        });
    }

    /**
     * Sets up the context menu for column visibility management.
     */
    private void setupContextMenu() {
        columnContextMenu = new ContextMenu();

        setOnContextMenuRequested(event -> {
            updateContextMenu();
            columnContextMenu.show(this, event.getScreenX(), event.getScreenY());
        });

        // Hide context menu on left click
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                columnContextMenu.hide();
            }
        });
    }

    private void setupIndexDisplay() {

        if (indexDisplayContainer == null) return;

        indexDisplayContainer.getChildren().clear();
        indexDisplayContainer.setFillWidth(true);
        indexDisplayContainer.setVisible(true);

        Region headerRegion = (Region) lookupAll(".column-header")
                .stream()
                .filter(n -> n instanceof Region)
                .filter(n -> ((Region) n).getHeight() > 0)
                .findFirst()
                .orElse(new Region());

        indexDisplayContainer.minWidthProperty().bind(headerRegion.widthProperty());
        indexDisplayContainer.prefWidthProperty().bind(headerRegion.widthProperty());
        indexDisplayContainer.maxWidthProperty().bind(headerRegion.widthProperty());

        // Create fixed header label
        Label headerLabel = new Label("#");

        headerLabel.minHeightProperty().bind(headerRegion.heightProperty());
        headerLabel.prefHeightProperty().bind(headerRegion.heightProperty());
        headerLabel.maxHeightProperty().bind(headerRegion.heightProperty());

        headerLabel.setAlignment(Pos.CENTER);
        headerLabel.minWidthProperty().bind(headerRegion.widthProperty());
        headerLabel.prefWidthProperty().bind(headerRegion.widthProperty());
        headerLabel.maxWidthProperty().bind(headerRegion.widthProperty());

        headerLabel.setStyle("-fx-font-family:'System';" +
                " -fx-background-color: #161b22;" +
                " -fx-border-color: #0d1117;" +
                " -fx-border-color: #292f35;" +
                " -fx-border-width: 1 0 1 0; " +
                "-fx-font-weight: bold;");

        // Create container for scrollable index labels
        VBox scrollableContent = new VBox();
        scrollableContent.setFillWidth(true);

        // Create index labels for each row
        for (int i = 0; i < getItems().size(); i++) {

            Label indexLabel = new Label(String.valueOf(i));
            indexLabel.setPrefHeight(CELL_WIDTH - 1);
            indexLabel.setMinHeight(CELL_WIDTH - 1);

            indexLabel.minWidthProperty().bind(headerRegion.widthProperty());
            indexLabel.prefWidthProperty().bind(headerRegion.widthProperty());
            indexLabel.maxWidthProperty().bind(headerRegion.widthProperty());

            indexLabel.setAlignment(Pos.CENTER);
            indexLabel.setStyle(
                    "-fx-background-color: #0d1117; " +
                    "-fx-border-color: #292f35; " +
                    "-fx-border-width: 1 0 1 0;"
            );
            scrollableContent.getChildren().add(indexLabel);
        }

        // Wrap scrollable content in ScrollPane
        ScrollPane scrollPane = new ScrollPane(scrollableContent);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Sync scroll position with table
        ScrollBar tableVScrollBar = getTableVerticalScrollBar(Orientation.VERTICAL);
        ScrollBar tableHScrollBar = getTableVerticalScrollBar(Orientation.HORIZONTAL);
        if (tableVScrollBar != null) {
            tableVScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> scrollPane.setVvalue(newVal.doubleValue()));

            scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> tableVScrollBar.setValue(newVal.doubleValue()));

            scrollPane.setVvalue(tableVScrollBar.getValue());
        }

        if (tableHScrollBar != null) {

            double scrollBarHeight = tableHScrollBar.getHeight();
            scrollPane.setPadding(new Insets(0, 0, scrollBarHeight, 0));
        }

        indexDisplayContainer.getChildren().addAll(headerLabel, scrollPane);
    }

    /**
     * Retrieves the corresponding ScrollBar of the table.
     *
     * @return The vertical or horizontal ScrollBar, or null if not found.
     */
    private ScrollBar getTableVerticalScrollBar(Orientation orientation) {

        for (Node node : lookupAll(".scroll-bar")) {

            if (node instanceof ScrollBar scrollBar) {
                if (scrollBar.getOrientation() == orientation) {
                    return scrollBar;
                }
            }
        }
        return null;
    }

    /**
     * Finds the column at the given X coordinate.
     * @param x the X coordinate relative to the table
     * @return the column at that position, or null if not found
     */
    private TableColumn<ObservableList<ColorData>, ?> getClickedColumn(double x) {
        double currentX = 0;

        for (TableColumn<ObservableList<ColorData>, ?> column : getColumns()) {

            // Skip hidden columns
            if (!column.isVisible()) continue;

            currentX += column.getWidth();

            if (x <= currentX) return column;
        }

        return null;
    }

    /**
     * Refreshes the table and updates all column headers.
     */
    @Override
    public void refresh() {
        updateAllHeaders();
        super.refresh();
    }

    private void initZoomListener(){

        debounceTimeline = new PauseTransition(Duration.millis(50));
        debounceTimeline.setOnFinished(event -> applyContentScale());

        setOnScroll(event -> {
            if (event.isControlDown()) {
                event.consume();

                double deltaY = event.getDeltaY();

                if (deltaY > 0)  zoomFactor = Math.min(2.0, zoomFactor + 0.1);
                else if (deltaY < 0) zoomFactor = Math.max(0.1, zoomFactor - 0.1);

                debounceTimeline.play();
            }
        });
    }

    private void applyContentScale() {

        // Scale cell content by adjusting row height
        setFixedCellSize(CELL_WIDTH * zoomFactor);

        // Scale column widths
        for (TableColumn<ObservableList<ColorData>, ?> column : getColumns()) {
            column.setPrefWidth(CELL_WIDTH * zoomFactor);
            column.setMinWidth(CELL_WIDTH * zoomFactor);
            column.setMaxWidth(CELL_WIDTH * zoomFactor);
        }

        refresh();
    }

    /**
     * Updates the context menu
     */
    private void updateContextMenu() {

        Set<String> selectedColumns = biomeTableViewSelectionModel.getSelectedColumns();
        columnContextMenu.getItems().clear();

        // Hide menu item
        MenuItem hideItem = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.hide_columns"));
        hideItem.setGraphic(GuiResourceService.getIcon(GuiResourceService.IconType.HIDE));
        hideItem.setOnAction(e -> {

            getColumns().stream()
                    .filter(column -> {
                        String columnName = (String) column.getUserData();
                        return selectedColumns.contains(columnName);
                    })
                    .forEach(column -> column.setVisible(false));

            biomeTableViewSelectionModel.clearSelection();
            updateAllHeaders();
            refresh();
        });

        // Show All menu item
        MenuItem showAllItem = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.show_all_columns"));
        showAllItem.setGraphic(GuiResourceService.getIcon(GuiResourceService.IconType.SHOW));
        showAllItem.setOnAction(e -> {showAllColumns(null);});

        // Sort Columns menu item
        MenuItem sortColumnsItem = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.sort_columns"));
        sortColumnsItem.setGraphic(GuiResourceService.getIcon(GuiResourceService.IconType.SORT));
        sortColumnsItem.setOnAction(e -> {

            List<TableColumn<ObservableList<ColorData>, ?>> columnsToSort = new ArrayList<>(getColumns());

            // Sort columns alphabetically by their userData (modifier name)
            columnsToSort.sort(Comparator.comparing(col -> ((String) col.getUserData()).toLowerCase()));

            // Move columns to their new positions
            for (int i = 0; i < columnsToSort.size(); i++) {
                TableColumn<ObservableList<ColorData>, ?> targetColumn = columnsToSort.get(i);

                getColumns().remove(targetColumn);
                getColumns().add(i, targetColumn);
            }

            reorderRowData();
            refresh();
        });

        MenuItem resetZoom = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.reset_zoom"));
        resetZoom.setGraphic(GuiResourceService.getIcon(GuiResourceService.IconType.ZOOM_RESET));
        resetZoom.setOnAction(e -> {

            zoomFactor = 1;
            applyContentScale();
            refresh();
        });

        MenuItem resetColumnEdit = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.reset_column_edit"));
        resetColumnEdit.setGraphic(GuiResourceService.getIcon(GuiResourceService.IconType.RESET));
        resetColumnEdit.setOnAction(e -> {

            resetChanges(biomeTableViewSelectionModel.getSelectedColumns());
        });

        if (!selectedColumns.isEmpty()) {

            columnContextMenu.getItems().addAll(hideItem,
                    showAllItem,
                    new SeparatorMenuItem(),
                    sortColumnsItem,
                    new SeparatorMenuItem(),
                    resetZoom,
                    new SeparatorMenuItem(),
                    resetColumnEdit);
        } else {

            columnContextMenu.getItems().addAll(showAllItem,
                    new SeparatorMenuItem(),
                    sortColumnsItem,
                    new SeparatorMenuItem(),
                    resetZoom);
        }
    }

    /**
     * Configures the table with the given biome key and color mappings.
     *
     * @param biomeKey      The key representing the biome.
     * @param colorMappings A map of modifier names to their corresponding color lists.
     */
    public void configure(int biomeKey, Map<String, List<String>> colorMappings) {

        this.zoomFactor = 1;
        this.biomeKey = biomeKey;
        this.biomeTableViewSelectionModel.clearSelection();
        List<String> modifierNames = colorMappings.keySet().stream().sorted().toList();

        getColumns().clear();
        getItems().clear();

        createColumns(modifierNames);
        populateRows(colorMappings, modifierNames);

        Platform.runLater(this::setupIndexDisplay);
    }

    /**
     * Creates columns for the table based on the given headers.
     *
     * @param headers A list of column headers.
     */
    private void createColumns(List<String> headers) {

        setFixedCellSize(-1);

        currentColumnOrder= new ArrayList<>(headers);

        for (int idx = 0; idx < headers.size(); idx++) {

            var column = createColumn(headers.get(idx), idx);
            getColumns().add(column);

            column.tableViewProperty().addListener((obs, oldTV, newTV) -> {
                if (newTV != null) {
                    reorderRowData();
                }
            });
        }
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

        // Add mouse click listener to parent so the click works on the full width of the header
        headerGroup.parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent != null) {
                newParent.setOnMouseClicked(mouseEvent -> {
                    String modifierName = (String) column.getUserData();
                    handleTableHeaderMouseEvent(modifierName, mouseEvent);
                });
            }
        });

        column.setGraphic(headerGroup);

        column.setPrefWidth(CELL_WIDTH);
        column.setMinWidth(CELL_WIDTH);
        column.setMaxWidth(CELL_WIDTH);
        column.setSortable(false);

        column.setCellValueFactory(param -> {
            String modifierName = (String) column.getUserData();

            // Access the entire row data
            ObservableList<ColorData> row = param.getValue();

            // Find the correct data index based on modifier name
            int dataIndex = getColumns().stream()
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

    /**
     * Handles mouse events on the table header for column selection.
     *
     * @param modifierName The modifier represented by the column.
     * @param mouseEvent   The mouse event triggered on the header.
     */
    private void handleTableHeaderMouseEvent(String modifierName, MouseEvent mouseEvent) {

        if (mouseEvent.getButton() == MouseButton.SECONDARY) return;

        // CTRL Click
        if (mouseEvent.isControlDown())
            selectColumnAppend(modifierName);

        // Shift Click
        else if (mouseEvent.isShiftDown())
            selectColumnRange(modifierName);

        // Regular Click
        else
            selectSingleColumn(modifierName);

        updateAllHeaders();
    }

    /**
     * Selects a single table column.
     * @param modifierName The modifier represented by the clicked column.
     */
    private void selectSingleColumn(String modifierName) {
        // Normal selection
        biomeTableViewSelectionModel.clearSelection();
        selectColumn(modifierName);
    }

    /**
     * Selects a range of columns between the anchor and the clicked column.
     * @param modifierName The modifier represented by the clicked column.
     */
    private void selectColumnRange(String modifierName) {
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
                if (!biomeTableViewSelectionModel.isColumnSelected(columnName))
                    biomeTableViewSelectionModel.selectColumn(columnName);
            }

            refresh();

            String formattedHeaders = biomeTableViewSelectionModel.getSelectedColumns().stream()
                    .map(this::formatHeaderString)
                    .collect(Collectors.joining(", "));

            if (clickHandler != null)
                clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, formattedHeaders));
        }
    }

    /**
     * Appends or removes a column from the current selection.
     *
     * @param modifierName The modifier name of the column.
     */
    private void selectColumnAppend(String modifierName) {
        if (biomeTableViewSelectionModel.isColumnSelected(modifierName)) {

            biomeTableViewSelectionModel.deselectColumn(modifierName);
            updateAllHeaders();

            if (clickHandler != null)
                clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, ""));
        } else {

            selectColumn(modifierName);
            updateAllHeaders();
        }
    }

    /**
     * Selects all columns in the table.
     */
    private void selectAllColumns() {
        biomeTableViewSelectionModel.clearSelection();

        for (TableColumn<ObservableList<ColorData>, ?> column : getColumns()) {
            String modifierName = (String) column.getUserData();
            if (modifierName != null) {
                biomeTableViewSelectionModel.selectColumn(modifierName);
            }
        }

        updateAllHeaders();
        refresh();

        String formattedHeaders = biomeTableViewSelectionModel.getSelectedColumns().stream()
                .map(this::formatHeaderString)
                .collect(Collectors.joining(", "));

        if (clickHandler != null) {
            clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, formattedHeaders));
        }
    }

    /**
     * Deselects all columns in the table.
     */
    private void deselectAllColumns() {
        biomeTableViewSelectionModel.clearSelection();
        updateAllHeaders();
        refresh();

        if (clickHandler != null) {
            clickHandler.accept(-1, new BiomeTableClickEvent(BiomeTableClickEventType.HEADER, ""));
        }
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

            var textScale = String.format("-fx-font-size: %.2fpt;", 12 * zoomFactor);

            if (biomeTableViewSelectionModel.isColumnSelected((String) column.getUserData())) {
                parentNode.setStyle("-fx-cursor: hand; -fx-background-color: " + HIGHLIGHT_COLOR + ";");

                if (!headerContainer.getChildren().isEmpty() && headerContainer.getChildren().getFirst() instanceof Text text) {
                    text.setStyle("-fx-fill: " + HIGHLIGHT_TEXT_COLOR + ";" + textScale);
                }

            } else {
                parentNode.setStyle("-fx-cursor: hand;");

                if (!headerContainer.getChildren().isEmpty() && headerContainer.getChildren().getFirst() instanceof Text text) {
                    text.setStyle(textScale);
                }
            }
        }
    }

    /**
     * Updates the styles of all column headers in the table.
     */
    private void updateAllHeaders() {
        for (int i = 0; i < getColumns().size(); i++) {

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
                colorBox.setPrefSize((CELL_WIDTH - 5) * zoomFactor, (CELL_WIDTH - 5) * zoomFactor);
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

            for (int columnIndex = 0; columnIndex < getColumns().size() - 1; columnIndex++) {

                // Get modifier name
                String columnName = (String) getColumns().get(columnIndex).getUserData();

                // Collect all modified ColorData in this column
                int dataColumnIndex = columnIndex;
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
    public void resetChanges(Set<String> selectedColumns) {

        for (ObservableList<ColorData> rowData : getItems())
            for (int columnIndex = 0 ; columnIndex < rowData.size() - 1; columnIndex++) {

                String columnName = (String) getColumns().get(columnIndex).getUserData();
                if (selectedColumns.contains(columnName)) rowData.get(columnIndex).reset();
            }
    }

    /**
     * Resets changes made in a specific column to their original values.
     *
     * @param columnIndex The index of the column to reset.
     */
    public void resetChanges(int columnIndex) {

        if (columnIndex == -1) return;

        for (ObservableList<ColorData> rowData : getItems())
            rowData.get(columnIndex).reset();
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

            var visibleColumns = getColumns().stream()
                    .filter(TableColumnBase::isVisible)
                    .toList();

            if (visibleColumns.size() > position.getColumn()) {

                // Get the actual column from the visual position
                TableColumn<ObservableList<ColorData>, ?> column = visibleColumns.get(position.getColumn());
                String modifierName = (String) column.getUserData();

                // Find the data index using the modifier name
                int dataIndex = currentColumnOrder.indexOf(modifierName);

                if (dataIndex >= 0 && dataIndex < rowData.size()) {
                    selectedColors.add(rowData.get(dataIndex));
                }
            }
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

    /**
     * Sets the container for displaying row indices.
     * @param indexColumnContainer The VBox container for index display.
     */
    public void setIndexDisplayContainer(VBox indexColumnContainer) {
        this.indexDisplayContainer = indexColumnContainer;
    }

    /**
     * Shows all columns in the table.
     * @param unused A placeholder parameter (not used).
     */
    public void showAllColumns(Void unused) {

        for (int i = 0; i < getColumns().size(); i++) {
            getColumns().get(i).setVisible(true);
        }
    }

    /**
     * Clears the table view, removing all columns and items.
     */
    public void clear() {
        biomeKey = -1;
        biomeTableViewSelectionModel.clearSelection();
        indexDisplayContainer.getChildren().clear();
        indexDisplayContainer.setVisible(false);
        getColumns().clear();
        getItems().clear();
        refresh();
    }

}