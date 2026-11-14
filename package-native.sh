#!/usr/bin/env bash
set -e

# ==============================================================================
# Script de packaging natif avec jpackage pour Grafino (Multi-OS)
# Support : Linux (deb, rpm, app-image), macOS (dmg, pkg, app-image), Windows (msi, exe, app-image)
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

PACKAGE_TYPE="${1:-app-image}"
APP_VERSION="${2:-1.0.5}"

# Nettoyage de la version pour jpackage (ex: "v1.0.0" -> "1.0.0", "1.0.0-SNAPSHOT" -> "1.0.0")
APP_VERSION="${APP_VERSION#v}"
APP_VERSION="${APP_VERSION%%-*}"

echo "=== [1/4] Détection de l'OS et de jpackage ==="

OS_NAME="$(uname -s)"
case "$OS_NAME" in
    Darwin*)
        OS_FAMILY="macos"
        ;;
    MINGW*|MSYS*|CYGWIN*)
        OS_FAMILY="windows"
        ;;
    *)
        OS_FAMILY="linux"
        ;;
esac
echo "Système d'exploitation détecté : $OS_FAMILY ($OS_NAME)"

# Règle Apple macOS : CFBundleVersion impose un premier numéro strictement positif (>= 1)
JPACKAGE_VERSION="$APP_VERSION"
FIRST_NUM=$(echo "$APP_VERSION" | cut -d. -f1)
if [ "$OS_FAMILY" = "macos" ] && [ "$FIRST_NUM" = "0" ]; then
    JPACKAGE_VERSION="1.${APP_VERSION#*.}"
    echo "Avis macOS : Version interne CFBundleVersion ajustée à $JPACKAGE_VERSION (Apple exige un premier chiffre >= 1)"
fi

JPACKAGE_CMD=""
if command -v jpackage &>/dev/null; then
    JPACKAGE_CMD="jpackage"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/jpackage" ]; then
    JPACKAGE_CMD="$JAVA_HOME/bin/jpackage"
elif [ -x "/usr/lib/jvm/java-27-openjdk/bin/jpackage" ]; then
    JPACKAGE_CMD="/usr/lib/jvm/java-27-openjdk/bin/jpackage"
elif [ -x "/usr/lib/jvm/java-25-openjdk/bin/jpackage" ]; then
    JPACKAGE_CMD="/usr/lib/jvm/java-25-openjdk/bin/jpackage"
elif [ -x "$HOME/.sdkman/candidates/java/current/bin/jpackage" ]; then
    JPACKAGE_CMD="$HOME/.sdkman/candidates/java/current/bin/jpackage"
fi

if [ -z "$JPACKAGE_CMD" ]; then
    echo "ERREUR : 'jpackage' n'a pas été trouvé."
    echo "Assurez-vous qu'un JDK 25/27+ est installé et que JAVA_HOME est configuré."
    exit 1
fi

echo "Utilisation de jpackage : $($JPACKAGE_CMD --version 2>&1 | head -n 1) ($JPACKAGE_CMD)"
echo "Type de paquet demandé  : $PACKAGE_TYPE"
echo "Version de l'application : $APP_VERSION"

echo "=== [2/4] Préparation de la distribution Gradle ==="
if [ "${SKIP_BUILD:-false}" != "true" ] || [ ! -d "build/install/Grafino/lib" ]; then
    if [ -x "./gradlew" ]; then
        ./gradlew installDist -PappVersion="$APP_VERSION" -x test
    else
        gradle installDist -PappVersion="$APP_VERSION" -x test
    fi
fi

echo "=== [3/4] Vérification des artefacts d'entrée ==="
INPUT_DIR="build/install/Grafino/lib"
if [ ! -d "$INPUT_DIR" ]; then
    echo "ERREUR : Répertoire introuvable : $INPUT_DIR"
    exit 1
fi

MAIN_JAR="$(basename "$(ls "$INPUT_DIR"/Grafino-*.jar 2>/dev/null | head -n 1)")"
if [ -z "$MAIN_JAR" ]; then
    echo "ERREUR : Aucun fichier Grafino-*.jar trouvé dans $INPUT_DIR"
    exit 1
fi
echo "Archive principale détectée : $MAIN_JAR"

DIST_DIR="dist"
mkdir -p "$DIST_DIR"
# Nettoyage préventif pour éviter "destination directory already exists"
rm -rf "$DIST_DIR/Grafino" "$DIST_DIR/Grafino.app"

ICON_OPT=()
if [ "$OS_FAMILY" = "macos" ] && [ -f "src/main/resources/icons/app-icon.icns" ]; then
    ICON_OPT=(--icon "src/main/resources/icons/app-icon.icns")
elif [ "$OS_FAMILY" = "windows" ] && [ -f "src/main/resources/icons/app-icon.ico" ]; then
    ICON_OPT=(--icon "src/main/resources/icons/app-icon.ico")
elif [ -f "src/main/resources/icons/app-icon.png" ]; then
    ICON_OPT=(--icon "src/main/resources/icons/app-icon.png")
fi

EXTRA_OPTS=()
if [ "$OS_FAMILY" = "linux" ] && [ "$PACKAGE_TYPE" != "app-image" ]; then
    EXTRA_OPTS=(--linux-shortcut --linux-menu-group "Development;Engineering;Education;" --linux-package-name "grafino" --linux-app-category "Education")
elif [ "$OS_FAMILY" = "macos" ]; then
    EXTRA_OPTS=(--mac-package-identifier "tk.tahri.grafino" --mac-package-name "Grafino")
elif [ "$OS_FAMILY" = "windows" ] && [ "$PACKAGE_TYPE" != "app-image" ]; then
    EXTRA_OPTS=(--win-menu --win-shortcut --win-dir-chooser)
fi

echo "=== [4/4] Création du binaire natif ($PACKAGE_TYPE) avec jpackage ==="

"$JPACKAGE_CMD" \
    --name "Grafino" \
    --app-version "$JPACKAGE_VERSION" \
    --vendor "TAHRI Khalid" \
    --description "Grafino - SFC and Grafcet Editor & Simulator (CEI 60848 & CEI 61131-3)" \
    --input "$INPUT_DIR" \
    --main-jar "$MAIN_JAR" \
    --main-class "tk.tahri.grafino.Main" \
    --dest "$DIST_DIR" \
    --type "$PACKAGE_TYPE" \
    "${ICON_OPT[@]}" \
    "${EXTRA_OPTS[@]}" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --java-options "-Dprism.verbose=false" \
    --verbose

echo ""
echo "=============================================================================="
echo " Packaging natif terminé avec succès !"
echo " Emplacement : $PROJECT_DIR/$DIST_DIR"
echo "=============================================================================="
