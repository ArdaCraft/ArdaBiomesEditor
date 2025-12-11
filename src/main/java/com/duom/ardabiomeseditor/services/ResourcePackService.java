package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.model.Biomes;
import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.model.Modifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.function.BiConsumer;

/**
 * Service for reading and processing resource packs containing Polytone data.
 */
public class ResourcePackService {

    private ResourcePackLoader loader;

    /**
     * Reads the resource pack from the specified path.
     * Extracts and processes relevant data from the resource pack.
     *
     * @param path The path to the resource pack file.
     */
    public void readResourcePack(Path path) throws MissingResourceException, IOException{

        if (Files.isDirectory(path)) {
            loader = new FolderResourcePackLoader();
        } else {
            loader = new ZipResourcePackLoader();
        }

        loader.load(path);
    }

    /**
     * Persists color changes to the resource pack.
     * Updates the PNG files in the resource pack with the modified color data.
     *
     * @param biomeKey The key of the biome being modified.
     * @param colorChanges A map of color changes to apply.
     * @param progressCallback A callback for reporting progress.
     * @throws IOException If an I/O error occurs.
     */
    public void persistColorChanges(int biomeKey, Map<String, List<ColorData>> colorChanges, BiConsumer<String, Double> progressCallback) throws IOException {

        loader.save(colorChanges, biomeKey, progressCallback);
    }

    /**
     * Retrieves the Biomes object containing biome data.
     *
     * @return The Biomes object.
     */
    public Biomes getBiomes() {
        return loader != null ? loader.getBiomes() : new Biomes();
    }

    /**
     * Retrieves the map of block modifiers.
     *
     * @return A map of block modifiers.
     */
    public Map<String, Modifier> getBlockModifiers() {
        return loader != null ? loader.getBlockModifiers() : new HashMap<>();
    }

    /**
     * Retrieves the path to the currently loaded resource pack.
     *
     * @return The path to the resource pack.
     */
    public Path getCurrentResourcePackPath() {

        return loader != null ? loader.getResourcePackPath() : null;
    }
}