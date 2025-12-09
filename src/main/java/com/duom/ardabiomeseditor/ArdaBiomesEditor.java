package com.duom.ardabiomeseditor;

import atlantafx.base.theme.PrimerDark;
import com.duom.ardabiomeseditor.services.ConfigurationService;
import com.duom.ardabiomeseditor.ui.controller.ArdaBiomesController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main class for the ArdaBiomes Editor application.
 * This class initializes the application, sets up the primary stage, and handles the application lifecycle.
 */
public class ArdaBiomesEditor extends Application {

    public static final Logger LOGGER;
    public static final ConfigurationService CONFIG;

    static {
        // Initialize configuration service - must be done first to set up logging directory
        CONFIG = new ConfigurationService();
        LOGGER = LogManager.getLogger(ArdaBiomesEditor.class);
    }

    /**
     * JavaFX entrypoint.
     * Sets up the primary stage, loads the FXML layout, and configures the application theme.
     *
     * @param stage The primary stage for this application.
     * @throws Exception If an error occurs during FXML loading or stage setup.
     */
    @Override
    public void start(Stage stage) throws Exception {

        LOGGER.info("Starting ArdaBiomes Editor");

        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        FXMLLoader loader = new FXMLLoader(ArdaBiomesEditor.class.getResource("/view/ardabiomes.fxml"));

        Scene scene = new Scene(loader.load());
        stage.setTitle("ArdaBiomes Editor");
        stage.setScene(scene);

        ArdaBiomesController controller = loader.getController();

        stage.setOnCloseRequest(event -> {

            event.consume();
            controller.onExitApplication(Platform::exit);
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}