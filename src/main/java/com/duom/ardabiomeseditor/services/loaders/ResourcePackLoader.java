package com.duom.ardabiomeseditor.services.loaders;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import com.duom.ardabiomeseditor.model.Namespace;
import com.duom.ardabiomeseditor.model.ResourceIdentifier;
import com.duom.ardabiomeseditor.model.polytone.*;
import com.duom.ardabiomeseditor.services.ColorMapService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Utility class for loading and saving Polytone resource packs.
 */
public class ResourcePackLoader {

    private final PolytoneResourcePack polytoneResourcePack;
    private static final String POLYTONE_MAPPINGS                    = "biome_id_mappers";
    private static final String POLYTONE_BLOCK_MODIFIERS_ROOT        = "block_modifiers";
    private static final String POLYTONE_DIMENSIONS_MODIFIERS_ROOT   = "dimension_modifiers";
    private static final String POLYTONE_FLUID_MODIFIERS_ROOT        = "fluid_modifiers";
    private static final String POLYTONE_PARTICLE_MODIFIERS_ROOT     = "particle_modifiers";
    private static final String POLYTONE_COLORMAPS_ROOT              = "colormaps";
    private static final String JSON_EXT = ".json";
    private static final String PNG_EXT = ".png";
    private Path resourcePackPath;

    public ResourcePackLoader(){

        polytoneResourcePack = new PolytoneResourcePack();
    }

    /** Loads the resource pack data from the specified path.
     * @param path the path to load the resource pack from
     * @throws MissingResourceException if a required resource is missing
     * @throws IOException if an I/O error occurs
     */
    public void load(Path path) throws MissingResourceException, IOException {
        readResourcePackData(path.getFileSystem(), path);
    }

    /**
     * Reads the resource pack data from the specified root path.
     * This method processes every Polytone root directories in the resource pack and
     * loads their biome ID mappings and modifiers.
     * @param fileSystem the file system to read from
     * @param root the root path of the resource pack
     * @throws IOException if an I/O error occurs
     */
    protected void readResourcePackData(FileSystem fileSystem, Path root) throws IOException {

        resourcePackPath = root;

        // Find all polytone roots in the resource pack
        Map<String, Path> polytoneRoots = resolvePolytoneRoots(fileSystem.getPath(root.toString()));

        // Read biome ID mappings and colormaps first - modifiers can reference them from other namespaces
        for (var namespace : polytoneRoots.keySet()) {

            ArdaBiomesEditor.LOGGER.info("Processing biome mappers and colormaps for namespace {}", namespace);
            Path subFolder = polytoneRoots.get(namespace);

            readBiomeMappings(namespace, subFolder.resolve(POLYTONE_MAPPINGS));
            readColormaps(namespace, subFolder.resolve(POLYTONE_COLORMAPS_ROOT));
        }

        // Read all modifiers
        for (var namespace : polytoneRoots.keySet()) {

            ArdaBiomesEditor.LOGGER.info("Processing namespace {}", namespace);
            Path subFolder = polytoneRoots.get(namespace);

            // Modifiers
            readModifiers(namespace, subFolder.resolve(POLYTONE_BLOCK_MODIFIERS_ROOT), Modifier.Type.BLOCK);
            readModifiers(namespace, subFolder.resolve(POLYTONE_DIMENSIONS_MODIFIERS_ROOT), Modifier.Type.DIMENSION);
            readModifiers(namespace, subFolder.resolve(POLYTONE_FLUID_MODIFIERS_ROOT), Modifier.Type.FLUID);
            readModifiers(namespace, subFolder.resolve(POLYTONE_PARTICLE_MODIFIERS_ROOT), Modifier.Type.PARTICLE);
        }

        ArdaBiomesEditor.LOGGER.info("Resource pack loaded successfully from {}", root);
    }

    /**
     * Resolves Polytone root directories within the given root path.
     * @param root the root path to search within
     * @return a map of namespaces to their corresponding polytones root paths
     * @throws IOException if an I/O error occurs
     */
    private Map<String, Path> resolvePolytoneRoots(Path root) throws IOException {

        Map<String, Path> polytoneRoots = new HashMap<>();
        List<Path> resolvedPaths;

        // Walk the file tree up to a depth of 3 to find 'polytone' directories
        try (var stream = Files.walk(root, 3)) {

            resolvedPaths = stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().equals("polytone"))
                    .filter(path -> root.relativize(path).getNameCount() == 3)
                    .toList();
        }

        // Map each found 'polytone' directory to its namespace
        resolvedPaths.forEach(path -> polytoneRoots.put(path.getParent().getFileName().toString(), path));

        return polytoneRoots;
    }

    /**
     * Reads biome ID mappings from the specified path and adds them to the PolytoneResourcePack.
     * This method processes a biome_id_mapper (as json). This method handles duplicates keys.
     * @param namespace the current namespace - the polytone root containing the asset
     * @param mappingsPath the path to the biome ID mappings file
     * @throws IOException if an I/O error occurs
     */
    private void readBiomeMappings(String namespace, Path mappingsPath) throws IOException {

        if (Files.exists(mappingsPath) && Files.isDirectory(mappingsPath)) {

            List<Path> biomeIdMappings;

            try (var stream = Files.walk(mappingsPath, 1)) {

                biomeIdMappings = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(JSON_EXT))
                        .toList();
            }

            for (Path biomeIdMappingPath : biomeIdMappings) {

                var fileName = biomeIdMappingPath.getFileName().toString();
                var fileNameWithoutExt = fileName.replaceAll(JSON_EXT, "");

                ArdaBiomesEditor.LOGGER.info("Reading biome mappings {}", fileName);

                JsonReader reader = new JsonReader(new StringReader(Files.readString(biomeIdMappingPath)));
                BiomeIdMapper biomeIdMapper = new BiomeIdMapper(fileNameWithoutExt, biomeIdMappingPath);

                reader.beginObject();
                while (reader.hasNext()) {

                    String key = reader.nextName();
                    int value = reader.nextInt();

                    if (key.equals("texture_size"))
                        biomeIdMapper.setTextureSize(value);
                    else if (!(key.contains(":placeholder")))
                        biomeIdMapper.getMappings().computeIfAbsent(key, k -> value);
                }
                reader.endObject();

                polytoneResourcePack.addBiomeIdMapper(namespace, biomeIdMapper);
            }
        }
    }

    /**
     * Reads colormaps from the specified path and adds them to the PolytoneResourcePack.
     * This method processes colormap files (as json).
     * @param namespace the current namespace - the polytone root containing the asset
     * @param colormapsDirectory the path to the colormaps directory
     * @throws IOException if an I/O error occurs
     */
    private void readColormaps(String namespace, Path colormapsDirectory) throws IOException {

        if (Files.exists(colormapsDirectory) && Files.isDirectory(colormapsDirectory)) {

            List<Path> colormapFiles;

            try (var stream = Files.walk(colormapsDirectory, 1)) {

                colormapFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(JSON_EXT))
                        .toList();
            }

            for (Path colormapPath : colormapFiles) {

                var fileName = colormapPath.getFileName().toString();
                var textureFilePath = colormapPath.getParent().resolve(fileName.replaceAll(JSON_EXT, PNG_EXT));
                var fileNameWithoutExt = fileName.replaceAll(JSON_EXT, "");

                ArdaBiomesEditor.LOGGER.info("Reading colormap definition {}", fileName);

                Colormap colormap = new Colormap(fileNameWithoutExt, colormapPath, textureFilePath);

                var jsonString = Files.readString(colormapPath);
                JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();

                readColormap(colormap, namespace, fileNameWithoutExt, root);
                polytoneResourcePack.addColormap(namespace, colormap);
            }
        }
    }

    /**
     * Reads all modifiers from the specified path and adds them to the PolytoneResourcePack.
     * This method processes modifier files (as json).
     * @param namespace the current namespace - the polytone root containing the asset
     * @param modifiersPath the path to the modifiers directory
     * @param modifierType the type of modifier being processed
     * @throws IOException if an I/O error occurs
     */
    private void readModifiers(String namespace, Path modifiersPath, Modifier.Type modifierType) throws IOException {

        if (Files.exists(modifiersPath) && Files.isDirectory(modifiersPath)) {

            ArdaBiomesEditor.LOGGER.info("Processing modifiers {}", modifiersPath);
            List<Path> jsonModifiers;

            try (var stream = Files.walk(modifiersPath, 1)) {

                jsonModifiers = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !path.toString().startsWith("_") && path.toString().endsWith(JSON_EXT))
                        .toList();
            }

            for (Path modifierPath : jsonModifiers) {

                var modifierName = modifierPath.getFileName().toString().replaceAll(JSON_EXT, "");
                Modifier modifier = new Modifier(modifierName, modifierPath, modifierType);

                var jsonString = Files.readString(modifierPath);
                JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();

                // Process inlined colormaps
                Map<String, JsonObject> inlinedColormaps = root.entrySet().stream()
                        .filter(entry -> entry.getKey().endsWith("colormap"))
                        .filter(entry -> entry.getValue().isJsonObject())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().getAsJsonObject()
                        ));

                // Process referenced colormaps
                Map<String, String> referencedColormaps = root.entrySet().stream()
                        .filter(entry -> entry.getKey().endsWith("colormap"))
                        .filter(entry -> entry.getValue().isJsonPrimitive())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().getAsString()
                        ));

                for (var colormapKey : inlinedColormaps.keySet()) {

                    JsonObject colormapObject = inlinedColormaps.get(colormapKey);

                    String colormapName = resolveColormapName(colormapKey, modifierPath);
                    Path colormapPath = getColormapPath(colormapName, modifierPath);

                    Colormap colormap = new Colormap(colormapName, modifierPath, colormapPath, PolytoneAssetDeclarationType.INLINE, modifier);
                    readColormap(colormap, namespace, colormapKey, colormapObject);

                    modifier.getColormaps().add(colormap);
                    polytoneResourcePack.addColormap(namespace, colormap);
                }

                for (var colormapKey : referencedColormaps.keySet()) {

                    String colormapRef = referencedColormaps.get(colormapKey);
                    Namespace colormapNamespace = Namespace.fromString(colormapRef);

                    if (colormapNamespace != null) {

                        Colormap colormap = polytoneResourcePack.getColormaps().get(colormapNamespace);

                        if (colormap != null) modifier.getColormaps().add(colormap);
                    }
                }

                polytoneResourcePack.addModifier(namespace, modifier);
            }
        }
    }

    /**
     * Reads a colormap from the given JSON object.
     *
     * @param colormapKey the namespace of the colormap
     * @param colormapObject the JSON object representing the colormap (can be inline in a modifier or a file content)
     */
    private void readColormap(Colormap colormap, String namespace, String colormapKey, JsonObject colormapObject) {

        var xAxis = colormapObject.get("x_axis");
        var yAxis = colormapObject.get("y_axis");
        var biomeIdMapper = colormapObject.get("biome_id_mapper");

        if (xAxis != null) colormap.setXAxis(xAxis.getAsString());
        if (yAxis != null) colormap.setYAxis(yAxis.getAsString());

        if (biomeIdMapper != null) {

            // Biome ID mapper reference - find the correct mapper
            if (biomeIdMapper.isJsonPrimitive() && biomeIdMapper.getAsJsonPrimitive().isString()) {

                var biomeIdMapperNamespace = Namespace.fromString(biomeIdMapper.getAsString());

                if (biomeIdMapperNamespace != null) {

                    BiomeIdMapper mapper = polytoneResourcePack.getBiomeIdMappers().get(biomeIdMapperNamespace);
                    colormap.setBiomeIdMapper(mapper);
                }
            // Inline biome ID mapper
            } else if (biomeIdMapper.isJsonObject()) {

                JsonObject mapperObject = biomeIdMapper.getAsJsonObject();
                BiomeIdMapper mapper = new BiomeIdMapper(colormapKey, colormap.getPath(), PolytoneAssetDeclarationType.INLINE, colormap);

                for (var entry : mapperObject.entrySet()) {

                    String key = entry.getKey();
                    int value = entry.getValue().getAsInt();

                    if (key.equals("texture_size"))
                        mapper.setTextureSize(value);
                    else
                        mapper.getMappings().put(key, value);
                }
                polytoneResourcePack.addBiomeIdMapper(namespace, mapper);
                colormap.setBiomeIdMapper(mapper);
            }
        }
    }

    /**
     * Resolves the colormap name based on the colormap key and the file path.
     * By default the colormap name is the filename without extension.
     * If the colormap key has a qualifier (e.g., "temperature_colormap"), then the qualifier is appended to the filename.
     *
     * @param colormapKey the key of the colormap (e.g., "temperature_colormap")
     * @param filePath the path to the file containing the colormap definition
     * @return the resolved colormap name
     */
    private String resolveColormapName(String colormapKey, Path filePath) {

        var filenameWithoutExt = filePath.getFileName().toString().replaceAll(JSON_EXT, "");
        var qualifier = colormapKey.replaceAll("_?colormap$", "");

        if (qualifier.isBlank())
            return filenameWithoutExt;
        else
            return String.format("%s_%s", filenameWithoutExt, qualifier);
    }

    /**
     * Constructs the expected colormap PNG file path based on the colormap key and the file path.
     *
     * @param colormapName the name of the colormap (e.g., "temperature_colormap")
     * @param filePath the path to the file containing the colormap definition
     * @return the constructed Path to the colormap PNG file
     */
    private Path getColormapPath(String colormapName, Path filePath) {

        return filePath.getParent().resolve(String.format("%s%s", colormapName, PNG_EXT));
    }

    /** Saves the color changes to the root biome. Each color change is tied to a modifier.
     * @param root the root resource identifier
     * @param colorChanges the color changes to apply
     * @param progressCallback a callback for reporting progress
     * @throws MissingResourceException if a required resource is missing
     * @throws IOException if an I/O error occurs
     */
    public void persistBiomeMappedColorChanges(ResourceIdentifier root, Map<ResourceIdentifier, int[]> colorChanges, BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException {

        var totalEntries = colorChanges.size();
        var progress = 0d;
        var biomeIndex = root.index();
        ArdaBiomesEditor.LOGGER.info("Updating {} assets", totalEntries);
        progressCallback.accept("Resolving " + totalEntries + " paths ", progress);

        Map<Colormap, Map<Integer, int[]>> resolveColormaps = new HashMap<>();
        Map<Namespace, Colormap> colormaps = polytoneResourcePack.getColormaps();

        for (ResourceIdentifier modifierIdentifier : colorChanges.keySet()) {

            Colormap colormap = colormaps.get(modifierIdentifier.namespace());

            if (colormap != null) {

                progress++;
                progressCallback.accept("Resolving " + modifierIdentifier, (progress / totalEntries * 2) / 100 );
                ArdaBiomesEditor.LOGGER.info("Resolving {}", modifierIdentifier);

                Map<Integer, int[]> biomeColors = new HashMap<>();
                biomeColors.put(biomeIndex, colorChanges.get(modifierIdentifier));
                resolveColormaps.put(colormap, biomeColors);
            }
        }

        persistColormaps(progressCallback, resolveColormaps, progress, totalEntries);
    }

    /**
     * Persists the colormaps with the specified color changes.
     * @param progressCallback a callback for reporting progress
     * @param colormapsChanges the colormaps to update with their respective color changes
     * @param progress the current progress value
     * @param totalEntries the total number of entries to process
     * @throws IOException if an I/O error occurs
     */
    private void persistColormaps(BiConsumer<String, Double> progressCallback, Map<Colormap, Map<Integer, int[]>> colormapsChanges, double progress, int totalEntries) throws IOException {

        for (Colormap colormap : colormapsChanges.keySet()) {

            ArdaBiomesEditor.LOGGER.info("Updating texture: {}", colormap.getTexturePath());
            progress++;
            progressCallback.accept("Writing " + colormap.getTexturePath(), (progress / totalEntries * 2) / 100 );

            ColorMapService.applyIndexedColorChangesToColormapTexture(colormap, colormapsChanges.get(colormap));
        }
    }

    /** Saves the color changes to the root modifier. Each color change is tied to a biome ID.
     * @param root the root resource identifier
     * @param colorChanges the color changes to apply
     * @param progressCallback a callback for reporting progress
     * @throws MissingResourceException if a required resource is missing
     * @throws IOException if an I/O error occurs
     */
    public void persistColormapColorChanges(ResourceIdentifier root, Map<ResourceIdentifier, int[]> colorChanges, BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException {

        var totalEntries = colorChanges.size();
        var progress = 0d;
        Map<Integer, int[]> indexedColors = new HashMap<>();
        Map<Colormap, Map<Integer, int[]>> resolvedColormaps = new HashMap<>();

        ArdaBiomesEditor.LOGGER.info("Updating {}", root.toString());
        progressCallback.accept("Resolving colormap path", progress);

        Colormap colormap = polytoneResourcePack.getColormaps().get(root.namespace());

        if (colormap != null) {

            // If the colormap is biome-mapped (either on x or y axis), resolve the biome IDs to indexes
            if (colormap.getxAxisMappingType() == Colormap.AxisMappingType.BIOME_ID ||
                colormap.getyAxisMappingType() == Colormap.AxisMappingType.BIOME_ID){

                resolveBiomeMappedColorChanges(root, colorChanges, colormap, indexedColors);
            } else {

                // Directly map resource IDs to indexes
                for (ResourceIdentifier resourceId : colorChanges.keySet()) {

                    indexedColors.put(resourceId.index(), colorChanges.get(resourceId));
                }
            }

            resolvedColormaps.put(colormap, indexedColors);
            persistColormaps(progressCallback, resolvedColormaps, progress, totalEntries);
        }
    }

    /**
     * Resolves biome-mapped color changes into indexed color changes based on the colormap's biome ID mapper.
     * @param root the root resource identifier
     * @param colorChanges the biome-mapped color changes
     * @param colormap the colormap associated with the color changes
     * @param indexedColors the map to populate with indexed color changes
     */
    private void resolveBiomeMappedColorChanges(ResourceIdentifier root, Map<ResourceIdentifier, int[]> colorChanges, Colormap colormap, Map<Integer, int[]> indexedColors) {

        BiomeIdMapper idMapper = colormap.getBiomeIdMapper();

        // Resolve from namespace
        if (idMapper == null) {

            idMapper = polytoneResourcePack.getBiomeIdMappers().entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().name().equals(root.namespace().name()))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElseGet(()->BiomeIdMapper.EMPTY);
        }

        Map<String, Integer> mappings = idMapper.getMappings();

        // Resolve a filtered list of all mappings targeted by the color changes
        for (ResourceIdentifier resourceId : colorChanges.keySet()) {

            Integer mappingIndex = mappings.get(resourceId.path());
            int[] colors = colorChanges.get(resourceId);;

            // Try to parse the resource ID path as an integer index if no mapping found
            if (mappingIndex == null) {

                try {
                    mappingIndex = Integer.parseInt(resourceId.path().trim());
                    colors = colorChanges.entrySet().stream()
                            .filter(entry -> entry.getKey().path().equals(resourceId.path()))
                            .findFirst()
                            .map(Map.Entry::getValue)
                            .orElse(colors);

                } catch (NumberFormatException ex) {/* Ignore - if this happens, an error should be raised during persistence */}
            }

            indexedColors.put(mappingIndex,colors);
        }
    }

    /** @return the path of the loaded resource pack. */
    public Path getResourcePackPath() {
        return resourcePackPath;
    }

    /** @return the loaded PolytoneResourcePack. */
    public PolytoneResourcePack getPolytoneResourcePack() {
        return polytoneResourcePack;
    }
}