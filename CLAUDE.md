# Bibliothèque de Contraintes - Bonnes Pratiques de Développement

## Architecture Modulaire

Ce projet implémente une bibliothèque de contraintes réutilisables pour la planification de shifts avec OR-Tools.

### Structure des Packages

```
com.cricri/
├── constraints/          # Contraintes réutilisables
├── objectives/          # Fonctions objectif
├── service/            # Services principaux (Context, Scheduler)
├── model/             # Modèles de données
└── Main.java         # Point d'entrée de démonstration
```

## Règles de Développement

### 0. Système de Configuration et Objectif Unifié (Architecture Refactorisée)

**Classes Clés :**
- `ConstraintType` : Enumération des types de contraintes disponibles
- `ConstraintNature` : HARD (obligatoire) ou SOFT (avec pénalité)
- `ConstraintConfig` : Record encapsulant la configuration complète d'une contrainte
- `ConstraintFactory` : Factory pour créer des contraintes à partir de configurations
- `ObjectiveWeight` : Enumération des poids d'objectif pour les contraintes SOFT
- `ObjectiveCollector` : Collecteur stateless pour l'objectif global unifié

**Nouveau système d'objectif unifié :**
- **UN SEUL** `model.maximize()` appelé avec l'objectif global
- Chaque contrainte SOFT contribue via `ObjectiveCollector.addTerm()`
- Poids automatiques basés sur la criticité : CRITICAL (±10000) > HIGH (±1000) > MEDIUM (±100) > LOW (±10)
- Maximisation (poids +) pour objectifs directs, minimisation (poids -) pour violations

**Avantages du système unifié :**
- Résout le conflit technique OR-Tools (un seul objectif possible)
- Configuration déclarative inchangée pour l'utilisateur
- Collecteur stateless respectant les principes CLAUDE.md
- Poids calibrés automatiquement selon le type de contrainte

### 1. Contraintes (package `constraints`)

**Règles obligatoires :**
- Toute contrainte DOIT implémenter l'interface `Constraint`
- Nom de classe DOIT finir par `Constraint` (ex: `MaxHoursPerWeekConstraint`)
- Méthode `getName()` DOIT retourner un nom descriptif avec paramètres (ex: "MaxHoursPerWeek(39.0h)")
- Contraintes DOIVENT être stateless (pas d'état interne modifiable)
- TOUJOURS appeler `context.ensureVariablesInitialized()` au début des méthodes apply
- Les contraintes SOFT DOIVENT utiliser le `ObjectiveCollector` passé en paramètre
- JAMAIS appeler directement `model.maximize()` ou `model.minimize()` dans les contraintes

**Poids d'objectif pour contraintes SOFT (ObjectiveWeight) :**
- `MAXIMIZE_CRITICAL (10000)` : Objectif critique (ex: MaximizeWorkingHours)
- `MAXIMIZE_HIGH/MEDIUM/LOW (1000/100/10)` : Objectifs secondaires
- `MINIMIZE_CRITICAL (-10000)` : Violations critiques (sécurité, repos minimum)
- `MINIMIZE_HIGH (-1000)` : Violations importantes (légal, heures max)
- `MINIMIZE_MEDIUM (-100)` : Violations moyennes (confort, jours de repos)
- `MINIMIZE_LOW (-10)` : Violations mineures
- `DISABLED (0)` : Pas d'objectif (contraintes HARD)

**Exemple type (Interface refactorisée avec ObjectiveCollector) :**
```java
public class MaContrainte implements Constraint {
    private final int parametre;
    private final ConstraintNature nature;
    private final ConstraintConfig config;

    public MaContrainte(int parametre, ConstraintNature nature, ConstraintConfig config) {
        this.parametre = parametre;
        this.nature = nature;
        this.config = config;
    }

    @Override
    public void applyHardConstraint(SchedulingContext context) {
        context.ensureVariablesInitialized();
        // Logique HARD : contraintes absolues avec addEquality(), addLessOrEqual(), etc.
    }

    @Override
    public void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector) {
        context.ensureVariablesInitialized();

        // Créer des variables de violation
        IntVar violationVar = context.getModel().newIntVar(0, 1000, "violation_" + getName());

        // Logique SOFT : contraintes avec violations possibles
        // ...

        // Ajouter la violation au collecteur avec le poids approprié
        ObjectiveWeight weight = (config != null) ? config.getObjectiveWeight() : ObjectiveWeight.MINIMIZE_MEDIUM;
        collector.addTerm(violationVar, weight.getWeight());
    }

    @Override
    public String getName() {
        return "MaContrainte(" + parametre + ", " + nature + ")";
    }

    @Override
    public ConstraintNature getNature() {
        return nature;
    }
}
```

### 1.1. Contraintes Spéciales

**MaximizeWorkingHoursConstraint (Refactorisée avec ObjectiveCollector) :**
Cette contrainte reproduit l'ancienne logique `addWeekdayStaffingObjective()` qui avait été supprimée, mais utilise maintenant le système d'objectif unifié.

```java
// Configuration inchangée pour l'utilisateur
ConstraintConfig.of(
    ConstraintType.MAXIMIZE_WORKING_HOURS,
    ConstraintNature.SOFT,
    "weekdayMultiplier", 2
)
```

**Fonctionnement (refactorisé) :**
- Ajoute ses termes au `ObjectiveCollector` via `collector.addTerm()`
- Maximise les heures réelles avec poids `MAXIMIZE_CRITICAL (10000)`
- Bonus pondéré pour les jours de semaine : `(weight * weekdayMultiplier) / 10`
- Participe à l'objectif global unifié (plus de conflit OR-Tools)

**Avantages post-refacto :**
- Compatible avec autres contraintes SOFT simultanément
- Poids automatique depuis `config.getObjectiveWeight()`
- Un seul `model.maximize()` global dans `ShiftScheduler.buildModel()`

### 3. Tests

**Règles obligatoires :**
- Chaque contrainte DOIT avoir des tests unitaires
- Tester les cas limites et les conflits
- Utiliser `SchedulingContext` dans les tests pour l'isolation
- Vérifier que la contrainte fonctionne avec différents jeux de données

**Structure de test recommandée (avec ObjectiveCollector) :**
```java
@Test
void testMaContrainteSoft() {
    // Arrange
    List<Employee> employees = createTestEmployees();
    List<Shift> shifts = createTestShifts();
    SchedulingContext context = new SchedulingContext(employees, shifts, indexMap);
    ObjectiveCollector collector = new ObjectiveCollector();
    MaContrainte constraint = new MaContrainte(parametre, ConstraintNature.SOFT, config);

    // Act
    constraint.applySoftConstraint(context, collector);

    // Assert
    assertFalse(collector.isEmpty(), "La contrainte SOFT doit ajouter des termes d'objectif");
    assertTrue(collector.getTermCount() > 0, "Des termes doivent être ajoutés au collecteur");

    // Test d'intégration avec solver
    context.getModel().maximize(collector.build());
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(context.getModel());
    assertTrue(status == OPTIMAL || status == FEASIBLE);
}

@Test
void testMaContrainteHard() {
    // Arrange
    SchedulingContext context = new SchedulingContext(employees, shifts, indexMap);
    MaContrainte constraint = new MaContrainte(parametre, ConstraintNature.HARD, config);

    // Act
    constraint.applyHardConstraint(context);

    // Assert - Vérifier que les contraintes HARD sont respectées
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(context.getModel());
    assertTrue(status == OPTIMAL || status == FEASIBLE);
    // Vérifications spécifiques aux contraintes HARD...
}
```

### 4. Configuration et Usage

**API Moderne avec ConstraintConfig (Objectif unifié automatique) :**
```java
// Configuration simplifiée - les poids d'objectif sont gérés automatiquement
List<ConstraintConfig> constraintConfigs = Arrays.asList(
    ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD),
    ConstraintConfig.of(ConstraintType.MAX_HOURS_PER_WEEK, ConstraintNature.SOFT, "maxHoursPerWeek", 39 * 60),
    ConstraintConfig.of(ConstraintType.MINIMUM_REST, ConstraintNature.HARD, "minRestHours", 11),
    ConstraintConfig.of(ConstraintType.MAXIMIZE_WORKING_HOURS, ConstraintNature.SOFT, "weekdayMultiplier", 2),
    ConstraintConfig.of(ConstraintType.MINIMUM_REST_DAYS, ConstraintNature.SOFT, "minRestDaysPerWeek", 2)
);

ShiftScheduler scheduler = new ShiftScheduler(employees, shifts);
for (ConstraintConfig config : constraintConfigs) {
    scheduler.withConstraint(ConstraintFactory.create(config));
}
// buildModel() gère automatiquement l'objectif unifié
scheduler.buildModel();

// Résultat : UN SEUL model.maximize() avec tous les termes SOFT combinés
```

**API Legacy (pour compatibilité avec les tests) :**
```java
// Utiliser les méthodes utilitaires dans TestDataFactory
ShiftScheduler scheduler = TestDataFactory.createStandardSchedulerWithConfig(
    employees, shifts, 39 * 60, 11, 5 * 60);
```

**Configurations Standard :**
- `TestDataFactory.createStandardScheduler()` : Contraintes standard (40h/semaine, 11h repos, 5h min/shift)
- `TestDataFactory.createMinimumCoverageScheduler()` : Seulement couverture minimum
- Utiliser `ConstraintConfig` pour des configurations personnalisées

### 5. Extensibilité

**Pour ajouter une nouvelle contrainte SOFT (avec ObjectiveCollector) :**

1. Créer la classe dans `constraints/` implémentant l'interface `Constraint`
2. Implémenter `applySoftConstraint(context, collector)` avec :
   - Création des variables de violation si nécessaire
   - Ajout des termes via `collector.addTerm(variable, weight.getWeight())`
   - Récupération du poids via `config.getObjectiveWeight()`
3. Ajouter le nouveau type dans `ConstraintType.java`
4. Ajouter le cas correspondant dans `ConstraintFactory.java`
5. Ajouter les tests dans `test/` (tester `ObjectiveCollector.getTermCount()`)
6. Documenter les paramètres et le type de violation/objectif

**Exemple complet d'ajout de contrainte :**
```java
// 1. Créer MaContrainte.java
public class MaContrainte implements Constraint {
    public MaContrainte(int param, ConstraintNature nature, ConstraintPriority priority) { ... }
}

// 2. Ajouter dans ConstraintType.java
MA_CONTRAINTE

// 3. Ajouter dans ConstraintFactory.java
case MA_CONTRAINTE -> createMaConstraint(config);

// 4. Usage avec ConstraintConfig (poids automatique)
ConstraintConfig.of(ConstraintType.MA_CONTRAINTE, ConstraintNature.SOFT, "param", valeur)
```

**Pour ajouter un nouvel objectif :**
- **Plus nécessaire** - Utiliser les contraintes SOFT avec `ObjectiveCollector`
- L'objectif global unifié combine automatiquement tous les termes
- Préférer créer une contrainte SOFT plutôt qu'une classe d'objectif séparée

### 6. Bonnes Pratiques OR-Tools

- Toujours nommer les variables avec des préfixes clairs (`assign_e1_s2`)
- Utiliser `LinearExprBuilder` pour construire les expressions complexes
- Préférer `addEquality()`, `addLessOrEqual()` aux formes avec constantes
- **JAMAIS** appeler `model.maximize()` ou `model.minimize()` dans les contraintes
- Pour les contraintes SOFT : utiliser `ObjectiveCollector.addTerm()` exclusivement
- Valider les indices avant d'accéder aux tableaux
- Gérer les cas où `shifts.isEmpty()` ou `employees.isEmpty()`

### 7. Performance

- Un seul `ObjectiveCollector` partagé pour toutes les contraintes SOFT
- Un seul appel `model.maximize()` dans `ShiftScheduler.buildModel()`
- Éviter les boucles imbriquées O(n³) quand possible
- Lazy loading des variables dans `SchedulingContext`
- Réutiliser les expressions communes
- Les poids négatifs (minimisation) et positifs (maximisation) sont combinés automatiquement

### 8. Documentation

**Obligatoire pour chaque contrainte :**
- Javadoc expliquant l'objectif
- Paramètres acceptés et leurs limites
- Exemple d'usage
- Complexité algorithmique si pertinente

**Exemple :**
```java
/**
 * Contrainte limitant le nombre d'heures maximum par semaine pour chaque employé.
 *
 * @param maxHoursPerWeek Limite en minutes (ex: 39*60 pour 39h)
 *
 * Exemple d'usage:
 * scheduler.withConstraint(new MaxHoursPerWeekConstraint(39 * 60));
 *
 * Complexité: O(employees × weeks × shifts)
 */
public class MaxHoursPerWeekConstraint implements Constraint {
    // ...
}
```

## Points d'Attention (Post-Refacto SOFT)

- **Thread Safety** : Les contraintes doivent être thread-safe (stateless)
- **ObjectiveCollector** : Thread-safe avec méthodes `synchronized`
- **Un seul objectif** : `ShiftScheduler.buildModel()` appelle `model.maximize()` une seule fois
- **Pas de conflit OR-Tools** : Toutes les contraintes SOFT contribuent au même objectif global
- **Validation** : Implémenter `validate()` pour vérifier les prérequis
- **Debugging** : Le nom retourné par `getName()` apparaît dans les logs + nombre de termes d'objectif
- **Compatibilité** : Tester l'interaction entre différentes contraintes SOFT via le collecteur

## Workflow de Développement

1. **Design** : Définir la contrainte mathématiquement
2. **Interface** : Créer la classe implémentant `Constraint`
3. **Tests** : Écrire les tests avant l'implémentation
4. **Implémentation** : Coder la logique OR-Tools
5. **Integration** : Tester avec d'autres contraintes
6. **Documentation** : Compléter la Javadoc et exemples

## Architecture de Tests

### Structure Recommandée

```
src/test/java/com/cricri/
├── constraints/          # Tests unitaires des contraintes
├── objectives/          # Tests des fonctions objectif
├── service/            # Tests des services (Scheduler, Context)
├── integration/        # Tests d'intégration bout-en-bout
├── performance/        # Tests de performance et benchmarks
├── robustness/         # Tests de robustesse et cas limites
└── testutils/          # Utilitaires de test réutilisables
    ├── ConstraintTestBase.java    # Classe de base pour tests de contraintes
    ├── SolverAssertions.java      # Assertions spécialisées OR-Tools
    └── TestDataFactory.java      # Factory pour données de test
```

### Règles pour les Tests

**Héritage et Structure :**
- Tests de contraintes DOIVENT étendre `ConstraintTestBase`
- Utiliser `setupSpecific()` pour la configuration spécifique au test
- Appeler `constraintPropertiesTest(constraint)` pour valider les propriétés de base

**Assertions Réutilisables :**
- Utiliser `SolverAssertions.assertSolutionExists()` au lieu de code dupliqué
- Privilégier `SolverAssertions.assertAllShiftsCovered()` pour vérifier la couverture
- Utiliser `SolverAssertions.solveAndAssertSolution()` pour résolution + vérification

**Données de Test :**
- Utiliser `TestDataFactory` pour créer des données cohérentes
- `createStandardEmployees()` et `createStandardWeekShifts()` pour cas standards
- `createConflictingShifts()` et `createTwoWeekShifts()` pour cas spéciaux

**Piège à Éviter - Tests d'Assumptions :**
- NE PAS tester la solution optimale trouvée par OR-Tools
- Tester UNIQUEMENT que les contraintes sont respectées
- OR-Tools peut trouver plusieurs solutions valides différentes
- Exemple INCORRECT : "chaque employé doit être assigné à exactement un shift"
- Exemple CORRECT : "chaque shift doit avoir entre min et max employés"

### Règles de Collaboration avec Claude

**Approche TDD Obligatoire :**
- TOUJOURS écrire les tests AVANT l'implémentation
- Cycle Rouge → Vert → Refactor systématique
- Ne jamais implémenter de fonctionnalités non demandées
- Tester les contraintes SOFT avec `ObjectiveCollector.getTermCount()`

**Règles de Communication :**
- Claude NE DOIT PAS ajouter de fonctionnalités sans autorisation explicite
- Claude NE DOIT PAS créer de tests non demandés
- Demander confirmation avant d'ajouter du code supplémentaire
- Expliquer le "pourquoi" avant le "comment"
- **NOUVEAU** : Toujours rappeler l'usage d'`ObjectiveCollector` pour les contraintes SOFT

**Philosophie Bibliothèque de Règles :**
- Chaque contrainte est une règle métier isolée et réutilisable
- Priorité à la composition plutôt qu'à l'héritage
- API fluide pour combiner les règles selon le besoin
- Chaque règle doit pouvoir être testée indépendamment

**Workflow de Développement avec Claude :**
1. **Demande** : L'utilisateur exprime un besoin précis
2. **Design** : Claude propose une approche (pas d'implémentation)
3. **Validation** : L'utilisateur approuve l'approche
4. **TDD** : Tests d'abord, implémentation ensuite
5. **Refactor** : Amélioration du code existant seulement si demandé

## Règles Strictes de Développement

**Règles de Code :**
- JAMAIS de code mort ou commenté
- JAMAIS de `System.out.println()` en production (utiliser les logs)
- JAMAIS de constantes magiques (toujours nommer les valeurs)
- Variables et méthodes en français (cohérence avec le domaine métier)

**Règles de Commits :**
- Un commit = une fonctionnalité complète et testée
- Messages de commit explicites avec contexte métier
- JAMAIS commit de code qui ne compile pas
- JAMAIS commit de tests qui échouent

**Règles d'Architecture :**
- Une contrainte = une responsabilité unique
- Séparation stricte contraintes / objectifs / services
- Pas de dépendances circulaires entre packages
- Interface avant implémentation (design by contract)
