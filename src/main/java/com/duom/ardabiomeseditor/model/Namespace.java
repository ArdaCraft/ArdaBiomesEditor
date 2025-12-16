package com.duom.ardabiomeseditor.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifies a logical resource namespace that maps to a physical file.
 *
 * <p>The {@code name} represents the global or external namespace,
 * while {@code localName} identifies the concrete resource within
 * that namespace.</p>
 *
 * <p>Namespaces are used as the root for resolving resource files.</p>
 */
public record Namespace(String name, String localName) implements Comparable<Namespace> {

    public static final String NAMESPACE_REGEX = "^(?<name>[a-z0-9._-]+):(?<localName>[a-z0-9/._-]+)$";

    public static Namespace fromString(String namespaceString) {

        Namespace namespace = null;
        Pattern pattern = Pattern.compile(Namespace.NAMESPACE_REGEX);
        Matcher matcher = pattern.matcher(namespaceString);

        if (matcher.matches()) {

            String name = matcher.group("name");
            String localName = matcher.group("localName");
            namespace = new Namespace(name, localName);
        }

        return namespace;
    }

    /**
     * @return the namespace in string form "name:localName"
     */
    @Override
    public String toString() {

        return String.format("%s:%s", name, localName);
    }

    @Override
    public int compareTo(Namespace o) {

        int nameComparison = this.name.compareTo(o.name);

        if (nameComparison != 0) return nameComparison;

        return this.localName.compareTo(o.localName);
    }
}