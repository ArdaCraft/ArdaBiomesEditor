package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.ArdaBiomesEditorConfiguration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing the application's configuration.
 * Handles loading, saving, and validating configuration files, as well as managing recent files.
 */
public class ConfigurationService {

    private static final String APP_NAME = "ArdaBiomesEditor";
    private static final String CONFIG_FILE = "config.json";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private ArdaBiomesEditorConfiguration  configuration;

    /**
     * Constructor for the ConfigurationService.
     * Initializes the configuration directory and sets up the configuration file path.
     */
    public ConfigurationService() {

        Path appDir = getConfigDirectory();
        System.setProperty("arda.log.dir", appDir.toString());

        try {

            Files.createDirectories(appDir);

        } catch (IOException e) {

            System.err.println("Failed to create config directory");
        }

        this.configPath = appDir.resolve(CONFIG_FILE);
    }

    /**
     * Determines the appropriate configuration directory based on the operating system.
     *
     * @return The path to the configuration directory.
     */
    private Path getConfigDirectory() {

        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            // Windows: %LOCALAPPDATA%\ArdaBiomesEditor
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                return Paths.get(localAppData, APP_NAME);
            }
            // Fallback
            return Paths.get(userHome, "AppData", "Local", APP_NAME);
        } else if (os.contains("mac")) {

            // macOS: ~/Library/Application Support/ArdaBiomesEditor
            return Paths.get(userHome, "Library", "Application Support", APP_NAME);
        } else {

            // Linux/Unix: ~/.config/ArdaBiomesEditor (XDG Base Directory)
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null)
                return Paths.get(xdgConfigHome, APP_NAME);

            return Paths.get(userHome, ".config", APP_NAME);
        }
    }

    /**
     * Loads the configuration from the configuration file.
     * If the file does not exist, initializes a new configuration.
     */
    private void loadConfig() {

        if (!Files.exists(configPath)) {

            ArdaBiomesEditor.LOGGER.info("Config file not found, creating new configuration");
            configuration = new ArdaBiomesEditorConfiguration();
        }

        try {

            String json = Files.readString(configPath);
            ArdaBiomesEditor.LOGGER.info("Configuration loaded successfully from: {}", configPath);

            configuration = gson.fromJson(json, ArdaBiomesEditorConfiguration.class);

        } catch (IOException e) {

            ArdaBiomesEditor.LOGGER.error("Failed to load config from {}: {}", configPath, e.getMessage(), e);
            configuration = new ArdaBiomesEditorConfiguration();
        }
    }

    /**
     * Saves the given configuration to the configuration file.
     *
     * @param config The configuration to save.
     */
    public void saveConfig(ArdaBiomesEditorConfiguration config) {

        try {

            String json = gson.toJson(config);
            Files.writeString(configPath, json);

        } catch (IOException e) {

            ArdaBiomesEditor.LOGGER.error("Failed to load config from {}: {}", configPath, e.getMessage(), e);

        }
    }

    /**
     * Adds a file path to the list of recent files in the configuration.
     * Ensures the list does not exceed the maximum allowed size.
     *
     * @param filePath The file path to add.
     */
    public void addRecentFile(String filePath) {

        loadConfig();

        List<String> recentFiles = new ArrayList<>(configuration.getRecentFiles());

        recentFiles.remove(filePath);
        recentFiles.addFirst(filePath);

        if (recentFiles.size() > configuration.getMaxRecentFiles()) {
            recentFiles = recentFiles.subList(0, configuration.getMaxRecentFiles());
            configuration.setRecentFiles(recentFiles);
        }

        validateRecentFiles();
    }

    /**
     * Retrieves the list of recent files from the configuration.
     * Validates the list to ensure all files exist.
     *
     * @return The list of recent files.
     */
    public List<String> recentFiles() {

        validateRecentFiles();
        return configuration.getRecentFiles();
    }

    /**
     * Validates the list of recent files in the configuration.
     * Removes any files that no longer exist.
     */
    private void validateRecentFiles() {
        loadConfig();

        List<String> validatedFiles = new ArrayList<>();
        List<String> recentFiles = configuration.getRecentFiles();

        for (String path : recentFiles) {
            if (Files.exists(Paths.get(path))) {
                validatedFiles.add(path);
            }
        }
        configuration.setRecentFiles(validatedFiles);

        saveConfig(configuration);
    }

    /**
     * Retrieves the directory where log files are stored.
     *
     * @return The path to the logs directory.
     */
    public Path getLogsDirectory(){

        return this.configPath.getParent();
    }
}