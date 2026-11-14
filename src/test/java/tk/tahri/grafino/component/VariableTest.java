package tk.tahri.grafino.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tk.tahri.grafino.model.IecDataType;
import tk.tahri.grafino.model.IecVariable;
import tk.tahri.grafino.model.IecVariableScope;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests du composant Variable observable (CEI 61131-3)")
class VariableTest {

    @Test
    @DisplayName("Inférence automatique de la portée selon l'adresse API")
    void testScopeInference() {
        var vInput = new Variable("dcy", Variable.Type.BOOL, "%I0.0", "Départ");
        assertEquals(IecVariableScope.VAR_INPUT, vInput.getScope());
        assertTrue(vInput.isInput());
        assertFalse(vInput.isOutput());

        var vOutput = new Variable("KM1", Variable.Type.BOOL, "%Q0.0", "Moteur");
        assertEquals(IecVariableScope.VAR_OUTPUT, vOutput.getScope());
        assertTrue(vOutput.isOutput());

        var vInternal = new Variable("mem1", Variable.Type.BOOL, "%M0.1", "Mémoire");
        assertEquals(IecVariableScope.VAR, vInternal.getScope());
        assertTrue(vInternal.isInternal());
    }

    @Test
    @DisplayName("Conversion bidirectionnelle entre Variable UI et IecVariable Model")
    void testConversionWithIecVariable() {
        var iecVar = new IecVariable("temp_cuve", IecDataType.REAL, IecVariableScope.VAR_INPUT, "%IW4", "25.5", "Capteur Température");

        // Conversion Model -> UI
        var uiVar = Variable.fromIecVariable(iecVar);
        assertEquals("temp_cuve", uiVar.getName());
        assertEquals(IecDataType.REAL, uiVar.getType());
        assertEquals(IecVariableScope.VAR_INPUT, uiVar.getScope());
        assertEquals("%IW4", uiVar.getAddress());
        assertEquals("25.5", uiVar.getInitialValue());

        // Modification UI
        uiVar.setName("temp_cuve_mod");
        uiVar.setAddress("%IW6");

        // Conversion UI -> Model
        var convertedBack = uiVar.toIecVariable();
        assertEquals("temp_cuve_mod", convertedBack.name());
        assertEquals("%IW6", convertedBack.hardwareAddress());
        assertEquals(IecDataType.REAL, convertedBack.type());
    }

    @Test
    @DisplayName("Le changement de portée (scope) adapte automatiquement le préfixe de l'adresse API")
    void testScopeChangeUpdatesAddress() {
        var v = new Variable("capteur", Variable.Type.BOOL, "%I0.3", "Capteur");
        assertEquals(IecVariableScope.VAR_INPUT, v.getScope());
        assertEquals("%I0.3", v.getAddress());

        // Passage à VAR_OUTPUT -> l'adresse devient %Q0.3
        v.setScope(IecVariableScope.VAR_OUTPUT);
        assertEquals("%Q0.3", v.getAddress());

        // Passage à VAR (mémoire interne) -> l'adresse devient %M0.3
        v.setScope(IecVariableScope.VAR);
        assertEquals("%M0.3", v.getAddress());

        // Retour à VAR_INPUT -> l'adresse redevient %I0.3
        v.setScope(IecVariableScope.VAR_INPUT);
        assertEquals("%I0.3", v.getAddress());
    }

    @Test
    @DisplayName("Validation et normalisation stricte des adresses API (CEI 61131-3)")
    void testAddressValidationAndNormalization() {
        // Formats valides
        assertTrue(Variable.isValidAddress("%I0.0"));
        assertTrue(Variable.isValidAddress("%I1.7"));
        assertTrue(Variable.isValidAddress("%IX0.0"));
        assertTrue(Variable.isValidAddress("%Q0.0"));
        assertTrue(Variable.isValidAddress("%M0.0"));
        assertTrue(Variable.isValidAddress("%IW2"));
        assertTrue(Variable.isValidAddress("%QW0"));
        assertTrue(Variable.isValidAddress("%MD10"));
        assertTrue(Variable.isValidAddress("D13"));
        assertTrue(Variable.isValidAddress("A0"));

        // Normalisation sans le % initial
        assertTrue(Variable.isValidAddress("i0.0"));
        assertEquals("%I0.0", Variable.normalizeAddress("i0.0"));
        assertEquals("%Q0.2", Variable.normalizeAddress("q0.2"));
        assertEquals("%IW4", Variable.normalizeAddress("iw4"));

        // Formats invalides
        assertFalse(Variable.isValidAddress("abc"));
        assertFalse(Variable.isValidAddress(""));
        assertFalse(Variable.isValidAddress(null));
        assertFalse(Variable.isValidAddress("%"));
        assertFalse(Variable.isValidAddress("123"));
        assertFalse(Variable.isValidAddress("%%I0.0"));
        assertFalse(Variable.isValidAddress("%I0.-1"));
        assertFalse(Variable.isValidAddress("%G0.0"));
        assertFalse(Variable.isValidAddress("%I0.8"));
        assertFalse(Variable.isValidAddress("%I0.9"));
        assertFalse(Variable.isValidAddress("%IX0.8"));
        assertFalse(Variable.isValidAddress("%Q0.8"));
        assertFalse(Variable.isValidAddress("%M0.8"));
        assertFalse(Variable.isValidAddress("%IB0.8"));

        // Formats mots/bits étendus toujours valides
        assertTrue(Variable.isValidAddress("%MW0.15"));

        // Test comportement setAddress avec valeur invalide
        var v = new Variable("test", Variable.Type.BOOL, "%I0.0", "");
        v.setAddress("texte_invalide");
        // L'adresse invalide est rejetée, l'adresse reste l'adresse par défaut conforme
        assertTrue(Variable.isValidAddress(v.getAddress()));
        assertEquals("%I0.0", v.getAddress());
    }

    @Test
    @DisplayName("Incrémentation des adresses bit CEI 61131-3 : après .7 vient l'octet suivant .0")
    void testBitAddressingRollover() {
        assertEquals("%I0.0", Variable.nextBitAddress("%I", List.of()));
        assertEquals("%I0.7", Variable.nextBitAddress("%I", List.of("%I0.0", "%I0.6")));
        assertEquals("%I1.0", Variable.nextBitAddress("%I", List.of("%I0.0", "%I0.7")));
        assertEquals("%I1.1", Variable.nextBitAddress("%I", List.of("%I0.7", "%I1.0")));
        assertEquals("%I2.0", Variable.nextBitAddress("%I", List.of("%I1.7")));

        assertEquals("%Q1.0", Variable.nextBitAddress("%Q", List.of("%Q0.7")));
        assertEquals("%M1.0", Variable.nextBitAddress("%M", List.of("%M0.7", "%MW0")));
    }
}
