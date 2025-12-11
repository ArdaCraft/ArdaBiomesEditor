package com.duom.ardabiomeseditor.ui.controller;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.services.I18nService;
import com.duom.ardabiomeseditor.services.ResourcePackService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.MissingResourceException;
import java.util.function.Consumer;

/**
 * Controller class for managing file-related operations in the ArdaBiomesEditor application.
 * Handles opening, saving, and managing recent files, as well as other file-related actions.
 */
public class FileManagementController {

    @FXML private Menu recentFilesMenu;
    @FXML private TextFlow currentFileLabel;

    private ResourcePackService resourcePackService;
    private Consumer<Path> onFileLoadedCallback;
    private Consumer<Runnable> saveCallback;
    private Consumer<Runnable> menuExitCallback;

    /**
     * Initializes the controller. Updates the recent files menu.
     */
    @FXML
    public void initialize() {

        updateRecentFilesMenu();
    }

    /**
     * Sets the resource pack service.
     *
     * @param resourcePackService The ResourcePackService instance to use.
     */
    public void setResourcePackService(ResourcePackService resourcePackService) {
        this.resourcePackService = resourcePackService;
    }

    /**
     * Sets the callback to be executed when a file is loaded.
     *
     * @param callback The callback function.
     */
    public void setOnFileLoadedCallback(Consumer<Path> callback) {
        this.onFileLoadedCallback = callback;
    }

    /**
     * Opens a file chooser dialog to select a ZIP file and loads the selected resource pack.
     */
    @FXML
    public void onOpenZip() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Files", "*.zip"));

        var file = chooser.showOpenDialog(null);
        if (file != null) {
            loadResourcePack(file.toPath(), false);
        }
    }

    /**
     * Opens a file chooser dialog to select a folder and loads the selected resource pack.
     */
    @FXML
    public void onOpenFolder(){

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Resource Pack Folder");

        var folder = chooser.showDialog(null);
        if (folder != null) {
            loadResourcePack(folder.toPath(), false);
        }
    }

    /**
     * Loads a resource pack from the specified file path.
     *
     * @param filePath The path to the resource pack file.
     * @param reload   Whether this is a reload operation.
     */
    public void loadResourcePack(Path filePath, boolean reload) {

        ArdaBiomesEditor.LOGGER.info("Loading resource pack {}", filePath.getFileName());
        ArdaBiomesEditor.CONFIG.addRecentFile(filePath.toAbsolutePath().toString());

        try {

            resourcePackService.readResourcePack(filePath);
        } catch (MissingResourceException mre){

            showErrorPopup(filePath,
                    I18nService.get("ardabiomeseditor.filmanagement.error.rp_loading_error_title"),
                    I18nService.get("ardabiomeseditor.filmanagement.error.rp_loading_error", filePath.toString()),
                    I18nService.get("ardabiomeseditor.filmanagement.error.missing_directory"));

        } catch (IOException ioe) {

            showErrorPopup(filePath,
                    I18nService.get("ardabiomeseditor.filmanagement.error.rp_loading_error_title"),
                    I18nService.get("ardabiomeseditor.filmanagement.error.rp_loading_error", filePath.toString()),
                    I18nService.get("ardabiomeseditor.filmanagement.error.rp_io_loading_error"));
        }

        Text editingText = new Text("Editing ");
        editingText.setStyle("-fx-font-weight: bold;");

        Hyperlink pathLink = new Hyperlink(filePath.toAbsolutePath().toString());
        pathLink.setStyle("-fx-text-fill: orange; -fx-underline: false;");
        pathLink.setOnAction(e -> openFileLocation(filePath));

        currentFileLabel.getChildren().setAll(editingText, pathLink);

        if (!reload) {
            ArdaBiomesEditor.CONFIG.addRecentFile(filePath.toAbsolutePath().toString());
            updateRecentFilesMenu();
        }

        if (onFileLoadedCallback != null) onFileLoadedCallback.accept(filePath);
    }

    private static void showErrorPopup(Path filePath, String title, String header, String content) {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        alert.getButtonTypes().setAll(new ButtonType(I18nService.get("ardabiomeseditor.generic.ok")));

        alert.showAndWait();
    }

    /**
     * Opens the file location of the specified file in the system's file explorer.
     *
     * @param filePath The path to the file.
     */
    private void openFileLocation(Path filePath) {
        try {
            Desktop.getDesktop().open(filePath.getParent().toFile());
        } catch (IOException e) {
            ArdaBiomesEditor.LOGGER.error("Failed to open file location", e);
        }
    }

    /**
     * Opens a recent file by its path.
     *
     * @param path The path to the recent file.
     * @return True if the file was successfully opened, false otherwise.
     */
    private boolean openRecentFile(String path) {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            ArdaBiomesEditor.LOGGER.warn("File not found: {}", path);
            return false;
        }

        loadResourcePack(filePath, false);
        return true;
    }

    /**
     * Updates the recent files menu with the list of recent files.
     */
    private void updateRecentFilesMenu() {
        recentFilesMenu.getItems().clear();

        List<String> recentFiles = ArdaBiomesEditor.CONFIG.recentFiles();

        if (recentFiles.isEmpty()) {

            MenuItem noFiles = new MenuItem(I18nService.get("ardabiomeseditor.generic.no_recent_files"));
            noFiles.setDisable(true);
            recentFilesMenu.getItems().add(noFiles);

        } else {

            for (String path : recentFiles) {

                MenuItem item = new MenuItem(path);
                item.setOnAction(e -> {if (!openRecentFile(path)) recentFilesMenu.getItems().remove(item);});
                recentFilesMenu.getItems().add(item);
            }
        }
    }

    /**
     * Opens the logs folder in the system's file explorer.
     */
    @FXML
    public void onOpenLogsFolder() {
        try {
            Path logsPath = ArdaBiomesEditor.CONFIG.getLogsDirectory();
            if (Files.exists(logsPath)) {
                Desktop.getDesktop().open(logsPath.toFile());
            } else {
                ArdaBiomesEditor.LOGGER.warn("Logs folder not found");
            }
        } catch (IOException e) {
            ArdaBiomesEditor.LOGGER.error("Failed to open logs folder", e);
        }
    }

    /**
     * Saves biome edits by invoking the save callback.
     */
    @FXML
    private void onSaveBiomeEdits() {
        saveCallback.accept(null);
    }

    /**
     * Exits the application by invoking the menu exit callback.
     */
    @FXML
    private void onExitApp(){
        menuExitCallback.accept(Platform::exit);
    }

    /**
     * Sets the callback to be executed when saving changes.
     *
     * @param callback The save callback function.
     */
    public void setSaveCallback(Consumer<Runnable> callback) {
        this.saveCallback = callback;
    }

    /**
     * Displays the "About" popup with application information.
     */
    @FXML
    public void onShowAboutPopup() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18nService.get("ardabiomeseditor.about.title"));
        alert.setHeaderText("ArdaBiomesEditor");

        try {
            var iconUrl = getClass().getResource("/icon_64.png");
            if (iconUrl != null) {
                var image = new javafx.scene.image.Image(iconUrl.toExternalForm(), 64, 64, true, true);
                alert.setGraphic(new javafx.scene.image.ImageView(image));
            }
        } catch (Exception e) {
            ArdaBiomesEditor.LOGGER.error("Failed to load icon", e);
        }

        alert.setContentText(I18nService.get("ardabiomeseditor.about.content"));
        alert.showAndWait();
    }

    /**
     * Sets the callback to be executed when exiting the application.
     *
     * @param menuExitCallback The exit callback function.
     */
    public void setMenuExitCallback(Consumer<Runnable> menuExitCallback) {
        this.menuExitCallback = menuExitCallback;
    }
}