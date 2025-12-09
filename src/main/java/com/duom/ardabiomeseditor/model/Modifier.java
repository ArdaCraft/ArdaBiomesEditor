package com.duom.ardabiomeseditor.model;

import com.duom.ardabiomeseditor.model.json.PolytoneModifier;

/**
 * Generic modifier descriptor, equivalent to a polytone modifier.
 */
public class Modifier {

    /**
     * Modifier name
     */
    private final String name;

    /**
     * Raw texture data (PNG)
     */
    private byte[] imageData;

    /**
     * Underlying Polytone modifier data (JSON)
     */
    private PolytoneModifier polytoneModifier;

    /**
     * Initialize modifier from Polytone modifier data and name
     * @param name Modifier name
     * @param polytoneModifier underlying Polytone modifier data (JSON)
     */
    public Modifier(String name, PolytoneModifier polytoneModifier) {
        this(name, polytoneModifier, null);
    }

    /**
     * Initialize modifier from name and raw texture data
     * @param name Modifier name
     * @param rawImageData Raw texture data
     */
    public Modifier(String name, byte[] rawImageData) {
        this(name, null, rawImageData);
    }

    /**
     * Initialize modifier from name, Polytone modifier data and raw texture data
     * @param name Modifier name
     * @param polytoneModifier underlying Polytone modifier data
     * @param rawImageData Raw texture data
     */
    private Modifier(String name, PolytoneModifier polytoneModifier, byte[] rawImageData) {

        this.name = name;
        this.polytoneModifier = polytoneModifier;
        this.imageData = rawImageData;
    }

    public String getName() {
        return name;
    }

    public PolytoneModifier getModifier() {
        return polytoneModifier != null ? polytoneModifier : new PolytoneModifier();
    }

    public void setModifier(PolytoneModifier polytoneModifier) {
        this.polytoneModifier = polytoneModifier;
    }

    public void setImageData(byte[] rawImageData) {
        this.imageData = rawImageData;
    }

    public byte[] getImageData() {
        return imageData != null ? imageData : new byte[0];
    }
}