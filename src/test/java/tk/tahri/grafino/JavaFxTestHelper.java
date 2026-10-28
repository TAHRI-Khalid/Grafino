package tk.tahri.grafino;

import javafx.application.Platform;

/**
 * Utilitaire d'initialisation du runtime JavaFX pour les tests unitaires headless.
 */
public final class JavaFxTestHelper {

    private static volatile boolean initialized = false;

    private JavaFxTestHelper() {
    }

    public static synchronized void initJavaFx() {
        if (!initialized) {
            try {
                Platform.startup(() -> {});
                Platform.setImplicitExit(false);
            } catch (IllegalStateException ignored) {
                // Toolkit déjà initialisé
            } catch (Throwable t) {
                System.err.println("Avertissement initialisation JavaFX : " + t.getMessage());
            }
            initialized = true;
        }
    }
}
