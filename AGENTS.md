# Guide pour Agents et Développeurs — Projet Grafino

## 1. Contexte & Mission du Projet

**Grafino** est un environnement pédagogique open-source conçu pour enseigner la programmation et la modélisation graphique avec le **Grafcet** (norme internationale **CEI 60848**) aux étudiants et futurs automaticiens industriels.

Le nom "Grafino" provient de la contraction de **Grafcet** et **Arduino** : à terme, le projet vise à permettre aux étudiants de concevoir graphiquement un Grafcet, de le tester en simulation interactive (pas-à-pas et en temps réel), puis de générer directement le code cible (croquis C++ Arduino ou automate programmable).

---

## 2. Architecture Technique

Le projet est développé en **Java 25** avec **JavaFX 27** et s'appuie sur une séparation claire des responsabilités :

```
tk.tahri.grafino/
├── model/                     # Modèle métier indépendant de tout framework UI
│   ├── GrafcetModel.java      # Conteneur global du projet
│   ├── StepModel.java         # Modélisation d'étape (initiale/normale, coordonnées, actions)
│   ├── TransitionModel.java   # Modélisation de transition (réceptivité booléenne)
│   ├── LinkModel.java         # Modélisation de liaison (arcs et points de passage)
│   ├── GrafcetAction.java     # Actions normalisées (qualificateurs N, S, R, D, P)
│   ├── VariableModel.java     # Variables d'automatismes (E/S, mémoires)
│   ├── VariableDirection.java # INPUT (%I), OUTPUT (%Q), INTERNAL (%M)
│   └── VariableType.java      # BOOL, INT
│
├── storage/                   # Couche de persistance et sérialisation (.tk)
│   └── GrafcetStorage.java    # Moteur de sauvegarde/lecture JSON structuré pour fichiers .tk
│
├── component/                 # Composants graphiques JavaFX pour le Canvas
│   ├── Component.java         # Classe de base pour éléments dessinables
│   ├── StepAndTransitionCommonBehavior.java # Comportement commun (poignées, position, liens)
│   ├── Step.java              # Rendu de l'étape (carré simple / double cadre initial CEI 60848)
│   ├── InitStep.java          # Rétrocompatibilité étape initiale
│   ├── Transition.java        # Rendu normalisé (trait, repère Ti, réceptivité dessinée à droite)
│   ├── Link.java              # Tracé orthogonal, synchronisation ET, flèche de remontée
│   ├── Action.java            # Record d'action UI
│   └── Variable.java          # Modèle de variable avec propriétés JavaFX
│
└── ui/                        # Interface Utilisateur et Vues
    ├── MainUI.java            # Fenêtre principale, barre de menus (Fichier/Édition/Aide), barre d'état
    ├── Grafcet.java           # Canvas de dessin orthogonal, gestion souris/sélection, rendu événementiel
    ├── TopToolBar.java        # Barre d'outils graphique (Step, Transition, Branches ET/OU, Supprimer)
    ├── StepProperty.java      # Panneau d'édition d'étape (checkbox initiale, actions avec qualificateurs)
    ├── TransitionProperty.java# Panneau d'édition de transition (réceptivités, assistants opérateurs)
    └── VariableTable.java     # Table éditable des variables (filtrage Entrées/Sorties)
```

---

## 3. Format de Fichier `.tk`

Les projets Grafino sont enregistrés sous l'extension **`.tk`** (fichier JSON lisible et standardisé UTF-8).

Structure d'un fichier `.tk` :
* `name` : Nom du projet / procédé.
* `variables` : Liste des entrées/sorties avec direction (`INPUT`, `OUTPUT`, `INTERNAL`), adresses automates et commentaires.
* `steps` : Étapes avec identifiant unique, coordonnées $(x, y)$, statut `initial` (booléen) et actions associées.
* `transitions` : Transitions avec coordonnées, identifiants et condition de `receptivity` (ex: `1`, `m . a`, `5s/X1`).
* `links` : Liaisons d'alternance avec coordonnées intermédiaires pour les boucles de retour.

---

## 4. Règles Métier & Normes CEI 60848

Toute contribution doit scrupuleusement respecter les règles fondamentales du Grafcet :
1. **Alternance stricte** : Une étape ne peut être reliée qu'à une transition, et inversement (interdiction de liaison étape-étape ou transition-transition).
2. **Sens d'évolution** : Du haut vers le bas par défaut ; toute liaison ascendante (boucle vers le haut) doit comporter une flèche d'orientation.
3. **Étape initiale** : Représentée par un double carré concentrique.
4. **Réceptivités** : Dessinées à droite du trait de transition en clair (condition booléenne ou `= 1` si toujours vraie).
5. **Actions** : Dessinées à droite de l'étape avec compartiment séparé pour le qualificateur (`N`, `S`, `R`, etc.).
6. **Synchronisation en ET** : Dessin en double barre horizontale lors de divergences ou convergences en ET.

---

## 5. Commandes de Compilation & Exécution

### Prérequis
* Java 25 (ou Java 21+ LTS)
* Gradle 9.x (ou le wrapper fourni)

### Exécution
```bash
# Lancement de l'application
gradle run

# En cas d'anciens démons Gradle bloqués
gradle --stop
gradle run
```

### Tests automatisés
```bash
gradle test
```

---

## 6. Feuille de Route (Roadmap)

- [x] **Étape 1 — Éditeur graphique aux normes et Persistance (.tk)** :
  - Tracé conforme CEI 60848 (étape initiale double encadrement, actions normalisées, réceptivités dessinées).
  - Sauvegarde et ouverture complètes au format `.tk`.
  - Menus Fichier standard (Nouveau, Ouvrir, Enregistrer, Enregistrer sous).
  - Suppression d'éléments (`Suppr`), réinitialisation propre des compteurs.
  - Découplage Modèle / Vue et passage à un rendu événementiel performant.

- [ ] **Étape 2 — Moteur de Simulation interactive (À venir)** :
  - Mode Édition / Mode Simulation.
  - Gestion du marquage dynamique (coloration verte / surlignage des étapes actives).
  - Panneau interactif de forçage des entrées/capteurs (0/1).
  - Évaluation cyclique synchrone du franchissement des transitions et exécution des actions (Set/Reset/Normal).

- [ ] **Étape 3 — Générateur de Code Automate / Arduino** :
  - Traduction automatique du modèle Grafcet vers un sketch Arduino `.ino` (boucle `loop()` cyclique d'automate).
  - Export optionnel en Structured Text (ST / CEI 61131-3).
