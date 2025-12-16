package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.model.polytone.Colormap;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Service class for handling color map I/O.
 */
public class ColorMapService {

    /**
     * Extracts hex color codes for a specific biome from the modifier's texture data.
     *
     * @param colormap   The colormap referencing the texture data.
     * @param biomeIndex The index of the biome (column in the texture).
     * @return A list of hex color codes for the specified biome.
     */
    public static int[] getColorsForBiomeId(Colormap colormap, int biomeIndex) {

        int[] colormapArgb = null;

        Path colormapTexturePath = colormap.getTexturePath();

        if (colormapTexturePath != null && Files.exists(colormapTexturePath) && colormapTexturePath.getFileName().toString().endsWith(".png")) {

            try {
                BufferedImage image = ImageIO.read(colormapTexturePath.toFile());
                if (image == null) {
                    throw new IllegalArgumentException("Unsupported or corrupted image file");
                }

                int width = image.getWidth();
                int height = image.getHeight();

                colormap.setTextureWidth(width);
                colormap.setTextureHeight(height);

                if (biomeIndex < 0) {
                    throw new IllegalArgumentException("Biome index out of bounds: " + biomeIndex);
                }

                if (colormap.getxAxisMappingType() == Colormap.AxisMappingType.BIOME_ID) {

                    if (biomeIndex >= width) {
                        throw new IllegalArgumentException("Biome index out of bounds on the X axis: " + biomeIndex);
                    }

                    colormapArgb = new int[height];
                    image.getRGB(biomeIndex, 0, 1, height, colormapArgb, 0, 1);

                } else if (colormap.getyAxisMappingType() == Colormap.AxisMappingType.BIOME_ID) {

                    if (biomeIndex >= height) {
                        throw new IllegalArgumentException("Biome index out of bounds on the Y axis: " + biomeIndex);
                    }

                    colormapArgb = new int[width];
                    image.getRGB(0, biomeIndex, width, 1, colormapArgb, 0, 1);
                }

            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read image data", e);
            }
        }

        return colormapArgb;
    }

    /**
     * Extracts all ARGB color codes from the colormap's texture data.
     *
     * @param colormap The colormap referencing the texture data.
     * @return An array of ARGB color codes.
     */
    public static int[] getAllColors(Colormap colormap) {

        int[] colors = new int[0];

        Path colormapTexturePath = colormap.getTexturePath();

        if (colormapTexturePath != null && Files.exists(colormapTexturePath) && colormapTexturePath.getFileName().toString().endsWith(".png")) {

            try (InputStream in = Files.newInputStream(colormapTexturePath)) {

                var image = ImageIO.read(in);
                var width = image.getWidth();
                var height = image.getHeight();

                colormap.setTextureWidth(width);
                colormap.setTextureHeight(height);

                int[] pixels = image.getRGB(
                        0,
                        0,
                        width,
                        height,
                        null,
                        0,
                        width
                );

                colors = new int[pixels.length];

                /*
                 * Image.getRGB returns pixels in row-major order
                 * Rotate through x and y to get colors in column-major order
                 */
                for (int column = 0; column < width; column++) {
                    for (int row = 0; row < height; row++) {
                        colors[column * height + row] = pixels[row * width + column];
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException("Failed to read image data", e);
            }
        }

        return colors;
    }

    /**
     * Applies color changes to a modifier texture based on the provided biome colors
     * using the mapping types of the colormap : x, y or both axes mapped to BIOME_ID.
     * <p>
     * Note: this method works with ARGB images, that is if the source image is indexed, it wont preserve the format.
     *
     * @param colormap      The colormap referencing the texture data.
     * @param indexedColors A map where keys are biome indices and values are arrays of ARGB color codes.
     * @throws IOException If an I/O error occurs during image reading or writing.
     */
    public static void applyIndexedColorChangesToColormapTexture(Colormap colormap, Map<Integer, int[]> indexedColors) throws IOException {

        Path texturePath = colormap.getTexturePath();

        if (texturePath == null || !Files.exists(texturePath) || !texturePath.toString().endsWith(".png")) {
            return;
        }

        BufferedImage image = getBufferedImage(texturePath);

        boolean isXaxisBiomeMapped = colormap.getxAxisMappingType() == Colormap.AxisMappingType.BIOME_ID;
        boolean isYaxisBiomeMapped = colormap.getyAxisMappingType() == Colormap.AxisMappingType.BIOME_ID;

        for (Map.Entry<Integer, int[]> entry : indexedColors.entrySet()) {

            int index = entry.getKey();
            int[] colors = entry.getValue();

            // Case 1: X & Y both BIOME_ID maps to a single pixel
            if (isXaxisBiomeMapped && isYaxisBiomeMapped) {

                writePixelColorData(index, colors[0], image);

                // Case 2: X axis BIOME_ID maps as a column
            } else if (isXaxisBiomeMapped) {

                writeColumnColorData(index, colors, image);

                // Case 3: Y axis BIOME_ID maps as a row (provided as column)
            } else if (isYaxisBiomeMapped) {

                writeRowColorData(index, colors, image);

                // Case 4: this colormap is function mapped - write the entire image as is
            } else {

                writeColumnColorData(index, colors, image);
            }
        }

        writeImage(image, texturePath);
    }

    /**
     * Loads an image from the specified path into a BufferedImage with ARGB format.
     * If the source image is in a different format, it is converted to ARGB, indexed images included.
     *
     * @param texturePath The path to the texture image.
     * @return A BufferedImage in ARGB format.
     * @throws IOException If an I/O error occurs during image reading.
     */
    private static BufferedImage getBufferedImage(Path texturePath) throws IOException {

        BufferedImage source = ImageIO.read(texturePath.toFile());
        BufferedImage image = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics = image.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();

        return image;
    }

    /**
     * Writes a single pixel color into the specified image.
     *
     * @param index The index for both x and y axis (pixel position).
     * @param color The ARGB color to write.
     * @param image The BufferedImage to write to.
     */
    private static void writePixelColorData(int index, int color, BufferedImage image) {

        int width = image.getWidth();
        int height = image.getHeight();

        if (index < 0 || index >= width || index >= height)
            throw new IllegalArgumentException("Index out of bounds: " + index + " for image size " + width + "x" + height);

        image.setRGB(index, index, color);
    }

    /**
     * Writes a column of colors into the specified image.
     *
     * @param columnIndex The column index (x axis in the texture)
     * @param colors      An array of ARGB color codes as a column (should match the height of the texture).
     * @param image       The BufferedImage to write to.
     */
    private static void writeColumnColorData(int columnIndex, int[] colors, BufferedImage image) {

        int width = image.getWidth();
        int height = image.getHeight();

        if (columnIndex < 0 || columnIndex >= width)
            throw new IllegalArgumentException("Index out of bounds : " + columnIndex + " for image width " + width);

        if (colors == null || colors.length != height)
            throw new IllegalArgumentException("Colors array height mismatch for index " + columnIndex + ": expected " + height + ", got " + (colors == null ? "null" : colors.length));

        for (int y = 0; y < height; y++) {
            image.setRGB(columnIndex, y, colors[y]);
        }
    }

    /**
     * Writes a row of colors into the specified image.
     *
     * @param rowIndex The row index (y axis in the texture)
     * @param colors   An array of ARGB color codes as a row (should match the width of the texture).
     * @param image    The BufferedImage to write to.
     */
    private static void writeRowColorData(int rowIndex, int[] colors, BufferedImage image) {

        int width = image.getWidth();
        int height = image.getHeight();

        if (rowIndex < 0 || rowIndex >= height)
            throw new IllegalArgumentException("Index out of bounds : " + rowIndex + " for image height " + height);

        if (colors == null || colors.length != width)
            throw new IllegalArgumentException("Colors array height mismatch for index " + rowIndex + ": expected " + width + ", got " + (colors == null ? "null" : colors.length));

        for (int x = 0; x < width; x++) {
            image.setRGB(x, rowIndex, colors[x]);
        }
    }

    /**
     * Writes the provided image to the specified output path.
     * If the image contains 256 or fewer colors, it will be saved as an 8bit indexed PNG.
     *
     * @param image      The BufferedImage to write.
     * @param outputPath The path to save the image.
     * @throws IOException If an I/O error occurs during image writing.
     */
    private static void writeImage(BufferedImage image, Path outputPath) throws IOException {

        BufferedImage outputImage;

        int width = image.getWidth();
        int height = image.getHeight();

        Set<Integer> colors = new HashSet<>(256);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                colors.add(image.getRGB(x, y));

                if (colors.size() > 256) break;
            }
        }

        if (colors.size() <= 256) {

            List<Integer> palette = new ArrayList<>(colors);
            int size = palette.size();

            byte[] r = new byte[size];
            byte[] g = new byte[size];
            byte[] b = new byte[size];
            byte[] a = new byte[size];

            for (int i = 0; i < size; i++) {

                int argb = palette.get(i);
                a[i] = (byte) ((argb >> 24) & 0xFF);
                r[i] = (byte) ((argb >> 16) & 0xFF);
                g[i] = (byte) ((argb >> 8) & 0xFF);
                b[i] = (byte) (argb & 0xFF);
            }

            IndexColorModel icm = new IndexColorModel(8, size, r, g, b, a);

            BufferedImage indexed = new BufferedImage(
                    image.getWidth(),
                    image.getHeight(),
                    BufferedImage.TYPE_BYTE_INDEXED,
                    icm
            );

            WritableRaster raster = indexed.getRaster();

            Map<Integer, Integer> indexMap = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                indexMap.put(palette.get(i), i);
            }

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    raster.setSample(x, y, 0, indexMap.get(argb));
                }
            }

            // Output as 8bit indexed PNG
            outputImage = indexed;

        } else {

            // Output as ARGB PNG
            outputImage = image;
        }

        ImageIO.write(outputImage, "png", outputPath.toFile());
    }
}