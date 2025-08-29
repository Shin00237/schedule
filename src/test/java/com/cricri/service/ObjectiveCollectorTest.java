package com.cricri.service;

import static org.junit.jupiter.api.Assertions.*;

import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour ObjectiveCollector.
 *
 * <p>Vérifie le fonctionnement du collecteur d'objectifs unifié, composant central de la refacto
 * SOFT.
 */
class ObjectiveCollectorTest {

  private ObjectiveCollector collector;
  private CpModel model;
  private IntVar var1, var2, var3;

  @BeforeEach
  void setUp() {
    collector = new ObjectiveCollector();
    model = new CpModel();
    var1 = model.newIntVar(0, 100, "var1");
    var2 = model.newIntVar(0, 100, "var2");
    var3 = model.newIntVar(0, 100, "var3");
  }

  @Test
  void testCollectorStartsEmpty() {
    assertTrue(collector.isEmpty());
    assertEquals(0, collector.getTermCount());
    assertEquals(LinearExpr.constant(0), collector.build());
  }

  @Test
  void testAddSingleTerm() {
    collector.addTerm(var1, 100);

    assertFalse(collector.isEmpty());
    assertEquals(1, collector.getTermCount());

    LinearExpr objective = collector.build();
    assertNotNull(objective);
    // OR-Tools ne permet pas d'inspecter facilement le contenu,
    // mais on peut vérifier qu'il n'est pas vide
    assertNotEquals(LinearExpr.constant(0), objective);
  }

  @Test
  void testAddMultipleTerms() {
    // Ajouter des termes avec différents poids (positifs et négatifs)
    collector.addTerm(var1, 1000); // MAXIMIZE_HIGH
    collector.addTerm(var2, -500); // Violation moyenne
    collector.addTerm(var3, 10); // MAXIMIZE_LOW

    assertEquals(3, collector.getTermCount());
    assertFalse(collector.isEmpty());

    LinearExpr objective = collector.build();
    assertNotNull(objective);
  }

  @Test
  void testZeroWeightIgnored() {
    collector.addTerm(var1, 100);
    collector.addTerm(var2, 0); // Doit être ignoré
    collector.addTerm(var3, -50);

    // Le terme avec poids 0 ne doit pas être compté
    assertEquals(2, collector.getTermCount());
  }

  @Test
  void testNullVariableThrows() {
    assertThrows(IllegalArgumentException.class, () -> collector.addTerm(null, 100));
  }

  @Test
  void testBuildIsIdempotent() {
    collector.addTerm(var1, 100);
    collector.addTerm(var2, -50);

    LinearExpr first = collector.build();
    LinearExpr second = collector.build();

    // build() doit pouvoir être appelé plusieurs fois
    assertNotNull(first);
    assertNotNull(second);
  }

  @Test
  void testThreadSafety() throws InterruptedException {
    // Test simple de concurrence
    Thread t1 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                collector.addTerm(var1, 1);
              }
            });

    Thread t2 =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                collector.addTerm(var2, 2);
              }
            });

    t1.start();
    t2.start();

    t1.join();
    t2.join();

    // Doit avoir exactement 200 termes
    assertEquals(200, collector.getTermCount());
  }
}
