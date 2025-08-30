# Règles de Développement pour Claude

## Architecture

**Packages :**
- `constraints/` : Contraintes réutilisables implémentant `Constraint`
- `service/` : `SchedulingContext`, `ShiftScheduler`, `ObjectiveCollector`
- `model/` : `Employee`, `Shift`, `ShiftType` (avec `LocalTime` + `Duration`)
- `testutils/` : `ConstraintTestBase`, `SolverAssertions`, `TestDataFactory`

## Contraintes

**Règles obligatoires :**
- Implémenter l'interface `Constraint` 
- Nom de classe finit par `Constraint`
- TOUJOURS appeler `context.ensureVariablesInitialized()`
- Contraintes stateless (pas d'état modifiable)
- JAMAIS appeler `model.maximize()` ou `model.minimize()` directement
- Contraintes SOFT utilisent `ObjectiveCollector.addTerm()`

**Exemple type :**
```java
public class MaContrainte implements Constraint {
    private final ConstraintConfig config;
    
    public MaContrainte(ConstraintConfig config) {
        this.config = config;
    }
    
    @Override
    public void applyHardConstraint(SchedulingContext context) {
        context.ensureVariablesInitialized();
        // Logique HARD avec addEquality(), addLessOrEqual()
    }
    
    @Override
    public void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector) {
        context.ensureVariablesInitialized();
        // Créer variables de violation
        // collector.addTerm(violationVar, config.getObjectiveWeight().getWeight());
    }
}
```

## Types Temporels

**Nouvelles règles (post-refactoring) :**
- `LocalTime` pour heures de début/fin
- `Duration` pour durées et pauses
- Conversion OR-Tools : `localTime.toSecondOfDay() / 60`
- Traversée minuit : `duration.isNegative() ? duration.plusDays(1) : duration`

## Tests

**Structure obligatoire :**
```java
class MaContrainteTest extends ConstraintTestBase {
    private MaContrainte constraint;
    
    @Override
    protected void setupSpecific() {
        constraint = new MaContrainte(
            ConstraintConfig.of(ConstraintType.MA_CONTRAINTE, ConstraintNature.HARD, "param", value)
        );
    }
    
    @Test
    void constraintPropertiesTest() {
        constraintPropertiesTest(constraint);
        assertEquals("Expected name", constraint.getName());
    }
    
    @Test
    void basicFunctionalityTest() {
        constraint.applyHardConstraint(context);
        CpSolver solver = SolverAssertions.solveAndAssertSolution(context);
        SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
    }
}
```

**Règles tests :**
- Étendre `ConstraintTestBase`
- Nommage avec suffixe `Test()` (pas `test()`)
- Utiliser `SolverAssertions` pour vérifications
- Utiliser `TestDataFactory` pour données
- NE PAS tester solutions optimales OR-Tools, tester seulement contraintes respectées
- Utiliser `createConflictingScenario()` pour tester infaisabilité

## Configuration

**API moderne :**
```java
ConstraintConfig.of(ConstraintType.TYPE, ConstraintNature.HARD)
ConstraintConfig.of(ConstraintType.TYPE, ConstraintNature.SOFT, "param", value)
```

## Règles Strictes

**Code :**
- JAMAIS de code mort ou commenté
- JAMAIS de `System.out.println()` (utiliser logs)  
- JAMAIS de constantes magiques
- JAMAIS de code prod ajouté uniquement pour tests
- Variables/méthodes en français
- Si code nécessaire pour tests : `testutils/` ou `@VisibleForTesting`

**TDD :**
- Tests AVANT implémentation
- Une contrainte = une responsabilité
- Ne jamais implémenter de fonctionnalités non demandées
- Demander confirmation avant ajout de code supplémentaire

**OR-Tools :**
- Variables nommées avec préfixes clairs (`assign_e1_s2`)
- Valider indices avant accès tableaux
- Gérer cas `shifts.isEmpty()` ou `employees.isEmpty()`
- UN SEUL `model.maximize()` dans `ShiftScheduler.buildModel()`