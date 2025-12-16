package com.duom.ardabiomeseditor.model;

import javafx.scene.control.TreeItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a node in the resource pack tree structure.
 * This is intended to model the hierarchical organization of resources
 */
public class ResourcePackTreeNode {

    private final Data value;
    private final List<ResourcePackTreeNode> children = new ArrayList<>();
    private ResourcePackTreeNode parent;

        /**
     * Constructs a ResourcePackTreeNode with the specified parameters.
     * @param name The name of the resource.
     * @param resourcePath The file path to the resource.
     * @param type The type of the resource.
     */
    public ResourcePackTreeNode(String name, Path resourcePath, Type type) {
        this.value = new Data(name, resourcePath, type, null);
    };

    /**
     * Constructs a ResourcePackTreeNode with the specified parameters.
     * @param name The name of the resource.
     * @param resourcePath The file path to the resource.
     * @param type The type of the resource.
     * @param resourceIdentifier The identifier for the resource.
     */
    public ResourcePackTreeNode(String name, Path resourcePath, Type type, ResourceIdentifier resourceIdentifier) {
        this.value = new Data(name, resourcePath, type, resourceIdentifier);
    }

    /**
     * Converts this ResourcePackTreeNode and its children into a JavaFX TreeItem structure.
     * @param node The ResourcePackTreeNode to convert.
     * @return A TreeItem representing the node and its children.
     */
    public static TreeItem<ResourcePackTreeNode.Data> toTreeItem(ResourcePackTreeNode node) {

        TreeItem<ResourcePackTreeNode.Data> item = new TreeItem<>(node.getValue());

        for (ResourcePackTreeNode child : node.getChildren())
            item.getChildren().add(toTreeItem(child));

        return item;
    }

    /**
     * Adds the specified child node only if it has children of its own.
     * @param child The child node to potentially add.
     */
    public void addChildIfNotEmpty(ResourcePackTreeNode child) {

        if (!child.getChildren().isEmpty()) {
            addChild(child);
        }
    }

    /**
     * Retrieves an unmodifiable list of child nodes.
     * @return List of child ResourcePackTreeNode objects.
     */
    public List<ResourcePackTreeNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Adds a child node to this node.
     * @param child The child node to add.
     */
    public void addChild(ResourcePackTreeNode child) {
        child.parent = this;
        children.add(child);
    }

    /**
     * Retrieves the Data value of this node.
     * @return The Data value.
     */
    public Data getValue() {
        return value;
    }

    /**
     * Enum representing the type of resource.
     */
    public enum Type {
        BIOME_ID_MAPPER,
        BIOME_MAPPING,
        DIRECTORY,
        COLORMAP,
        MODIFIER,
        OTHER
    }

    public record Data(String name, Path resourcePath, Type type, ResourceIdentifier resourceIdentifier) {}
}