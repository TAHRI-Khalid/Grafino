package tk.tahri.grafino;

/**
 * Lanceur principal indépendant pour le packaging exécutable (JAR autonome et jpackage).
 * 
 * Permet d'éviter la vérification stricte du runtime JavaFX au démarrage
 * lorsque l'application est packagée en JAR universel sans options de module-path.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
