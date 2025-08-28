package com.cricri.constraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.model.ShiftType;
import com.cricri.model.Week;
import com.cricri.service.SchedulingContext;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;

class MinimumCoverageConstraintTest {

  private List<Employee> employees;
  private List<Shift> shifts;
  private SchedulingContext context;
  private MinimumCoverageConstraint constraint;

  @BeforeEach
  void setUp() {
    Loader.loadNativeLibraries();

    // Employés de test
    employees = Arrays.asList(
        new Employee("E1", "Alice"),
        new Employee("E2", "Bob"),
        new Employee("E3", "Charlie"));

    // Shifts de test
    ShiftType normalShift = new ShiftType("NORMAL", 480, 960, 480, 45); // 8h-16h
    Week week = Week.create(0);
    shifts = Arrays.asList(
        new Shift("Lundi-MATIN", week.getDay(0), normalShift, 1, 2), // min=1, max=2
        new Shift("Lundi-SOIR", week.getDay(0), normalShift, 2, 3)); // min=2, max=3

    // Contexte de test
    context = new SchedulingContext(employees, shifts, new HashMap<>());
    constraint = new MinimumCoverageConstraint();
  }

  @Test
  void testApplyConstraint() {
    // Appliquer la contrainte
    constraint.apply(context);

    // Résoudre
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(context.getModel());

    // Vérifier qu'une solution existe
    assertTrue(status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
        "Une solution doit exister");

    // Vérifier les contraintes de couverture
    for (int s = 0; s < shifts.size(); s++) {
      Shift shift = shifts.get(s);
      int assignedCount = 0;
      
      for (int e = 0; e < employees.size(); e++) {
        if (solver.value(context.getAssignments()[e][s]) == 1) {
          assignedCount++;
        }
      }

      assertTrue(assignedCount >= shift.minEmployes(),
          "Le shift " + shift.id() + " doit avoir au moins " + shift.minEmployes() + " employé(s)");
      assertTrue(assignedCount <= shift.maxEmployes(),
          "Le shift " + shift.id() + " ne doit pas avoir plus de " + shift.maxEmployes() + " employé(s)");
      
      // Vérifier la variable employeesPerShift
      assertEquals(assignedCount, solver.value(context.getEmployeesPerShift()[s]),
          "La variable employeesPerShift doit correspondre au nombre d'assignations");
    }
  }

  @Test
  void testConstraintName() {
    assertEquals("MinimumCoverage", constraint.getName());
  }

  @Test
  void testConstraintPriority() {
    assertEquals(-10, constraint.getPriority());
  }

  @Test
  void testValidation() {
    assertTrue(constraint.validate(context), "La validation doit réussir avec un contexte valide");
  }

  @Test
  void testInfeasibleCase() {
    // Créer un cas impossible : 1 seul employé pour un shift qui en demande 2
    List<Employee> oneEmployee = Arrays.asList(new Employee("E1", "Alice"));
    List<Shift> impossibleShifts = Arrays.asList(
        new Shift("Impossible", Week.create(0).getDay(0), 
            new ShiftType("TEST", 480, 960, 480, 0), 2, 2)); // min=2, max=2

    SchedulingContext impossibleContext = new SchedulingContext(
        oneEmployee, impossibleShifts, new HashMap<>());
    
    constraint.apply(impossibleContext);
    
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(impossibleContext.getModel());
    
    assertEquals(CpSolverStatus.INFEASIBLE, status,
        "Le problème devrait être infaisable avec 1 employé pour un shift qui en demande 2");
  }
}