package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.Modifier;
import com.duom.ardabiomeseditor.services.GuiResourceService;
import com.duom.ardabiomeseditor.services.I18nService;
import com.duom.ardabiomeseditor.services.ModifierService;
import com.duom.ardabiomeseditor.services.ResourcePackService;
import com.duom.ardabiomeseditor.ui.views.BiomeTableView;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Controller for managing biome selection in the UI.
 * Handles interactions with the biome list and updates the biome table view accordingly.
 */
public class BiomeSelectorController {

    @FXML private ListView<String> biomeList;
    private MenuItem sortMenuItem;

    private String previousSelection = null;
    private BiomeTableView biomeTableView;
    private ResourcePackService resourcePackService;
    private Consumer<String> selectionChangedCallback;

    private enum BiomeSortType {
        ALPHABETICAL,
        BY_ID
    }

    /**
     * Initializes the controller. Sets up the listener for biome selection changes.
     */
    @FXML
    public void initialize() {

        initializeContextMenu();

        biomeList.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
            if (event.isSecondaryButtonDown()) {
                event.consume();
            }
        });

        biomeList.getSelectionModel().selectedItemProperty().addListener(this::handleBiomeSelection);
        biomeList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {

                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                } else if (item.contains(":")){

                    var itemName = Arrays.stream(item.split(":")[1].split("_"))
                            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                            .collect(Collectors.joining(" "));
                    setText(itemName);
                } else
                    setText(item);
            }
        });
    }

    private void initializeContextMenu(){

        var contextMenu = new ContextMenu();
        sortMenuItem = new MenuItem(I18nService.get("ardabiomeseditor.biome.list.sort.alphabetically"));
        sortMenuItem.setUserData(BiomeSortType.BY_ID);

        sortMenuItem.setGraphic(GuiResourceService.getIcon(GuiResourceService.IconType.SORT));
        sortMenuItem.setOnAction(e -> {

            BiomeSortType sortType = sortMenuItem.getUserData() != null ? (BiomeSortType) sortMenuItem.getUserData() : BiomeSortType.BY_ID;

            if (sortType == BiomeSortType.BY_ID) {
                sortMenuItem.setText(I18nService.get("ardabiomeseditor.biome.list.sort.alphabetically"));
                sortMenuItem.setUserData(BiomeSortType.ALPHABETICAL);
            } else {
                sortMenuItem.setText(I18nService.get("ardabiomeseditor.biome.list.sort.by.id"));
                sortMenuItem.setUserData(BiomeSortType.BY_ID);
            }

            loadBiomes();
        });

        contextMenu.getItems().add(sortMenuItem);
        biomeList.setContextMenu(contextMenu);
    }

    /**
     * Resets the reference to the biome table view.
     * @param biomeTableView The new biome table view to associate with this controller.
     */
    public void resetBiomeTableView(BiomeTableView biomeTableView) {
        this.biomeTableView = biomeTableView;
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
    public void setSetSelectionChangedCallback(Consumer<String> callback) {
        this.selectionChangedCallback = callback;
    }

    /**
     * Loads the list of biomes into the ListView.
     * Retrieves biome names from the resource pack service and sorts them alphabetically.
     */
    public void loadBiomes() {

        var selectedItem = biomeList.getSelectionModel().getSelectedItem();

        var biomeMappings = resourcePackService.getBiomes().getBiomeMapping();
        var sortType = sortMenuItem.getUserData() != null ? (BiomeSortType) sortMenuItem.getUserData() : BiomeSortType.BY_ID;

        if (sortType == BiomeSortType.BY_ID) {
            biomeList.setItems(FXCollections.observableArrayList(
                    biomeMappings.entrySet().stream()
                            .sorted(Comparator.comparingInt(Map.Entry::getValue))
                            .map(Map.Entry::getKey)
                            .toList()
            ));
        } else {

            biomeList.setItems(FXCollections.observableArrayList(
                    biomeMappings.entrySet().stream()
                            .sorted(Comparator.comparing(Map.Entry::getKey))
                            .map(Map.Entry::getKey)
                            .toList()
            ));
        }

        if (selectedItem != null) {
            biomeList.getSelectionModel().select(selectedItem);
        }
    }

    /**
     * Handles biome selection changes in the ListView.
     * Logs the selection event, checks if the selection has changed, and triggers the callback if necessary.
     * @param observable The observable value representing the selection.
     * @param oldValue The previously selected biome.
     * @param newValue The newly selected biome.
     */
    private void handleBiomeSelection(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        ArdaBiomesEditor.LOGGER.info("Biome selection event old:[{}] new:[{}]", oldValue, newValue);

        if (newValue == null || newValue.equals(previousSelection)) return;

        var biomeKey = resourcePackService.getBiomes().getBiomeMapping().getOrDefault(newValue, 0);

        if (biomeKey != biomeTableView.getBiomeKey())
            selectionChangedCallback.accept(previousSelection);
    }

    public void confirmSelectionChange() {
        previousSelection = biomeList.getSelectionModel().getSelectedItem();
    }

    public void revertSelection() {
        biomeList.getSelectionModel().selectedItemProperty().removeListener(this::handleBiomeSelection);
        biomeList.getSelectionModel().select(previousSelection);
        biomeList.getSelectionModel().selectedItemProperty().addListener(this::handleBiomeSelection);
    }

    public void loadNewBiomeData(String newBiomeKey){

        var biomeKey = resourcePackService.getBiomes().getBiomeMapping().getOrDefault(newBiomeKey, 0);
        loadNewBiomeData(biomeKey);
    }

    /**
     * Loads data for the newly selected biome and updates the biome table view.
     * @param biomeId The id of the selected biome.
     */
    private void loadNewBiomeData(int biomeId) {
        ArdaBiomesEditor.LOGGER.info("Loading biome for key {}", biomeId);

        Map<String, List<String>> colorMappings = new HashMap<>();

        for (Modifier modifier : resourcePackService.getColorModifiers().values()) {
            if (modifier.getModifier() != null) {
                colorMappings.put(modifier.getName(), ModifierService.getColorsForBiome(modifier, biomeId));
            }
        }

        biomeTableView.configure(biomeId, colorMappings);
    }

    /**
     * Resets the current selection in the ListView.
     */
    public void resetSelection() {
        previousSelection = null;
        biomeList.getSelectionModel().clearSelection();
    }

    /**
     * Retrieves the currently selected biome from the ListView.
     * @return The name of the currently selected biome.
     */
    public String getCurrentSelectedBiome() {
        return biomeList.getSelectionModel().getSelectedItem();
    }
}