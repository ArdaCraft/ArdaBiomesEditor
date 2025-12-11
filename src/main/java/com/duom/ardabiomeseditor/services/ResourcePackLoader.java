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

    String POLYTONE_ROOT = "assets/polytone/polytone/";
    String POLYTONE_MAPPINGS = POLYTONE_ROOT + "biome_id_mappers/ardabiomes.json";
    String POLYTONE_BLOCK_MODIFIERS_ROOT = POLYTONE_ROOT + "block_modifiers/"; // JSON / PNG
    String JSON_EXT = ".json";
    String PNG_EXT = ".png";

    private static final Gson GSON = new Gson();

    protected final Biomes biomes;
    protected final Map<String, Modifier> blockModifiers;

    public ResourcePackLoader(){

        biomes = new Biomes();
        blockModifiers = new HashMap<>();
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
     */
    protected void readEntry(String assetName, byte[] payload) {

        var trimmedAssetName = assetName
                .replaceAll(JSON_EXT, "")
                .replaceAll(PNG_EXT, "");

        if (!assetName.startsWith("_")) {

            if (assetName.endsWith(JSON_EXT)) {

                PolytoneModifier content = GSON.fromJson(new String(payload), PolytoneModifier.class);

                if (blockModifiers.containsKey(trimmedAssetName))
                    blockModifiers.get(trimmedAssetName).setModifier(content);
                else
                    blockModifiers.put(trimmedAssetName, new Modifier(trimmedAssetName, content));

            } else if (assetName.endsWith(PNG_EXT)) {

                if (blockModifiers.containsKey(trimmedAssetName))
                    blockModifiers.get(trimmedAssetName).setImageData(payload);
                else
                    blockModifiers.put(trimmedAssetName, new Modifier(trimmedAssetName, payload));
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

    public Map<String, Modifier> getBlockModifiers(){
        return blockModifiers;
    }

    public Biomes getBiomes(){
        return biomes;
    }

    protected void persistColorChanges(Map<String, List<ColorData>> colorChanges, int biomeKey, BiConsumer<String, Double> progressCallback, String assetsRoot, FileSystem fileSystem) throws MissingResourceException, IOException {

        int totalEntries = colorChanges.size();
        int processed = 0;

        for (Map.Entry<String, List<ColorData>> change : colorChanges.entrySet()) {
            String assetName = change.getKey();
            Path pngPath = fileSystem.getPath(assetsRoot + POLYTONE_BLOCK_MODIFIERS_ROOT + assetName + PNG_EXT);

            if (Files.exists(pngPath)) {
                ArdaBiomesEditor.LOGGER.info("Updating asset: {}", assetName);
                progressCallback.accept("Processing " + assetName, (double) processed++ / totalEntries * 100);

                byte[] originalData = Files.readAllBytes(pngPath);
                byte[] modifiedData = ModifierService.applyColorChangesToModifierTexture(
                        originalData,
                        biomeKey,
                        change.getValue()
                );

                Files.write(pngPath, modifiedData);
            } else {
                ArdaBiomesEditor.LOGGER.error("Missing asset in resource pack: {}", assetName);
                throw new MissingResourceException ("Missing resource : " + POLYTONE_BLOCK_MODIFIERS_ROOT + assetName + PNG_EXT, "png", assetName);
            }
        }
    }
}