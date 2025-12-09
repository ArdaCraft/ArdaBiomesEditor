package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.model.ColorData;
import com.duom.ardabiomeseditor.model.Modifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModifierService {

    /**
     * Extracts hex color codes for a specific biome from the modifier's texture data.
     *
     * @param modifier   The modifier containing the texture mapping.
     * @param biomeIndex The index of the biome (column in the texture).
     * @return A list of hex color codes for the specified biome.
     */
    public static List<String> getColorsForBiome(Modifier modifier, int biomeIndex) {

        List<String> hexColors = new ArrayList<>();

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(modifier.getImageData()));

            if (biomeIndex < 0 || biomeIndex >= image.getWidth()) {
                throw new IllegalArgumentException("Biome index out of bounds");
            }

            for (int row = 0; row < image.getHeight(); row++) {
                int rgb = image.getRGB(biomeIndex, row);

                // Extract RGB components
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                // Convert to hex format
                String hexColor = String.format("#%02x%02x%02x", red, green, blue);
                hexColors.add(hexColor);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image data", e);
        }

        return hexColors;
    }

    /**
     * Applies color changes to the modifier's texture data for a specific biome.
     *
     * @param imageData The original image data as a byte array.
     * @param biomeKey  The key of the biome (column in the texture) to modify.
     * @param colors    A list of ColorData objects representing the new colors.
     * @return A byte array containing the modified image data.
     * @throws IOException If the image data cannot be read or written.
     */
    public static byte[] applyColorChangesToModifierTexture(byte[] imageData, int biomeKey, List<ColorData> colors) throws IOException {

        try {

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));

            for (int y = 0; y < colors.size(); y++) {

                String hexColor = colors.get(y).getCurrentColor();

                // Convert hex color to RGB integer
                int rgb = Integer.parseInt(hexColor.substring(1), 16);

                image.setRGB(biomeKey, y, rgb);
            }

            // Write the modified image back to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);

            return baos.toByteArray();

        } catch (IOException e) {

            throw new IOException("Failed to read image data", e);
        }
    }
}