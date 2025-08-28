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

### 1. Contraintes (package `constraints`)

**Règles obligatoires :**
- Toute contrainte DOIT implémenter l'interface `Constraint`
- Nom de classe DOIT finir par `Constraint` (ex: `MaxHoursPerWeekConstraint`)
- Méthode `getName()` DOIT retourner un nom descriptif avec paramètres (ex: "MaxHoursPerWeek(39.0h)")
- Contraintes DOIVENT être stateless (pas d'état interne modifiable)
- TOUJOURS appeler `context.ensureVariablesInitialized()` au début de `apply()`

**Priorités recommandées :**
- `-10` : Contraintes fondamentales (couverture minimum)
- `-5` : Contraintes de cohérence (assignation-heures)  
- `-3` : Contraintes de sécurité (repos minimum)
- `0` : Contraintes normales (heures max/semaine)
- `5` : Contraintes de confort (jours de repos)

**Exemple type :**
```java
public class MaContrainte implements Constraint {
    private final int parametre;
    
    public MaContrainte(int parametre) {
        this.parametre = parametre;
    }
    
    @Override
    public void apply(SchedulingContext context) {
        context.ensureVariablesInitialized();
        // Logique de contrainte...
    }
    
    @Override
    public String getName() {
        return "MaContrainte(" + parametre + ")";
    }
    
    @Override
    public int getPriority() {
        return 0; // Adapter selon le type
    }
}
```

### 2. Objectifs (package `objectives`)

**Règles obligatoires :**
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

**API Fluide Recommandée :**
```java
ModularShiftScheduler scheduler = new ModularShiftScheduler(employees, shifts)
    .withMinimumCoverage()
    .withMaxHoursPerWeek(39 * 60)
    .withMinimumRest(11)
    .withConstraint(new MaContraintCustom(params))
    .withWeekdayPreference();

scheduler.buildModel();
```

**Configurations Prédéfinies :**
- `withStandardConstraints()` : Contraintes de base pour planning classique
- Ajouter d'autres presets selon les besoins métier

### 5. Extensibilité

**Pour ajouter une nouvelle contrainte :**

1. Créer la classe dans `constraints/`
2. Implémenter l'interface `Constraint`
3. Ajouter les tests dans `test/`
4. Optionnel : Ajouter une méthode convenience dans `ModularShiftScheduler`
5. Documenter les paramètres et cas d'usage

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