package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.Modifier;
import com.duom.ardabiomeseditor.services.ModifierService;
import com.duom.ardabiomeseditor.services.ResourcePackService;
import com.duom.ardabiomeseditor.ui.views.BiomeTableView;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Controller for managing biome selection in the UI.
 * Handles interactions with the biome list and updates the biome table view accordingly.
 */
public class BiomeSelectorController {

    @FXML private ListView<String> biomeList;

    private BiomeTableView biomeTableView;
    private ResourcePackService resourcePackService;
    private Consumer<Runnable> selectionChangedCallback;

    /**
     * Initializes the controller. Sets up the listener for biome selection changes.
     */
    @FXML
    public void initialize() {

        biomeList.getSelectionModel().selectedItemProperty().addListener(this::handleBiomeSelection);
        biomeList.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
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
    public void setSetSelectionChangedCallback(Consumer<Runnable> callback) {
        this.selectionChangedCallback = callback;
    }

    /**
     * Loads the list of biomes into the ListView.
     * Retrieves biome names from the resource pack service and sorts them alphabetically.
     */
    public void loadBiomes() {
        biomeList.setItems(FXCollections.observableArrayList(
                resourcePackService.getBiomes().getBiomeMapping().keySet().stream().sorted().toList()
        ));
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

        if (newValue == null || newValue.equals(oldValue)) return;

        var biomeKey = resourcePackService.getBiomes().getBiomeMapping().getOrDefault(newValue, 0);

        if (biomeKey != biomeTableView.getBiomeKey()) {
            selectionChangedCallback.accept(() -> {

                loadNewBiomeData(biomeKey);
            });
        }
    }

    /**
     * Loads data for the newly selected biome and updates the biome table view.
     * @param biomeKey The key of the selected biome.
     */
    private void loadNewBiomeData(int biomeKey) {
        ArdaBiomesEditor.LOGGER.info("Loading biome for key {}", biomeKey);

        Map<String, List<String>> colorMappings = new HashMap<>();

        for (Modifier modifier : resourcePackService.getBlockModifiers().values()) {
            if (modifier.getModifier() != null) {
                colorMappings.put(modifier.getName(), ModifierService.getColorsForBiome(modifier, biomeKey));
            }
        }

        biomeTableView.configure(biomeKey, colorMappings);
    }

    /**
     * Resets the biome table view to reflect the current biome selection.
     * If a biome is currently selected, it re-selects it in the ListView.
     */
    public void resetBiomeTableView() {
        var currentBiome = biomeList.getSelectionModel().getSelectedItem();
        if (currentBiome != null) biomeList.getSelectionModel().select(currentBiome);
    }
}