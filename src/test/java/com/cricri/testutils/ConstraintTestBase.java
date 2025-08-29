package com.cricri.testutils;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cricri.constraints.Constraint;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.Week;
import com.cricri.service.SchedulingContext;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;

/**
 * Classe de base abstraite pour les tests de contraintes.
 *
 * <p>Fournit un setup commun et des méthodes utilitaires pour tester les contraintes individuelles
 * de manière isolée.
 */
public abstract class ConstraintTestBase {

  private static final Logger logger = LoggerFactory.getLogger(ConstraintTestBase.class);

  // Données de test communes
  protected List<Employee> employees;
  protected List<Shift> shifts;
  protected Week week1;
  protected Week week2;
  protected SchedulingContext context;
  protected CpSolver solver;

  @BeforeEach
  void setUp() {
    // Initialisation OR-Tools
    Loader.loadNativeLibraries();

    // Données de base réutilisables
    employees = TestDataFactory.createStandardEmployees();
    Week[] weeks = TestDataFactory.createStandardWeeks();
    week1 = weeks[0];
    week2 = weeks[1];
    shifts = TestDataFactory.createStandardWeekShifts(week1);

    // Contexte et solver
    context = TestDataFactory.createContext(employees, shifts);
    solver = new CpSolver();

    // Setup spécifique au test
    setupSpecific();
  }

  /**
   * Méthode appelée après le setup général pour permettre la customisation par les classes filles.
   */
  protected void setupSpecific() {
    // Par défaut, ne fait rien - à override si nécessaire
  }

  /**
   * Teste une contrainte avec des données standard en mode HARD.
   *
   * @param constraint La contrainte à tester
   */
  protected void testConstraintWithStandardData(Constraint constraint) {
    testHardConstraintWithStandardData(constraint);
  }

  /**
   * Teste une contrainte en mode HARD avec des données standard.
   *
   * @param constraint La contrainte à tester
   */
  protected void testHardConstraintWithStandardData(Constraint constraint) {
    // Appliquer la contrainte en mode HARD
    constraint.applyHardConstraint(context);

    // Vérifier que la solution existe
    SolverAssertions.assertSolutionExists(
        solver,
        context.getModel(),
        "La contrainte HARD " + constraint.getName() + " devrait permettre une solution");

    // Vérifications de base
    SolverAssertions.assertAllShiftsCovered(solver, context.getAssignments(), shifts);
  }

  /**
   * Teste qu'une contrainte rend un scénario infaisable en mode HARD.
   *
   * @param constraint La contrainte à tester
   * @param impossibleContext Contexte qui devrait être infaisable
   * @param reason Raison de l'infaisabilité attendue
   */
  protected void testConstraintMakesScenarioInfeasible(
      Constraint constraint, SchedulingContext impossibleContext, String reason) {

    constraint.applyHardConstraint(impossibleContext);

    SolverAssertions.assertNoSolutionExists(
        solver,
        impossibleContext.getModel(),
        "La contrainte HARD "
            + constraint.getName()
            + " devrait rendre ce scénario infaisable: "
            + reason);
  }


  /**
   * Crée un contexte avec des employés et shifts personnalisés.
   *
   * @param customEmployees Liste d'employés
   * @param customShifts Liste de shifts
   * @return Contexte configuré
   */
  protected SchedulingContext createCustomContext(
      List<Employee> customEmployees, List<Shift> customShifts) {
    return TestDataFactory.createContext(customEmployees, customShifts);
  }

  /**
   * Teste les propriétés de base d'une contrainte.
   *
   * @param constraint La contrainte à tester
   */
  protected void testConstraintProperties(Constraint constraint) {
    // Vérifier que le nom n'est pas null ou vide
    String name = constraint.getName();
    assert name != null : "Le nom de la contrainte ne doit pas être null";
    assert !name.trim().isEmpty() : "Le nom de la contrainte ne doit pas être vide";
    // Vérifier que la validation passe avec un contexte valide
    assert constraint.validate(context) : "La validation devrait passer avec un contexte valide";
  }

  /**
   * Crée un scénario impossible avec un seul employé et trop de shifts. Utile pour tester les
   * contraintes qui limitent les assignations.
   *
   * @return Contexte impossible
   */
  protected SchedulingContext createImpossibleScenario() {
    List<Employee> oneEmployee = TestDataFactory.createEmployees(1);
    List<Shift> manyShifts = TestDataFactory.createWeekShifts(week1, 1, 1); // 7 shifts
    return TestDataFactory.createContext(oneEmployee, manyShifts);
  }

  /**
   * Crée un scénario avec des shifts en conflit temporel.
   *
   * @return Contexte avec conflits
   */
  protected SchedulingContext createConflictingScenario() {
    List<Shift> conflictingShifts = TestDataFactory.createConflictingShifts(week1);
    return TestDataFactory.createContext(employees, conflictingShifts);
  }

  /**
   * Crée un scénario sur deux semaines pour tester les contraintes temporelles.
   *
   * @return Contexte sur 2 semaines
   */
  protected SchedulingContext createTwoWeekScenario() {
    List<Shift> twoWeekShifts = TestDataFactory.createTwoWeekShifts(week1, week2);
    return TestDataFactory.createContext(employees, twoWeekShifts);
  }

  /**
   * Affiche des informations de debug sur la solution trouvée.
   *
   * @param constraint La contrainte testée
   */
  protected void debugSolution(Constraint constraint) {
    logger.debug("\n=== Debug: {} ===", constraint.getName());
    logger.debug("Employés: {}, Shifts: {}", employees.size(), shifts.size());

    if (solver != null) {
      logger.debug("Temps de résolution: {}s", solver.wallTime());

      // Afficher les assignations si la solution existe
      for (int e = 0; e < employees.size(); e++) {
        for (int s = 0; s < shifts.size(); s++) {
          if (solver.value(context.getAssignments()[e][s]) == 1) {
            logger.debug("  {} -> {}", employees.get(e).nom(), shifts.get(s).id());
          }
        }
      }
    }
  }
}
