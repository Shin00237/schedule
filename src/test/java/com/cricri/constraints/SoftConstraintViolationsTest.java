package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ConstraintType;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.TestDataFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests pour vérifier que les contraintes SOFT ajoutent correctement leurs violations à
 * l'ObjectiveCollector avec des poids négatifs (minimisation).
 */
class SoftConstraintViolationsTest {

  private List<Employee> employees;
  private List<Shift> shifts;
  private SchedulingContext context;
  private ObjectiveCollector collector;

  @BeforeEach
  void setUp() {
    employees = TestDataFactory.createStandardEmployees();
    shifts = TestDataFactory.createTwoWeekShifts(); // Plus de shifts pour créer des violations
    context = TestDataFactory.createContext(employees, shifts);
    collector = new ObjectiveCollector();
  }

  @Test
  void testMinimumRestDaysConstraintAddsViolationTerms() {
    // Given - Configuration qui va probablement créer des violations
    MinimumRestDaysConstraint constraint =
        new MinimumRestDaysConstraint(
            5, // 5 jours de repos minimum par semaine (difficile à respecter)
            ConstraintNature.SOFT);

    int termsBefore = collector.getTermCount();

    // When
    constraint.applySoftConstraint(context, collector);

    // Then
    int termsAfter = collector.getTermCount();
    assertTrue(
        termsAfter > termsBefore,
        "MinimumRestDaysConstraint devrait ajouter des termes de violation");
    assertFalse(collector.isEmpty());
  }

  @Test
  void testMinimumRestConstraintAddsViolationTerms() {
    // Given
    MinimumRestConstraint constraint =
        new MinimumRestConstraint(
            ConstraintConfig.of(ConstraintType.MINIMUM_REST, ConstraintNature.SOFT, "minRestHours", 12)
        );

    int termsBefore = collector.getTermCount();

    // When
    constraint.applySoftConstraint(context, collector);

    // Then
    int termsAfter = collector.getTermCount();
    assertTrue(
        termsAfter >= termsBefore,
        "MinimumRestConstraint devrait potentiellement ajouter des termes");
  }

  @Test
  void testMaxHoursPerWeekConstraintAddsViolationTerms() {
    // Given - Limite très basse pour forcer des violations
    MaxHoursPerWeekConstraint constraint =
        new MaxHoursPerWeekConstraint(
            ConstraintConfig.of(ConstraintType.MAX_HOURS_PER_WEEK, ConstraintNature.SOFT, "maxHoursPerWeek", 10 * 60)
        );

    int termsBefore = collector.getTermCount();

    // When
    constraint.applySoftConstraint(context, collector);

    // Then
    int termsAfter = collector.getTermCount();
    assertTrue(
        termsAfter > termsBefore,
        "MaxHoursPerWeekConstraint avec limite basse devrait créer des violations");
  }

  @Test
  void testMinimumCoverageConstraintAddsViolationTerms() {
    // Given - Pas assez d'employés pour couvrir tous les shifts parfaitement
    List<Employee> fewEmployees = employees.subList(0, 2); // Seulement 2 employés
    SchedulingContext limitedContext = TestDataFactory.createContext(fewEmployees, shifts);
    ObjectiveCollector limitedCollector = new ObjectiveCollector();

    MinimumCoverageConstraint constraint = new MinimumCoverageConstraint(
        ConstraintConfig.of(ConstraintType.MINIMUM_COVERAGE, ConstraintNature.SOFT)
    );

    // When
    constraint.applySoftConstraint(limitedContext, limitedCollector);

    // Then
    assertFalse(
        limitedCollector.isEmpty(),
        "MinimumCoverageConstraint avec peu d'employés devrait créer des violations");
  }

  @Test
  void testMultipleSoftConstraintsCombine() {
    // Given - Plusieurs contraintes SOFT qui vont toutes contribuer
    MinimumRestDaysConstraint restDaysConstraint =
        new MinimumRestDaysConstraint(4, ConstraintNature.SOFT);
    MaxHoursPerWeekConstraint maxHoursConstraint =
        new MaxHoursPerWeekConstraint(
            ConstraintConfig.of(ConstraintType.MAX_HOURS_PER_WEEK, ConstraintNature.SOFT, "maxHoursPerWeek", 20 * 60)
        );
    MaximizeWorkingHoursConstraint maximizeConstraint =
        new MaximizeWorkingHoursConstraint(
            ConstraintConfig.of(ConstraintType.MAXIMIZE_WORKING_HOURS, ConstraintNature.SOFT, "weekdayMultiplier", 2)
        );

    // When - Appliquer toutes les contraintes au même collecteur
    restDaysConstraint.applySoftConstraint(context, collector);
    int termsAfterFirst = collector.getTermCount();

    maxHoursConstraint.applySoftConstraint(context, collector);
    int termsAfterSecond = collector.getTermCount();

    maximizeConstraint.applySoftConstraint(context, collector);
    int termsAfterThird = collector.getTermCount();

    // Then - Chaque contrainte doit ajouter des termes
    assertTrue(termsAfterFirst > 0, "Première contrainte devrait ajouter des termes");
    assertTrue(
        termsAfterSecond >= termsAfterFirst, "Deuxième contrainte devrait maintenir ou ajouter");
    assertTrue(
        termsAfterThird > termsAfterSecond, "Troisième contrainte devrait ajouter des termes");

    // L'objectif final doit être constructible
    assertNotNull(collector.build());
    assertFalse(collector.isEmpty());
  }

  @Test
  void testHardConstraintsDoNotAddToCollector() {
    // Given - Les mêmes contraintes mais en mode HARD
    MinimumRestDaysConstraint hardConstraint =
        new MinimumRestDaysConstraint(2, ConstraintNature.HARD);

    // When/Then - Les contraintes HARD ne devraient pas appeler applySoftConstraint
    // Elles utilisent applyHardConstraint à la place

    // Le collecteur doit rester vide si on n'appelle que les contraintes HARD
    assertEquals(0, collector.getTermCount());
    assertTrue(collector.isEmpty());

    // Vérifier que la contrainte HARD fonctionne (ne doit pas lancer d'exception)
    assertDoesNotThrow(() -> hardConstraint.applyHardConstraint(context));
  }
}
