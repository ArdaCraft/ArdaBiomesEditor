package com.duom.ardabiomeseditor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for the Arda Biomes Editor application.
 *
 * This class manages the application's configuration settings, including
 * the list of recently accessed files and the maximum number of recent files
 * to retain.
 */
public class ArdaBiomesEditorConfiguration {

    private List<String> recentFiles = new ArrayList<>();
    private final int maxRecentFiles = 10;

    /**
     * Retrieves the list of recently accessed files.
     *
     * @return A list of file paths representing the recently accessed files.
     */
    public List<String> getRecentFiles() {
        return recentFiles;
    }

    /**
     * Updates the list of recently accessed files.
     *
     * @param recentFiles A list of file paths to set as the recently accessed files.
     */
    public void setRecentFiles(List<String> recentFiles) {
        this.recentFiles = recentFiles;
    }

    /**
     * Retrieves the maximum number of recent files to retain.
     *
     * @return The maximum number of recent files.
     */
    public int getMaxRecentFiles() {
        return maxRecentFiles;
    }
}