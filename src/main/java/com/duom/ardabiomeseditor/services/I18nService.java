package com.duom.ardabiomeseditor.services;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 Internationalization helper :
 - Loads `i18n.messages` resource bundles from src/main/resources/i18n
 - Provides I18n.get(key, args...) with MessageFormat support
 - Exposes a locale property so UI can listen for changes if needed
*/
public final class I18nService {
    private static final String BUNDLE_BASE = "i18n.messages";
    private static final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(Locale.getDefault());

    private I18nService() {}

    /**
     * Retrieves the resource bundle for the current locale.
     *
     * @return The resource bundle for the current locale.
     */
    private static ResourceBundle bundle() {

        return ResourceBundle.getBundle(BUNDLE_BASE, locale.get());
    }

    /**
     * Retrieves a localized message for the given key, with optional arguments for formatting.
     *
     * If the key is not found in the resource bundle, the key itself is returned as a fallback.
     *
     * @param key The key for the localized message.
     * @param args Optional arguments for formatting the message.
     * @return The localized message, formatted with the provided arguments if applicable.
     */
    public static String get(String key, Object... args) {

        try {

            String pattern = bundle().getString(key);
            return (args == null || args.length == 0) ? pattern : MessageFormat.format(pattern, args);

        } catch (MissingResourceException e) {

            // Fallback: return key or formatted key
            return (args == null || args.length == 0) ? key : MessageFormat.format(key, args);
        }
    }
}