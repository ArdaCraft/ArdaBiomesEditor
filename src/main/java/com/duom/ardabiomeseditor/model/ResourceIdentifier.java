package com.duom.ardabiomeseditor.model;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Identifies a specific JSON resource or element within a namespaced file.
 *
 * <p>A {@code ResourceIdentifier} consists of:
 * <ul>
 *   <li>a {@link Namespace} identifying the target file</li>
 *   <li>a {@code path} identifying a JSON element or resource within that file</li>
 * </ul>
 *
 * <p>The {@code path} is interpreted as a JSON path or pointer
 * relative to the root of the resource file.</p>
 *
 * <p>This type represents a fully-qualified reference to a JSON value.</p>
 */
public record ResourceIdentifier(Namespace namespace, String path, int index, DisplayStyle displayStyle,
                                 ComparisonMethod comparisonMethod) implements Comparable<ResourceIdentifier> {

    public ResourceIdentifier(Namespace namespace, String path, DisplayStyle displayStyle, ComparisonMethod comparisonMethod) {
        this(namespace, path, Integer.MIN_VALUE, displayStyle, comparisonMethod);
    }

    @Override
    public String toString() {

        var displayString = String.format("%s/%s/%s", namespace.toString(), path, index);

        if (displayStyle != DisplayStyle.DEFAULT) {

            if (displayStyle == DisplayStyle.LOCAL_NAME)
                displayString = namespace.localName();

            if (displayStyle == DisplayStyle.PATH)
                displayString = path;

            String[] words = displayString.replaceAll("^.*:|_", " ").split(" ");
            displayString = Arrays.stream(words)
                    .filter(s -> !s.isBlank())
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        }

        return displayString;
    }

    @Override
    public int compareTo(ResourceIdentifier o) {

        if (comparisonMethod == ComparisonMethod.INDEX) return Integer.compare(index, o.index);
        if (comparisonMethod == ComparisonMethod.LOCAL_NAME)
            return namespace.localName().compareTo(o.namespace.localName());
        if (comparisonMethod == ComparisonMethod.PATH) return this.path.compareTo(o.path);

        int namespaceComparison = this.namespace.compareTo(o.namespace);

        if (namespaceComparison != 0) return namespaceComparison;

        return this.path.compareTo(o.path);
    }

    /*
     * Utilities
     */

    /**
     * Defines how the ResourceIdentifier is displayed as a string.
     */
    public enum DisplayStyle {
        DEFAULT,
        LOCAL_NAME,
        PATH;
    }

    /**
     * Defines how ResourceIdentifiers are compared to each other.
     */
    public enum ComparisonMethod {
        DEFAULT,
        INDEX,
        LOCAL_NAME,
        PATH;
    }
}