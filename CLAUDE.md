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

### 0. Système de Configuration (Nouvelle Architecture)

**Classes Clés :**
- `ConstraintType` : Enumération des types de contraintes disponibles
- `ConstraintNature` : HARD (obligatoire) ou SOFT (avec pénalité)
- `ConstraintPriority` : Priorités nommées (FUNDAMENTAL, CONSISTENCY, SAFETY, NORMAL, COMFORT, OPTIMIZATION)
- `ConstraintConfig` : Record encapsulant la configuration complète d'une contrainte
- `ConstraintFactory` : Factory pour créer des contraintes à partir de configurations

**Avantages du nouveau système :**
- Configuration déclarative depuis interface graphique ou fichiers
- Séparation claire entre types, natures et priorités
- Support natif des contraintes souples (SOFT)
- Factory pattern pour une création centralisée
- Paramètres typés avec validation

**Migration depuis l'ancien système :**
- `withStandardConstraints()` → `TestDataFactory.createStandardSchedulerWithConfig()`
- `withMinimumCoverage()` → `ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ...)`
- Méthodes fluides → Configuration par liste de `ConstraintConfig`

### 1. Contraintes (package `constraints`)

**Règles obligatoires :**
- Toute contrainte DOIT implémenter l'interface `Constraint`
- Nom de classe DOIT finir par `Constraint` (ex: `MaxHoursPerWeekConstraint`)
- Méthode `getName()` DOIT retourner un nom descriptif avec paramètres (ex: "MaxHoursPerWeek(39.0h)")
- Contraintes DOIVENT être stateless (pas d'état interne modifiable)
- TOUJOURS appeler `context.ensureVariablesInitialized()` au début de `apply()`

**Priorités avec énumérations (Nouveau Système) :**
- `ConstraintPriority.FUNDAMENTAL (-10)` : Contraintes fondamentales (couverture minimum)
- `ConstraintPriority.CONSISTENCY (-5)` : Contraintes de cohérence (assignation-heures)
- `ConstraintPriority.SAFETY (-3)` : Contraintes de sécurité (repos minimum)
- `ConstraintPriority.NORMAL (0)` : Contraintes normales (heures max/semaine)
- `ConstraintPriority.COMFORT (5)` : Contraintes de confort (jours de repos)
- `ConstraintPriority.OPTIMIZATION (10)` : Contraintes d'optimisation (maximisation heures)

**Exemple type (Nouvelle Interface) :**
```java
public class MaContrainte implements Constraint {
    private final int parametre;
    private final ConstraintNature nature;
    private final ConstraintPriority priority;

    public MaContrainte(int parametre, ConstraintNature nature, ConstraintPriority priority) {
        this.parametre = parametre;
        this.nature = nature;
        this.priority = priority;
    }

    @Override
    public void apply(SchedulingContext context) {
        context.ensureVariablesInitialized();
        // Logique de contrainte...
    }

    @Override
    public String getName() {
        return "MaContrainte(" + parametre + ", " + nature + ")";
    }

    @Override
    public ConstraintPriority getPriority() {
        return priority;
    }

    @Override
    public ConstraintNature getNature() {
        return nature;
    }
}
```

### 1.1. Contraintes Spéciales

**MaximizeWorkingHoursConstraint :**
Cette contrainte reproduit l'ancienne logique `addWeekdayStaffingObjective()` qui avait été supprimée. Elle résout le problème où les employés ne travaillaient que 5h au lieu de 8h+ par shift.

```java
// Remplace l'ancienne addWeekdayStaffingObjective()
ConstraintConfig.of(
    ConstraintType.MAXIMIZE_WORKING_HOURS,
    ConstraintNature.SOFT,
    ConstraintPriority.OPTIMIZATION,
    "weekdayMultiplier", 2  // Pondération ×3 pour jours de semaine (1 + 2)
)
```

**Fonctionnement :**
- Maximise les heures réelles travaillées (pousse vers la durée complète des shifts)
- Favorise les assignations en semaine plutôt qu'en weekend
- Utilise `model.maximize()` pour optimiser les heures totales
- Nature SOFT avec priorité OPTIMIZATION pour s'appliquer après les contraintes critiques

**Migration :**
- Ancien : `addWeekdayStaffingObjective()` dans le scheduler
- Nouveau : `MaximizeWorkingHoursConstraint` configurée comme contrainte

### 2. Objectifs (package `objectives`) - En Cours de Migration

**⚠️ Statut : La plupart des objectifs ont été migrés vers des contraintes SOFT**

**Anciennes fonctions objectif migrées :**
- `WeekdayPreferenceObjective` → `WeekdayPreferenceConstraint` + `MaximizeWorkingHoursConstraint`
- `addWeekdayStaffingObjective()` → `MaximizeWorkingHoursConstraint`

**Règles pour les objectifs restants :**
- Toute fonction objectif DOIT implémenter `ObjectiveFunction`
- Nom de classe DOIT finir par `Objective`
- Une seule fonction objectif peut être appliquée (combine les critères si nécessaire)
- Toujours utiliser `context.getModel().maximize()` ou `minimize()`

### 3. Tests

**Règles obligatoires :**
- Chaque contrainte DOIT avoir des tests unitaires
- Tester les cas limites et les conflits
- Utiliser `SchedulingContext` dans les tests pour l'isolation
- Vérifier que la contrainte fonctionne avec différents jeux de données

**Structure de test recommandée :**
```java
@Test
void testMaContrainte() {
    // Arrange
    List<Employee> employees = createTestEmployees();
    List<Shift> shifts = createTestShifts();
    SchedulingContext context = new SchedulingContext(employees, shifts, indexMap);
    MaContrainte constraint = new MaContrainte(parametre);

    // Act
    constraint.apply(context);
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(context.getModel());

    // Assert
    assertTrue(status == OPTIMAL || status == FEASIBLE);
    // Vérifications spécifiques...
}
```

### 4. Configuration et Usage

**API Moderne avec ConstraintConfig (Recommandée) :**
```java
List<ConstraintConfig> constraintConfigs = Arrays.asList(
    ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.HARD, ConstraintPriority.FUNDAMENTAL),
    ConstraintConfig.of(ConstraintType.MAX_HOURS_PER_WEEK, ConstraintNature.HARD, ConstraintPriority.NORMAL, "maxHoursPerWeek", 39 * 60),
    ConstraintConfig.of(ConstraintType.MINIMUM_REST, ConstraintNature.HARD, ConstraintPriority.SAFETY, "minRestHours", 11),
    ConstraintConfig.of(ConstraintType.WEEKDAY_PREFERENCE, ConstraintNature.SOFT, ConstraintPriority.COMFORT, "multiplier", 1)
);

ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts);
for (ConstraintConfig config : constraintConfigs) {
    scheduler.withConstraint(ConstraintFactory.create(config));
}
scheduler.buildModel();
```

**API Legacy (pour compatibilité avec les tests) :**
```java
// Utiliser les méthodes utilitaires dans TestDataFactory
ModularShiftScheduler scheduler = TestDataFactory.createStandardSchedulerWithConfig(
    employees, shifts, 39 * 60, 11, 5 * 60);
```

**Configurations Standard :**
- `TestDataFactory.createStandardScheduler()` : Contraintes standard (40h/semaine, 11h repos, 5h min/shift)
- `TestDataFactory.createMinimumCoverageScheduler()` : Seulement couverture minimum
- Utiliser `ConstraintConfig` pour des configurations personnalisées

### 5. Extensibilité

**Pour ajouter une nouvelle contrainte :**

1. Créer la classe dans `constraints/` implémentant l'interface `Constraint`
2. Ajouter le nouveau type dans `ConstraintType.java`
3. Ajouter le cas correspondant dans `ConstraintFactory.java`
4. Ajouter les tests dans `test/`
5. Documenter les paramètres et cas d'usage

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

// 4. Usage avec ConstraintConfig
ConstraintConfig.of(ConstraintType.MA_CONTRAINTE, ConstraintNature.HARD, ConstraintPriority.NORMAL, "param", valeur)
```

**Pour ajouter un nouvel objectif :**
1. Créer la classe dans `objectives/`
2. Implémenter `ObjectiveFunction`
3. S'assurer qu'il combine bien avec les objectifs existants

### 6. Bonnes Pratiques OR-Tools

- Toujours nommer les variables avec des préfixes clairs (`assign_e1_s2`)
- Utiliser `LinearExprBuilder` pour construire les expressions complexes
- Préférer `addEquality()`, `addLessOrEqual()` aux formes avec constantes
- Valider les indices avant d'accéder aux tableaux
- Gérer les cas où `shifts.isEmpty()` ou `employees.isEmpty()`

### 7. Performance

- Les contraintes sont appliquées dans l'ordre de priorité
- Éviter les boucles imbriquées O(n³) quand possible
- Lazy loading des variables dans `SchedulingContext`
- Réutiliser les expressions communes

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

## Points d'Attention

- **Thread Safety** : Les contraintes doivent être thread-safe (stateless)
- **Validation** : Implémenter `validate()` pour vérifier les prérequis
- **Debugging** : Le nom retourné par `getName()` apparaît dans les logs
- **Compatibilité** : Tester l'interaction entre différentes contraintes

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
- Appeler `testConstraintProperties(constraint)` pour valider les propriétés de base

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

**Règles de Communication :**
- Claude NE DOIT PAS ajouter de fonctionnalités sans autorisation explicite
- Claude NE DOIT PAS créer de tests non demandés
- Demander confirmation avant d'ajouter du code supplémentaire
- Expliquer le "pourquoi" avant le "comment"

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
