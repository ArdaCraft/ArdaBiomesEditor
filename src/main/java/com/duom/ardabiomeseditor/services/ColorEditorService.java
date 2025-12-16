package com.duom.ardabiomeseditor.services;

import java.awt.*;

/**
 * Service for color editing operations, specifically HSB adjustments.
 */
public class ColorEditorService {

    private ColorEditorService() {/* Static utility class */}

    /**
     * Performance optimized HSB shift algorithm. Avoid object allocations.
     *
     * @param argb            the ARGB color to modify
     * @param hueShift        the hue shift in degrees (-180 to 180)
     * @param saturationShift the saturation shift (-1.0 to 1.0)
     * @param brightnessShift the brightness shift (-1.0 to 1.0)
     * @return the shifted ARGB color
     */
    public static int applyHsb(int argb, double hueShift, double saturationShift, double brightnessShift) {

        double r = ((argb >> 16) & 0xFF) / 255.0;
        double g = ((argb >> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;

        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;

        // Calculate HSB inline
        double h, s, brightness = max;

        if (max == 0) {
            s = 0;
            h = 0;
        } else {
            s = delta / max;

            if (delta == 0) {
                h = 0;
            } else {
                if (max == r) {
                    h = 60 * ((g - b) / delta);
                } else if (max == g) {
                    h = 60 * (((b - r) / delta) + 2);
                } else {
                    h = 60 * (((r - g) / delta) + 4);
                }
            }
        }

        // Apply shifts
        if (hueShift != 0) {
            h = wrapHue(h + hueShift);
        } else {
            h = wrapHue(h);
        }

        if (saturationShift != 0) {
            s = clamp(s + saturationShift);
        }

        if (brightnessShift != 0) {
            brightness = clamp(brightness + brightnessShift);
        }

        // Convert back inline
        double c = brightness * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = brightness - c;

        double r1 = 0, g1 = 0, b1 = 0;

        if (h < 60) {
            r1 = c;
            g1 = x;
        } else if (h < 120) {
            r1 = x;
            g1 = c;
        } else if (h < 180) {
            g1 = c;
            b1 = x;
        } else if (h < 240) {
            g1 = x;
            b1 = c;
        } else if (h < 300) {
            r1 = x;
            b1 = c;
        } else {
            r1 = c;
            b1 = x;
        }

        int rOut = (int) Math.round((r1 + m) * 255);
        int gOut = (int) Math.round((g1 + m) * 255);
        int bOut = (int) Math.round((b1 + m) * 255);

        int a = (argb >> 24) & 0xFF;

        return (a << 24)
                | (clamp8(rOut) << 16)
                | (clamp8(gOut) << 8)
                | clamp8(bOut);
    }

    /**
     * Wraps hue to [0, 360) range.
     *
     * @param h the hue value
     * @return the wrapped hue value
     */
    private static double wrapHue(double h) {
        h = h % 360.0;
        return h < 0 ? h + 360.0 : h;
    }

    /**
     * Clamps a double to [0.0, 1.0] range.
     *
     * @param v the double value
     * @return the clamped value
     */
    private static double clamp(double v) {
        return Math.clamp(v, 0.0, 1.0);
    }

    /**
     * Clamps an integer to [0, 255] range.
     *
     * @param v the integer value
     * @return the clamped value
     */
    private static int clamp8(int v) {
        return Math.clamp(v, 0, 255);
    }

    /**
     * Applies opacity to an ARGB color.
     *
     * @param colorData the ARGB color
     * @param opacity   the opacity value (0.0 to 1.0)
     * @return the ARGB color with applied opacity
     */
    public static int applyOpacity(int colorData, Double opacity) {

        int alpha = (int) Math.round(clamp(opacity) * 255.0);

        // Clear old alpha and set new one
        return (colorData & 0x00FFFFFF) | (alpha << 24);
    }

    /* Utility methods */

    /**
     * Computes the HSB shift between two ARGB colors.
     *
     * @param argbOriginal the original ARGB color
     * @param argbModified the modified ARGB color
     * @return the HSB shift as an HSB record
     */
    public static HSB computeHsbShift(int argbOriginal, int argbModified) {

        double r1 = ((argbOriginal >> 16) & 0xFF) / 255.0;
        double g1 = ((argbOriginal >> 8) & 0xFF) / 255.0;
        double b1 = (argbOriginal & 0xFF) / 255.0;

        double r2 = ((argbModified >> 16) & 0xFF) / 255.0;
        double g2 = ((argbModified >> 8) & 0xFF) / 255.0;
        double b2 = (argbModified & 0xFF) / 255.0;

        float[] hsb1 = Color.RGBtoHSB(
                (int) (r1 * 255),
                (int) (g1 * 255),
                (int) (b1 * 255),
                null
        );

        float[] hsb2 = Color.RGBtoHSB(
                (int) (r2 * 255),
                (int) (g2 * 255),
                (int) (b2 * 255),
                null
        );

        double hueShift = wrapHue(hsb2[0] * 360.0 - hsb1[0] * 360.0);
        if (hueShift > 180.0) {
            hueShift -= 360.0;
        }

        double saturationShift = hsb2[1] - hsb1[1];
        double brightnessShift = hsb2[2] - hsb1[2];

        return new HSB(hueShift, saturationShift, brightnessShift);
    }

    /**
     * Extracts the opacity from an ARGB color.
     *
     * @param color the ARGB color
     * @return the opacity value (0.0 to 1.0)
     */
    public static double getOpacity(int color) {
        return ((color >> 24) & 0xFF) / 255.0;
    }

    public record HSB(double hue, double saturation, double brightness) {}
}