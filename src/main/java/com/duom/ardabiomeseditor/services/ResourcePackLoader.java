package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.Biomes;
import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.model.Modifier;
import com.duom.ardabiomeseditor.model.json.PolytoneModifier;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ResourcePackLoader {

    String POLYTONE_ROOT = "polytone/";
    String POLYTONE_MAPPINGS = POLYTONE_ROOT + "biome_id_mappers/";
    String POLYTONE_BLOCK_MODIFIERS_ROOT = POLYTONE_ROOT + "block_modifiers/";
    String POLYTONE_DIMENSIONS_MODIFIERS_ROOT = POLYTONE_ROOT + "dimension_modifiers/";
    String POLYTONE_FLUID_MODIFIERS_ROOT = POLYTONE_ROOT + "fluid_modifiers/";
    String POLYTONE_PARTICLE_MODIFIERS_ROOT = POLYTONE_ROOT + "particle_modifiers/";
    String JSON_EXT = ".json";
    String PNG_EXT = ".png";

    private static final Gson GSON = new Gson();

    protected final Biomes biomes;
    protected final Map<String, Modifier> blockModifiers;
    protected final Map<String, Modifier> dimensionsModifiers;
    protected final Map<String, Modifier> fluidModifiers;
    protected final Map<String, Modifier> particleModifiers;

    protected Path polytoneBlockModifiersRoot;
    protected Path polytoneDimensionsModifiersRoot;
    protected Path polytonFluidModifiersRoot;
    protected Path polytonParticleModifiersRoot;
    protected Path polytoneMappings;

    public ResourcePackLoader(){

        biomes = new Biomes();

        blockModifiers = new HashMap<>();
        dimensionsModifiers = new HashMap<>();
        fluidModifiers = new HashMap<>();
        particleModifiers = new HashMap<>();
    }

    public abstract void load(Path path) throws MissingResourceException, IOException;
    public abstract void save(Map<String, List<ColorData>> colorChanges, int biomeKey, BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException;
    public abstract Path getResourcePackPath();

    /**
     * Reads and processes a block entry (JSON or PNG).
     * Updates the blockModifiers map with the parsed data.
     *
     * @param assetName the asset name.
     * @param payload the asset as a byte array.
     * @param modifierMap the map to store the modifiers.
     */
    protected void readEntry(String assetName, byte[] payload, Map<String, Modifier> modifierMap) {

        var trimmedAssetName = assetName
                .replaceAll(JSON_EXT, "")
                .replaceAll(PNG_EXT, "");

        if (!assetName.startsWith("_")) {

            if (assetName.endsWith(JSON_EXT)) {

                PolytoneModifier content = GSON.fromJson(new String(payload), PolytoneModifier.class);

                if (modifierMap.containsKey(trimmedAssetName))
                    modifierMap.get(trimmedAssetName).setModifier(content);
                else
                    modifierMap.put(trimmedAssetName, new Modifier(trimmedAssetName, content));

            } else if (assetName.endsWith(PNG_EXT)) {

                if (modifierMap.containsKey(trimmedAssetName))
                    modifierMap.get(trimmedAssetName).setImageData(payload);
                else
                    modifierMap.put(trimmedAssetName, new Modifier(trimmedAssetName, payload));
            }
        }
    }

    /**
     * Reads and processes the biome mappings entry.
     * Extracts biome mappings and initializes the Biomes object.
     *
     * @param payload the biome mappings file as a byte array.
     * @throws IOException If an I/O error occurs.
     */
    protected void readBiomeMappingsEntry(byte[] payload) throws IOException {

        // Mappings file can contain duplicate keys
        String rawMappings = new String(payload);
        Map<String, Integer> biomeMap = new HashMap<>();
        var textureSize =  256;
        var placeholderCount = 0;

        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(rawMappings);

        while (matcher.find()) {

            String key = matcher.group(1);
            int value = Integer.parseInt(matcher.group(2));

            if (key.equals("texture_size"))
                textureSize = value;
            else if (!key.contains(":placeholder"))
                biomeMap.put(key, value);
            else
                placeholderCount++;
        }

        biomes.init(biomeMap, textureSize, placeholderCount);
    }

    protected void persistColorChanges(Map<String, List<ColorData>> colorChanges,
                                       int biomeKey,
                                       FileSystem fileSystem,
                                       String assetsRoot,
                                       BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException {

        Map<String, List<ColorData>> blockModifiercolorChanges       = new HashMap<>();
        Map<String, List<ColorData>> dimensionsModifiercolorChanges  = new HashMap<>();
        Map<String, List<ColorData>> fluidModifiercolorChanges       = new HashMap<>();
        Map<String, List<ColorData>> particleModifiercolorChanges    = new HashMap<>();

        for (var key : colorChanges.keySet()){
            if (blockModifiers.containsKey(key)) blockModifiercolorChanges.put(key, colorChanges.get(key));
            if (dimensionsModifiers.containsKey(key)) dimensionsModifiercolorChanges.put(key, colorChanges.get(key));
            if (fluidModifiers.containsKey(key)) fluidModifiercolorChanges.put(key, colorChanges.get(key));
            if (particleModifiers.containsKey(key)) particleModifiercolorChanges.put(key, colorChanges.get(key));
        }

        int totalEntries = blockModifiercolorChanges.size() + dimensionsModifiercolorChanges.size()
                + fluidModifiercolorChanges.size() + particleModifiercolorChanges.size();

        var processed = persistColorChanges(blockModifiercolorChanges, biomeKey, fileSystem, assetsRoot, polytoneBlockModifiersRoot.toString(), totalEntries, 0, progressCallback);
        processed += persistColorChanges(dimensionsModifiercolorChanges, biomeKey, fileSystem, assetsRoot, polytoneDimensionsModifiersRoot.toString(), totalEntries, processed, progressCallback);
        processed += persistColorChanges(fluidModifiercolorChanges, biomeKey, fileSystem, assetsRoot, polytonFluidModifiersRoot.toString(), totalEntries, processed, progressCallback);
        persistColorChanges(particleModifiercolorChanges, biomeKey, fileSystem, assetsRoot, polytonParticleModifiersRoot.toString(), totalEntries, processed, progressCallback);
    }

    private int persistColorChanges(Map<String, List<ColorData>> colorChanges,
                                     int biomeKey,
                                     FileSystem fileSystem,
                                     String assetsRoot,
                                     String modifierRoot,
                                     int totalEntries,
                                     int currentProcessed,
                                     BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException {

        int processed = currentProcessed;

        for (Map.Entry<String, List<ColorData>> change : colorChanges.entrySet()) {
            String assetName = change.getKey();

            Path pngPath;
            try (var pathStream = Files.walk(fileSystem.getPath(assetsRoot))) {

                pngPath = pathStream
                        .filter(p -> p.toString().endsWith(modifierRoot + fileSystem.getSeparator() + assetName + PNG_EXT))
                        .findFirst()
                        .orElse(null);
            }

            if (pngPath != null && Files.exists(pngPath)) {
                processed++;
                ArdaBiomesEditor.LOGGER.info("Updating asset: {}", assetName);
                progressCallback.accept("Processing " + assetName, (double) processed / totalEntries * 100);

                byte[] originalData = Files.readAllBytes(pngPath);
                byte[] modifiedData = ModifierService.applyColorChangesToModifierTexture(
                        originalData,
                        biomeKey,
                        change.getValue()
                );

                Files.write(pngPath, modifiedData);

            } else {
                ArdaBiomesEditor.LOGGER.error("Missing asset in resource pack: {}", assetName);
                throw new MissingResourceException ("Missing resource : " + modifierRoot + fileSystem.getSeparator() + assetName + PNG_EXT, "png", assetName);
            }
        }

        return processed;
    }

    /**
     * Resolves a sub-path within the given root directory that ends with the specified leaf string.
     *
     * @param root The root directory to start the search from.
     * @param leaf The leaf string that the resolved path should end with.
     * @return The resolved Path if found, otherwise null.
     * @throws IOException If an I/O error occurs.
     */
    protected Path resolveSubDirectory(Path root, String leaf) throws IOException {

        Path resolvedPath;

        try (var stream = Files.walk(root)) {
            resolvedPath = stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.toAbsolutePath().endsWith(leaf))
                    .findFirst()
                    .orElse(null);
        }

        return resolvedPath;
    }

    /**
     * Validates the structure of the resource pack located at the given root path.
     *
     * @param root The root path of the resource pack.
     * @throws MissingResourceException If required files or directories are missing.
     * @throws IOException If an I/O error occurs.
     */
    protected void validateResourcePackStructure(Path root) throws MissingResourceException, IOException {

        var polytoneRoot = resolveSubDirectory(root, "polytone");
        var polytoneMappingsDirectory = resolveSubDirectory(polytoneRoot, POLYTONE_MAPPINGS);

        polytoneBlockModifiersRoot = resolveSubDirectory(polytoneRoot, POLYTONE_BLOCK_MODIFIERS_ROOT);
        polytoneDimensionsModifiersRoot = resolveSubDirectory(polytoneRoot, POLYTONE_DIMENSIONS_MODIFIERS_ROOT);
        polytonFluidModifiersRoot = resolveSubDirectory(polytoneRoot, POLYTONE_FLUID_MODIFIERS_ROOT);
        polytonParticleModifiersRoot = resolveSubDirectory(polytoneRoot, POLYTONE_PARTICLE_MODIFIERS_ROOT);

        // Validate presence of biome mappings file
        if (polytoneMappingsDirectory == null || !Files.exists(polytoneMappingsDirectory) || !Files.isDirectory(polytoneMappingsDirectory)) {
            ArdaBiomesEditor.LOGGER.error("Missing folder: {}", POLYTONE_MAPPINGS);
            throw new MissingResourceException ("Missing required folder: " + POLYTONE_MAPPINGS, POLYTONE_MAPPINGS, "mappings configuration");
        } else {

            try (var stream = Files.list(polytoneMappingsDirectory)) {
                polytoneMappings = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(JSON_EXT))
                        .findFirst()
                        .orElse(null);
            }

            if (!Files.exists(polytoneMappings) || !Files.isRegularFile(polytoneMappings)) {
                ArdaBiomesEditor.LOGGER.error("Missing json file in : {}", POLYTONE_MAPPINGS);
                throw new MissingResourceException ("Missing required json in: " + POLYTONE_MAPPINGS, POLYTONE_MAPPINGS, "mappings configuration");
            }
        }

        // Validate required paths
        logModifierValidationEvent(polytoneBlockModifiersRoot, POLYTONE_BLOCK_MODIFIERS_ROOT);
        logModifierValidationEvent(polytoneDimensionsModifiersRoot, POLYTONE_DIMENSIONS_MODIFIERS_ROOT);
        logModifierValidationEvent(polytonFluidModifiersRoot, POLYTONE_FLUID_MODIFIERS_ROOT);
        logModifierValidationEvent(polytonParticleModifiersRoot, POLYTONE_PARTICLE_MODIFIERS_ROOT);
    }

    private void logModifierValidationEvent(Path modifierPath, String lookupName) {
        if (modifierPath == null || !Files.exists(modifierPath) || !Files.isDirectory(modifierPath)) {
            ArdaBiomesEditor.LOGGER.warn("Missing folder: {}", lookupName);
        }
    }

    protected void validateLoadedModifiers() {

        pruneIncompleteModifiers(blockModifiers);
        pruneIncompleteModifiers(dimensionsModifiers);
        pruneIncompleteModifiers(fluidModifiers);
        pruneIncompleteModifiers(particleModifiers);
    }

    private void pruneIncompleteModifiers(Map<String, Modifier> modifiers) {

        modifiers.entrySet().removeIf(entry -> {
            boolean isIncomplete = entry.getValue().getImageData().length == 0 || entry.getValue().getModifier() == null;
            if (isIncomplete) {
                ArdaBiomesEditor.LOGGER.warn("Removing incomplete modifier: {} (missing {})",
                        entry.getKey(),
                        entry.getValue().getImageData().length == 0 ? "image data" : "polytone modifier");
            }
            return isIncomplete;
        });
    }


    public Map<String, Modifier> getDimensionsModifiers() {
        return dimensionsModifiers;
    }

    public Map<String, Modifier> getFluidModifiers() {
        return fluidModifiers;
    }

    public Map<String, Modifier> getParticleModifiers() {
        return particleModifiers;
    }

    public Map<String, Modifier> getBlockModifiers(){
        return blockModifiers;
    }

    public Biomes getBiomes(){
        return biomes;
    }
}