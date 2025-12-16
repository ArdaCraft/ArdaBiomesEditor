package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.ResourceIdentifier;
import com.duom.ardabiomeseditor.services.I18nService;
import com.duom.ardabiomeseditor.services.ResourcePackService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;

/**
 * Main UI controller - orchestrates the various sub-controllers and manages user interactions.
 */
public class ArdaBiomesController {

    private final ResourcePackService resourcePackService = new ResourcePackService();

    /* UI Elements */

    @FXML private StackPane rootPane;
    @FXML private ColormapController colormapController;
    @FXML private VBox progressOverlay;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private ResourceSelectorController resourceSelectorController;
    @FXML private FileManagementController fileManagementController;

    /**
     * Initializes the controller and sets up the GUI components.
     */
    @FXML
    public void initialize() {
        ArdaBiomesEditor.LOGGER.info("Initializing controller GUI components");
        initializeSubControllers();

        loadStyleSheets();
    }

    /**
     * Loads the CSS stylesheets for the application.
     */
    private void loadStyleSheets() {
        var cssResource = getClass().getResource("/css/ardabiomes-editor.css");

        if (cssResource != null)
            rootPane.getStylesheets().add(cssResource.toExternalForm());
    }

    /**
     * Initializes the sub-controllers and sets up their dependencies and callbacks.
     */
    private void initializeSubControllers() {

        resourceSelectorController.setResourcePackService(resourcePackService);
        resourceSelectorController.setBiomeMappingSelectionChangedCallback(this::biomeSelectionChanged);
        resourceSelectorController.setColormapSelectionChangedCallback(this::colormapSelectionChanged);
        resourceSelectorController.setDefaultSelectionChangedCallback(this::defaultSelection);

        fileManagementController.setResourcePackService(resourcePackService);
        fileManagementController.setOnFileLoadedCallback(refreshList -> {
            if (!refreshList) resourceSelectorController.reload();
        });

        fileManagementController.setSaveCallback(this::saveBiomeEdits);
        fileManagementController.setMenuExitCallback(this::onExitApplication);
        fileManagementController.setResourcePackLoadCallback(this::clearUiOnResourcePackLoad);
    }

    /**
     * Saves the current biome edits and executes a callback upon success.
     *
     * @param onSuccess The callback to execute after successfully saving the edits.
     */
    private void saveBiomeEdits(Runnable onSuccess) {

        ArdaBiomesEditor.LOGGER.info("Persisting biome edits");

        Map<ResourceIdentifier, int[]> colorChanges = colormapController.getAllColorChanges();
        ResourceIdentifier currentSelection = colormapController.getDisplayedResourceIdentifier();
        boolean updateBiome = colormapController.getDisplayedResourceType() == ColormapController.DisplayedResourceType.BIOME_MAPPED_COLORMAP;

        if (colorChanges.isEmpty()) return;

        Task<Void> saveTask = createSaveTask(currentSelection, colorChanges, updateBiome);

        progressBar.progressProperty().bind(saveTask.progressProperty());
        progressLabel.textProperty().bind(saveTask.messageProperty());

        saveTask.setOnRunning(e -> progressOverlay.setVisible(true));
        saveTask.setOnSucceeded(e -> handleSaveSuccess(onSuccess));
        saveTask.setOnFailed(e -> handleSaveFailure(saveTask));

        new Thread(saveTask).start();
    }

    /**
     * Creates a task for saving colormaps edits.
     *
     * @param root               The root resource identifier. Can be a pointer to a mapped biome or modifier.
     * @param colorChanges       A map of color changes to persist.
     * @param biomeMappedChanges Indicates if the changes are biome-mapped - ie edits to multiple modifiers or single modifier.
     * @return A task that performs the save operation.
     */
    private Task<Void> createSaveTask(ResourceIdentifier root, Map<ResourceIdentifier, int[]> colorChanges, boolean biomeMappedChanges) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage(I18nService.get("ardabiomeseditor.save.step.persisting",
                        resourcePackService.getCurrentResourcePackPath()));
                updateProgress(0, 100);

                Thread.sleep(10);

                updateMessage(I18nService.get("ardabiomeseditor.save.step.updating_maps"));
                updateProgress(5, 100);

                resourcePackService.persistColorChanges(
                        root,
                        colorChanges,
                        biomeMappedChanges,
                        (message, progress) -> {
                            updateMessage(message);
                            updateProgress(5 + (progress * 0.85), 100);
                        }
                );

                updateProgress(95, 100);
                updateMessage(I18nService.get("ardabiomeseditor.save.step.refresh_rp"));

                colormapController.persistColorChanges();

                updateProgress(100, 100);
                updateMessage(I18nService.get("ardabiomeseditor.save.step.complete"));

                return null;
            }
        };
    }

    /**
     * Handles the successful completion of a save operation.
     *
     * @param onSuccess The callback to execute after a successful save.
     */
    private void handleSaveSuccess(Runnable onSuccess) {
        ArdaBiomesEditor.LOGGER.info("Successfully persisted edits");

        fileManagementController.loadResourcePack(resourcePackService.getCurrentResourcePackPath(), true);
        progressOverlay.setVisible(false);
        colormapController.persistColorChanges();

        if (onSuccess != null) onSuccess.run();
    }

    /**
     * Handles a failure during the save operation.
     *
     * @param saveTask The task that encountered the failure.
     */
    private void handleSaveFailure(Task<Void> saveTask) {

        ArdaBiomesEditor.LOGGER.error("Error while persisting edits", saveTask.getException());
        progressOverlay.setVisible(false);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18nService.get("ardabiomeseditor.filmanagement.error.rp_saving_error_title"));
        alert.setHeaderText(I18nService.get("ardabiomeseditor.filmanagement.error.rp_saving_error_title"));
        alert.setContentText(saveTask.getException().getMessage());

        alert.getButtonTypes().setAll(new ButtonType(I18nService.get("ardabiomeseditor.generic.ok")));

        alert.showAndWait();
    }

    /**
     * Handles changes in biome selection.
     *
     * @param previousSelection The previous biome selection.
     */
    private void biomeSelectionChanged(ResourceIdentifier previousSelection) {

        var currentSelection = resourceSelectorController.getTreeSelection();
        configureColormap(previousSelection, () -> loadBiomeColormap(currentSelection));
    }

    /**
     * Handles changes in colormap selection.
     *
     * @param previousSelection the previous colormap selection.
     */
    private void colormapSelectionChanged(ResourceIdentifier previousSelection) {

        var currentSelection = resourceSelectorController.getTreeSelection();
        configureColormap(previousSelection, () -> loadColormap(currentSelection));
    }

    /**
     * Handles selection for an unsupported resource type.
     *
     * @param previousSelection the previous resource selection.
     */
    private void defaultSelection(ResourceIdentifier previousSelection) {

        if (previousSelection != null && colormapController.hasUnsavedChanges()) {

            showUnsavedChangesDialog(() -> {
                        colormapController.setVisible(false);
                    },
                    () -> {
                        colormapController.resetChanges();
                        colormapController.setVisible(false);
                    },
                    () -> {
                        resourceSelectorController.revertSelection(previousSelection);
                    });

        } else {

            colormapController.setVisible(false);
        }
    }


    /**
     * Configures the colormap controller based on the current selection.
     * Prompts the user to handle unsaved changes if necessary.
     *
     * @param previousSelection      The previous resource identifier selection.
     * @param onSwitchToNewSelection The callback to execute when switching to the new selection.
     */
    private void configureColormap(ResourceIdentifier previousSelection, Runnable onSwitchToNewSelection) {

        colormapController.setVisible(true);

        if (previousSelection != null && colormapController.hasUnsavedChanges()) {

            showUnsavedChangesDialog(onSwitchToNewSelection,
                    () -> {
                        colormapController.resetChanges();
                        onSwitchToNewSelection.run();
                    },
                    () -> {
                        resourceSelectorController.revertSelection(previousSelection);
                    });

        } else {

            onSwitchToNewSelection.run();
        }
    }


    /**
     * Loads color mappings for the specified biome identifier and updates the UI.
     * This method lists all the modifiers in the identifier's namespace that have colormaps and loads their mapped colors.
     *
     * @param identifier The resource identifier of the biome (namespace, biome_id_mapper name and biome index).
     */
    private void loadBiomeColormap(ResourceIdentifier identifier) {

        if (identifier == null) return;

        ArdaBiomesEditor.LOGGER.info("Loading mapped colors Biome {} in namespace {}", identifier, identifier.namespace());
        colormapController.configure(identifier,
                ColormapController.DisplayedResourceType.BIOME_MAPPED_COLORMAP,
                resourcePackService.getMappedColorsFromBiome(identifier));
    }

    /**
     * Loads color mappings for the specified modifier identifier and updates the UI.
     * This method loads all the colors mapped by the modifier's colormaps.
     *
     * @param identifier The resource identifier of the colormap (namespace and colormap name).
     */
    private void loadColormap(ResourceIdentifier identifier) {

        if (identifier == null) return;

        ArdaBiomesEditor.LOGGER.info("Loading mapped colors for Colormap {} in {}", identifier, identifier.namespace());
        colormapController.configure(identifier,
                ColormapController.DisplayedResourceType.COLORMAP,
                resourcePackService.getColormapColors(identifier));
    }

    /**
     * Displays a dialog to handle unsaved changes.
     * Provides options to save, reset, or cancel the operation.
     *
     * @param onSave   The callback to execute if the user chooses to save.
     * @param onReset  The callback to execute if the user chooses to reset.
     * @param onCancel The callback to execute if the user cancels the operation.
     */
    private void showUnsavedChangesDialog(Runnable onSave, Runnable onReset, Runnable onCancel) {

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18nService.get("ardabiomeseditor.biometableview.alert.title.unsaved_changes"));
        alert.setHeaderText(I18nService.get("ardabiomeseditor.biometableview.alert.header.unsaved_changes"));
        alert.setContentText(I18nService.get("ardabiomeseditor.biometableview.alert.content.unsaved_changes"));

        ButtonType saveButton = new ButtonType(I18nService.get("ardabiomeseditor.generic.save"));
        ButtonType resetButton = new ButtonType(I18nService.get("ardabiomeseditor.generic.reset"));
        ButtonType cancelButton = new ButtonType(I18nService.get("ardabiomeseditor.generic.cancel"));

        alert.getButtonTypes().setAll(saveButton, resetButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == saveButton) saveBiomeEdits(onSave);
            else if (response == resetButton) onReset.run();
            else onCancel.run();
        });
    }

    /**
     * Clears the UI components when a new resource pack is loaded.
     *
     * @param filePath the path of the loaded resource pack.
     */
    private void clearUiOnResourcePackLoad(String filePath) {

        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setTitle("ArdaBiomes Editor - " + filePath);

        resourceSelectorController.resetSelection();
        colormapController.setVisible(false);
    }

    /**
     * Handles the application exit process.
     * Prompts the user to save unsaved changes before exiting.
     *
     * @param exitAction The callback to execute for exiting the application.
     */
    public void onExitApplication(Runnable exitAction) {

        if (colormapController.hasUnsavedChanges()) {

            showUnsavedChangesDialog(
                    () -> {
                        saveBiomeEdits(exitAction);
                    },
                    () -> {
                        colormapController.resetChanges();
                        exitAction.run();
                    },
                    () -> {
                    }
            );

        } else {

            Platform.exit();
        }
    }
}