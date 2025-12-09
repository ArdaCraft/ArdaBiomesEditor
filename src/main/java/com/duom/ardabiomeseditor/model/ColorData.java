package com.duom.ardabiomeseditor.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;

/**
 * Represents color data with both current and original color values.
 * Provides functionality to adjust colors using HSL (Hue, Saturation, Lightness)
 * and convert them to hex format for display.
 */
public class ColorData {

    private final StringProperty currentColor = new SimpleStringProperty();
    private final StringProperty originalColor = new SimpleStringProperty();

    private double currentH, currentS, currentL;
    private final double originalH;
    private final double originalS;
    private final double originalL;

    /**
     * Constructs a ColorData object from a hex color string.
     * Initializes both current and original color values.
     *
     * @param hexColor The initial color in hex format.
     */
    public ColorData(String hexColor) {

        Color color = Color.web(hexColor);
        originalH = currentH = color.getHue();
        originalS = currentS = color.getSaturation();
        originalL = currentL = color.getBrightness();

        this.currentColor.set(hexColor);
        this.originalColor.set(hexColor);
    }

    /**
     * Adjusts the current color's HSL values by the specified shifts.
     * Ensures the values remain within valid ranges.
     *
     * @param hueShift The amount to shift the hue (in degrees).
     * @param satShift The amount to shift the saturation (0 to 1).
     * @param lightShift The amount to shift the lightness (0 to 1).
     */
    public void adjustHSL(double hueShift, double satShift, double lightShift) {

        currentH = (originalH + hueShift) % 360;
        if (currentH < 0) currentH += 360;
        currentS = Math.max(0, Math.min(1, originalS + satShift));
        currentL = Math.max(0, Math.min(1, originalL + lightShift));

        // Convert to hex only when needed for display
        currentColor.set(hslToHex(currentH, currentS, currentL));
    }

    /**
     * Retrieves the current color in hex format.
     *
     * @return The current color as a hex string.
     */
    public String getCurrentColor() {
        return currentColor.get();
    }

    /**
     * Provides access to the current color property.
     *
     * @return The current color property.
     */
    public StringProperty currentColorProperty() {
        return currentColor;
    }

    /**
     * Retrieves the original color in hex format.
     *
     * @return The original color as a hex string.
     */
    public String getOriginalColor() {
        return originalColor.get();
    }

    /**
     * Checks if the current color has been modified from the original.
     *
     * @return True if the current color differs from the original, false otherwise.
     */
    public boolean isModified() {
        return !getCurrentColor().equals(getOriginalColor());
    }

    /**
     * Resets the current color to the original color.
     */
    public void reset() {
        currentH = originalH;
        currentS = originalS;
        currentL = originalL;
        currentColor.set(originalColor.get());
    }

    /**
     * Converts HSL values to a hex color string.
     *
     * @param h The hue value (0-360 degrees).
     * @param s The saturation value (0-1).
     * @param l The lightness value (0-1).
     * @return The corresponding hex color string.
     */
    private String hslToHex(double h, double s, double l) {
        Color color = Color.hsb(h, s, l);
        return String.format("#%02x%02x%02x",
                (int)(color.getRed() * 255),
                (int)(color.getGreen() * 255),
                (int)(color.getBlue() * 255));
    }

    /**
     * Sets the current color to the specified hex color.
     * Updates the HSL values to match the new color.
     *
     * @param hexColor The new color in hex format.
     */
    public void setCurrentColor(String hexColor) {

        Color color = Color.web(hexColor);
        currentH = color.getHue();
        currentS = color.getSaturation();
        currentL = color.getBrightness();
        currentColor.set(hexColor);
    }
}