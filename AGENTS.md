# Guide pour Agents et Développeurs — Projet Grafino

## 1. Contexte & Mission du Projet

**Grafino** est un environnement logiciel pédagogique open-source conçu pour l'enseignement et l'ingénierie des automatismes industriels selon la norme internationale **SFC (Sequential Function Chart — CEI 61131-3)** et le **Grafcet (CEI 60848)**.

Le projet est principalement destiné aux étudiants et formateurs de l'enseignement technique supérieur (BTS CRSA, BTS CIRA, BUT GEII, Licences professionnelles et Écoles d'ingénieurs).

Le nom **Grafino** symbolise la passerelle entre :
* La modélisation séquentielle graphique formelle (**Grafcet / SFC**).
* L'exécution concrète sur automates programmables industriels (**API / PLC** Siemens, Schneider, Codesys) et cibles embarquées (**Arduino / microcontrôleurs**).

### Objectif global du cycle de développement :
1. **Édition graphique** ergonomique conforme aux règles internationales.
2. **Simulation synchrone interactive** (pas-à-pas et temps réel, forçage des entrées capteurs `%I` et observation des préactionneurs de sortie `%Q`).
3. **Validation sémantique formelle** (détection des violations d'alternance, blocages ou conflits).
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
6. **Typage des variables (CEI 61131-3)** :
   * Types de données : `BOOL`, `BYTE`, `WORD`, `INT`, `DINT`, `REAL`, `TIME` (`T#2s`), `STRING`.
   * Portées : `VAR_INPUT` (%I), `VAR_OUTPUT` (%Q), `VAR` (%M local), `VAR_GLOBAL` (%G).

---

## 3. Architecture Technique & Bonnes Pratiques Java 25+

Le projet utilise **Java 25** avec **JavaFX 27** et **Gradle 9.5.1**. Il applique une séparation stricte des responsabilités (Clean Architecture / Modèle Découplé) :

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
├── component/                 # Composants visuels pour le Canvas JavaFX
│   ├── Component.java         # Classe de base des éléments graphiques
│   ├── StepAndTransitionCommonBehavior.java # Interactions (poignées, sélection, déplacement)
│   ├── Step.java              # Rendu de l'étape et des compartiments d'actions
│   ├── Transition.java        # Rendu du trait de transition et de la réceptivité
│   ├── Link.java              # Tracé orthogonal Manhattan, flèches et double barre
│   ├── Action.java            # Association graphique d'action
│   └── Variable.java          # Modèle de variable observable pour les tables JavaFX
│
├── storage/                   # Persistance et sérialisation (.sfc / .tk)
│   └── (Moteur de lecture/écriture JSON et conformité d'exportation)
│
└── ui/                        # Interface Utilisateur JavaFX 27
    ├── MainUI.java            # Fenêtre principale, menus et barre d'état
    ├── Grafcet.java           # Canvas interactif : zoom centré, panoramique, magnétisme grille
    ├── TopToolBar.java        # Barre d'outils graphique
    ├── StepProperty.java      # Panneau d'édition des étapes et des blocs d'actions
    ├── TransitionProperty.java# Panneau d'édition des réceptivités et conditions ST
    └── VariableTable.java     # Table éditable des entrées/sorties et adresses API
```

### Principes Java 25+ appliqués :
* **`records`** : Modélisation immuable des données de domaine (`SfcStep`, `SfcTransition`, `SfcActionBlock`, `IecVariable`, `SfcProgram`).
* **`sealed interface`** : `SfcElement permits SfcStep, SfcTransition, SfcBranch` garantissant au compilateur l'exhaustivité totale lors des expressions `switch` avec pattern matching.
* **Compact Constructors** : Validation stricte des invariants dès l'instanciation (interdiction de noms vides, durée obligatoire si qualificateur temporisé, collections rendues immuables par `List.copyOf`).
* **Indépendance UI** : Le paquet `tk.tahri.grafino.model` n'importe aucune classe JavaFX, permettant l'exécution de simulations et de tests unitaires ultra-rapides en mode headless.

---

## 4. Spécification du Format de Fichier JSON (`.sfc` / `.tk`)

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

## 5. Feuille de Route (Roadmap d'Amélioration)

```mermaid
flowchart TD
    subgraph Etape1 ["Étape 1 : Modèle CEI 61131-3 (Terminée)"]
        A1[Records & Sealed SfcElement] --> A2[Qualificateurs N, S, R, L, D, P]
        A2 --> A3[Variables typées VAR_INPUT / VAR_OUTPUT]
        A3 --> A4[Validation formelle et Tests JUnit 5]
    end

    subgraph Etape2 ["Étape 2 : Persistance & Intégration UI (En cours)"]
        B1[Couche de stockage JSON .sfc] --> B2[Pont Modèle pur <-> Composants JavaFX]
        B2 --> B3[Panneau d'édition des actions IEC avec durées]
        B3 --> B4[Export vectoriel PNG Haute Définition]
    end

    subgraph Etape3 ["Étape 3 : Moteur de Simulation Synchrone"]
        C1[Calcul cyclique du marquage Step.X] --> C2[Gestion temporelle Step.T et qualificateurs L/D]
        C2 --> C3[Pupitre virtuel : Forçage des entrées %I]
        C3 --> C4[Modes Pas-à-pas et Continu]
    end

    subgraph Etape4 ["Étape 4 : Génération de Code Cible"]
        D1[Exportateur Structured Text ST IEC 61131-3] --> D2[Exportateur Arduino C++ .ino]
        D2 --> D3[Exportateur standard PLCopen XML]
    end

    Etape1 --> Etape2
    Etape2 --> Etape3
    Etape3 --> Etape4
```

---

## 6. Commandes de Compilation & Exécution

### Prérequis
* **Java 25** (ou JDK 21+ compatible)
* **Gradle 9.5.1** (via wrapper fourni)

### Commandes usuelles :
```bash
# Lancer l'application Grafino
./gradlew run

# Exécuter l'ensemble des tests unitaires automatisés
./gradlew test

# Nettoyer les artefacts de build
./gradlew clean
```
