package com.duom.ardabiomeseditor.model.polytone;

import com.duom.ardabiomeseditor.model.Namespace;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Polytone resource pack containing modifiers, colormaps, and biome ID mappers.
 */
public class PolytoneResourcePack {

    /**
     * Maps namespace to Modifiers
     */
    private final Map<Namespace, Modifier> modifiers;

    /**
     * Maps namespace to Colormaps
     */
    private final Map<Namespace, Colormap> colormaps;

    /**
     * Maps namespace to BiomeIdMappers
     */
    private final Map<Namespace, BiomeIdMapper> biomeIdMappers;

    public PolytoneResourcePack() {

        this.modifiers = new HashMap<>();
        this.colormaps = new HashMap<>();
        this.biomeIdMappers = new HashMap<>();
    }

    public Map<Namespace, Modifier> getModifiers() {
        return Collections.unmodifiableMap(modifiers);
    }

    public Map<Namespace, Colormap> getColormaps() {
        return Collections.unmodifiableMap(colormaps);
    }

    public Map<Namespace, BiomeIdMapper> getBiomeIdMappers() {
        return Collections.unmodifiableMap(biomeIdMappers);
    }

    public void addBiomeIdMapper(String namespace, BiomeIdMapper biomeIdMapper) {

        Namespace biomeIdMapperNamespace = new Namespace(namespace, biomeIdMapper.getName());
        biomeIdMappers.put(biomeIdMapperNamespace, biomeIdMapper);
    }

    public void addModifier(String namespace, Modifier modifier) {

        Namespace modifierNamespace = new Namespace(namespace, modifier.getName());
        modifiers.put(modifierNamespace, modifier);
    }

    public void addColormap(String namespace, Colormap colormap) {
        Namespace colormapNamespace = new Namespace(namespace, colormap.getName());
        colormaps.put(colormapNamespace, colormap);
    }

}