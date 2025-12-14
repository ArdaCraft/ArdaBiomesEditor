package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.services.*;
import com.duom.ardabiomeseditor.ui.views.BiomeTableView;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing the main application logic and coordinating interactions
 * between the UI components and the underlying services.
 */
public class ArdaBiomesController {

    @FXML private StackPane rootPane;
    @FXML private VBox biomeEditorContent;

    @FXML private Label biomeTitle;

    @FXML private BiomeTableView biomeTableView;
    @FXML private VBox progressOverlay;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button saveEditsButton;

    @FXML private VBox indexColumnContainer;

    @FXML private ColorEditorController colorEditorController;
    @FXML private BiomeSelectorController biomeSelectionController;
    @FXML private FileManagementController fileManagementController;

    private final ResourcePackService resourcePackService = new ResourcePackService();

    /**
     * Initializes the controller and sets up the GUI components.
     */
    @FXML
    public void initialize() {
        ArdaBiomesEditor.LOGGER.info("Initializing controller GUI components");
        initializeSubControllers();

        saveEditsButton.setGraphic(GuiResourceService.getIcon(GuiResourceService.IconType.SAVE));
    }

    /**
     * Initializes the sub-controllers and sets up their dependencies and callbacks.
     */
    private void initializeSubControllers() {

        colorEditorController.setBiomeTableView(biomeTableView);

        biomeTableView.setClickHandler((col, event) -> colorEditorController.handleTableClick(event));
        biomeTableView.setIndexDisplayContainer(indexColumnContainer);

        biomeSelectionController.resetBiomeTableView(biomeTableView);
        biomeSelectionController.setResourcePackService(resourcePackService);
        biomeSelectionController.setSetSelectionChangedCallback(this::biomeSelectionChanged);

        fileManagementController.setResourcePackService(resourcePackService);
        fileManagementController.setOnFileLoadedCallback(path -> biomeSelectionController.loadBiomes());
        fileManagementController.setShowAllCallback(biomeTableView::showAllColumns);
        fileManagementController.setSaveCallback(this::saveBiomeEdits);
        fileManagementController.setMenuExitCallback(this::onExitApplication);
        fileManagementController.setResourcePackLoadCallback(this::clearUiOnResourcePackLoad);
    }

    @FXML
    public void onSaveBiomeEdits(){
        saveBiomeEdits(()->{});
    }

    /**
     * Saves the current biome edits and executes a callback upon success.
     * @param onSuccess The callback to execute after successfully saving the edits.
     */
    private void saveBiomeEdits(Runnable onSuccess) {

        ArdaBiomesEditor.LOGGER.info("Persisting biome edits");

        Map<String, List<ColorData>> colorChanges = biomeTableView.getAllColorChanges();
        if (colorChanges.isEmpty()) return;

        Task<Void> saveTask = createSaveTask(colorChanges);

        progressBar.progressProperty().bind(saveTask.progressProperty());
        progressLabel.textProperty().bind(saveTask.messageProperty());

        saveTask.setOnRunning(e -> progressOverlay.setVisible(true));
        saveTask.setOnSucceeded(e -> handleSaveSuccess(onSuccess));
        saveTask.setOnFailed(e -> handleSaveFailure(saveTask));

        new Thread(saveTask).start();
    }

    /**
     * Creates a task for saving biome edits.
     * @param colorChanges A map of color changes to persist.
     * @return A task that performs the save operation.
     */
    private Task<Void> createSaveTask(Map<String, List<ColorData>> colorChanges) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage(I18nService.get("ardabiomeseditor.save.step.persisting",
                        resourcePackService.getCurrentResourcePackPath().getFileName().toString()));
                updateProgress(0, 100);

                Thread.sleep(10);

                updateMessage(I18nService.get("ardabiomeseditor.save.step.updating_maps"));
                updateProgress(5, 100);

                resourcePackService.persistColorChanges(
                        biomeTableView.getBiomeKey(),
                        colorChanges,
                        (message, progress) -> {
                            updateMessage(message);
                            updateProgress(5 + (progress * 0.9), 100);
                        }
                );

                updateProgress(95, 100);
                updateMessage(I18nService.get("ardabiomeseditor.save.step.refresh_rp"));

                biomeTableView.persistColorChanges();

                updateProgress(100, 100);
                updateMessage(I18nService.get("ardabiomeseditor.save.step.complete"));

                return null;
            }
        };
    }

    /**
     * Handles the successful completion of a save operation.
     * @param onSuccess The callback to execute after a successful save.
     */
    private void handleSaveSuccess(Runnable onSuccess) {
        ArdaBiomesEditor.LOGGER.info("Successfully persisted edits");

        fileManagementController.loadResourcePack(resourcePackService.getCurrentResourcePackPath(), true);

        progressOverlay.setVisible(false);

        if (onSuccess != null) onSuccess.run();
    }

    /**
     * Handles a failure during the save operation.
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
     * Prompts the user to save unsaved changes before switching to the new selection.
     * @param oldSelection The previous biome selection.
     */
    private void biomeSelectionChanged(String oldSelection) {

        biomeEditorContent.setManaged(true);
        biomeEditorContent.setVisible(true);

        var currentSelection = biomeSelectionController.getCurrentSelectedBiome();

        if (biomeTableView.hasUnsavedChanges() && oldSelection != null && !oldSelection.equals(currentSelection)) {

            showUnsavedChangesDialog(() -> {

                        biomeTitle.setText(currentSelection);
                        biomeSelectionController.confirmSelectionChange();
                        biomeSelectionController.loadNewBiomeData(currentSelection);
                    },
                    () -> {
                        biomeTitle.setText(oldSelection);
                        biomeTableView.resetChanges(biomeTableView.getSelectedColumns());
                        colorEditorController.resetAndHideUi();
                        biomeSelectionController.confirmSelectionChange();
                        biomeSelectionController.loadNewBiomeData(currentSelection);
                    },
                    () -> {
                        biomeTitle.setText(oldSelection);
                        biomeSelectionController.revertSelection();
            });

        } else {

            biomeTitle.setText(currentSelection);
            colorEditorController.resetAndHideUi();
            biomeSelectionController.confirmSelectionChange();
            biomeSelectionController.loadNewBiomeData(currentSelection);
        }
    }

    /**
     * Displays a dialog to handle unsaved changes.
     * Provides options to save, reset, or cancel the operation.
     * @param onSave The callback to execute if the user chooses to save.
     * @param onReset The callback to execute if the user chooses to reset.
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
     * @param filePath the path of the loaded resource pack.
     */
    private void clearUiOnResourcePackLoad(String filePath) {

        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setTitle("ArdaBiomes Editor - " + filePath);

        biomeTableView.clear();
        colorEditorController.resetAndHideUi();
        biomeSelectionController.resetSelection();

        biomeEditorContent.setManaged(false);
        biomeEditorContent.setVisible(false);
    }

    /**
     * Handles the application exit process.
     * Prompts the user to save unsaved changes before exiting.
     * @param exitAction The callback to execute for exiting the application.
     */
    public void onExitApplication(Runnable exitAction){

        if (biomeTableView.hasUnsavedChanges()) {

            showUnsavedChangesDialog(
                    () -> {
                        saveBiomeEdits(exitAction);
                    },
                    () -> {
                        biomeTableView.resetChanges(biomeTableView.getSelectedColumns());
                        exitAction.run();
                    },
                    () -> {}
            );

        } else {

            Platform.exit();
        }
    }
}