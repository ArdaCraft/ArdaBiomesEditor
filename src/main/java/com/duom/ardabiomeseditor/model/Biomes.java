package com.duom.ardabiomeseditor.model;

import java.util.Map;

/**
 * Biome descriptor
 */
public class Biomes {

    /**
     * Mapping of biome names to their respective indices in the texture
     */
    private Map<String, Integer> biomeMapping;

    /**
     * Size of the texture (square)
     */
    private int textureSize;

    /**
     * Number of placeholder biomes in the mappings definitions json
     */
    private int placehoderCount;


    /**
     * Initialize biome descriptor
     *
     * @param biomeMap Mapping of biome names to their respective indices in the texture
     * @param textureSize Size of the texture (square)
     * @param placeholderCount Number of placeholder biomes in the mappings definitions json
     */
    public void init(Map<String, Integer> biomeMap, int textureSize, int placeholderCount) {

        this.biomeMapping = biomeMap;
        this.textureSize = textureSize;
        this.placehoderCount = placeholderCount;
    }

    public Map<String, Integer> getBiomeMapping() {
        return biomeMapping;
    }

    public int getTextureSize() {
        return textureSize;
    }

    public int getPlacehoderCount() {
        return placehoderCount;
    }
}