package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.model.Biomes;
import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.model.Modifier;
import com.duom.ardabiomeseditor.model.json.PolytoneModifier;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for reading and processing resource packs containing Polytone data.
 */
public class ResourcePackService {

    private static final String POLYTONE_ROOT = "assets/polytone/polytone/";
    private static final String POLYTONE_MAPPINGS = POLYTONE_ROOT + "biome_id_mappers/ardabiomes.json";
    private static final String POLYTONE_BLOCK_MODIFIERS_ROOT = POLYTONE_ROOT + "block_modifiers/"; // JSON / PNG
    private static final String JSON_EXT = ".json";
    private static final String PNG_EXT = ".png";

    private static final Pattern ZIPFILE_EDIT_SUFFIX_PATTERN = Pattern.compile("-edit(\\d*)");

    private static final Gson GSON = new Gson();

    private final Biomes biomes;
    private final Map<String, Modifier> blockModifiers;
    private Path zipPath;

    /**
     * Constructor for initializing the ResourcePackService.
     * Initializes the biomes and blockModifiers data structures.
     */
    public ResourcePackService(){

        biomes = new Biomes();
        blockModifiers = new HashMap<>();
    }

    /**
     * Reads the resource pack from the specified path.
     * Extracts and processes relevant data from the resource pack.
     *
     * @param path The path to the resource pack file.
     */
    public void readResourcePack(Path path) {

        this.zipPath = path;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null)
                readResourcePackEntry(entry.getName(), entry, zis);

        } catch (IOException e) {

            throw new RuntimeException(e);
        }
    }

    /**
     * Processes a single entry in the resource pack.
     * Determines the type of entry and delegates processing accordingly.
     *
     * @param entryName The name of the entry.
     * @param entry The ZipEntry object.
     * @param zis The ZipInputStream for reading the entry data.
     * @throws IOException If an I/O error occurs.
     */
    private void readResourcePackEntry(String entryName, ZipEntry entry, ZipInputStream zis) throws IOException {

        if (entryName.startsWith(POLYTONE_ROOT)) {

            // Process base biome mappings file
            if (entryName.equals(POLYTONE_MAPPINGS)) {

                readBiomeMappingsEntry(entry, zis);
            }

            if (entryName.startsWith(POLYTONE_BLOCK_MODIFIERS_ROOT)) {

                readBlockEntry(entry, zis);
            }
        }
    }

    /**
     * Reads and processes the biome mappings entry.
     * Extracts biome mappings and initializes the Biomes object.
     *
     * @param entry The ZipEntry object.
     * @param zis The ZipInputStream for reading the entry data.
     * @throws IOException If an I/O error occurs.
     */
    private void readBiomeMappingsEntry(ZipEntry entry, ZipInputStream zis) throws IOException {

        if (entry.getName().endsWith(JSON_EXT)) {

            // Mappings file can contain duplicate keys
            String rawMappings = new String(zis.readAllBytes());
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
    }

    /**
     * Reads and processes a block entry (JSON or PNG).
     * Updates the blockModifiers map with the parsed data.
     *
     * @param entry The ZipEntry object.
     * @param zis The ZipInputStream for reading the entry data.
     * @throws IOException If an I/O error occurs.
     */
    private void readBlockEntry(ZipEntry entry, ZipInputStream zis) throws IOException {

        byte[] payload = zis.readAllBytes();
        var rawAssetName = entry.getName()
                .substring(POLYTONE_BLOCK_MODIFIERS_ROOT.length());
        var assetName = rawAssetName
                .replaceAll(JSON_EXT, "")
                .replaceAll(PNG_EXT, "");

        if (!rawAssetName.startsWith("_")) {

            if (rawAssetName.endsWith(JSON_EXT)) {

                PolytoneModifier content = GSON.fromJson(new String(payload), PolytoneModifier.class);

                if (blockModifiers.containsKey(assetName))
                    blockModifiers.get(assetName).setModifier(content);
                else
                    blockModifiers.put(assetName, new Modifier(assetName, content));

            } else if (rawAssetName.endsWith(PNG_EXT)) {

                if (blockModifiers.containsKey(assetName))
                    blockModifiers.get(assetName).setImageData(payload);
                else
                    blockModifiers.put(assetName, new Modifier(assetName, payload));
            }
        }
    }

    /**
     * Retrieves the Biomes object containing biome data.
     *
     * @return The Biomes object.
     */
    public Biomes getBiomes() {
        return biomes;
    }

    /**
     * Retrieves the map of block modifiers.
     *
     * @return A map of block modifiers.
     */
    public Map<String, Modifier> getBlockModifiers() {
        return blockModifiers;
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

        if (zipPath == null) throw new RuntimeException("No resource pack loaded.");

        zipPath = determineTargetPath();

        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {

            int totalEntries = colorChanges.size();
            int processed = 0;

            for (Map.Entry<String, List<ColorData>> change : colorChanges.entrySet()) {
                String assetName = change.getKey();
                Path pngPath = zipFs.getPath(POLYTONE_BLOCK_MODIFIERS_ROOT + assetName + PNG_EXT);

                if (Files.exists(pngPath)) {
                    progressCallback.accept("Processing " + assetName, (double) processed++ / totalEntries * 100);

                    byte[] originalData = Files.readAllBytes(pngPath);
                    byte[] modifiedData = ModifierService.applyColorChangesToModifierTexture(
                            originalData,
                            biomeKey,
                            change.getValue()
                    );

                    Files.write(pngPath, modifiedData);
                }
            }
        }
    }

    /**
     * Determines the target path for saving the resource pack.
     * Creates a new version of the resource pack if necessary.
     *
     * @return The target path for the resource pack.
     * @throws IOException If an I/O error occurs.
     */
    private Path determineTargetPath() throws IOException {

        Path targetPath = zipPath;
        String fileName = zipPath.getFileName().toString();
        Matcher matcher = ZIPFILE_EDIT_SUFFIX_PATTERN.matcher(fileName);

        if (!matcher.find()) {

            // No "-edit" suffix exists, create first edit copy
            String baseName = getBaseFileName(fileName);
            String extension = getFileExtension(fileName);
            targetPath = zipPath.getParent().resolve(baseName + "-edit" + extension);
            Files.copy(zipPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } else {

            // Has "-edit" or "-editN", check if locked
            if (isFileLocked(zipPath)) {

                // File is locked, create next edit version
                targetPath = getNextEditPath(zipPath);
                Files.copy(zipPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return targetPath;
    }

    /**
     * Checks if the specified file is locked.
     *
     * @param original The path to the file to check.
     * @return True if the file is locked, false otherwise.
     */
    private boolean isFileLocked(Path original) {

        Path lockTestPath = original.getParent().resolve(original.getFileName().toString() + ".locktest");

        try {
            Files.move(original, lockTestPath, StandardCopyOption.ATOMIC_MOVE);
            Files.move(lockTestPath, original, StandardCopyOption.ATOMIC_MOVE);
            return false; // Successfully moved and back, file is not locked
        } catch (IOException e) {
            // Failed to move, file is locked
            try {
                // Clean up if lockTestPath was created
                Files.deleteIfExists(lockTestPath);
            } catch (IOException ignored) {}
            return true;
        }
    }

    /**
     * Generates the next available edit path for the resource pack.
     *
     * @param originalPath The original path of the resource pack.
     * @return The next available edit path.
     */
    private Path getNextEditPath(Path originalPath) {

        String fileName = originalPath.getFileName().toString();
        String baseName = getBaseFileName(fileName);
        String extension = getFileExtension(fileName);
        Path parent = originalPath.getParent();

        int editNumber = 1;
        Path newPath;

        do {
            newPath = parent.resolve(baseName + "-edit" + editNumber + extension);
            editNumber++;
        } while (Files.exists(newPath));

        return newPath;
    }

    /**
     * Extracts the base file name from the given file name.
     *
     * @param fileName The file name to process.
     * @return The base file name.
     */
    private String getBaseFileName(String fileName) {
        Matcher matcher = ZIPFILE_EDIT_SUFFIX_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return fileName.substring(0, matcher.start());
        }

        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    /**
     * Extracts the file extension from the given file name.
     *
     * @param fileName The file name to process.
     * @return The file extension.
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }

    /**
     * Retrieves the path to the currently loaded resource pack.
     *
     * @return The path to the resource pack.
     */
    public Path getCurrentResourcePackPath() {
        return zipPath;
    }
}