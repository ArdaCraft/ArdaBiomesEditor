package com.duom.ardabiomeseditor.model.polytone;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Biome ID mapper descriptor, equivalent to a biome id mapper json definition.
 * Maps biome names to indices.
 */
public class BiomeIdMapper extends PolytoneAsset {

    /**
     * Convenience - empty mapper instance.
     */
    public static final BiomeIdMapper EMPTY = new BiomeIdMapper("", Path.of(""), PolytoneAssetDeclarationType.UNDEFINED, null);
    /**
     * Mapping of biome names to their respective indices
     */
    private final Map<String, Integer> mappings;
    /**
     * Size of the texture (square)
     */
    private int textureSize;

    /**
     * Constructs a standalone Biome Id Mapper with the specified name and path.
     *
     * @param name The name of the biome ID mapper.
     * @param path The path to the biome ID mapper from the root of the resource pack.
     */
    public BiomeIdMapper(String name, Path path) {
        this(name, path, PolytoneAssetDeclarationType.STANDALONE, null);
    }

    /**
     * Constructs a Biome Id Mapper with the specified name, path, declaration type, and declaring asset.
     * This constructor is used for inline declarations.
     *
     * @param name            The name of the biome ID mapper.
     * @param path            The path to the biome ID mapper from the root of the resource pack.
     * @param declarationType The type of declaration for this asset.
     * @param declaringAsset  The asset that declares this asset, if applicable.
     */
    public BiomeIdMapper(String name, Path path, PolytoneAssetDeclarationType declarationType, PolytoneAsset declaringAsset) {
        super(name, path, declarationType, declaringAsset);

        this.mappings = new LinkedHashMap<>();
    }

    public Map<String, Integer> getMappings() {
        return mappings;
    }

    public int getTextureSize() {
        return textureSize;
    }

    public void setTextureSize(int textureSize) {
        this.textureSize = textureSize;
    }
}