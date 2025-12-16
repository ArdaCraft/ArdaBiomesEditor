package com.duom.ardabiomeseditor.model.polytone;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic modifier descriptor, equivalent to a polytone modifier in json.
 */
public class Modifier extends PolytoneAsset {

    /**
     * A modifier can reference multiple colormaps (either inline or external)
     */
    private final List<Colormap> colormaps;

    /**
     * The type of the modifier
     */
    private final Type type;

    /**
     * Constructs a Modifier with the specified name, path, and type.
     * A modifier is always a standalone asset.
     *
     * @param name The name of the modifier.
     * @param path The path to the modifier from the root of the resource pack.
     * @param type The type of the modifier.
     */
    public Modifier(String name, Path path, Type type) {

        super(name, path, PolytoneAssetDeclarationType.STANDALONE, null);
        this.type = type;
        this.colormaps = new ArrayList<>();
    }

    public List<Colormap> getColormaps() {
        return colormaps;
    }

    public Type getType() {
        return type;
    }

    /**
     * Enumeration of modifier types.
     */
    public enum Type {
        BIOME,
        BLOCK,
        DIMENSION,
        ITEM,
        FLUID,
        PARTICLE,
        UNKNOWN;

        @Override
        public String toString() {
            return String.format("%s_modifiers", name().toLowerCase());
        }
    }
}