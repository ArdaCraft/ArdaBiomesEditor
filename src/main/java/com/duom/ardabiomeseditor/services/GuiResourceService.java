package com.duom.ardabiomeseditor.services;

import com.duom.ardabiomeseditor.ArdaBiomesEditor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class GuiResourceService {

    public enum IconType {
        SORT("/assets/icons/mdi/mdi-sort-variant.png"),
        HIDE("/assets/icons/mdi/mdi-hide.png"),
        SHOW("/assets/icons/mdi/mdi-show.png"),
        ZOOM_IN("/assets/icons/mdi/mdi-zoom-in.png"),
        ZOOM_OUT("/assets/icons/mdi/mdi-zoom-out.png"),
        ZOOM_RESET("/assets/icons/tabler/tabler-zoom-reset.png"),
        RESET("/assets/icons/ri/ri-reset-left-fill.png"),
        SAVE("/assets/icons/mdi/mdi-content-save.png");
        final String path;

        IconType(String path) {
            this.path = path;
        }
    }

    public static ImageView getIcon(IconType icon){

        ImageView imageView = null;

        try {
            var iconUrl = GuiResourceService.class.getResource(icon.path);

            if (iconUrl != null) {
                imageView = new ImageView(new Image(iconUrl.toExternalForm()));
                imageView.setFitHeight(16);
                imageView.setFitWidth(16);
            }
        } catch (Exception e) {
            ArdaBiomesEditor.LOGGER.warn("Failed to load icon", e);
        }

        return imageView;
    }
}