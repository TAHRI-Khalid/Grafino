# Guide pour Agents et Développeurs — Projet Grafino

## 1. Contexte & Mission du Projet

**Grafino** est un environnement logiciel pédagogique open-source conçu pour l'enseignement et l'ingénierie des automatismes industriels selon la norme internationale **SFC (Sequential Function Chart — CEI 61131-3)** et le **Grafcet (CEI 60848)**.

Le projet est principalement destiné aux étudiants et formateurs de l'enseignement technique supérieur (BTS CRSA, BTS CIRA, BUT GEII, Licences professionnelles et Écoles d'ingénieurs).

Le nom **Grafino** symbolise la passerelle entre :
* La modélisation séquentielle graphique formelle (**Grafcet / SFC**).
* L'exécution concrète sur automates programmables industriels (**API / PLC** Siemens, Schneider, Codesys) et cibles embarquées (**Arduino / microcontrôleurs**).

### Objectif global du cycle de développement :
1. **Édition graphique fluide & assistée (style Siemens TIA Portal S7-GRAPH)** :
   * Insertion contextuelle intelligente en un clic (*Smart Quick-Add Handles*).
   * Aimantation magnétique automatique (*Smart Snapping* à $\pm 14\text{ px}$) avec lignes guides de repère.
   * Auto-Layout et centrage hiérarchique instantané (`Ctrl + L` / bouton `📐 Aligner`).
   * Navigation Pan & Zoom fluide centré sur le curseur (`Ctrl + Molette`).
   * Édition in-place directe sur le Canvas (double-clic pour éditer réceptivités et étapes).
2. **Simulation synchrone interactive** (pas-à-pas et temps réel, forçage des entrées capteurs `%I` et observation des préactionneurs de sortie `%Q`).
3. **Validation sémantique formelle** (détection des violations d'alternance, adresses IEC invalides, blocages ou conflits).
4. **Exportation documentaire** (PNG haute résolution pour rapports de TP et dossiers techniques).
5. **Génération directe de code cible** (croquis Arduino C++ `.ino` et Structured Text ST CEI 61131-3).

---

## 2. Normes Industrielles de Référence

Le projet prend en compte la complémentarité entre les deux normes fondamentales :
* **CEI 60848 (Grafcet)** : Norme de spécification fonctionnelle graphique pour cahier des charges.
* **CEI 61131-3 (SFC)** : Norme internationale de programmation logicielle pour Automates Programmables Industriels (API).

### Règles Métier Incontournables :
1. **Alternance stricte** : Une étape ne peut être raccordée qu'à une transition, et une transition qu'à une étape. Aucune liaison directe étape-étape ou transition-transition n'est autorisée. Le curseur fournit un retour visuel vert/rouge en temps réel lors du raccordement.
2. **Sens d'évolution et flèches** : L'évolution naturelle s'effectue du haut vers le bas **sans flèche**. Toute liaison ascendante (boucle de reprise vers le haut) comporte obligatoirement une flèche triangulaire orientée vers le haut (`▲`) et transite par une gouttière latérale pour éviter toute collision graphique.
3. **Étape initiale** : Au moins une étape initiale doit être définie (représentée par un double carré concentrique).
4. **Symbole de transition** : Une transition est strictement représentée par un trait simple transversal perpendiculaire à la liaison (repère $T_n$ à gauche, condition en Structured Text à droite).
5. **Structures de divergence et convergence** :
   * **Sélection de séquence (OU)** : trait horizontal simple continu reliant l'ensemble des branches alternatives ($X_{min}$ à $X_{max}$).
   * **Parallélisme / Synchronisation (ET)** : **double barre horizontale** continue (exactement 2 lignes parallèles avec écartement de $6\text{ px}$).
6. **Qualificateurs d'actions CEI 61131-3** :
   * `N` : Non-stored (action continue pendant l'activité de l'étape).
   * `S` : Set / Mémorisé à 1.
   * `R` : Reset (remise à 0).
   * `P`, `P1`, `P0` : Impulsions (Pulse) sur front d'activation ou désactivation.
   * `L` : Time Limited (action limitée dans le temps `T#...`).
   * `D` : Time Delayed (action retardée par une temporisation `T#...`).
   * `SD`, `DS`, `SL` : Combinaisons temporisées et mémorisées.
7. **Typage et Adressage des variables (CEI 61131-3)** :
   * Types de données : `BOOL`, `BYTE`, `WORD`, `INT`, `DINT`, `REAL`, `TIME` (`T#2s`), `STRING`.
   * Portées : `VAR_INPUT` (%I), `VAR_OUTPUT` (%Q), `VAR` (%M local), `VAR_GLOBAL` (%G).
   * Format d'adresse matérielle : `%[I|Q|M][X|B|W|D]?[0-9]+(\.[0-9]+)?` (ex : `%I0.0`, `%Q0.1`, `%M0.0`, `%IW2`).
   * Couplage bidirectionnel strict entre la portée et l'adresse avec résolution automatique des collisions.
8. **Temporisateurs dans les transitions (CEI 60848 & CEI 61131-3)** :
   * **Format Grafcet standard CEI 60848 (format par défaut de Grafino)** : $t_n / X_i / d$ (générique $t / X_i / d$, ex : `t/X1/5s`, `t1/X2/500ms`). La réceptivité devient vraie dès que l'étape associée $X_i$ (par défaut l'étape amont directe) a été active de manière continue pendant la durée $d$.
   * **Format Structured Text CEI 61131-3** : $X_i.t \ge T\#d$ ou $Step_i.t \ge T\#d$ (ex : `X1.t >= T#5s`).
   * Assistant interactif dans la barre latérale avec détection automatique de l'étape amont, calcul d'aperçu dynamique et injection contextuelle.

---

## 3. Architecture Technique & Bonnes Pratiques Java 21+ / 25+

Le projet applique une séparation stricte des responsabilités (Clean Architecture / Modèle Découplé) :

```
tk.tahri.grafino/
├── model/                     # Modèle métier pur CEI 61131-3 (AUCUNE dépendance JavaFX)
│   ├── SfcElement.java        # Sealed interface racine (permits SfcStep, SfcTransition, SfcBranch)
│   ├── SfcStep.java           # Record d'étape (id, name, number, isInitial, actionBlocks)
│   ├── SfcTransition.java     # Record de transition (id, name, number, conditionST en Structured Text)
│   ├── SfcTimerCondition.java # Record de temporisateur normalisé (parsing regex, formats t/Xi/d et ST)
│   ├── SfcBranch.java         # Record de synchronisation et aiguillage
│   ├── SfcBranchType.java     # Enum : DIVERGENCE_OR, CONVERGENCE_OR, DIVERGENCE_AND, CONVERGENCE_AND
│   ├── SfcLink.java           # Record d'arc orienté avec points de routage Manhattan
│   ├── SfcPoint.java          # Coordonnée 2D pure (x, y)
│   ├── SfcQualifier.java      # Enum des qualificateurs CEI 61131-3 (N, S, R, P, L, D, SD, DS, SL)
│   ├── SfcActionBlock.java    # Record d'action (qualificateur, variable cible, durée Duration, condition)
│   ├── IecDataType.java       # Types de données élémentaires (BOOL, INT, REAL, TIME...)
│   ├── IecVariableScope.java  # Portées IEC : VAR_INPUT (%I), VAR_OUTPUT (%Q), VAR (%M), VAR_GLOBAL
│   ├── IecVariable.java       # Record de variable automate (nom, type, portée, adresse, valeur init)
│   ├── SfcProgram.java        # Conteneur POU racine (Program Organization Unit) avec validation formelle
│   └── SfcValidationMessage.java # Diagnostics d'analyse sémantique et erreurs de règles
│
├── storage/                   # Persistance JSON indépendante (zéro dépendance externe)
│   ├── JsonValue.java         # Sealed interface du modèle AST JSON (records JsonObject, JsonArray...)
│   ├── JsonParser.java        # Parseur et formateur JSON natif
│   └── SfcStorage.java        # Sauvegarde/chargement .sfc et rétrocompatibilité .tk
│
├── component/                 # Composants visuels pour le Canvas JavaFX
│   ├── Component.java         # Classe de base des éléments graphiques (Teal #008184, sélection jaune #FFCC00)
│   ├── StepAndTransitionCommonBehavior.java # Interactions, poignées contextuelles TIA Portal ([+], [⑂], [✕])
│   ├── Step.java              # Rendu de l'étape (simple ou double cadre initial) et blocs d'actions
│   ├── Transition.java        # Rendu de la transition (trait transversal unique) et détection étape amont
│   ├── Link.java              # Tracé orthogonal Manhattan, double barre ET, simple barre OU, boucle ascendante
│   ├── Action.java            # Association graphique d'action (qualificateur, variable, paramètre)
│   └── Variable.java          # Modèle observable avec validation et couplage scope/adresse
│
└── ui/                        # Interface Utilisateur JavaFX inspirée d'Arduino IDE & TIA Portal
    ├── AppTheme.java          # Design tokens & charte visuelle (Teal Arduino #008184, cartes, puces, couleurs sémantiques)
    ├── MainUI.java            # Fenêtre principale avec SplitPane vertical rétractable pleine hauteur
    ├── Grafcet.java           # Canvas fluide (Smart Snap, lignes guides, Auto-Layout, Pan/Zoom, édition in-place)
    ├── TopToolBar.java        # Barre d'outils graphique (création, suppression 🗑, auto-layout 📐, zoom ＋/－)
    ├── StepProperty.java      # Sidebar d'édition d'étape (étape initiale, qualificateurs, cartes d'action)
    ├── TransitionProperty.java# Sidebar d'édition de transition (condition ST, temporisateurs t/Xi/d, puces %I, opérateurs)
    └── VariableTable.java     # Table des variables (onglets chromatiques, recherche, boutons %I/%Q/%M)
```

### Principes appliqués :
* **`records`** : Modélisation immuable des données de domaine (`SfcStep`, `SfcTransition`, `SfcActionBlock`, `IecVariable`, `SfcProgram`).
* **`sealed interface`** : `SfcElement permits SfcStep, SfcTransition, SfcBranch` garantissant l'exhaustivité totale lors des expressions `switch` avec pattern matching.
* **Indépendance UI** : Le paquet `tk.tahri.grafino.model` n'importe aucune classe JavaFX, permettant l'exécution de simulations et de tests unitaires ultra-rapides en mode headless.
* **Smart Alignment** : Algorithme `computeSmartSnap` avec tolérance dynamique de $\pm 14\text{ px}$ et détection prioritaire des relations de parenté.
* **Auto-Layout BFS** : Algorithme de placement hiérarchique régulier ordonnant les couches en tenant compte des divergences et convergences sans chevauchement.

---

## 4. Charte Graphique & Design System

L'interface associe les codes visuels emblématiques de **l'IDE Arduino** et la rigueur fonctionnelle de **TIA Portal** :

| Rôle | Teinte | Hex | Usage |
| :--- | :--- | :--- | :--- |
| **Arduino Teal (Primaire)** | Teal classique | `#008184` | Accent principal, barre d'outils, boutons d'action majeurs |
| **Teal Vibrant (Accent)** | Teal clair | `#00979D` | Survol, boutons de sortie `%Q`, lignes guides de repère magnétique |
| **Sélection Canvas (Jaune)** | Jaune vif | `#FFCC00` | Contour de surbrillance de l'étape, transition ou liaison sélectionnée |
| **Entrées Capteurs (%I)** | Vert Émeraude | `#10B981` | Bouton d'ajout rapide `＋ Entrée (%I)`, validation de raccordement conforme |
| **Sorties Préactionneurs (%Q)**| Bleu Cyan | `#0284C7` | Bouton d'ajout rapide `＋ Sortie (%Q)` et bouton de branche `[⑂]` |
| **Mémoires Internes (%M)** | Violet / Pourpre | `#7C3AED` | Bouton d'ajout rapide `＋ Mémoire (%M)` et onglet actif |
| **Teal Light (Surfaces & Puces)**| Fond cyan doux | `#E5F5F5` | Fond des puces capteurs `%I`, badges actifs, surlignage |
| **Arduino Orange** | Orange d'alerte | `#E67E22` | Badge Transition `T<n>`, qualificateurs temporisés `L`/`D` |
| **Arduino Red** | Rouge brique | `#D9381E` | Actions Reset `R`, bouton suppression `[✕]` / `🗑`, alerte de violation d'alternance |
| **Grille technique** | Gris bleuté doux | `#D7DEE4` | Quadrillage à points d'ingénierie TIA Portal (pas de 24px) |
| **Surfaces Principales** | Blanc pur | `#FFFFFF` | Surface du Canvas Grafcet, fond des cartes d'édition |
| **Surfaces Secondaires** | Gris très doux | `#F7F9FA` | Fond des panneaux latéraux (`Sidebar`), fond de la table |
| **Bordures & Séparateurs** | Gris neutre | `#E1E4E8` | Séparateurs, contours de champs de texte et de cartes |
| **Typographie Code** | Monospace | `'JetBrains Mono', Consolas` | Adresses API (`%I0.0`), expressions ST, réceptivités in-place |

---

## 5. Raccourcis Clavier & Ergonomie de Dessin

| Raccourci | Action |
| :--- | :--- |
| **`T`** ou **`Entrée`** | Insérer la Transition suivante après l'Étape sélectionnée |
| **`S`** ou **`Entrée`** | Insérer l'Étape suivante après la Transition sélectionnée |
| **`B`** | Ouvrir une branche (OU sur une étape, ET sur une transition) |
| **`Suppr`** / **`Retour arrière`** | Supprimer l'élément ou la liaison sélectionnée |
| **`Ctrl + L`** | **Auto-Layout : réaligner et centrer automatiquement tout le diagramme** |
| **`Ctrl + Molette`** | Zoom fluide centré sur le curseur |
| **`Clic Milieu / Droit + Glisser`** | Déplacement panoramique (*Pan*) dans le schéma |
| **`Double-clic sur Transition`** | Édition in-place immédiate de la réceptivité sur le Canvas |
| **`Double-clic sur Étape`** | Édition in-place immédiate du numéro d'étape |
| **`Échap`** | Désélectionner tout |

---

## 6. Spécification du Format de Fichier JSON (`.sfc` / `.tk`)

Exemple de structure d'un programme POU SFC standard :
```json
{
  "programName": "Perceuse_Automatique",
  "variables": [
    {
      "name": "dcy",
      "type": "BOOL",
      "scope": "VAR_INPUT",
      "hardwareAddress": "%I0.0",
      "initialValue": "FALSE",
      "description": "Bouton Départ de Cycle"
    },
    {
      "name": "ROTATION",
      "type": "BOOL",
      "scope": "VAR_OUTPUT",
      "hardwareAddress": "%Q0.0",
      "initialValue": "FALSE",
      "description": "Moteur rotation broche"
    }
  ],
  "steps": [
    {
      "id": "s0",
      "name": "Step0",
      "number": 0,
      "isInitial": true,
      "x": 400.0,
      "y": 60.0,
      "actionBlocks": []
    },
    {
      "id": "s1",
      "name": "Step1",
      "number": 1,
      "isInitial": false,
      "x": 400.0,
      "y": 200.0,
      "actionBlocks": [
        {
          "qualifier": "N",
          "variableName": "ROTATION",
          "duration": null,
          "inlineCondition": null
        }
      ]
    }
  ],
  "transitions": [
    {
      "id": "t1",
      "name": "T1",
      "number": 1,
      "conditionST": "dcy",
      "x": 400.0,
      "y": 130.0
    }
  ],
  "links": [
    {
      "id": "l1",
      "sourceId": "s0",
      "targetId": "t1",
      "intermediatePoints": []
    }
  ],
  "branches": []
}
```

---

## 7. Feuille de Route (Roadmap d'Amélioration)

```mermaid
flowchart TD
    subgraph Etape1 ["Étape 1 : Modèle CEI 61131-3 (Terminée)"]
        A1[Records & Sealed SfcElement] --> A2[Qualificateurs N, S, R, L, D, P]
        A2 --> A3[Variables typées VAR_INPUT / VAR_OUTPUT]
        A3 --> A4[Validation formelle et Tests JUnit 5]
    end

    subgraph Etape2 ["Étape 2 : Édition Graphique TIA Portal & Persistance (Terminée)"]
        B1[Smart Snapping ±14px & Lignes guides] --> B2[Quick-Add Handles contextuels]
        B2 --> B3[Tracé canonique CEI 60848 ET/OU & Boucles]
        B3 --> B4[Auto-Layout hiérarchique Ctrl+L]
        B4 --> B5[Pan & Zoom fluide + Grille technique]
        B5 --> B6[Édition in-place réceptivités et étapes]
        B6 --> B7[Persistance JSON .sfc et .tk]
    end

    subgraph Etape3 ["Étape 3 : Moteur de Simulation Synchrone (Prochaine étape)"]
        C1[Calcul cyclique du marquage Step.X] --> C2[Gestion temporelle Step.T et qualificateurs L/D]
        C2 --> C3[Pupitre virtuel : Forçage des entrées %I]
        C3 --> C4[Modes Pas-à-pas et Temps Réel continu]
    end

    subgraph Etape4 ["Étape 4 : Génération de Code Cible"]
        D1[Exportateur Structured Text ST IEC 61131-3] --> D2[Exportateur croquis Arduino C++ .ino]
        D2 --> D3[Exportateur standard PLCopen XML]
    end

    Etape1 --> Etape2
    Etape2 --> Etape3
    Etape3 --> Etape4
```

---

## 8. Commandes de Compilation & Exécution

### Prérequis
* **JDK 21+ ou Java 25**
* **Gradle 9.4+ / 9.5+** (ou script `./gradlew`)

### Commandes usuelles :
```bash
# Lancer l'application Grafino
./gradlew run

# Exécuter l'ensemble des 20 tests unitaires automatisés
./gradlew test

# Nettoyer les artefacts de build
./gradlew clean
```
