package com.cricri.constraints.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests de scénarios pour ObjectiveWeight - validation des poids et comportements. */
class ObjectiveWeightScenariosTest {

  @Test
  void testWeightHierarchy() {
    // Given/When/Then - Vérifier la hiérarchie des poids

    // CRITICAL > HIGH > MEDIUM > LOW pour maximisation
    assertTrue(
        ObjectiveWeight.MAXIMIZE_CRITICAL.getWeight() > ObjectiveWeight.MAXIMIZE_HIGH.getWeight());
    assertTrue(
        ObjectiveWeight.MAXIMIZE_HIGH.getWeight() > ObjectiveWeight.MAXIMIZE_MEDIUM.getWeight());
    assertTrue(
        ObjectiveWeight.MAXIMIZE_MEDIUM.getWeight() > ObjectiveWeight.MAXIMIZE_LOW.getWeight());

    // CRITICAL < HIGH < MEDIUM < LOW pour minimisation (valeurs négatives)
    assertTrue(
        ObjectiveWeight.MINIMIZE_CRITICAL.getWeight() < ObjectiveWeight.MINIMIZE_HIGH.getWeight());
    assertTrue(
        ObjectiveWeight.MINIMIZE_HIGH.getWeight() < ObjectiveWeight.MINIMIZE_MEDIUM.getWeight());
    assertTrue(
        ObjectiveWeight.MINIMIZE_MEDIUM.getWeight() < ObjectiveWeight.MINIMIZE_LOW.getWeight());

    // DISABLED est neutre
    assertEquals(0, ObjectiveWeight.DISABLED.getWeight());
  }

  @Test
  void testMaximizeWeightsArePositive() {
    assertTrue(ObjectiveWeight.MAXIMIZE_CRITICAL.getWeight() > 0);
    assertTrue(ObjectiveWeight.MAXIMIZE_HIGH.getWeight() > 0);
    assertTrue(ObjectiveWeight.MAXIMIZE_MEDIUM.getWeight() > 0);
    assertTrue(ObjectiveWeight.MAXIMIZE_LOW.getWeight() > 0);
  }

  @Test
  void testMinimizeWeightsAreNegative() {
    assertTrue(ObjectiveWeight.MINIMIZE_CRITICAL.getWeight() < 0);
    assertTrue(ObjectiveWeight.MINIMIZE_HIGH.getWeight() < 0);
    assertTrue(ObjectiveWeight.MINIMIZE_MEDIUM.getWeight() < 0);
    assertTrue(ObjectiveWeight.MINIMIZE_LOW.getWeight() < 0);
  }

  @Test
  void testCriticalDominatesOtherLevels() {
    // MAXIMIZE_CRITICAL doit dominer toutes les violations sauf MINIMIZE_CRITICAL
    long maxCritical = ObjectiveWeight.MAXIMIZE_CRITICAL.getWeight();
    long minHigh = Math.abs(ObjectiveWeight.MINIMIZE_HIGH.getWeight());
    long minMedium = Math.abs(ObjectiveWeight.MINIMIZE_MEDIUM.getWeight());
    long minLow = Math.abs(ObjectiveWeight.MINIMIZE_LOW.getWeight());

    assertTrue(maxCritical > minHigh, "MAXIMIZE_CRITICAL doit dominer MINIMIZE_HIGH");
    assertTrue(maxCritical > minMedium, "MAXIMIZE_CRITICAL doit dominer MINIMIZE_MEDIUM");
    assertTrue(maxCritical > minLow, "MAXIMIZE_CRITICAL doit dominer MINIMIZE_LOW");

    // Mais MINIMIZE_CRITICAL doit pouvoir contrebalancer MAXIMIZE_CRITICAL
    assertEquals(
        Math.abs(ObjectiveWeight.MINIMIZE_CRITICAL.getWeight()),
        maxCritical,
        "MINIMIZE_CRITICAL et MAXIMIZE_CRITICAL doivent avoir la même magnitude");
  }

  @ParameterizedTest
  @EnumSource(ObjectiveWeight.class)
  void testIsMaximizingMethod(ObjectiveWeight weight) {
    boolean expectedMaximizing = weight.name().startsWith("MAXIMIZE");
    assertEquals(expectedMaximizing, weight.isMaximizing());
  }

  @ParameterizedTest
  @EnumSource(ObjectiveWeight.class)
  void testIsMinimizingMethod(ObjectiveWeight weight) {
    boolean expectedMinimizing = weight.name().startsWith("MINIMIZE");
    assertEquals(expectedMinimizing, weight.isMinimizing());
  }

  @ParameterizedTest
  @EnumSource(ObjectiveWeight.class)
  void testIsDisabledMethod(ObjectiveWeight weight) {
    boolean expectedDisabled = weight == ObjectiveWeight.DISABLED;
    assertEquals(expectedDisabled, weight.isDisabled());
  }

  @Test
  void testMutualExclusivity() {
    for (ObjectiveWeight weight : ObjectiveWeight.values()) {
      // Un poids ne peut être qu'une seule chose à la fois
      int trueCount = 0;
      if (weight.isMaximizing()) trueCount++;
      if (weight.isMinimizing()) trueCount++;
      if (weight.isDisabled()) trueCount++;

      assertEquals(
          1,
          trueCount,
          weight.name() + " doit être exactement maximizing, minimizing, ou disabled");
    }
  }

  @Test
  void testScenarioMaximizeWorkingHoursVsViolations() {
    // Scénario : MaximizeWorkingHours (CRITICAL) vs plusieurs violations moyennes
    long maximizeWeight = ObjectiveWeight.MAXIMIZE_CRITICAL.getWeight();
    long violationWeight = ObjectiveWeight.MINIMIZE_MEDIUM.getWeight();

    // Simulation : 100 heures à maximiser vs 5 violations moyennes
    long maximizeContribution = 100 * maximizeWeight;
    long violationContribution = 5 * violationWeight; // Négatif

    long totalObjective = maximizeContribution + violationContribution;

    // L'objectif devrait rester positif (maximiser domine)
    assertTrue(
        totalObjective > 0,
        "MaximizeWorkingHours CRITICAL devrait dominer quelques violations MEDIUM");
  }

  @Test
  void testScenarioCriticalViolationStopsOptimization() {
    // Scénario : Une violation CRITICAL doit significativement réduire l'objectif
    long maximizeWeight = ObjectiveWeight.MAXIMIZE_CRITICAL.getWeight();
    long criticalViolationWeight = ObjectiveWeight.MINIMIZE_CRITICAL.getWeight();

    // Simulation : 1 heure vs 1 violation critique (scénario équilibré)
    long maximizeContribution = 1 * maximizeWeight;
    long violationContribution = 1 * criticalViolationWeight; // Très négatif

    long totalObjective = maximizeContribution + violationContribution;

    // L'objectif devrait être 0 (équilibré) car CRITICAL = -CRITICAL
    assertEquals(
        0,
        totalObjective,
        "Une violation CRITICAL devrait exactement contrebalancer un gain CRITICAL");

    // Test avec plus de violations que de gains
    long moreViolationsObjective = maximizeContribution + 2 * criticalViolationWeight;
    assertTrue(
        moreViolationsObjective < 0,
        "Plus de violations CRITICAL que de gains devrait donner un objectif négatif");
  }

  @Test
  void testScenarioMultipleLevelBalance() {
    // Scénario complexe : mélange de différents niveaux
    long objective = 0;

    // Contributions positives
    objective += 50 * ObjectiveWeight.MAXIMIZE_CRITICAL.getWeight(); // Heures critiques
    objective += 20 * ObjectiveWeight.MAXIMIZE_LOW.getWeight(); // Bonus mineurs

    // Violations
    objective += 2 * ObjectiveWeight.MINIMIZE_HIGH.getWeight(); // Violations importantes
    objective += 5 * ObjectiveWeight.MINIMIZE_LOW.getWeight(); // Violations mineures

    // L'objectif final devrait être positif mais réduit par les violations
    assertTrue(objective > 0, "Objectif devrait rester positif avec violations limitées");
    assertTrue(
        objective < 50 * ObjectiveWeight.MAXIMIZE_CRITICAL.getWeight(),
        "Les violations devraient réduire l'objectif");
  }
}
