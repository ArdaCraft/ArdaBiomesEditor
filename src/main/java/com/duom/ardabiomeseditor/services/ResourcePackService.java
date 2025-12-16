package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.model.Namespace;
import com.duom.ardabiomeseditor.model.ResourceIdentifier;
import com.duom.ardabiomeseditor.model.ResourcePackTreeNode;
import com.duom.ardabiomeseditor.model.polytone.BiomeIdMapper;
import com.duom.ardabiomeseditor.model.polytone.Colormap;
import com.duom.ardabiomeseditor.model.polytone.PolytoneAssetDeclarationType;
import com.duom.ardabiomeseditor.services.loaders.ResourcePackLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Service for reading and processing resource packs containing Polytone data.
 * Provides methods to read resource packs, persist color changes, and retrieve biome mappings.
 */
public class ResourcePackService {

    private ResourcePackLoader loader;
    private ResourcePackTreeService treeService;

    /**
     * Reads the resource pack from the specified path.
     * Extracts and processes relevant data from the resource pack.
     *
     * @param path The path to the resource pack file.
     */
    public void readResourcePack(Path path) throws MissingResourceException, IOException {

        loader = new ResourcePackLoader();
        loader.load(path);
        treeService = new ResourcePackTreeService(path, loader.getPolytoneResourcePack());
    }

    /**
     * Persists color changes to the resource pack.
     *
     * @param root               The root resource identifier.
     * @param colorChanges       A map of resource identifiers to their corresponding color changes.
     * @param biomeMappedChanges Indicates if the changes are biome-mapped.
     * @param progressCallback   A callback function to report progress.
     * @throws MissingResourceException If a required resource is missing.
     * @throws IOException              If an I/O error occurs during persistence.
     */
    public void persistColorChanges(ResourceIdentifier root,
                                    Map<ResourceIdentifier, int[]> colorChanges,
                                    boolean biomeMappedChanges,
                                    BiConsumer<String, Double> progressCallback) throws MissingResourceException, IOException {

        if (loader != null) {
            if (biomeMappedChanges)
                loader.persistBiomeMappedColorChanges(root, colorChanges, progressCallback);
            else
                loader.persistColormapColorChanges(root, colorChanges, progressCallback);
        }
    }

    /**
     * Retrieves the path to the currently loaded resource pack.
     *
     * @return The path to the resource pack.
     */
    public Path getCurrentResourcePackPath() {

        return loader != null ? loader.getResourcePackPath() : null;
    }

    /**
     * Retrieves the name of the currently loaded resource pack.
     *
     * @return The name of the resource pack.
     */
    public String getCurrentResourcePackName() {

        return loader != null ? loader.getResourcePackPath().getFileName().toString() : null;
    }

    /**
     * Retrieves a hierarchical tree structure of biome ID mappers.
     *
     * @return The root node of the biome ID mappers tree.
     */
    public ResourcePackTreeNode getBiomeIdMappersTree() {

        return treeService.getBiomeIdMappersTree();
    }

    public ResourcePackTreeNode getResourceTree() {

        return treeService.getResourceTree();
    }

    /**
     * Retrieves a hierarchical tree structure of colormaps.
     *
     * @return A map representing the colormaps tree structure.
     */
    public ResourcePackTreeNode getColormapsTree() {

        return treeService.getColormapsTree();
    }

    /**
     * Retrieves a mapping of resource identifiers to lists of hex color codes for the specified biome.
     *
     * @param identifier The resource identifier of the biome.
     * @return A map where keys are resource identifiers of colormaps and values are lists of hex color codes.
     */
    public Map<ResourceIdentifier, int[]> getMappedColorsFromBiome(ResourceIdentifier identifier) {

        Map<ResourceIdentifier, int[]> colorMappings = new HashMap<>();

        if (identifier != null) {

            var parentNamespace = identifier.namespace();
            BiomeIdMapper biomeIdMapper = getBiomeIdMapper(identifier);
            List<Colormap> colormapsInNamespace = new ArrayList<>();
            Map<Namespace, Colormap> allColormaps = loader.getPolytoneResourcePack().getColormaps();

            int mappedBiomeIndex = biomeIdMapper.getMappings().getOrDefault(identifier.path(), -1);

            // If the mapping is inlined, find the declaring colormap
            if (biomeIdMapper.getDeclarationType() == PolytoneAssetDeclarationType.INLINE) {

                colormapsInNamespace.add(allColormaps.values().stream()
                        .filter(colormap -> colormap.getPath().equals(biomeIdMapper.getPath()))
                        .findFirst()
                        .orElse(Colormap.EMPTY));

                // Else find all non-applicable colormaps - ie not  inlined
            } else {

                colormapsInNamespace = allColormaps.values().stream()
                        .filter(colormap -> colormap.getBiomeIdMapper() == null || colormap.getBiomeIdMapper().equals(biomeIdMapper))
                        .toList();
            }

            for (Colormap colormap : colormapsInNamespace) {

                int[] colormapColors = ColorMapService.getColorsForBiomeId(colormap, mappedBiomeIndex);

                if (colormapColors != null && colormapColors.length > 0) {

                    var modifierName = colormap.getName();

                    var modifierNamespace = new Namespace(parentNamespace.name(), modifierName);

                    colorMappings.put(new ResourceIdentifier(modifierNamespace,
                                    colormap.getName(),
                                    ResourceIdentifier.DisplayStyle.PATH,
                                    ResourceIdentifier.ComparisonMethod.LOCAL_NAME),
                            colormapColors);
                }
            }
        }

        return colorMappings;
    }

    /**
     * Retrieves the BiomeIdMapper for the specified resource identifier.
     *
     * @param identifier The resource identifier of the biome.
     * @return The BiomeIdMapper associated with the given identifier.
     */
    private BiomeIdMapper getBiomeIdMapper(ResourceIdentifier identifier) {

        Map<Namespace, BiomeIdMapper> biomeIdMappers = loader.getPolytoneResourcePack().getBiomeIdMappers();
        return biomeIdMappers.getOrDefault(identifier.namespace(), BiomeIdMapper.EMPTY);
    }

    /**
     * Retrieves a mapping of resource identifiers to lists of argb int color codes for all colors
     * defined in the colormap associated with the specified modifier.
     *
     * @param identifier The resource identifier of the modifier.
     * @return A map where keys are resource identifiers and values are lists of argb int color codes.
     */
    public Map<ResourceIdentifier, int[]> getColormapColors(ResourceIdentifier identifier) {

        Map<ResourceIdentifier, int[]> colorMappings = new HashMap<>();
        var parentNamespace = identifier.namespace();

        Colormap colormap = getColormap(parentNamespace);

        boolean xAxisBiomeIdMapped = colormap.getxAxisMappingType() == Colormap.AxisMappingType.BIOME_ID;
        boolean yAxisBiomeIdMapped = colormap.getyAxisMappingType() == Colormap.AxisMappingType.BIOME_ID;

        // If either axis is biome ID mapped, retrieve biome mapped colors
        if (xAxisBiomeIdMapped || yAxisBiomeIdMapped) {

            colorMappings = getBiomeMappedColorsFromColormap(identifier);

        } else {

            var allColors = ColorMapService.getAllColors(colormap);
            int width = colormap.getTextureWidth();
            int height = colormap.getTextureHeight();

            for (int idx = 0; idx < width; idx++) {

                int[] slice = new int[height];

                for (int y = 0; y < height; y++) {
                    slice[y] = allColors[idx * height + y];
                }

                ResourceIdentifier id = new ResourceIdentifier(
                        identifier.namespace(),
                        Integer.toString(idx),
                        idx,
                        ResourceIdentifier.DisplayStyle.PATH,
                        ResourceIdentifier.ComparisonMethod.INDEX
                );

                colorMappings.put(id, slice);
            }
        }

        return colorMappings;
    }

    /**
     * Retrieves the colormap corresponding to the given namespace.
     *
     * @param namespace The namespace of the colormap.
     * @return The colormap if found; otherwise, an empty colormap.
     */
    public Colormap getColormap(Namespace namespace) {

        return loader.getPolytoneResourcePack().getColormaps().getOrDefault(namespace, Colormap.EMPTY);
    }

    /**
     * Retrieves a mapping of resource identifiers to lists of argb int color codes for all biomes
     * associated with the specified modifier.
     *
     * @param identifier The resource identifier of the modifier.
     * @return A map where keys are resource identifiers of biomes and values are lists of argb int color codes.
     */
    public Map<ResourceIdentifier, int[]> getBiomeMappedColorsFromColormap(ResourceIdentifier identifier) {

        Map<ResourceIdentifier, int[]> colorMappings = new HashMap<>();
        var parentNamespace = identifier.namespace();

        Colormap colormap = getColormap(parentNamespace);

        final BiomeIdMapper biomeIdMapper = getColormapBiomeIdMapper(identifier.namespace(), colormap);

        var allColors = ColorMapService.getAllColors(colormap);
        int width = colormap.getTextureWidth();
        int height = colormap.getTextureHeight();

        boolean xAxisBiomeIdMapped = colormap.getxAxisMappingType() == Colormap.AxisMappingType.BIOME_ID;
        boolean yAxisBiomeIdMapped = colormap.getyAxisMappingType() == Colormap.AxisMappingType.BIOME_ID;

        for (int biomeIndex = 0; biomeIndex < (xAxisBiomeIdMapped ? width : height); biomeIndex++) {

            int[] slice;

            // Case 1: X = BIOME_ID, Y = BIOME_ID - single pixel
            if (xAxisBiomeIdMapped && yAxisBiomeIdMapped) {

                // biomeIndex maps to both axes
                int color = (biomeIndex < height)
                        ? allColors[biomeIndex * height + biomeIndex]
                        : 0x00000000; // Transparent

                slice = new int[]{color};

                // Case 2: X = BIOME_ID maps to a  column
            } else if (xAxisBiomeIdMapped) {

                slice = new int[height];

                for (int y = 0; y < height; y++) {
                    slice[y] = allColors[biomeIndex * height + y];
                }

                // Case 3: Y = BIOME_ID maps to a row
            } else {

                slice = new int[width];

                for (int x = 0; x < width; x++) {
                    slice[x] = allColors[x * height + biomeIndex];
                }
            }

            int finalBiomeIndex = biomeIndex;
            var biomeName = biomeIdMapper.getMappings().entrySet()
                    .stream()
                    .filter(e -> e.getValue() == finalBiomeIndex)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(Integer.toString(biomeIndex));

            ResourceIdentifier id = new ResourceIdentifier(
                    new Namespace(parentNamespace.name(), biomeIdMapper.getName()),
                    biomeName,
                    biomeIndex,
                    ResourceIdentifier.DisplayStyle.PATH,
                    ResourceIdentifier.ComparisonMethod.INDEX
            );

            colorMappings.put(id, slice);
        }

        return colorMappings;
    }

    /**
     * Retrieves the BiomeIdMapper associated with the given colormap.
     * If the colormap does not have an explicitly set mapper, it resolves it from the colormap's namespace.
     *
     * @param namespace the namespace of the colormap
     * @param colormap  the colormap to retrieve the mapper for
     * @return the BiomeIdMapper associated with the colormap
     */
    private BiomeIdMapper getColormapBiomeIdMapper(Namespace namespace, Colormap colormap) {

        BiomeIdMapper biomeIdMapper = colormap.getBiomeIdMapper();

        // Mapper is not explicitly set - resolve it from the colormaps namespace
        if (biomeIdMapper == null) {

            var biomeIdMappers = loader.getPolytoneResourcePack().getBiomeIdMappers();
            biomeIdMapper = biomeIdMappers.keySet()
                    .stream()
                    .filter(mapperNs -> mapperNs.name().equals(namespace.name()))
                    .findFirst()
                    .map(biomeIdMappers::get)
                    .orElse(BiomeIdMapper.EMPTY);

        }
        return biomeIdMapper;
    }
}