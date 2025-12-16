package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.model.Namespace;
import com.duom.ardabiomeseditor.model.ResourceIdentifier;
import com.duom.ardabiomeseditor.model.ResourcePackTreeNode;
import com.duom.ardabiomeseditor.model.polytone.*;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for building tree structures representing resources in a Polytone resource pack.
 */
public class ResourcePackTreeService {

    private final PolytoneResourcePack resourcePack;

    private final Path resourcePackPath;

    private ResourcePackTreeNode resourcePackTree;
    private ResourcePackTreeNode biomeIdMappersTree;
    private ResourcePackTreeNode colormapsTree;

    public ResourcePackTreeService(Path resourcePackPath, PolytoneResourcePack resourcePack) {

        this.resourcePackPath = resourcePackPath;
        this.resourcePack = resourcePack;
    }

    /**
     * Builds and returns the tree structure representing colormaps in the resource pack.
     *
     * @return The root node of the colormaps tree.
     */
    public ResourcePackTreeNode getColormapsTree() {

        if (colormapsTree == null) {

            colormapsTree = new ResourcePackTreeNode(resourcePackPath.getFileName().toString(),
                    resourcePackPath,
                    ResourcePackTreeNode.Type.DIRECTORY);

            // Find all unique root namespaces
            var rootNamespaces = getUniqueRootNamespaces(resourcePack.getColormaps());

            for (String namespace : rootNamespaces) {

                var namespaceNode = new ResourcePackTreeNode(namespace,
                        resourcePackPath.resolve(namespace),
                        ResourcePackTreeNode.Type.DIRECTORY);

                buildStandaloneColormapsTree(namespace, namespaceNode, false);
                buildInlinedColormapsTree(namespace, namespaceNode, false);

                colormapsTree.addChildIfNotEmpty(namespaceNode);
            }
        }

        return colormapsTree;
    }

    /**
     * Builds the tree structure for standalone colormaps (ie defined in the colormaps subfolder) within the specified namespace.
     *
     * @param namespace     The namespace to build the tree for.
     * @param namespaceNode The parent node representing the namespace.
     */
    private void buildStandaloneColormapsTree(String namespace, ResourcePackTreeNode namespaceNode, boolean includeInlinedMappers) {

        var standaloneColormapsInNamespace = getStandaloneColormapsInNamespace(namespace);

        var standaloneColormapsNode = new ResourcePackTreeNode("colormaps",
                resourcePackPath.resolve(namespace),
                ResourcePackTreeNode.Type.DIRECTORY);

        for (Colormap standaloneColormap : standaloneColormapsInNamespace) {

            var colormapNode = new ResourcePackTreeNode(standaloneColormap.getName(),
                    resourcePackPath.resolve(standaloneColormap.getPath()),
                    ResourcePackTreeNode.Type.COLORMAP,
                    new ResourceIdentifier(new Namespace(namespace, standaloneColormap.getName()),
                            standaloneColormap.getName(),
                            ResourceIdentifier.DisplayStyle.PATH,
                            ResourceIdentifier.ComparisonMethod.LOCAL_NAME));

            if (includeInlinedMappers) {

                buildInlinedBiomeIdMapperNode(namespace, standaloneColormap.getBiomeIdMapper(), colormapNode);
            }

            standaloneColormapsNode.addChild(colormapNode);
        }

        namespaceNode.addChildIfNotEmpty(standaloneColormapsNode);
    }

    /**
     * Builds the tree structure for inlined colormaps within the specified namespace.
     * Works on the modifiers belonging to the specified namespace
     * since inlined colormaps can only be declared within modifiers.
     *
     * @param namespace     The namespace to build the tree for.
     * @param namespaceNode The parent node representing the namespace.
     */
    private void buildInlinedColormapsTree(String namespace, ResourcePackTreeNode namespaceNode, boolean includeInlinedMappers) {

        // Group modifiers by type within the namespace
        var allModifiersInNamespace = getModifiersInNamespace(namespace);

        for (var modifierType : allModifiersInNamespace.keySet()) {

            var modifierTypeNode = new ResourcePackTreeNode(modifierType.toString(),
                    resourcePackPath.resolve(modifierType.toString()),
                    ResourcePackTreeNode.Type.DIRECTORY);

            for (Modifier modifier : allModifiersInNamespace.get(modifierType)) {

                var modifierNode = new ResourcePackTreeNode(modifier.getName(),
                        resourcePackPath.resolve(namespace),
                        ResourcePackTreeNode.Type.MODIFIER);

                for (Colormap inlinedColormap : modifier.getColormaps()) {

                    if (inlinedColormap.getDeclarationType() == PolytoneAssetDeclarationType.INLINE) {

                        var colormapNode = new ResourcePackTreeNode(inlinedColormap.getName(),
                                resourcePackPath.resolve(inlinedColormap.getPath()),
                                ResourcePackTreeNode.Type.COLORMAP,
                                new ResourceIdentifier(new Namespace(namespace, inlinedColormap.getName()),
                                        inlinedColormap.getName(),
                                        ResourceIdentifier.DisplayStyle.PATH,
                                        ResourceIdentifier.ComparisonMethod.LOCAL_NAME));

                        if (includeInlinedMappers) {
                            buildInlinedBiomeIdMapperNode(namespace, inlinedColormap.getBiomeIdMapper(), colormapNode);
                        }

                        modifierNode.addChild(colormapNode);
                    }
                }

                modifierTypeNode.addChildIfNotEmpty(modifierNode);
            }

            namespaceNode.addChildIfNotEmpty(modifierTypeNode);
        }
    }

    /**
     * Builds and returns the tree structure representing all resources in the resource pack.
     *
     * @return The root node of the resource pack tree.
     */
    public ResourcePackTreeNode getResourceTree() {

        if (resourcePackTree == null) {

            resourcePackTree = new ResourcePackTreeNode(resourcePackPath.getFileName().toString(),
                    resourcePackPath,
                    ResourcePackTreeNode.Type.DIRECTORY);

            var allNamespaces = getUniqueRootNamespaces(resourcePack.getBiomeIdMappers());
            allNamespaces.addAll(getUniqueRootNamespaces(resourcePack.getColormaps()));
            allNamespaces.addAll(getUniqueRootNamespaces(resourcePack.getModifiers()));

            for (String namespace : allNamespaces) {

                var namespaceNode = new ResourcePackTreeNode(namespace,
                        resourcePackPath.resolve(namespace),
                        ResourcePackTreeNode.Type.DIRECTORY);

                // Build subtrees
                buildStandaloneMappersTree(namespace, namespaceNode);
                buildStandaloneColormapsTree(namespace, namespaceNode, true);
                buildInlinedColormapsTree(namespace, namespaceNode, true);

                resourcePackTree.addChildIfNotEmpty(namespaceNode);
            }
        }

        return resourcePackTree;
    }

    /**
     * Builds and returns the tree structure representing biome ID mappers in the resource pack.
     *
     * @return The root node of the biome ID mappers tree.
     */
    public ResourcePackTreeNode getBiomeIdMappersTree() {

        if (biomeIdMappersTree == null) {

            biomeIdMappersTree = new ResourcePackTreeNode(resourcePackPath.getFileName().toString(),
                    resourcePackPath,
                    ResourcePackTreeNode.Type.DIRECTORY);

            // Group mappers by namespace
            var uniqueNamespaces = getUniqueRootNamespaces(resourcePack.getBiomeIdMappers());

            for (String namespace : uniqueNamespaces) {

                var namespaceNode = new ResourcePackTreeNode(namespace,
                        resourcePackPath.resolve(namespace),
                        ResourcePackTreeNode.Type.DIRECTORY);

                buildStandaloneMappersTree(namespace, namespaceNode);
                buildInlinedMappersTree(namespace, namespaceNode);

                biomeIdMappersTree.addChild(namespaceNode);
            }
        }

        return biomeIdMappersTree;
    }

    /**
     * Retrieves the set of unique root namespaces from the given assets map.
     *
     * @param assets The map of namespaces to assets.
     * @return A set of unique root namespace names.
     */
    private Set<String> getUniqueRootNamespaces(Map<Namespace, ?> assets) {

        return assets.keySet()
                .stream()
                .map(Namespace::name)
                .collect(Collectors.toSet());
    }

    /**
     * Builds the tree structure for standalone biome ID mappers within the specified namespace.
     *
     * @param namespace     The namespace to build the tree for.
     * @param namespaceNode The parent node representing the namespace.
     */
    private void buildStandaloneMappersTree(String namespace, ResourcePackTreeNode namespaceNode) {

        var standaloneMappersInNamespace = resourcePack.getBiomeIdMappers().entrySet()
                .stream()
                .filter(entry -> entry.getKey().name().equals(namespace))
                .filter(entry -> entry.getValue().getDeclarationType() == PolytoneAssetDeclarationType.STANDALONE)
                .map(Map.Entry::getValue)
                .toList();

        if (!standaloneMappersInNamespace.isEmpty()) {

            var standaloneMappersNode = new ResourcePackTreeNode("biome_id_mappers",
                    resourcePackPath.resolve(namespace),
                    ResourcePackTreeNode.Type.DIRECTORY);

            for (BiomeIdMapper standaloneMapper : standaloneMappersInNamespace) {

                var mapperNode = new ResourcePackTreeNode(standaloneMapper.getName(),
                        resourcePackPath.resolve(standaloneMapper.getPath()),
                        ResourcePackTreeNode.Type.BIOME_ID_MAPPER,
                        new ResourceIdentifier(
                                new Namespace(namespace, standaloneMapper.getName()),
                                standaloneMapper.getName(),
                                ResourceIdentifier.DisplayStyle.PATH,
                                ResourceIdentifier.ComparisonMethod.LOCAL_NAME));

                buildBiomeIdMapperLeaves(namespace, standaloneMapper, mapperNode);
                standaloneMappersNode.addChild(mapperNode);
            }

            namespaceNode.addChild(standaloneMappersNode);
        }
    }

    /**
     * Builds the tree structure for inlined biome ID mappers within the specified namespace.
     * Inlined mappers can be declared within colormaps either standalone (colormaps in the colormaps folder) or inlined
     * within a modifier.
     *
     * @param namespace     The namespace to build the tree for.
     * @param namespaceNode The parent node representing the namespace.
     */
    private void buildInlinedMappersTree(String namespace, ResourcePackTreeNode namespaceNode) {

        var standaloneColormaps = getStandaloneColormapsInNamespace(namespace);
        var modifiersInNamespace = getModifiersInNamespace(namespace);

        // Standalone colormaps

        var colormapsDirectoryNode = new ResourcePackTreeNode("colormaps",
                resourcePackPath.resolve("colormaps"),
                ResourcePackTreeNode.Type.DIRECTORY);

        for (Colormap colormap : standaloneColormaps) {

            var biomeIdMapper = colormap.getBiomeIdMapper();
            ResourcePackTreeNode colormapNode = new ResourcePackTreeNode(colormap.getName(),
                    resourcePackPath.resolve(colormap.getPath()),
                    ResourcePackTreeNode.Type.COLORMAP);

            buildInlinedBiomeIdMapperNode(namespace, biomeIdMapper, colormapNode);
            colormapsDirectoryNode.addChildIfNotEmpty(colormapNode);
        }

        namespaceNode.addChildIfNotEmpty(colormapsDirectoryNode);

        // Inlined colormaps within modifiers
        for (var modifierType : modifiersInNamespace.keySet()) {

            var modifierTypeNode = new ResourcePackTreeNode(modifierType.toString(),
                    resourcePackPath.resolve(modifierType.toString()),
                    ResourcePackTreeNode.Type.DIRECTORY);

            for (Modifier modifier : modifiersInNamespace.get(modifierType)) {

                for (Colormap modifierColormap : modifier.getColormaps()) {

                    boolean colormapIsInlined = modifierColormap.getDeclarationType() == PolytoneAssetDeclarationType.INLINE;
                    boolean colormapHasInlinedMapper = modifierColormap.getBiomeIdMapper() != null &&
                            modifierColormap.getBiomeIdMapper().getDeclarationType() == PolytoneAssetDeclarationType.INLINE;

                    if (colormapIsInlined && colormapHasInlinedMapper) {

                        var modifierNode = new ResourcePackTreeNode(modifier.getName(),
                                resourcePackPath.resolve(namespace),
                                ResourcePackTreeNode.Type.MODIFIER);

                        var colormapNode = new ResourcePackTreeNode(modifierColormap.getName(),
                                resourcePackPath.resolve(modifierColormap.getPath()),
                                ResourcePackTreeNode.Type.COLORMAP);

                        var biomeIdMapper = modifierColormap.getBiomeIdMapper();

                        var mapperNode = new ResourcePackTreeNode("biome_id_mapper",
                                resourcePackPath.resolve(biomeIdMapper.getPath()),
                                ResourcePackTreeNode.Type.BIOME_ID_MAPPER,
                                new ResourceIdentifier(new Namespace(namespace, biomeIdMapper.getName()),
                                        "biome_id_mapper",
                                        ResourceIdentifier.DisplayStyle.PATH,
                                        ResourceIdentifier.ComparisonMethod.LOCAL_NAME));

                        buildBiomeIdMapperLeaves(namespace, biomeIdMapper, mapperNode);

                        colormapNode.addChildIfNotEmpty(mapperNode);
                        modifierNode.addChildIfNotEmpty(colormapNode);
                        modifierTypeNode.addChildIfNotEmpty(modifierNode);
                    }
                }

                namespaceNode.addChildIfNotEmpty(modifierTypeNode);
            }
        }
    }

    /**
     * Builds the leaf nodes for a biome ID mapper, representing its biome mappings.
     *
     * @param namespace        The namespace of the mapper.
     * @param standaloneMapper The biome ID mapper to build leaves for.
     * @param mapperNode       The parent node representing the biome ID mapper.
     */
    private void buildBiomeIdMapperLeaves(String namespace, BiomeIdMapper standaloneMapper, ResourcePackTreeNode mapperNode) {

        for (var mappingEntry : standaloneMapper.getMappings().entrySet()) {

            var biomeName = mappingEntry.getKey();
            var biomeIndex = mappingEntry.getValue();

            var biomeNode = new ResourcePackTreeNode(biomeName,
                    resourcePackPath.resolve(standaloneMapper.getPath()),
                    ResourcePackTreeNode.Type.BIOME_MAPPING,
                    new ResourceIdentifier(new Namespace(namespace, standaloneMapper.getName()),
                            biomeName,
                            biomeIndex,
                            ResourceIdentifier.DisplayStyle.PATH,
                            ResourceIdentifier.ComparisonMethod.INDEX));

            mapperNode.addChild(biomeNode);
        }
    }

    /**
     * Retrieves a list of standalone colormaps within the specified namespace.
     *
     * @param namespace The namespace to filter colormaps by.
     * @return A list of standalone colormaps in the specified namespace.
     */
    private List<Colormap> getStandaloneColormapsInNamespace(String namespace) {

        return resourcePack.getColormaps().entrySet()
                .stream()
                .filter(entry -> entry.getKey().name().equals(namespace))
                .filter(entry -> entry.getValue().getDeclarationType() == PolytoneAssetDeclarationType.STANDALONE)
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Retrieves a map of modifiers grouped by their type within the specified namespace.
     *
     * @param namespace The namespace to filter modifiers by.
     * @return A map where the key is the modifier type and the value is a list of modifiers of that type.
     */
    private Map<Modifier.Type, List<Modifier>> getModifiersInNamespace(String namespace) {
        return resourcePack.getModifiers().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(namespace))
                .collect(Collectors.groupingBy(
                        entry -> entry.getValue().getType(),
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private void buildInlinedBiomeIdMapperNode(String namespace, BiomeIdMapper biomeIdMapper, ResourcePackTreeNode colormapNode) {

        // Candidate only if it has an inlined biome ID mapper
        if (biomeIdMapper != null && biomeIdMapper.getDeclarationType() == PolytoneAssetDeclarationType.INLINE) {

            var mapperNode = new ResourcePackTreeNode("biome_id_mapper",
                    resourcePackPath.resolve(biomeIdMapper.getPath()),
                    ResourcePackTreeNode.Type.BIOME_ID_MAPPER,
                    new ResourceIdentifier(new Namespace(namespace, biomeIdMapper.getName()),
                            "biome_id_mapper",
                            ResourceIdentifier.DisplayStyle.PATH,
                            ResourceIdentifier.ComparisonMethod.LOCAL_NAME));

            buildBiomeIdMapperLeaves(namespace, biomeIdMapper, mapperNode);

            colormapNode.addChildIfNotEmpty(mapperNode);
        }
    }

}