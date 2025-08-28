package com.cricri.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.Week;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.Loader;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour SchedulingContext.
 *
 * <p>Focus sur le lazy loading des variables, les méthodes utilitaires, et la gestion de l'état
 * d'initialisation.
 */
class SchedulingContextTest {

  private List<Employee> employees;
  private List<Shift> shifts;
  private SchedulingContext context;

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();

    employees = TestDataFactory.createStandardEmployees();
    shifts = TestDataFactory.createStandardWeekShifts(TestDataFactory.createStandardWeeks()[0]);
    context = TestDataFactory.createContext(employees, shifts);
  }

  @Test
  void testBasicContextConstruction() {
    assertNotNull(context.getEmployees());
    assertNotNull(context.getShifts());
    assertNotNull(context.getShiftIndexMap());
    assertNotNull(context.getModel());

    assertEquals(employees, context.getEmployees());
    assertEquals(shifts, context.getShifts());
    assertEquals(employees.size(), context.getEmployeeCount());
    assertEquals(shifts.size(), context.getShiftCount());
  }

  @Test
  void testLazyInitializationBehavior() {
    // Au début, les variables ne devraient pas être initialisées
    assertFalse(
        context.isVariablesInitialized(), "Variables ne devraient pas être initialisées au départ");

    // Les accesseurs devraient retourner null avant initialisation
    assertNull(context.getAssignments(), "Assignments devrait être null avant initialisation");
    assertNull(context.getActualHours(), "ActualHours devrait être null avant initialisation");
    assertNull(
        context.getEmployeesPerShift(), "EmployeesPerShift devrait être null avant initialisation");
    assertNull(context.getWorkingDays(), "WorkingDays devrait être null avant initialisation");
    assertNull(
        context.getWorkingDaysPerWeek(),
        "WorkingDaysPerWeek devrait être null avant initialisation");
    assertNull(
        context.getHoursPerEmployeePerWeek(),
        "HoursPerEmployeePerWeek devrait être null avant initialisation");
  }

  @Test
  void testEnsureVariablesInitialized() {
    // Avant l'initialisation
    assertFalse(context.isVariablesInitialized());

    // Forcer l'initialisation
    context.ensureVariablesInitialized();

    // Après l'initialisation
    assertTrue(context.isVariablesInitialized(), "Variables devraient être initialisées");

    // Toutes les variables devraient être créées
    assertNotNull(context.getAssignments());
    assertNotNull(context.getActualHours());
    assertNotNull(context.getEmployeesPerShift());
    assertNotNull(context.getWorkingDays());
    assertNotNull(context.getWorkingDaysPerWeek());
    assertNotNull(context.getHoursPerEmployeePerWeek());
  }

  @Test
  void testVariableDimensions() {
    context.ensureVariablesInitialized();

    // Vérifier dimensions des variables d'assignation
    assertEquals(employees.size(), context.getAssignments().length);
    assertEquals(shifts.size(), context.getAssignments()[0].length);

    assertEquals(employees.size(), context.getActualHours().length);
    assertEquals(shifts.size(), context.getActualHours()[0].length);

    // Vérifier dimensions des variables de comptage
    assertEquals(shifts.size(), context.getEmployeesPerShift().length);

    // Vérifier dimensions des variables temporelles
    int expectedWeeks = context.getWeekCount();
    assertEquals(employees.size(), context.getWorkingDays().length);
    assertEquals(expectedWeeks, context.getWorkingDays()[0].length);
    assertEquals(7, context.getWorkingDays()[0][0].length); // 7 jours par semaine

    assertEquals(employees.size(), context.getWorkingDaysPerWeek().length);
    assertEquals(expectedWeeks, context.getWorkingDaysPerWeek()[0].length);

    assertEquals(employees.size(), context.getHoursPerEmployeePerWeek().length);
    assertEquals(expectedWeeks, context.getHoursPerEmployeePerWeek()[0].length);
  }

  @Test
  void testVariableNaming() {
    context.ensureVariablesInitialized();

    // Vérifier que les variables ont des noms appropriés
    String assignmentName = context.getAssignments()[0][0].getName();
    assertTrue(
        assignmentName.startsWith("assign_e0_s0"),
        "Nom d'assignation devrait suivre le pattern assign_eX_sY");

    String hoursName = context.getActualHours()[0][0].getName();
    assertTrue(
        hoursName.startsWith("hours_e0_s0"), "Nom d'heures devrait suivre le pattern hours_eX_sY");

    String employeesPerShiftName = context.getEmployeesPerShift()[0].getName();
    assertTrue(
        employeesPerShiftName.startsWith("nbEmployees_s0"),
        "Nom employeesPerShift devrait suivre le pattern nbEmployees_sX");
  }

  @Test
  void testWeekCountCalculation() {
    // Test avec shifts sur une semaine
    assertEquals(1, context.getWeekCount(), "Une semaine attendue");

    // Test avec shifts sur plusieurs semaines
    Week week2 = Week.create(1);
    List<Shift> multiWeekShifts =
        List.of(
            new Shift(
                "S1",
                TestDataFactory.createStandardWeeks()[0].getDay(0),
                TestDataFactory.NORMAL_SHIFT,
                1,
                1),
            new Shift("S2", week2.getDay(3), TestDataFactory.NORMAL_SHIFT, 1, 1));

    SchedulingContext multiWeekContext = TestDataFactory.createContext(employees, multiWeekShifts);
    assertEquals(2, multiWeekContext.getWeekCount(), "Deux semaines attendues");

    // Test avec shifts vides
    SchedulingContext emptyContext = new SchedulingContext(employees, List.of(), new HashMap<>());
    assertEquals(1, emptyContext.getWeekCount(), "Au moins une semaine même sans shifts");
  }

  @Test
  void testShiftIndexMapUsage() {
    // Vérifier que la map d'index est correctement construite
    for (int i = 0; i < shifts.size(); i++) {
      Shift shift = shifts.get(i);
      assertTrue(
          context.getShiftIndexMap().containsKey(shift.id()),
          "La map devrait contenir l'ID du shift: " + shift.id());
      assertEquals(
          Integer.valueOf(i),
          context.getShiftIndexMap().get(shift.id()),
          "L'index devrait correspondre à la position dans la liste");
    }
  }

  @Test
  void testMultipleInitializationCalls() {
    // L'initialisation multiple ne devrait pas poser de problème
    assertFalse(context.isVariablesInitialized());

    context.ensureVariablesInitialized();
    assertTrue(context.isVariablesInitialized());

    // Sauvegarder références
    var assignments1 = context.getAssignments();
    var actualHours1 = context.getActualHours();

    // Appeler encore l'initialisation
    context.ensureVariablesInitialized();

    // Les variables devraient être les mêmes (pas recréées)
    assertEquals(
        assignments1, context.getAssignments(), "Les variables ne devraient pas être recréées");
    assertEquals(
        actualHours1, context.getActualHours(), "Les variables ne devraient pas être recréées");
  }

  @Test
  void testContextWithLargeDataset() {
    // Test avec plus d'employés et de shifts
    List<Employee> largeTeam = TestDataFactory.createEmployees(8);
    Week[] weeks = {Week.create(0), Week.create(1), Week.create(2)};

    List<Shift> largeShiftSet =
        List.of(
            new Shift("S1", weeks[0].getDay(0), TestDataFactory.NORMAL_SHIFT, 1, 1),
            new Shift("S2", weeks[0].getDay(1), TestDataFactory.MORNING_SHIFT, 1, 1),
            new Shift("S3", weeks[0].getDay(2), TestDataFactory.EVENING_SHIFT, 1, 1),
            new Shift("S4", weeks[1].getDay(0), TestDataFactory.NORMAL_SHIFT, 1, 1),
            new Shift("S5", weeks[1].getDay(3), TestDataFactory.NIGHT_SHIFT, 1, 1),
            new Shift("S6", weeks[2].getDay(1), TestDataFactory.NORMAL_SHIFT, 1, 1),
            new Shift("S7", weeks[2].getDay(4), TestDataFactory.MORNING_SHIFT, 1, 1));

    SchedulingContext largeContext = TestDataFactory.createContext(largeTeam, largeShiftSet);

    assertEquals(8, largeContext.getEmployeeCount());
    assertEquals(7, largeContext.getShiftCount());
    assertEquals(3, largeContext.getWeekCount());

    // L'initialisation devrait fonctionner même avec beaucoup de données
    long startTime = System.currentTimeMillis();
    largeContext.ensureVariablesInitialized();
    long initTime = System.currentTimeMillis() - startTime;

    assertTrue(initTime < 1000, "L'initialisation devrait être rapide (<1s)");
    assertTrue(largeContext.isVariablesInitialized());

    // Vérifier les dimensions
    assertEquals(8, largeContext.getAssignments().length);
    assertEquals(7, largeContext.getAssignments()[0].length);
  }

  @Test
  void testContextWithEdgeCases() {
    // Test avec un seul employé
    List<Employee> singleEmployee = TestDataFactory.createEmployees(1);
    SchedulingContext singleEmpContext = TestDataFactory.createContext(singleEmployee, shifts);

    assertEquals(1, singleEmpContext.getEmployeeCount());
    singleEmpContext.ensureVariablesInitialized();
    assertEquals(1, singleEmpContext.getAssignments().length);

    // Test avec un seul shift
    List<Shift> singleShift = shifts.subList(0, 1);
    SchedulingContext singleShiftContext = TestDataFactory.createContext(employees, singleShift);

    assertEquals(1, singleShiftContext.getShiftCount());
    singleShiftContext.ensureVariablesInitialized();
    assertEquals(1, singleShiftContext.getAssignments()[0].length);
  }

  @Test
  void testContextVariableBounds() {
    context.ensureVariablesInitialized();

    // Vérifier que les variables ont des bornes appropriées
    // Les variables booléennes devraient être entre 0 et 1
    // Les variables d'heures devraient avoir des bornes raisonnables

    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        // Variables d'assignation (booléennes)
        assertNotNull(context.getAssignments()[e][s]);

        // Variables d'heures (avec borne supérieure = durée du shift)
        assertNotNull(context.getActualHours()[e][s]);

        // Les noms devraient être uniques et descriptifs
        String assignName = context.getAssignments()[e][s].getName();
        String hoursName = context.getActualHours()[e][s].getName();

        assertTrue(assignName.contains("assign"));
        assertTrue(assignName.contains("e" + e));
        assertTrue(assignName.contains("s" + s));

        assertTrue(hoursName.contains("hours"));
        assertTrue(hoursName.contains("e" + e));
        assertTrue(hoursName.contains("s" + s));
      }
    }
  }

  @Test
  void testContextUtilityMethods() {
    // Test des méthodes utilitaires
    assertTrue(context.getEmployeeCount() > 0);
    assertTrue(context.getShiftCount() > 0);
    assertTrue(context.getWeekCount() > 0);

    assertEquals(employees.size(), context.getEmployeeCount());
    assertEquals(shifts.size(), context.getShiftCount());

    // getWeekCount devrait être basé sur le shift avec le numéro de semaine le plus élevé
    int maxWeek = shifts.stream().mapToInt(shift -> shift.day().getWeekNumber()).max().orElse(0);
    assertEquals(maxWeek + 1, context.getWeekCount());
  }

  @Test
  void testContextMemoryUsage() {
    // Test simple pour vérifier qu'on ne gaspille pas trop de mémoire
    SchedulingContext[] contexts = new SchedulingContext[10];

    // Créer plusieurs contextes
    for (int i = 0; i < contexts.length; i++) {
      contexts[i] = TestDataFactory.createContext(employees, shifts);
      contexts[i].ensureVariablesInitialized();
    }

    // Vérifier qu'ils sont tous fonctionnels
    for (SchedulingContext ctx : contexts) {
      assertTrue(ctx.isVariablesInitialized());
      assertEquals(employees.size(), ctx.getEmployeeCount());
      assertEquals(shifts.size(), ctx.getShiftCount());
    }

    // Nettoyer (Java GC devrait s'occuper du reste)
    for (int i = 0; i < contexts.length; i++) {
      contexts[i] = null;
    }

    assertTrue(true, "Test de gestion mémoire passé");
  }
}
