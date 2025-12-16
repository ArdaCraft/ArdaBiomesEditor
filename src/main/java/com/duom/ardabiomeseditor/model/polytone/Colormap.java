package com.duom.ardabiomeseditor.model.polytone;

import java.nio.file.Path;

/**
 * Generic colormap descriptor, equivalent to a polytone colormap json definition.
 * Can be of type INLINE (declared in a modifier) or FILE (declared in its own file).
 */
public class Colormap extends PolytoneAsset {

    /**
     * Convenience - Empty colormap constant.
     */
    public static final Colormap EMPTY = new Colormap("", Path.of(""), Path.of(""), PolytoneAssetDeclarationType.UNDEFINED, null);

    /**
     * Path to the texture file.
     */
    private Path texturePath;

    /**
     * Texture dimensions.
     */
    private int textureWidth, textureHeight;

    /**
     * X axis mapping definition.
     */
    private String xAxis;

    /**
     * Y axis mapping definition.
     */
    private String yAxis;

    /**
     * X axis mapping type.
     */
    private AxisMappingType xAxisMappingType;

    /**
     * Y axis mapping type.
     */
    private AxisMappingType yAxisMappingType;

    /**
     * Inline Biome ID mapper associated with this colormap, if any.
     */
    private BiomeIdMapper biomeIdMapper;

    public Colormap(String name, Path declarationPath, Path texturePath) {

        this(name, declarationPath, texturePath, PolytoneAssetDeclarationType.STANDALONE, null);
    }

    public Colormap(String name, Path declarationPath, Path texturePath, PolytoneAssetDeclarationType type, PolytoneAsset declaringAsset) {

        super(name, declarationPath, type, declaringAsset);
        this.texturePath = texturePath;
    }

    public String getXAxis() {
        return xAxis;
    }

    public void setXAxis(String xAxis) {
        this.xAxis = xAxis;
        this.xAxisMappingType = "biome_id".equals(this.xAxis) ? AxisMappingType.BIOME_ID : AxisMappingType.FUNCTION;
    }

    public String getYAxis() {
        return yAxis;
    }

    public void setYAxis(String yAxis) {
        this.yAxis = yAxis;
        this.yAxisMappingType = "biome_id".equals(this.yAxis) ? AxisMappingType.BIOME_ID : AxisMappingType.FUNCTION;
    }

    public AxisMappingType getxAxisMappingType() {
        return xAxisMappingType;
    }

    public void setxAxisMappingType(AxisMappingType xAxisMappingType) {
        this.xAxisMappingType = xAxisMappingType;
    }

    public AxisMappingType getyAxisMappingType() {
        return yAxisMappingType;
    }

    public void setyAxisMappingType(AxisMappingType yAxisMappingType) {
        this.yAxisMappingType = yAxisMappingType;
    }

    public BiomeIdMapper getBiomeIdMapper() {
        return biomeIdMapper;
    }

    public void setBiomeIdMapper(BiomeIdMapper biomeIdMapper) {
        this.biomeIdMapper = biomeIdMapper;
    }

    public Path getTexturePath() {
        return texturePath;
    }

    public void setTexturePath(Path texturePath) {
        this.texturePath = texturePath;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public void setTextureHeight(int textureHeight) {
        this.textureHeight = textureHeight;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public void setTextureWidth(int textureWidth) {
        this.textureWidth = textureWidth;
    }

    /**
     * Enumeration of possible axis mapping types.
     */
    public enum AxisMappingType {

        BIOME_ID,
        FUNCTION
    }
}