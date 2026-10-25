package tk.tahri.grafino.model;

import java.util.Objects;

/**
 * Modélisation d'une variable d'automatisme selon la norme CEI 61131-3.
 *
 * @param name            Nom symbolique légal IEC (ex: "dcy", "capteur_haut", "MOTEUR_AVANT")
 * @param type            Type de donnée élémentaire (BOOL, INT, TIME, etc.)
 * @param scope           Portée de la variable (VAR_INPUT, VAR_OUTPUT, VAR, etc.)
 * @param hardwareAddress Adresse physique ou broche (ex: "%I0.0", "%Q0.1", "Pin 2")
 * @param initialValue    Valeur initiale à la mise sous tension (ex: "FALSE", "10", "T#2s")
 * @param description     Commentaire fonctionnel
 */
public record IecVariable(
        String name,
        IecDataType type,
        IecVariableScope scope,
        String hardwareAddress,
        String initialValue,
        String description
) {
    public IecVariable {
        Objects.requireNonNull(name, "Le nom de la variable est obligatoire.");
        Objects.requireNonNull(type, "Le type de la variable est obligatoire.");
        Objects.requireNonNull(scope, "La portée de la variable est obligatoire.");

        name = name.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la variable ne peut être vide.");
        }

        hardwareAddress = (hardwareAddress != null) ? hardwareAddress.trim() : "";
        initialValue = (initialValue != null && !initialValue.isBlank()) ? initialValue.trim() : type.defaultValue();
        description = (description != null) ? description.trim() : "";
    }

    /**
     * Constructeur pratique avec valeur initiale par défaut du type.
     */
    public IecVariable(String name, IecDataType type, IecVariableScope scope, String hardwareAddress, String description) {
        this(name, type, scope, hardwareAddress, null, description);
    }
}
