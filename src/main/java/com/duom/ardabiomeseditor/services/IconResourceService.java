package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for loading SVG icons and converting them to JavaFX Nodes.
 * This service is designed to handle simple SVG icons and avoids the use of xml parsing.
 */
public class IconResourceService {

    private static final Map<CacheKey, WeakReference<Node>> CACHE = new HashMap<>();

    private record CacheKey(String path, int size, Color color) {}

    private static final Pattern PATH_PATTERN = Pattern.compile("<path[^>]*d=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIDTH_PATTERN = Pattern.compile("width=\"([^\"]+)\"");
    private static final Pattern HEIGHT_PATTERN = Pattern.compile("height=\"([^\"]+)\"");
    private static final Pattern VIEWBOX_PATTERN = Pattern.compile("viewBox=\"([^\"]+)\"");


    /**
     * Retrieves a 16px by 16px white icon as an ImageView based on the specified IconType.
     *
     * @param icon The type of icon to retrieve.
     * @return An ImageView containing the icon, or null if loading failed.
     */
    public static Node getIcon(IconType icon) {

        return loadIcon(icon.path, 16, Color.WHITE);
    }

    /**
     * Retrieves a 16px by 16px colored icon based on the specified IconType.
     *
     * @param icon The type of icon to retrieve.
     * @return A Node containing the colored icon, or null if loading failed.
     */
    public static Node getColoredIcon(IconType icon) {

        return loadIcon(icon.path, 16, icon.color);
    }

    /**
     * Loads an SVG icon from cache if possible, else loads if from the resources and cache it.
     *
     * @param iconPath The path to the SVG icon resource.
     * @param size     The desired size (width and height) of the icon.
     * @param color    The color to apply to the SVG paths.
     * @return A JavaFX Node representing the scaled and colored SVG icon.
     */
    public static Node loadIcon(String iconPath, int size, Color color) {

        CacheKey key = new CacheKey(iconPath, size, color);
        WeakReference<Node> ref = CACHE.get(key);

        Node cached = ref != null ? ref.get() : null;

        if (cached != null) {
            return cloneNode(cached);
        }

        Node icon = loadSvg(iconPath, size, color);
        CACHE.put(key, new WeakReference<>(icon));

        return cloneNode(icon);
    }

    /**
     * Loads an SVG icon from the resources, scales it to the specified size, and applies the specified color.
     *
     * @param iconPath The path to the SVG icon resource.
     * @param size     The desired size (width and height) of the icon.
     * @param color    The color to apply to the SVG paths.
     * @return A JavaFX Node representing the scaled and colored SVG icon.
     */
    private static StackPane loadSvg(String iconPath, int size, Color color) {
        StackPane iconPane = new StackPane();
        iconPane.setAlignment(Pos.CENTER);
        iconPane.setMinSize(size, size);
        iconPane.setMaxSize(size, size);
        iconPane.setPrefSize(size, size);

        try (InputStream is = IconResourceService.class.getResourceAsStream(iconPath)) {

            if (is == null)
                throw new IllegalArgumentException("SVG not found: " + iconPath);

            String svg = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            double[] dimensions = extractDimensions(svg);
            double svgWidth = dimensions[0];
            double svgHeight = dimensions[1];

            Matcher matcher = PATH_PATTERN.matcher(svg);

            double scale = size / Math.max(svgWidth, svgHeight);
            while (matcher.find()) {

                SVGPath path = new SVGPath();
                path.setContent(matcher.group(1));
                path.getStyleClass().add("svg-icon");
                path.setFill(color);
                path.setScaleX(scale);
                path.setScaleY(scale);
                iconPane.getChildren().add(path);
            }

            if (iconPane.getChildren().isEmpty()) {
                ArdaBiomesEditor.LOGGER.warn("No <path> elements found in SVG");
            }

        } catch (Exception e) {
            ArdaBiomesEditor.LOGGER.error("Failed to load SVG icon: {}", iconPath, e);
        }

        return iconPane;
    }

    /**
     * Clones a JavaFX Node.
     *
     * @param node The Node to clone.
     * @return A cloned Node.
     */
    private static Node cloneNode(Node node) {

        if (!(node instanceof StackPane original)) {
            return node;
        }

        StackPane clone = new StackPane();
        clone.setAlignment(original.getAlignment());
        clone.setMinSize(original.getMinWidth(), original.getMinHeight());
        clone.setMaxSize(original.getMaxWidth(), original.getMaxHeight());
        clone.setPrefSize(original.getPrefWidth(), original.getPrefHeight());

        for (Node child : original.getChildren()) {

            if (child instanceof SVGPath originalPath) {

                SVGPath clonedPath = new SVGPath();
                clonedPath.setContent(originalPath.getContent());
                clonedPath.setFill(originalPath.getFill());
                clonedPath.setScaleX(originalPath.getScaleX());
                clonedPath.setScaleY(originalPath.getScaleY());
                clonedPath.getStyleClass().addAll(originalPath.getStyleClass());
                clone.getChildren().add(clonedPath);
            }
        }

        return clone;
    }

    /**
     * Extracts SVG width/height using width/height or viewBox.
     * @param svg The SVG content as a string.
     * @return An array containing width and height.
     */
    private static double[] extractDimensions(String svg) {
        Matcher w = WIDTH_PATTERN.matcher(svg);
        Matcher h = HEIGHT_PATTERN.matcher(svg);

        if (w.find() && h.find()) {
            return new double[]{parseSize(w.group(1)), parseSize(h.group(1))};
        }

        Matcher vb = VIEWBOX_PATTERN.matcher(svg);
        if (vb.find()) {
            String[] parts = vb.group(1).split("\\s+");
            if (parts.length == 4) {
                return new double[]{
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                };
            }
        }

        // fallback for most icon sets (MDI, Feather, etc.)
        return new double[]{24, 24};
    }

    /**
     * Parses a size string, removing any non-numeric characters.
     * @param value The size string (e.g., "24px", "1.5em").
     * @return The numeric size as a double.
     */
    private static double parseSize(String value) {
        return Double.parseDouble(value.replaceAll("[^0-9.]", ""));
    }

    public enum IconType {
        SORT("/assets/icons/mdi/mdi--sort-variant.svg"),
        HIDE("/assets/icons/mdi/mdi--hide.svg"),
        SHOW("/assets/icons/mdi/mdi--show.svg"),
        ZOOM_RESET("/assets/icons/tabler/tabler--zoom-reset.svg"),
        ZOOM_IN("/assets/icons/mdi/mdi--zoom-in.svg"),
        ZOOM_OUT("/assets/icons/mdi/mdi--zoom-out.svg"),
        AUTO_FIT_COLUMNS("/assets/icons/tabler/tabler--arrow-autofit-content-filled.svg"),
        AUTO_FIT_ROWS("/assets/icons/tabler/tabler--arrow-autofit-height-filled.svg"),
        RESET("/assets/icons/ri/ri--reset-left-fill.svg"),
        SAVE("/assets/icons/mdi/mdi--content-save.svg"),
        CHECKBOX_BLANK("/assets/icons/mdi/mdi--checkbox-blank-outline.svg"),
        CHECKBOX_CHECKED("/assets/icons/mdi/mdi--checkbox-outline.svg"),
        VISIBILITY_OFF("/assets/icons/mdi/mdi--visibility-off-outline.svg"),
        VISIBILITY_ON("/assets/icons/mdi/mdi--visibility-outline.svg"),
        FOLDER("/assets/icons/mdi/mdi--folder.svg", Color.rgb(236, 198, 92)),
        FOLDER_OPEN("/assets/icons/mdi/mdi--folder-open.svg", Color.rgb(236, 198, 92)),
        BIOME_ID_MAPPER("/assets/icons/mdi/mdi--map-legend.svg"),
        COLORMAP("/assets/icons/mdi/mdi--color.svg"),
        MODIFIER("/assets/icons/mdi/mdi--cog.svg"),
        CHECKER("/assets/icons/mdi/mdi--checkerboard.svg");

        final String path;
        final Color color;

        IconType(String path) {

            this(path, Color.WHITE);
        }

        IconType(String path, Color color) {

            this.path = path;
            this.color = color;
        }
    }
}