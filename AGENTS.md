# Guide pour Agents et Développeurs — Projet Grafino

## 1. Contexte & Mission du Projet

**Grafino** est un environnement logiciel pédagogique open-source conçu pour l'enseignement et l'ingénierie des automatismes industriels selon la norme internationale **SFC (Sequential Function Chart — CEI 61131-3)** et le **Grafcet (CEI 60848)**.

Le projet est principalement destiné aux étudiants et formateurs de l'enseignement technique supérieur (BTS CRSA, BTS CIRA, BUT GEII, Licences professionnelles et Écoles d'ingénieurs).

Le nom **Grafino** symbolise la passerelle entre :
* La modélisation séquentielle graphique formelle (**Grafcet / SFC**).
* L'exécution concrète sur automates programmables industriels (**API / PLC** Siemens, Schneider, Codesys) et cibles embarquées (**Arduino / microcontrôleurs**).

### Objectif global du cycle de développement :
1. **Édition graphique** ergonomique conforme aux règles internationales et adaptative en plein écran.
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
1. **Alternance stricte** : Une étape ne peut être raccordée qu'à une transition, et une transition qu'à une étape. Aucune liaison directe étape-étape ou transition-transition n'est autorisée.
2. **Sens d'évolution et flèches** : L'évolution naturelle s'effectue du haut vers le bas. Toute liaison ascendante (boucle de reprise vers le haut) comporte obligatoirement une flèche orientée vers le haut.
3. **Étape initiale** : Au moins une étape initiale doit être définie (représentée par un double carré concentrique).
4. **Qualificateurs d'actions CEI 61131-3** :
   * `N` : Non-stored (action continue pendant l'activité de l'étape).
   * `S` : Set / Mémorisé à 1.
   * `R` : Reset (remise à 0).
   * `P`, `P1`, `P0` : Impulsions (Pulse) sur front d'activation ou désactivation.
   * `L` : Time Limited (action limitée dans le temps `T#...`).
   * `D` : Time Delayed (action retardée par une temporisation `T#...`).
   * `SD`, `DS`, `SL` : Combinaisons temporisées et mémorisées.
5. **Structures de divergence et convergence** :
   * **Sélection de séquence (OU)** : traits simples alternatifs avec priorités d'évaluation.
   * **Parallélisme / Synchronisation (ET)** : double barre horizontale simultanée.
6. **Typage et Adressage des variables (CEI 61131-3)** :
   * Types de données : `BOOL`, `BYTE`, `WORD`, `INT`, `DINT`, `REAL`, `TIME` (`T#2s`), `STRING`.
   * Portées : `VAR_INPUT` (%I), `VAR_OUTPUT` (%Q), `VAR` (%M local), `VAR_GLOBAL` (%G).
   * Format d'adresse matérielle : `%[I|Q|M][X|B|W|D]?[0-9]+(\.[0-9]+)?` (ex : `%I0.0`, `%Q0.1`, `%M0.0`, `%IW2`).
   * Couplage bidirectionnel strict entre la portée et l'adresse avec résolution automatique des collisions.

---

## 3. Architecture Technique & Bonnes Pratiques Java 25+

Le projet utilise **Java 25** avec **JavaFX 27** et **Gradle**. Il applique une séparation stricte des responsabilités (Clean Architecture / Modèle Découplé) :

```
tk.tahri.grafino/
├── model/                     # Modèle métier pur CEI 61131-3 (AUCUNE dépendance JavaFX)
│   ├── SfcElement.java        # Sealed interface racine (permits SfcStep, SfcTransition, SfcBranch)
│   ├── SfcStep.java           # Record d'étape (id, name, number, isInitial, actionBlocks)
│   ├── SfcTransition.java     # Record de transition (id, name, number, conditionST en Structured Text)
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
│   ├── StepAndTransitionCommonBehavior.java # Interactions (poignées, sélection, déplacement)
│   ├── Step.java              # Rendu de l'étape (simple ou double cadre initial) et actions
│   ├── Transition.java        # Rendu de la transition et condition ST
│   ├── Link.java              # Tracé orthogonal Manhattan, flèches et double barre
│   ├── Action.java            # Association graphique d'action (qualificateur, variable, paramètre)
│   └── Variable.java          # Modèle observable avec validation et couplage scope/adresse
│
└── ui/                        # Interface Utilisateur JavaFX 27 inspirée d'Arduino IDE
    ├── AppTheme.java          # Design tokens & charte visuelle (Teal Arduino #008184, cartes, puces, couleurs sémantiques)
    ├── MainUI.java            # Fenêtre principale avec SplitPane vertical rétractable pleine hauteur
    ├── Grafcet.java           # Canvas fluide pleine surface (ResizableCanvas)
    ├── TopToolBar.java        # Barre d'outils graphique avec infobulles descriptives
    ├── StepProperty.java      # Sidebar d'édition d'étape (étape initiale, qualificateurs, cartes d'action)
    ├── TransitionProperty.java# Sidebar d'édition de transition (condition ST, puces %I, opérateurs)
    └── VariableTable.java     # Table des variables (onglets chromatiques, recherche, boutons %I/%Q/%M)
```

### Principes Java 25+ appliqués :
* **`records`** : Modélisation immuable des données de domaine (`SfcStep`, `SfcTransition`, `SfcActionBlock`, `IecVariable`, `SfcProgram`).
* **`sealed interface`** : `SfcElement permits SfcStep, SfcTransition, SfcBranch` garantissant l'exhaustivité totale lors des expressions `switch` avec pattern matching.
* **Compact Constructors** : Validation stricte des invariants dès l'instanciation (noms non vides, durées obligatoires pour temporisations).
* **Indépendance UI** : Le paquet `tk.tahri.grafino.model` n'importe aucune classe JavaFX, permettant l'exécution de simulations et de tests unitaires ultra-rapides en mode headless.

---

## 4. Charte Graphique & Design System (Inspiration Arduino IDE)

L'interface adopte les codes visuels emblématiques de **l'IDE Arduino (Arduino IDE 2.x)** :

| Rôle | Teinte | Hex | Usage |
| :--- | :--- | :--- | :--- |
| **Arduino Teal (Primaire)** | Teal classique | `#008184` | Accent principal, barre d'outils, boutons d'action majeurs |
| **Teal Vibrant (Accent)** | Teal clair | `#00979D` | Survol, boutons de sortie `%Q`, éléments sélectionnés |
| **Sélection Canvas (Jaune)** | Jaune vif | `#FFCC00` | Contour de surbrillance de l'étape, transition ou liaison sélectionnée |
| **Entrées Capteurs (%I)** | Vert Émeraude | `#10B981` | Bouton d'ajout rapide `＋ Entrée (%I)` et onglet actif |
| **Sorties Préactionneurs (%Q)**| Bleu Cyan | `#0284C7` | Bouton d'ajout rapide `＋ Sortie (%Q)` et onglet actif |
| **Mémoires Internes (%M)** | Violet / Pourpre | `#7C3AED` | Bouton d'ajout rapide `＋ Mémoire (%M)` et onglet actif |
| **Teal Light (Surfaces & Puces)**| Fond cyan doux | `#E5F5F5` | Fond des puces capteurs `%I`, badges actifs, surlignage |
| **Arduino Orange** | Orange d'alerte | `#E67E22` | Badge Transition `T<n>`, qualificateurs temporisés `L`/`D` |
| **Arduino Red** | Rouge brique | `#D9381E` | Actions Reset `R`, suppression 🗑, alertes de syntaxe d'adresse |
| **Surfaces Principales** | Blanc pur | `#FFFFFF` | Surface du Canvas Grafcet, fond des cartes d'édition |
| **Surfaces Secondaires** | Gris très doux | `#F7F9FA` | Fond des panneaux latéraux (`Sidebar`), fond de la table |
| **Bordures & Séparateurs** | Gris neutre | `#E1E4E8` | Séparateurs, contours de champs de texte et de cartes |
| **Typographie Code** | Monospace | `'JetBrains Mono', Consolas` | Adresses API (`%I0.0`), expressions ST, qualificateurs |

---

## 5. Spécification du Format de Fichier JSON (`.sfc` / `.tk`)

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

## 6. Feuille de Route (Roadmap d'Amélioration)

```mermaid
flowchart TD
    subgraph Etape1 ["Étape 1 : Modèle CEI 61131-3 (Terminée)"]
        A1[Records & Sealed SfcElement] --> A2[Qualificateurs N, S, R, L, D, P]
        A2 --> A3[Variables typées VAR_INPUT / VAR_OUTPUT]
        A3 --> A4[Validation formelle et Tests JUnit 5]
    end

    subgraph Etape2 ["Étape 2 : Persistance & Intégration UI (Terminée)"]
        B1[Couche de stockage JSON .sfc et .tk] --> B2[Table des variables rétractable SplitPane]
        B2 --> B3[Validation adresses IEC et couplage Scope/Adresse]
        B3 --> B4[Sidebar d'édition Step / Transition style Arduino]
        B4 --> B5[Canvas adaptatif plein écran ResizableCanvas]
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

## 7. Commandes de Compilation & Exécution

### Prérequis
* **Java 25** (ou JDK 21+ compatible)
* **Gradle 9.5.1** (ou gradle système)

### Commandes usuelles :
```bash
# Lancer l'application Grafino
gradle run

# Exécuter l'ensemble des tests unitaires automatisés
gradle test

# Nettoyer les artefacts de build
gradle clean
```
