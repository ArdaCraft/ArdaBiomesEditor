package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.model.Modifier;

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
    public void load(Path root) throws MissingResourceException, IOException {

        validateResourcePackStructure(root);

        resourcePackPath = root;

        readBiomeMappingsEntry(Files.readAllBytes(polytoneMappings));

        readModifiers(polytoneBlockModifiersRoot, blockModifiers);
        readModifiers(polytoneDimensionsModifiersRoot, dimensionsModifiers);
        readModifiers(polytonFluidModifiersRoot, fluidModifiers);
        readModifiers(polytonParticleModifiersRoot, particleModifiers);

        validateLoadedModifiers();
    }

    private void readModifiers(Path root, Map<String, Modifier> modifiers) throws IOException {

        if (Files.exists(root)) {

            try (var files = Files.walk(root)) {

                files.filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                readEntry(file.getFileName().toString(), Files.readAllBytes(file), modifiers);
                            } catch (IOException e) {

                                ArdaBiomesEditor.LOGGER.error("Failed to read file: {}", file, e);
                            }
                        });
            }

        } else {
            ArdaBiomesEditor.LOGGER.warn("Missing directory: {}", root);
        }
    }

    @Override
    public void save(Map<String, List<ColorData>> colorChanges, int biomeKey, BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException {

        if (resourcePackPath == null) throw new RuntimeException("No resource pack loaded.");

        persistColorChanges(colorChanges,
                biomeKey,
                resourcePackPath.getFileSystem(),
                resourcePackPath.toAbsolutePath() + File.separator,
                progressCallback);
    }

    @Override
    public Path getResourcePackPath() {
        return resourcePackPath;
    }

}