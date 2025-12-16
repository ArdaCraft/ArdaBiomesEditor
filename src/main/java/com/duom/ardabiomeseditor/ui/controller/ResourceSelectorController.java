package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.ResourceIdentifier;
import com.duom.ardabiomeseditor.model.ResourcePackTreeNode;
import com.duom.ardabiomeseditor.services.I18nService;
import com.duom.ardabiomeseditor.services.IconResourceService;
import com.duom.ardabiomeseditor.services.ResourcePackService;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Controller for managing biome selection in the UI.
 * Handles interactions with the biome list and updates the biome table view accordingly.
 */
public class ResourceSelectorController {

    /* UI Elements */

    @FXML private TreeView<ResourcePackTreeNode.Data> resourcePackTreeview;
    @FXML private ComboBox<TreeResourceType> resourceSelectionCombo;

    /**
     * Context menu display preferences for the treeview (sorting / naming).
     */
    private UserDisplayPreferences treeDisplayPreferences;

    private ResourcePackService resourcePackService;

    /* Callbacks */

    private Consumer<ResourceIdentifier> biomeSelectionChangedCallback;
    private Consumer<ResourceIdentifier> colormapSelectionChangedCallback;
    private Consumer<ResourceIdentifier> defaultSelectionChangedCallback;

    private ChangeListener<TreeItem<ResourcePackTreeNode.Data>> treeSelectionListener;

    /**
     * Initializes the controller. Sets up the listener for biome selection changes.
     */
    @FXML
    public void initialize() {

        treeDisplayPreferences = new UserDisplayPreferences(TreeSortType.BY_ID, false, true);

        resourceSelectionCombo.setItems(FXCollections.observableArrayList(TreeResourceType.values()));
        resourceSelectionCombo.getSelectionModel().selectFirst();
        resourceSelectionCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {

            switch (newVal) {
                case ALL             -> {showAllResourcesTree();}
                case BIOME_ID_MAPPER -> {showBiomeIdMappersTree();}
                case COLORMAP        -> {showColormapsTree();}
            }
        });

        initializeContextMenu(resourcePackTreeview, treeDisplayPreferences);

        treeSelectionListener = this::handleTreeSelection;

        resourcePackTreeview.getSelectionModel().selectedItemProperty().addListener(treeSelectionListener);
        resourcePackTreeview.setCellFactory(this::initializeTreeCellFactory);
    }

    /**
     * Initializes the tree cell factory for the given TreeView.
     * Sets up how each cell in the tree is displayed based on the resource type.
     * @param treeView The TreeView to initialize the cell factory for.
     * @return A TreeCell configured for displaying resource pack tree nodes.
     */
    private TreeCell<ResourcePackTreeNode.Data> initializeTreeCellFactory(TreeView<ResourcePackTreeNode.Data> treeView){

        return new TreeCell<>() {
            @Override
            protected void updateItem(ResourcePackTreeNode.Data item, boolean empty) {

                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);

                if (!empty && item != null) {

                    TreeItem<ResourcePackTreeNode.Data> treeItem = getTreeItem();
                    boolean isExpanded = treeItem != null && treeItem.isExpanded();

                    switch (item.type()) {
                        case DIRECTORY          -> setGraphic(isExpanded ? IconResourceService.getColoredIcon(IconResourceService.IconType.FOLDER_OPEN) : IconResourceService.getColoredIcon(IconResourceService.IconType.FOLDER));
                        case BIOME_ID_MAPPER    -> setGraphic(IconResourceService.getIcon(IconResourceService.IconType.BIOME_ID_MAPPER));
                        case COLORMAP           -> setGraphic(IconResourceService.getIcon(IconResourceService.IconType.COLORMAP));
                        case MODIFIER           -> setGraphic(IconResourceService.getIcon(IconResourceService.IconType.MODIFIER));
                    }

                    if (item.resourceIdentifier() != null)

                        setText(treeDisplayPreferences.showFormatedNames ? item.resourceIdentifier().toString() : item.resourceIdentifier().namespace().toString());
                    else
                        setText(item.name());
                }
            }
        };
    }

    /**
     * Initializes the context menu for the biome list view.
     * Sets up sorting and display options for the biome items.
     */
    private void initializeContextMenu(TreeView<ResourcePackTreeNode.Data> treeView, UserDisplayPreferences userPreferences)  {

        var contextMenu = new ContextMenu();
        MenuItem sortMenuItem = new MenuItem(I18nService.get("ardabiomeseditor.biome.list.sort.alphabetically"));

        sortMenuItem.setGraphic(IconResourceService.getIcon(IconResourceService.IconType.SORT));
        sortMenuItem.setOnAction(e -> {

            if (userPreferences.sortType == TreeSortType.BY_ID) {
                sortMenuItem.setText(I18nService.get("ardabiomeseditor.biome.list.sort.by.id"));
                userPreferences.sortType = TreeSortType.ALPHABETICAL;
            } else {
                sortMenuItem.setText(I18nService.get("ardabiomeseditor.biome.list.sort.alphabetically"));
                userPreferences.sortType = TreeSortType.BY_ID;
            }

            sortTreeView(treeView, userPreferences);
        });

        CheckMenuItem showFormatedNamesMenuItem = new CheckMenuItem(I18nService.get("ardabiomeseditor.biome.list.format.names"));
        showFormatedNamesMenuItem.setSelected(true);
        showFormatedNamesMenuItem.setOnAction(e -> {
            userPreferences.showFormatedNames = showFormatedNamesMenuItem.isSelected();
            treeView.refresh();
        });

        contextMenu.getItems().addAll(sortMenuItem, new SeparatorMenuItem(), showFormatedNamesMenuItem);
        treeView.setContextMenu(contextMenu);

        treeView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.isSecondaryButtonDown()) event.consume();
        });
    }

    /**
     * Sorts the given TreeView based on the user's display preferences.
     * @param treeView The TreeView to sort.
     * @param userPreferences The user's display preferences.
     */
    private void sortTreeView(TreeView<ResourcePackTreeNode.Data> treeView, UserDisplayPreferences userPreferences){

        TreeItem<ResourcePackTreeNode.Data> root = treeView.getRoot();

        if (root != null) {

            sortTreeItem(root, userPreferences.sortType);
            treeView.refresh();
        }
    }

    /**
     * Recursively sorts the given TreeItem and its children based on the specified sort type.
     * @param item The TreeItem to sort.
     * @param sortType The type of sorting to apply.
     */
    private void sortTreeItem(TreeItem<ResourcePackTreeNode.Data> item, TreeSortType sortType) {
        if (item.getChildren().isEmpty()) {
            return;
        }

        // Sort children based on sort type
        Comparator<TreeItem<ResourcePackTreeNode.Data>> comparator;

        if (sortType == TreeSortType.ALPHABETICAL) {
            comparator = Comparator.comparing(treeItem -> {

                ResourcePackTreeNode.Data data = treeItem.getValue();
                if (data.resourceIdentifier() != null) {
                    return data.resourceIdentifier().path();
                }
                return data.name();
            });
        } else {

            comparator = Comparator.comparing(treeItem -> {
                ResourcePackTreeNode.Data data = treeItem.getValue();
                if (data.resourceIdentifier() != null) {
                    return data.resourceIdentifier().index();
                }
                return 0;
            });
        }

        FXCollections.sort(item.getChildren(), comparator);

        // Recursively sort all children
        for (TreeItem<ResourcePackTreeNode.Data> child : item.getChildren()) {
            sortTreeItem(child, sortType);
        }
    }

    /**
     * Sets the resource pack service used to retrieve biome data.
     * @param resourcePackService The resource pack service to use.
     */
    public void setResourcePackService(ResourcePackService resourcePackService) {
        this.resourcePackService = resourcePackService;
    }

    /**
     * Sets the callback to be executed when the biome selection changes.
     * @param callback The callback to execute.
     */
    public void setBiomeMappingSelectionChangedCallback(Consumer<ResourceIdentifier> callback) {
        this.biomeSelectionChangedCallback = callback;
    }

    /**
     * Sets the callback to be executed when the modifier selection changes.
     * @param callback The callback to execute.
     */
    public void setColormapSelectionChangedCallback(Consumer<ResourceIdentifier> callback) {
        this.colormapSelectionChangedCallback = callback;
    }

    /**
     * Sets the callback to be executed when the default selection changes.
     * @param defaultSelectionChangedCallback The callback to execute.
     */
    public void setDefaultSelectionChangedCallback(Consumer<ResourceIdentifier> defaultSelectionChangedCallback) {
        this.defaultSelectionChangedCallback = defaultSelectionChangedCallback;
    }

    /**
     * Reloads the biome ID mappings from the resource pack service into the TreeView.
     */
    public void reload() {

        switch (resourceSelectionCombo.getSelectionModel().getSelectedItem()) {
            case ALL             -> {showAllResourcesTree();}
            case BIOME_ID_MAPPER -> {showBiomeIdMappersTree();}
            case COLORMAP        -> {showColormapsTree();}
        }
    }

    /**
     * Expands the TreeView to the specified item.
     * @param item The TreeItem to expand to.
     */
    private void expandTo(TreeItem<ResourcePackTreeNode.Data> item) {

        if (item == null) return;

        TreeItem<?> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
    }

    /**
     * Expands the TreeView to the specified number of levels.
     * @param levels The number of levels to expand.
     */
    private void expand(int levels, TreeItem<ResourcePackTreeNode.Data> root){

        if (levels <= 0 || root == null) return;

        root.setExpanded(true);
        for (TreeItem<ResourcePackTreeNode.Data> child : root.getChildren()) {
            expand(levels - 1, child);
        }
    }

    /**
     * Recursively searches for a TreeItem with the specified value in the tree.
     * @param root The root TreeItem to start the search from.
     * @param value The value to search for.
     * @return An Optional containing the found TreeItem, or empty if not found.
     */
    private Optional<TreeItem<ResourcePackTreeNode.Data>> findTreeItem(TreeItem<ResourcePackTreeNode.Data> root, ResourceIdentifier value) {

        if (Objects.equals(root.getValue().resourceIdentifier(), value)) {
            return Optional.of(root);
        }

        for (TreeItem<ResourcePackTreeNode.Data> child : root.getChildren()) {
            Optional<TreeItem<ResourcePackTreeNode.Data>> found = findTreeItem(child, value);
            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    private void showAllResourcesTree(){

        populateTree(resourcePackService.getResourceTree());
    }

    /**
     * Reloads the biome ID mappings into its TreeView.
     * Preserves the current selection if possible.
     */
    private void showBiomeIdMappersTree() {

        populateTree(resourcePackService.getBiomeIdMappersTree());
    }

    /**
     * Populates the TreeView with the given root node.
     * @param rootNode The root node to populate the TreeView with.
     */
    private void populateTree(ResourcePackTreeNode rootNode){

        ResourceIdentifier selectedValue = getTreeSelection(resourcePackTreeview);

        TreeItem<ResourcePackTreeNode.Data> root = ResourcePackTreeNode.toTreeItem(rootNode);
        sortTreeItem(root, treeDisplayPreferences.sortType);
        root.setExpanded(true);

        resourcePackTreeview.setRoot(root);

        if (selectedValue != null) {
            selectItem(selectedValue, resourcePackTreeview);
        } else {
            expand(2, root);
        }
    }

    /**
     * Selects the specified ResourceIdentifier in the TreeView.
     * @param selectedValue The ResourceIdentifier to select.
     * @param treeView The TreeView to select the item in.
     */
    private void selectItem(ResourceIdentifier selectedValue, TreeView< com.duom.ardabiomeseditor.model.ResourcePackTreeNode.Data> treeView) {

        if (selectedValue != null) {
            findTreeItem(treeView.getRoot(), selectedValue).ifPresent(item -> {
                expandTo(item);
                treeView.getSelectionModel().select(item);
            });
        }
    }

    /**
     * Retrieves the currently selected ResourceIdentifier from the TreeView.
     * @param treeView The TreeView to get the selection from.
     * @return The selected ResourceIdentifier, or null if none is selected.
     */
    private ResourceIdentifier getTreeSelection(TreeView< com.duom.ardabiomeseditor.model.ResourcePackTreeNode.Data> treeView) {

        var selectedItem = treeView.getSelectionModel().getSelectedItem();
        ResourceIdentifier selectedValue = null;

        if (selectedItem != null) {
            selectedValue = selectedItem.getValue().resourceIdentifier();
        }
        return selectedValue;
    }

    /**
     * Reloads the modifiers into its TreeView.
     * Preserves the current selection if possible.
     */
    private void showColormapsTree() {
        populateTree(resourcePackService.getColormapsTree());
    }

    /**
     * Handles selection changes in the modifier TreeView.
     * @param observable the observable value.
     * @param oldValue the old selected TreeItem.
     * @param newValue the new selected TreeItem.
     */
    private void handleTreeSelection(ObservableValue<? extends TreeItem<ResourcePackTreeNode.Data>> observable,
                                     TreeItem<ResourcePackTreeNode.Data> oldValue,
                                     TreeItem<ResourcePackTreeNode.Data> newValue) {

        if (newValue != null) {

            ResourceIdentifier previousSelection = null;
            if (oldValue != null) previousSelection = oldValue.getValue().resourceIdentifier();

            switch (newValue.getValue().type()) {
                case BIOME_MAPPING -> {
                    ArdaBiomesEditor.LOGGER.info("Biome Id Mapper selection event old:[{}] new:[{}]", oldValue, newValue);
                    biomeSelectionChangedCallback.accept(previousSelection);
                }
                case COLORMAP -> {
                    ArdaBiomesEditor.LOGGER.info("Colormap selection event old:[{}] new:[{}]", oldValue, newValue);
                    colormapSelectionChangedCallback.accept(previousSelection);
                }
                case null, default -> {
                    ArdaBiomesEditor.LOGGER.info("Default selection event old:[{}] new:[{}]", oldValue, newValue);
                    defaultSelectionChangedCallback.accept(previousSelection);
                }
            }
        }
    }

    /**
     * Reverts the selection in the ListView to the previous selection.
     * @param previousSelection The resource identifier of the previous selection.
     */
    public void revertSelection(ResourceIdentifier previousSelection) {

        // Remove listener to prevent triggering selection change callback
        resourcePackTreeview.getSelectionModel().selectedItemProperty().removeListener(treeSelectionListener);
        selectItem(previousSelection, resourcePackTreeview);
        resourcePackTreeview.getSelectionModel().selectedItemProperty().addListener(treeSelectionListener);
    }

    /**
     * Resets the current selection in the Treeview.
     */
    public void resetSelection() {

        resourcePackTreeview.getSelectionModel().clearSelection();
    }

    /**
     * Retrieves the currently selected ResourceIdentifier from the TreeView.
     * @return The selected ResourceIdentifier, or null if none is selected.
     */
    public ResourceIdentifier getTreeSelection() {

        ResourceIdentifier selection = null;
        TreeItem<ResourcePackTreeNode.Data> treeItem = resourcePackTreeview.getSelectionModel().getSelectedItem();

        if (treeItem != null) {
            selection = treeItem.getValue().resourceIdentifier();
        }

        return selection;
    }

    /**
     * Enumeration for colormap list sorting types.
     */
    private enum TreeSortType {
        ALPHABETICAL,
        BY_ID
    }

    private enum TreeResourceType {

        ALL("All"),
        BIOME_ID_MAPPER("Biome ID Mappers"),
        COLORMAP("Colormaps");

        private final String displayName;

        TreeResourceType(String name) {
            displayName = name;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /* Data elements */

    /**
     * Class to hold user display preferences for the biome and modifier lists.
     */
    private static class UserDisplayPreferences{

        protected TreeSortType sortType;
        protected boolean showNamespaces;
        protected boolean showFormatedNames;
        public UserDisplayPreferences(TreeSortType sortType, boolean showNamespaces, boolean showFormatedNames){
            this.sortType = sortType;
            this.showNamespaces = showNamespaces;
            this.showFormatedNames = showFormatedNames;
        }
    }
}