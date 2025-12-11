package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.ColorData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.function.BiConsumer;

public class FolderResourcePackLoader extends ResourcePackLoader{

    private Path resourcePackPath;

    @Override
    public void load(Path path) throws MissingResourceException, IOException {

        Path polytoneRoot = path.resolve(POLYTONE_ROOT);
        Path polytoneBlockModifiersRoot = path.resolve(POLYTONE_BLOCK_MODIFIERS_ROOT);
        Path polytoneMappings = path.resolve(POLYTONE_MAPPINGS);

        // Validate required paths
        if (!Files.exists(polytoneRoot)) {
            ArdaBiomesEditor.LOGGER.error("Missing directory: {}", POLYTONE_ROOT);
            throw new MissingResourceException("Missing required directory: " + POLYTONE_ROOT, POLYTONE_ROOT, "polytone root");
        }
        if (!Files.exists(polytoneMappings) || !Files.isRegularFile(polytoneMappings)) {
            ArdaBiomesEditor.LOGGER.error("Missing file: {}", POLYTONE_MAPPINGS);
            throw new MissingResourceException ("Missing required file: " + POLYTONE_MAPPINGS, POLYTONE_MAPPINGS, "mappings configuration");
        }

        resourcePackPath = path;

        readBiomeMappingsEntry(Files.readAllBytes(polytoneMappings));

        if (Files.exists(polytoneBlockModifiersRoot)) {

            try (var files = Files.walk(polytoneBlockModifiersRoot)) {
                files.filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                readEntry(file.getFileName().toString(), Files.readAllBytes(file));
                            } catch (IOException e) {

                                ArdaBiomesEditor.LOGGER.error("Failed to read file: {}", file, e);
                            }
                        });
            }

        } else {
            ArdaBiomesEditor.LOGGER.warn("Missing directory: {}", POLYTONE_BLOCK_MODIFIERS_ROOT);
        }
    }

    @Override
    public void save(Map<String, List<ColorData>> colorChanges, int biomeKey, BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException {

        if (resourcePackPath == null) throw new RuntimeException("No resource pack loaded.");

        persistColorChanges(colorChanges,
                biomeKey,
                progressCallback,
                resourcePackPath.toAbsolutePath().toString() + File.separator,
                resourcePackPath.getFileSystem());
    }

    @Override
    public Path getResourcePackPath() {
        return resourcePackPath;
    }

}