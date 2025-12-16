package com.duom.ardabiomeseditor.model.polytone;

import java.nio.file.Path;

/**
 * Base class for Polytone assets such as Modifiers, Colormaps, and BiomeIdMappers.
 */
public abstract class PolytoneAsset {

    private final PolytoneAssetDeclarationType declarationType;
    private final PolytoneAsset declaringAsset;

    /** Asset name - Json file name without extension. */
    protected final String name;

    /** Path to the asset from the root of the resource pack. */
    protected final Path path;

    /**
     * Constructs a PolytoneAsset with the specified name, path, declaration type, and declaring asset.
     *
     * @param name          The name of the asset.
     * @param path          The path to the asset from the root of the resource pack.
     * @param declarationType The type of declaration for this asset.
     * @param declaringAsset  The asset that declares this asset, if applicable.
     */
    public PolytoneAsset(String name, Path path, PolytoneAssetDeclarationType declarationType, PolytoneAsset declaringAsset) {
        this.declarationType = declarationType;
        this.name = name;
        this.path = path;
        this.declaringAsset = declaringAsset;
    }

    /**
     * @return the asset name - file name without extension by default.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the path to the asset from the root of the resource pack.
     */
    public Path getPath() {
        return path;
    }

    public PolytoneAssetDeclarationType getDeclarationType() { return declarationType; }

    public PolytoneAsset getDeclaringAsset() { return declaringAsset; }
}