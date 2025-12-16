package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.model.ResourceIdentifier;
import com.duom.ardabiomeseditor.services.ColorEditorService;
import com.duom.ardabiomeseditor.services.IconResourceService;
import com.duom.ardabiomeseditor.services.I18nService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Controller for the colormap editor view.
 * Manages the display and interaction with the colormap data, including zooming, panning, and column manipulation.
 */
public class ColormapController {

    /**
     * Rendered pixel size - 1:64 to 1:1 (ie base texture size)
     */
    private static final double MAX_PIXEL_SIZE = 64;
    private static final double MIN_PIXEL_SIZE = 1;

    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass MODIFIED_PSEUDO_CLASS = PseudoClass.getPseudoClass("modified");
    private static final PseudoClass CHECKER_PSEUDO_CLASS = PseudoClass.getPseudoClass("checker");

    /**
     * Maximum texture size to prevent exceeding GPU limits
     */
    private static final double MAX_TEXTURE_SIZE = 16384d;

    /**
     * Number of extra rows/columns to buffer when rendering visible area - improves panning smoothness
     */
    private static final int CANVAS_BUFFER = 8;

    /**
     * Canvas last mouse position for panning
     */
    final ObjectProperty<Point2D> lastMousePosition = new SimpleObjectProperty<>();

    /**
     * Screen pixel scale for pixel snapping
     * Canvas works with floating point coordinates, layout components work with device pixels.
     * Syncing both coordinate systems requires rounding on the canvas.
     */
    private final Screen screen = Screen.getPrimary();
    private final double pixelScaleX = screen.getOutputScaleX();
    private final double pixelScaleY = screen.getOutputScaleY();

    /**
     * Canvas zooming elements
     */
    private final DoubleProperty zoomFactor = new SimpleDoubleProperty(1.0);
    private final StringExpression DEFAULT_FONT_SIZE_BINDING = Bindings.format("-fx-font-size: %.1fpx;", zoomFactor.multiply(.3));

    /* UI Elements */

    @FXML private Canvas canvas;
    @FXML private Pane canvasPane;
    @FXML private ScrollPane canvasScroll, headerScroll;
    @FXML private StackPane headersContainer;

    @FXML private HBox columnHeaders;
    @FXML private BorderPane canvasLayout;

    @FXML private Label headerHoverLabel;
    @FXML private Label headerSelectionLabel;
    @FXML private Region insertLine;
    @FXML private VBox rowIndex;
    @FXML private SplitPane editorRoot;
    @FXML private BorderPane colormapRoot;
    @FXML private ColorEditorController colorEditorController;
    @FXML private ToggleButton showCheckerToggle;
    @FXML private Button resetZoomButton;
    @FXML private Button fitColumnsToViewButton;
    @FXML private Slider zoomSlider;
    @FXML private Button zoomOutButton, zoomInButton;

    /*
     * Header, selection management and zooming
     */

    /**
     * Columns representing the mapped data - Texture width
     */
    private List<Column> columns;

    /**
     * Number of rows in the colormap - Texture height
     */
    private int rowCount;

    /**
     * Underlying image for the canvas rendering
     */
    private WritableImage image;
    private PixelWriter pixelWriter;

    /**
     * Debouncers for canvas redraw and zoom updates
     */
    private PauseTransition canvasRedrawDebouncer;
    private PauseTransition zoomDebouncer;

    private double lastVerticalScrollDelta = 0;

    private ResourceIdentifier displayedResourceIdentifier;
    private DisplayedResourceType displayedResourceType;

    private ContextMenu headerContextMenu;

    /**
     * Snaps a value to the nearest pixel based on the provided pixel scale.
     *
     * @param value      The value to snap.
     * @param pixelScale The pixel scale factor.
     * @return The snapped value.
     */
    private static double snap(double value, double pixelScale) {
        return Math.round(value * pixelScale) / pixelScale;
    }

    /**
     * Configures the colormap controller.
     *
     * @param root          The root resource identifier.
     * @param type          The type of displayed resource.
     * @param colorMappings A map of resource identifiers to lists of integer color values.
     */
    public void configure(ResourceIdentifier root, DisplayedResourceType type, Map<ResourceIdentifier, int[]> colorMappings) {

        initColormap(colorMappings);

        displayedResourceIdentifier = root;
        displayedResourceType = type;

        canvasRedrawDebouncer = new PauseTransition(Duration.millis(5));
        canvasRedrawDebouncer.setOnFinished(actionEvent -> this.redraw());
        zoomDebouncer = new PauseTransition(Duration.millis(5));
        zoomDebouncer.setOnFinished(actionEvent -> this.updateZoomFactor());

        headerSelectionLabel.setText("");

        if (!colorMappings.isEmpty()) {
            configureCanvas();
            configureHeaderContextMenu();
            configureIndexColumn();
            configureHeaders();

            colorEditorController.setHsbAdjustmentConsumer(this::applyHsb);
            colorEditorController.setOpacityAdjustmentConsumer(this::applyOpacity);
            colorEditorController.toggleHsbEditorVisibility(false);
        }

        configureBottomToolbarButtons();
    }

    /**
     * Initializes the colormap columns and rows from the provided color mappings.
     *
     * @param colorMappings A map of resource identifiers to lists of integer color values.
     */
    private void initColormap(Map<ResourceIdentifier, int[]> colorMappings) {

        columns = new ArrayList<>(colorMappings.size());
        List<ColorEditorController.ColumnItem> editorItems = new ArrayList<>();

        initializeColumns(colorMappings);

        // Identify maximum row count
        rowCount = columns.stream()
                .mapToInt(column -> column.colorData.length)
                .max()
                .orElse(0);

        columns.forEach(column ->
                editorItems.add(new ColorEditorController.ColumnItem(column.identifier.toString(),
                        column.index,
                        column.selected,
                        column.visible)));

        // Update editor columns
        colorEditorController.setColumnsList(editorItems);
    }

    /**
     * Initializes columns based on biome ID mapping.
     *
     * @param colorMappings A map of resource identifiers to lists of integer color values.
     */
    private void initializeColumns(Map<ResourceIdentifier, int[]> colorMappings) {

        List<ResourceIdentifier> sortedHeaders = colorMappings.keySet().stream()
                .sorted()
                .toList();

        for (ResourceIdentifier identifier : sortedHeaders) {

            var visible = new SimpleBooleanProperty(true);
            var selected = new SimpleBooleanProperty(false);
            var index = sortedHeaders.indexOf(identifier);

            selected.addListener((obs, old, newValue) -> {
                if (newValue) updateHsbSliders(getColumn(index));
            });
            visible.addListener((obs, old, newValue) -> {
                refreshHeaders();
                redraw();
            });

            columns.add(new Column(index,
                    identifier,
                    colorMappings.get(identifier),
                    colorMappings.get(identifier),
                    selected,
                    visible,
                    new SimpleBooleanProperty(false)));
        }
    }

    /**
     * Configures the canvas size and bindings based on the number of columns and rows.
     */
    private void configureCanvas() {

        canvasPane.setPrefWidth(columns.size());
        canvasPane.setPrefHeight(rowCount);

        image = new WritableImage(columns.size(), rowCount);
        pixelWriter = image.getPixelWriter();

        colormapRoot.addEventFilter(ScrollEvent.SCROLL, this::handleScrollWheelEvents);
        colormapRoot.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyboardEvents);
        canvasScroll.vvalueProperty().addListener((obs, oldVal, newVal) -> canvasRedrawDebouncer.play());
        canvasScroll.hvalueProperty().addListener((obs, oldVal, newVal) -> canvasRedrawDebouncer.play());

        // Mouse panning
        canvasScroll.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                lastMousePosition.set(new Point2D(e.getSceneX(), e.getSceneY()));
                canvasScroll.setCursor(Cursor.MOVE); // cross arrow
                e.consume();
            }
        });

        canvasScroll.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.getButton() == MouseButton.MIDDLE && lastMousePosition.get() != null) {

                Point2D last = lastMousePosition.get();
                double dx = e.getSceneX() - last.getX();
                double dy = e.getSceneY() - last.getY();

                lastMousePosition.set(new Point2D(e.getSceneX(), e.getSceneY()));

                // viewport & content sizes
                Bounds viewport = canvasScroll.getViewportBounds();
                Node content = canvasScroll.getContent();
                Bounds contentBounds = content.getLayoutBounds();

                double extraWidth = contentBounds.getWidth() - viewport.getWidth();
                double extraHeight = contentBounds.getHeight() - viewport.getHeight();

                if (extraWidth > 0) {
                    double hDelta = dx / extraWidth;
                    canvasScroll.setHvalue(Math.clamp(canvasScroll.getHvalue() - hDelta, 0, 1));
                }

                if (extraHeight > 0) {
                    double vDelta = dy / extraHeight;
                    canvasScroll.setVvalue(Math.clamp(canvasScroll.getVvalue() - vDelta, 0, 1));
                }

                e.consume();
            }
        });

        canvasScroll.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.MIDDLE) {
                lastMousePosition.set(null);
                canvasScroll.setCursor(Cursor.DEFAULT);
                e.consume();
            }
        });

        colormapRoot.layoutBoundsProperty().addListener(observable -> canvasRedrawDebouncer.play());
        Platform.runLater(() -> computeZoomBounds(false, false));
    }

    /**
     * Handles keyboard events for column selection shortcuts.
     *
     * @param keyEvent The key event.
     */
    private void handleKeyboardEvents(KeyEvent keyEvent) {

        if (!keyEvent.isConsumed()) {

            // Select all columns
            if (keyEvent.isControlDown() && keyEvent.getCode() == KeyCode.A) {

                columns.forEach(column -> column.selected.set(true));
                keyEvent.consume();
            }

            // Deselect all columns
            if (keyEvent.isControlDown() && keyEvent.getCode() == KeyCode.D || keyEvent.getCode() == KeyCode.ESCAPE) {

                clearSelection();
                keyEvent.consume();
            }

            // Invert selection
            if (keyEvent.isControlDown() && keyEvent.getCode() == KeyCode.I) {

                columns.forEach(column -> column.selected.setValue(!column.selected.get()));
                keyEvent.consume();
            }

            // Hide columns
            if (keyEvent.isControlDown() && keyEvent.getCode() == KeyCode.H) {

                List<Column> selectedColumns = columns.stream()
                        .filter(column -> column.selected.get())
                        .toList();

                selectedColumns.forEach(column -> column.visible.set(false));
                keyEvent.consume();
            }

            // Show columns
            if (keyEvent.isAltDown() && keyEvent.getCode() == KeyCode.H) {

                columns.forEach(column -> column.visible.set(true));
                keyEvent.consume();
            }
        }
    }

    /**
     * Handles scroll events on the canvas for panning and zooming.
     *
     * @param event The scroll event.
     */
    private void handleScrollWheelEvents(ScrollEvent event) {

        if (!event.isConsumed()) {
            // Horizontal scroll (shift key)
            if (event.isShiftDown() || event.getDeltaX() != 0) {
                double contentWidth = canvasScroll.getContent()
                        .getBoundsInLocal()
                        .getWidth();
                double viewportWidth = canvasScroll.getViewportBounds()
                        .getWidth();

                if (contentWidth > viewportWidth) {
                    double delta = (event.getDeltaY() + event.getDeltaX())
                            / (contentWidth - viewportWidth);

                    canvasScroll.setHvalue(Math.clamp(canvasScroll.getHvalue() - delta, 0, 1));
                }

            } else if (event.isControlDown()) {
                // Zoom
                lastVerticalScrollDelta = event.getDeltaY();
                zoomDebouncer.playFromStart();

            } else {
                // Vertical scroll
                double contentHeight = canvasScroll.getContent()
                        .getBoundsInLocal()
                        .getHeight();
                double viewportHeight = canvasScroll.getViewportBounds()
                        .getHeight();

                if (contentHeight > viewportHeight) {
                    double delta = event.getDeltaY()
                            / (contentHeight - viewportHeight);

                    canvasScroll.setVvalue(Math.clamp(canvasScroll.getVvalue() - delta, 0, 1));
                }
            }

            event.consume();
        }
    }

    /**
     * Configures the context menu for column headers.
     */
    private void configureHeaderContextMenu() {

        headerContextMenu = new ContextMenu();

        // Hide menu item
        MenuItem hideItem = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.hide_columns"));
        hideItem.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.HIDE));
        hideItem.setOnAction(e -> {

            ContextMenu menu = ((MenuItem) e.getSource()).getParentPopup();
            Node owner = menu.getOwnerNode();

            List<Column> selectedColumns = columns.stream()
                    .filter(column -> column.selected.get())
                    .toList();

            if (selectedColumns.isEmpty() && owner.getUserData() instanceof Integer) {

                int columnIndex = (int) owner.getUserData();
                Column column = getColumn(columnIndex);
                if (column != null)
                    column.visible.set(false);
            } else
                selectedColumns.forEach(column -> column.visible.set(false));
        });

        // Show All menu item
        MenuItem showAllItem = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.show_all_columns"));
        showAllItem.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.SHOW));
        showAllItem.setOnAction(e -> columns.forEach(column -> column.visible.set(true)));

        // Sort Columns menu item
        MenuItem sortColumnsItem = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.sort_columns"));
        sortColumnsItem.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.SORT));
        sortColumnsItem.setOnAction(e -> {
            columns.sort(Comparator.comparing(column -> column.identifier));
            canvasRedrawDebouncer.play();
            refreshHeaders();
        });

        MenuItem resetZoom = new MenuItem(I18nService.get("ardabiomeseditor.biometableview.reset_zoom"));
        resetZoom.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.ZOOM_RESET));
        resetZoom.setOnAction(e -> computeZoomBounds(true, false));

        headerContextMenu.getItems().addAll(hideItem,
                showAllItem,
                new SeparatorMenuItem(),
                sortColumnsItem,
                new SeparatorMenuItem(),
                resetZoom);
    }

    /**
     * Configures the index column to display row indices alongside the canvas.
     */
    private void configureIndexColumn() {

        rowIndex.getChildren().clear();

        rowIndex.minWidthProperty().bind(zoomFactor);
        rowIndex.prefWidthProperty().bind(zoomFactor);

        for (int idx = 0; idx < rowCount; idx++) {

            Label indexLabel = new Label(String.valueOf(idx));
            indexLabel.setAlignment(Pos.CENTER);

            // Label sizing
            indexLabel.styleProperty().bind(DEFAULT_FONT_SIZE_BINDING);

            /*
             * VBox will ignore prefHeight if it would violate minimums or font metrics
             * Override minHeight to 0 to force correct scaling
             */
            indexLabel.setMinHeight(0);
            indexLabel.prefHeightProperty().bind(zoomFactor);
            indexLabel.minWidthProperty().bind(zoomFactor);
            indexLabel.prefWidthProperty().bind(zoomFactor);

            rowIndex.getChildren().add(indexLabel);
        }
    }

    /**
     * Configures the header scroll pane to synchronize with the canvas scroll pane.
     */
    private void configureHeaders() {

        headerScroll.hvalueProperty().bind(canvasScroll.hvalueProperty());
        headerScroll.prefHeightProperty().bind(zoomFactor.multiply(4));
        headerScroll.maxHeightProperty().bind(zoomFactor.multiply(4));
        headerScroll.minHeightProperty().bind(zoomFactor.multiply(4));

        refreshHeaders();
    }

    /**
     * Configures the zoom and fit buttons.
     */
    private void configureBottomToolbarButtons() {

        canvasLayout.pseudoClassStateChanged(CHECKER_PSEUDO_CLASS, showCheckerToggle.isSelected());
        showCheckerToggle.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.CHECKER));
        showCheckerToggle.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            canvasLayout.pseudoClassStateChanged(CHECKER_PSEUDO_CLASS, isNowSelected);
        });

        resetZoomButton.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.AUTO_FIT_ROWS));
        fitColumnsToViewButton.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.AUTO_FIT_COLUMNS));
        zoomFactor.addListener((obs, oldVal, newVal) -> zoomSlider.setValue(newVal.doubleValue()));
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {

            double snapped = Math.round(newVal.doubleValue() * 2) / 2.0;

            if (snapped != newVal.doubleValue()) {

                zoomSlider.setValue(snapped);

            } else if (zoomFactor.get() != snapped) {

                zoomFactor.setValue(snapped);
                canvasRedrawDebouncer.play();
            }
        });

        zoomInButton.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.ZOOM_IN));
        zoomInButton.setOnMouseClicked(event -> zoomSlider.increment());
        zoomOutButton.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.ZOOM_OUT));
        zoomOutButton.setOnMouseClicked(event -> zoomSlider.decrement());
    }

    /**
     * Applies HSB adjustments to the selected columns.
     *
     * @param hsb The HSB adjustments to apply.
     */
    private void applyHsb(ColorEditorService.HSB hsb) {

        if (columns == null) return;

        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {

            Column column = columns.get(colIndex);

            if (column.selected.get()) {

                int[] shiftedColors = new int[column.colorData().length];

                for (int j = 0; j < column.colorData().length; j++) {
                    shiftedColors[j] = ColorEditorService.applyHsb(
                            column.originalColorData()[j],
                            hsb.hue(),
                            hsb.saturation(),
                            hsb.brightness()
                    );
                }

                column.modified.set(!Arrays.equals(column.originalColorData, shiftedColors));

                columns.set(colIndex, new Column(column.index,
                        column.identifier,
                        column.originalColorData(),
                        shiftedColors,
                        column.selected,
                        column.visible,
                        column.modified));
            }
        }
        redraw();
    }

    /**
     * Applies opacity adjustments to the selected columns.
     *
     * @param opacity The opacity value to apply (0.0 to 1.0).
     */
    private void applyOpacity(Double opacity) {
        if (columns == null) return;

        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {

            Column column = columns.get(colIndex);

            if (column.selected.get()) {

                int[] shiftedColors = new int[column.colorData().length];

                for (int j = 0; j < column.colorData().length; j++) {
                    shiftedColors[j] = ColorEditorService.applyOpacity(column.colorData[j], opacity);
                }

                column.modified.set(!Arrays.equals(column.originalColorData, shiftedColors));

                columns.set(colIndex, new Column(column.index,
                        column.identifier,
                        column.originalColorData(),
                        shiftedColors,
                        column.selected,
                        column.visible,
                        column.modified));
            }
        }
        redraw();
    }

    /**
     * Updates the zoom factor based on the vertical scroll delta.
     */
    private void updateZoomFactor() {

        if (lastVerticalScrollDelta != 0) {

            double delta = lastVerticalScrollDelta > 0 ? 1 : -1;

            var newZoomFactor = zoomFactor.get() + delta;

            double computedTexWidth = (columns.size() + 1) * newZoomFactor;
            double computedTexHeight = rowCount * newZoomFactor;

            newZoomFactor = Math.clamp(newZoomFactor, MIN_PIXEL_SIZE, MAX_PIXEL_SIZE);

            // Ensure that we do not exceed maximum canvas size
            if (newZoomFactor > 0 && computedTexWidth < MAX_TEXTURE_SIZE && computedTexHeight < MAX_TEXTURE_SIZE) {

                zoomFactor.set(newZoomFactor);
                canvasRedrawDebouncer.play();
            }
        }
    }

    /**
     * Builds the column headers with drag-and-drop and selection functionality.
     */
    private void refreshHeaders() {

        columnHeaders.getChildren().clear();
        columns.forEach(column -> column.selected.set(false));

        // Add left padding to headers to align with canvas
        headersContainer.paddingProperty().bind(Bindings.createObjectBinding(() -> new Insets(0, 0, 0, zoomFactor.get()), zoomFactor));

        for (int columnIdx = 0; columnIdx < columns.size(); columnIdx++) {

            Column column = columns.get(columnIdx);

            if (!column.visible.get()) continue;

            Label header = new Label(column.identifier.toString());
            header.setUserData(column.index);
            header.setAlignment(Pos.CENTER);

            configureMouseEvents(header, column);
            configureDragAndDrop(header, columnIdx);

            header.setRotate(90);

            header.styleProperty().bind(DEFAULT_FONT_SIZE_BINDING);

            header.prefHeightProperty().bind(zoomFactor);
            header.prefWidthProperty().bind(zoomFactor.multiply(4));
            header.pseudoClassStateChanged(MODIFIED_PSEUDO_CLASS, column.modified.get());

            Group headerWrapper = new Group();
            headerWrapper.getStyleClass().add("colormap-column-header");
            headerWrapper.getChildren().add(header);

            columnHeaders.getChildren().add(headerWrapper);
        }

        columnHeaders.setOnDragExited(e -> insertLine.setVisible(false));
    }

    /**
     * Configures mouse events for a column header, including context menu and selection.
     *
     * @param header The header label to configure.
     * @param column The column associated with the header.
     */
    private void configureMouseEvents(Label header, Column column) {

        header.setOnContextMenuRequested(e -> {
            headerContextMenu.show(header, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        // Select
        header.setOnMouseClicked(event -> handleHeaderMouseClick(event, column.index));

        column.selected().addListener((obs, wasSelected, isSelected) -> {
            header.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, isSelected);
            colorEditorController.toggleHsbEditorVisibility(columns.stream().anyMatch(col -> col.selected.get()));
        });

        column.modified().addListener((obs, wasModified, isModified) ->
                header.pseudoClassStateChanged(MODIFIED_PSEUDO_CLASS, isModified)
        );

        header.setOnMouseMoved(event -> {

            // Current hovered column name in white
            headerHoverLabel.setText(column.identifier.toString());
            var anySelected = columns.stream().anyMatch(col -> col.selected.get());

            // Add selection info if any columns are selected
            if (anySelected) {
                List<String> selectedNames = new ArrayList<>();

                for (Column col : columns)
                    if (col.selected.get()) selectedNames.add(col.identifier.toString());

                // Show up to 2 names
                int displayCount = Math.min(2, selectedNames.size());
                StringBuilder selectedText = new StringBuilder(" ");
                for (int i = 0; i < displayCount; i++) {
                    if (i > 0) selectedText.append(", ");
                    selectedText.append(selectedNames.get(i));
                }

                // Add "and X more" if more than 2
                if (selectedNames.size() > 2) {
                    selectedText.append(" and ").append(selectedNames.size() - 2).append(" more");
                }

                headerSelectionLabel.setText(selectedText.toString());
            }
        });

        header.setOnMouseExited(event -> {

            headerHoverLabel.setText("");

            var anySelected = columns.stream().anyMatch(col -> col.selected.get());
            if (anySelected) {
                List<String> selectedNames = new ArrayList<>();
                for (Column col : columns)
                    if (col.selected.get()) selectedNames.add(col.identifier.toString());

                int displayCount = Math.min(2, selectedNames.size());
                StringBuilder selectedText = new StringBuilder();
                for (int i = 0; i < displayCount; i++) {
                    if (i > 0) selectedText.append(", ");
                    selectedText.append(selectedNames.get(i));
                }

                if (selectedNames.size() > 2) {
                    selectedText.append(" and ").append(selectedNames.size() - 2).append(" more");
                }

                headerSelectionLabel.setText(selectedText.toString());
            }
        });
    }

    /**
     * Configures drag-and-drop functionality for column headers.
     *
     * @param header The header label to configure.
     * @param index  The index of the column associated with the header.
     */
    private void configureDragAndDrop(Label header, int index) {

        // Drag start
        header.setOnDragDetected(event -> {

            Set<Integer> indices = new LinkedHashSet<>();
            for (int idx = 0; idx < columns.size(); idx++) {
                Column col = columns.get(idx);
                if (col.selected.get()) {
                    indices.add(idx);
                }
            }
            indices.add(index);

            String selection = indices.stream()
                    .map(idx -> Integer.toString(idx))
                    .collect(Collectors.joining(","));

            Dragboard dragboard = header.startDragAndDrop(TransferMode.MOVE);
            dragboard.setContent(Map.of(DataFormat.PLAIN_TEXT, selection));
            event.consume();
        });

        // Drag over
        header.setOnDragOver(e -> {

            e.acceptTransferModes(TransferMode.MOVE);

            Bounds bounds = header.localToScene(header.getBoundsInLocal());
            double mouseX = e.getSceneX();

            boolean insertAfter = mouseX > (bounds.getMinX() + bounds.getWidth() / 2);

            double x;

            if (insertAfter) x = bounds.getMaxX();
            else x = bounds.getMinX();

            // Convert scene X to container local X
            double localX = headersContainer.sceneToLocal(x, 0).getX() - zoomFactor.get();

            insertLine.setTranslateX(localX);
            insertLine.prefHeightProperty().bind(columnHeaders.prefHeightProperty());
            insertLine.setVisible(true);

            e.consume();
        });

        // Drop
        header.setOnDragDropped(e -> {

            insertLine.setVisible(false);
            Dragboard dragboard = e.getDragboard();

            Object csvSelection = dragboard.getContent(DataFormat.PLAIN_TEXT);
            List<Integer> selectedIndices = csvSelection == null
                    ? new ArrayList<>()
                    : Arrays.stream(((String) csvSelection).split(","))
                    .map(Integer::parseInt)
                    .toList();

            Bounds bounds = header.localToScene(header.getBoundsInLocal());
            double mouseX = e.getSceneX();
            boolean insertAfter = mouseX > bounds.getMinX() + bounds.getWidth() / 2;

            if (!selectedIndices.isEmpty()) {

                boolean samePositionInsert = insertAfter && selectedIndices.getFirst().equals(index + 1) ||
                        !insertAfter && selectedIndices.getFirst().equals(index - 1);

                if (!samePositionInsert) {

                    // Move all selected columns
                    moveColumns(selectedIndices, index, insertAfter);
                    refreshHeaders();
                }
            }

            e.setDropCompleted(true);
            e.consume();
        });

        header.setOnDragExited(e -> insertLine.setVisible(false));
    }

    /**
     * Handles mouse click events on column headers for selection.
     *
     * @param event The mouse event.
     * @param index The index of the clicked column.
     */
    private void handleHeaderMouseClick(MouseEvent event, int index) {

        if (event.getButton() == MouseButton.PRIMARY) {

            if (event.isShiftDown()) {

                selectColumnRange(index);

            } else {

                if (event.isControlDown()) {

                    if (!getColumn(index).selected.get()) selectColumn(index);
                    else deselectColumn(index);

                } else {

                    clearSelection();
                    selectColumn(index);
                }
            }
        }
    }

    /**
     * Selects a range of columns between the anchor (first selected) and the clicked column.
     * This method works on the visual order of columns, not their original indices.
     *
     * @param index The index of the clicked column.
     */
    private void selectColumnRange(int index) {

        int columnVisualIndex = IntStream.range(0, columns.size())
                .filter(i -> columns.get(i).index == index)
                .findFirst()
                .orElse(index);

        int anchorVisualIndex = IntStream.range(0, columns.size())
                .filter(i -> columns.get(i).selected.get())
                .findFirst()
                .orElse(index);

        // Select range between anchor and clicked column
        int start = Math.min(anchorVisualIndex, columnVisualIndex);
        int end = Math.clamp(Math.max(anchorVisualIndex, columnVisualIndex) + 1, 0, columns.size());

        IntStream.range(start, end)
                .forEach(idx -> columns.get(idx).selected.set(true));
    }

    /**
     * Clears the selection of all columns.
     */
    private void clearSelection() {

        columns.forEach(column -> column.selected.set(false));
    }

    /**
     * Selects a single column by index.
     *
     * @param index The index of the column to select.
     */
    private void selectColumn(int index) {

        var column = getColumn(index);

        column.selected.set(true);
    }

    /**
     * Updates the HSB sliders in the color editor based on the selected column.
     * If multiple columns are selected, resets the sliders to default.
     *
     * @param column The column to use for updating the sliders.
     */
    private void updateHsbSliders(Column column) {

        boolean multipleSelected =
                columns.stream()
                        .filter(col -> col.selected.get())
                        .limit(2)
                        .count() == 2;

        if (multipleSelected) {

            colorEditorController.setHsbSliders(new ColorEditorService.HSB(0, 0, 0));

        } else {

            var currentColorData = column.colorData;
            var initialColorData = column.originalColorData;

            if (currentColorData != null && initialColorData != null && currentColorData.length > 0 && initialColorData.length > 0) {
                colorEditorController.setHsbSliders(ColorEditorService.computeHsbShift(initialColorData[0], currentColorData[0]));
                colorEditorController.setOpacitySlider(ColorEditorService.getOpacity(currentColorData[0]));
            }

        }
    }

    /**
     * Deselects a single column by index.
     *
     * @param index The index of the column to deselect.
     */
    private void deselectColumn(int index) {

        getColumn(index).selected.set(false);
    }

    /**
     * Retrieves a column by its index.
     *
     * @param index The index of the column to retrieve.
     * @return The column with the specified index, or null if not found.
     */
    private Column getColumn(int index) {

        return columns.stream().filter(col -> col.index == index).findFirst().orElse(null);
    }

    /**
     * Moves the selected columns to a new target index.
     *
     * @param selectedIndices The indices of the selected columns to move.
     * @param targetIndex     The target index to move the columns to.
     * @param insertAfter     Whether to insert after the target index.
     */
    private void moveColumns(List<Integer> selectedIndices, final int targetIndex, boolean insertAfter) {
        if (selectedIndices.isEmpty()) return;

        // Resolve selected columns (preserve current order)
        List<Column> selectedColumns = IntStream.range(0, columns.size())
                .filter(selectedIndices::contains)
                .mapToObj(idx -> columns.get(idx))
                .toList();

        if (selectedColumns.isEmpty() || selectedIndices.size() != selectedColumns.size()) return;

        // Adjust target index for after-insert
        long leftShift = selectedIndices.stream()
                .filter(idx -> idx < targetIndex)
                .count();
        leftShift = Math.clamp(leftShift, 0, selectedIndices.size());

        if (insertAfter) leftShift--;

        columns.removeAll(selectedColumns);

        // Clamp for safety
        int insertIndex = Math.clamp(targetIndex - leftShift, 0, columns.size());

        // Insert
        columns.addAll(insertIndex, selectedColumns);

        canvasRedrawDebouncer.play();
    }

    /**
     * Redraws the visible portion of the colormap on the canvas.
     * The underlying image can be very large when zoomed in (for a 256x256 texture at 16x scale, it's 4096x4096 pixels),
     * adding a zoom factor would exceed maximum texture size on many GPUs.
     * Therefore, we only write the visible pixels into the visible potion of the viewport.
     */
    private void redraw() {

        GraphicsContext graphicsContext = canvas.getGraphicsContext2D();
        graphicsContext.setImageSmoothing(false);

        int texWidth = getVisibleColumnCount();
        int texHeight = rowCount;

        Bounds viewport = canvasScroll.getViewportBounds();
        int viewportWidth = (int) viewport.getWidth();
        int viewportHeight = (int) viewport.getHeight();

        double scale = snap(zoomFactor.get(), pixelScaleX);
        canvasPane.setPrefWidth(zoomFactor.get() * texWidth);
        canvasPane.setPrefHeight(zoomFactor.get() * texHeight);

        // How many source pixels fit in the viewport
        int nbCellsWidth = (int) Math.ceil(viewportWidth / scale);
        int nbCellsHeight = (int) Math.ceil(viewportHeight / scale);

        // Account for zoomed out views larger than the image
        nbCellsWidth = Math.min(nbCellsWidth, texWidth);
        nbCellsHeight = Math.min(nbCellsHeight, texHeight);

        // Scrollable range in source pixels (image coordinates)
        int texScrollRangeX = texWidth - nbCellsWidth;
        int texScrollRangeY = texHeight - nbCellsHeight;

        // Compute the scroll offset in image coordinates
        int texCoordsScrollOffsetX = (int) Math.round(canvasScroll.getHvalue() * texScrollRangeX);
        int texCoordsScrollOffsetY = (int) Math.round(canvasScroll.getVvalue() * texScrollRangeY);

        texCoordsScrollOffsetX = Math.clamp(texCoordsScrollOffsetX, 0, texScrollRangeX);
        texCoordsScrollOffsetY = Math.clamp(texCoordsScrollOffsetY, 0, texScrollRangeY);

        GridBuffer yBuffer = computeBufferBeforeAfter(texCoordsScrollOffsetY, nbCellsHeight, texHeight);
        GridBuffer xBuffer = computeBufferBeforeAfter(texCoordsScrollOffsetX, nbCellsWidth, texWidth);

        // Set canvas size to viewport + buffer
        canvas.setWidth(nbCellsWidth * scale + xBuffer.total() * scale);
        canvas.setHeight(nbCellsHeight * scale + yBuffer.total() * scale);

        // Pixel array reading offset
        var offset = (texCoordsScrollOffsetY - yBuffer.before) * texWidth + (texCoordsScrollOffsetX - xBuffer.before);

        // Write only visible pixels and extra buffer pixels into viewport
        pixelWriter = image.getPixelWriter();
        pixelWriter.setPixels(
                0,
                0,
                nbCellsWidth + xBuffer.total(),
                nbCellsHeight + yBuffer.total(),
                PixelFormat.getIntArgbInstance(),
                flattenColumns(texWidth),
                offset,
                texWidth
        );

        // Clear canvas
        graphicsContext.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Offset canvas from the top-left of the pane to align drawn image to the visible area
        double drawOffsetX = snap((texCoordsScrollOffsetX - xBuffer.before) * scale, pixelScaleX);
        double drawOffsetY = snap((texCoordsScrollOffsetY - yBuffer.before) * scale, pixelScaleY);

        canvas.setLayoutX(drawOffsetX);
        canvas.setLayoutY(drawOffsetY);

        // Calculate drawing area size
        double drawingAreaWidth = snap(nbCellsWidth + xBuffer.total(), pixelScaleX) * scale;
        double drawingAreaHeight = snap(nbCellsHeight + yBuffer.total(), pixelScaleY) * scale;

        // Draw scaled pixels
        graphicsContext.drawImage(
                image,
                0,
                0,
                nbCellsWidth + xBuffer.total(),
                nbCellsHeight + yBuffer.total(),
                0,
                0,
                drawingAreaWidth,
                drawingAreaHeight
        );
    }

    /**
     * Computes the number of buffered rows/columns before and after the visible area.
     *
     * @param firstVisibleRow The index of the first visible row/column.
     * @param nbCells         The number of visible rows/columns.
     * @param textSize        The total number of rows/columns in the texture.
     * @return A GridBuffer containing the number of buffered rows/columns before and after.
     */
    private GridBuffer computeBufferBeforeAfter(int firstVisibleRow, int nbCells, int textSize) {
        // First and last visible rows
        int lastVisibleRow = firstVisibleRow + nbCells;

        // How much space exists before and after
        int availableRowsAfter = Math.clamp(textSize - 1 - lastVisibleRow, 0, textSize);

        // Clamp buffer
        int numberOfBufferedRowsBefore = Math.min(ColormapController.CANVAS_BUFFER, firstVisibleRow);
        int numberOfBufferedRowsAfter = Math.min(ColormapController.CANVAS_BUFFER, availableRowsAfter);

        return new GridBuffer(numberOfBufferedRowsBefore, numberOfBufferedRowsAfter);
    }

    /**
     * Flattens the columnar color data into a single row-major array.
     *
     * @return A flattened array of color data.
     */
    private int[] flattenColumns(int visibleColumnCount) {

        // Allocate a 2D array flattened row-major:
        int[] buf = new int[visibleColumnCount * rowCount];

        int visibleIndex = 0;
        for (Column column : columns) {

            if (!column.visible.get()) continue;

            int[] col = column.colorData();

            for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
                // guard if a column has fewer rows than rowCount
                int value = rowIdx < col.length ? col[rowIdx] : 0;
                buf[rowIdx * visibleColumnCount + visibleIndex] = value;
            }

            visibleIndex++;
        }

        return buf;
    }

    /**
     * Resets the zoom factor to fit the entire colormap width within the viewport.
     */
    @FXML
    public void onResetZoom() {
        computeZoomBounds();
    }

    /**
     * Resets the zoom factor to fit the entire colormap width within the viewport.
     */
    private void computeZoomBounds() {
        computeZoomBounds(false, true);
    }

    /**
     * Resets the zoom factor to fit the entire colormap width within the viewport.
     */
    private void computeZoomBounds(boolean fitWidth, boolean fitHeight) {

        var viewport = canvasScroll.getViewportBounds();
        double viewportWidth = viewport.getWidth();
        double viewportHeight = viewport.getHeight();

        // Account for index column
        int columns = getVisibleColumnCount() + 1;
        int rows = rowCount;

        // Always keep slider bounds stable
        zoomSlider.setMin(MIN_PIXEL_SIZE);
        zoomSlider.setMax(MAX_PIXEL_SIZE);

        double zoom;

        if (fitWidth) {
            double pixelSizeForWidth = viewportWidth / columns;
            zoom = Math.clamp(pixelSizeForWidth, MIN_PIXEL_SIZE, MAX_PIXEL_SIZE);

        } else if (fitHeight) {
            double pixelSizeForHeight = viewportHeight / rows;
            zoom = Math.clamp(pixelSizeForHeight, MIN_PIXEL_SIZE, MAX_PIXEL_SIZE);

        } else {
            // 50% zoom = midpoint
            zoom = (MIN_PIXEL_SIZE + MAX_PIXEL_SIZE) / 2.0;
        }

        zoomFactor.set(zoom);
        canvasRedrawDebouncer.play();
    }

    /* GUI Buttons handlers */

    /**
     * Counts the number of visible columns.
     *
     * @return The count of visible columns.
     */
    private int getVisibleColumnCount() {

        return Math.toIntExact(columns.stream()
                .filter(column -> column.visible.get())
                .count());
    }

    /**
     * Fits the columns to the view by adjusting the zoom factor.
     */
    @FXML
    public void onFitColumnsToView() {
        computeZoomBounds(true, false);
    }

    /**
     * Checks if there are any unsaved changes in the colormap.
     *
     * @return true if there are unsaved changes, false otherwise.
     */
    public boolean hasUnsavedChanges() {

        boolean unsavedChanges = false;

        if (columns != null) unsavedChanges = columns.stream().anyMatch(column -> column.modified.get());

        return unsavedChanges;
    }

    /**
     * Resets all modified columns to their original color data.
     */
    public void resetChanges() {

        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {

            Column column = columns.get(colIndex);

            if (column.modified.get()) {

                columns.set(colIndex, new Column(column.index,
                        column.identifier,
                        column.originalColorData(),
                        column.originalColorData(),
                        column.selected,
                        column.visible,
                        new SimpleBooleanProperty(false)));
            }
        }
        canvasRedrawDebouncer.play();
    }

    /**
     * Retrieves all color changes made to the colormap.
     *
     * @return A map of resource identifiers to their modified color data.
     */
    public Map<ResourceIdentifier, int[]> getAllColorChanges() {

        Map<ResourceIdentifier, int[]> changes = new HashMap<>();

        columns.stream()
                .filter(column -> column.modified.get())
                .forEach(column -> changes.put(column.identifier, column.colorData()));

        return changes;
    }

    /**
     * Gets the resource identifier of the displayed colormap.
     *
     * @return The resource identifier of the displayed colormap.
     */
    public ResourceIdentifier getDisplayedResourceIdentifier() {
        return displayedResourceIdentifier;
    }

    /**
     * Gets the type of resource displayed in the colormap.
     *
     * @return The displayed resource type.
     */
    public DisplayedResourceType getDisplayedResourceType() {
        return displayedResourceType;
    }

    /**
     * Persists all color changes made to the colormap by updating the original color data.
     */
    public void persistColorChanges() {

        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {

            Column column = columns.get(colIndex);

            if (column.modified.get()) {

                columns.set(colIndex, new Column(column.index,
                        column.identifier,
                        column.colorData(),
                        column.colorData(),
                        column.selected,
                        column.visible,
                        new SimpleBooleanProperty(false)));
            }
        }
        Platform.runLater(this::refreshHeaders);
        canvasRedrawDebouncer.play();
    }

    /**
     * Sets the visibility of the colormap view.
     *
     * @param visible true to make the colormap visible, false to hide it.
     */
    public void setVisible(boolean visible) {
        editorRoot.setVisible(visible);
    }

    /* Data elements */

    /**
     * Enum representing the type of resource displayed in the colormap.
     */
    public enum DisplayedResourceType {
        BIOME_MAPPED_COLORMAP,
        COLORMAP
    }

    /**
     * Simple record to represent a colormap column
     *
     * @param index             Biome index or Modifier index
     * @param identifier        Resource identifier
     * @param originalColorData initial color data
     * @param colorData         displayed color data
     */
    public record Column(int index, ResourceIdentifier identifier, int[] originalColorData, int[] colorData,
                         BooleanProperty selected, BooleanProperty visible, BooleanProperty modified) {
    }

    /**
     * Simple record to represent number of rows to be added before and after visible area
     *
     * @param before number of rows before
     * @param after  number of rows after
     */
    private record GridBuffer(int before, int after) {
        int total() {
            return before + after;
        }
    }
}