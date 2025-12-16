module com.duom.ardabiomeseditor {
    opens assets.icons.mdi;
    opens css;

    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires jdk.zipfs;
    requires java.management;

    // external libraries (module names assume automatic-module-name or modular jars)
    requires com.fasterxml.jackson.databind;
    requires com.google.gson;
    requires atlantafx.base;
    requires org.apache.logging.log4j;
    requires javafx.graphics;
    requires javafx.base;

    // allow FXMLLoader to access controller/view classes reflectively
    opens com.duom.ardabiomeseditor.ui.controller to javafx.fxml;
    opens com.duom.ardabiomeseditor.model to javafx.fxml, com.google.gson, com.fasterxml.jackson.databind;
    opens com.duom.ardabiomeseditor to org.apache.logging.log4j;

    // export main application packages if needed by other modules/tools
    exports com.duom.ardabiomeseditor;
    exports com.duom.ardabiomeseditor.ui.controller;
    exports com.duom.ardabiomeseditor.services;
    exports com.duom.ardabiomeseditor.model;
    exports com.duom.ardabiomeseditor.model.polytone;
    opens com.duom.ardabiomeseditor.model.polytone to com.fasterxml.jackson.databind, com.google.gson, javafx.fxml;
    exports com.duom.ardabiomeseditor.services.loaders;
    opens com.duom.ardabiomeseditor.services.loaders to com.google.gson;
    opens com.duom.ardabiomeseditor.services to com.google.gson, org.apache.logging.log4j;
}