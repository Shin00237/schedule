package com.cricri.constraints;

import com.cricri.constraints.config.ConstraintConfig;
import com.cricri.constraints.config.ParameterKey;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ObjectiveWeight;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;
import java.util.List;
import java.util.Map;

public class BlockShiftForEmployeeConstraint implements Constraint {
  private final ConstraintNature nature;
  private final ConstraintConfig config;

  public BlockShiftForEmployeeConstraint(ConstraintConfig config) {
    this.nature = config.nature();
    this.config = config;
  }

  @Override
  public void applyHardConstraint(SchedulingContext context) {
    context.ensureVariablesInitialized();

    Map<String, List<String>> blockedAssignments =
        config.getParameter(ParameterKey.BLOCKED_ASSIGNMENTS);

    // Si aucun blocage configuré, ne rien faire
    if (blockedAssignments.isEmpty()) {
      return;
    }

    List<Employee> employees = context.getEmployees();
    List<Shift> shifts = context.getShifts();

    // Pour chaque employé ayant des blocages
    for (Map.Entry<String, List<String>> entry : blockedAssignments.entrySet()) {
      String employeeId = entry.getKey();
      List<String> blockedShiftIds = entry.getValue();

      // Trouver l'index de l'employé
      int employeeIndex = findEmployeeIndex(employees, employeeId);
      if (employeeIndex == -1) {
        continue; // Employé non trouvé, ignorer
      }

      // Pour chaque shift bloqué pour cet employé
      for (String shiftId : blockedShiftIds) {
        int shiftIndex = findShiftIndex(shifts, shiftId);
        if (shiftIndex == -1) {
          continue; // Shift non trouvé, ignorer
        }

        // Contrainte HARD : assignment[employeeIndex][shiftIndex] == 0
        context.getModel().addEquality(context.getAssignments()[employeeIndex][shiftIndex], 0);
      }
    }
  }

  @Override
  public void applySoftConstraint(SchedulingContext context, ObjectiveCollector collector) {
    context.ensureVariablesInitialized();

    Map<String, List<String>> blockedAssignments =
        config.getParameter(ParameterKey.BLOCKED_ASSIGNMENTS);

    // Si aucun blocage configuré, ne rien faire
    if (blockedAssignments.isEmpty()) {
      return;
    }

    List<Employee> employees = context.getEmployees();
    List<Shift> shifts = context.getShifts();

    // Pour chaque employé ayant des préférences de blocage
    for (Map.Entry<String, List<String>> entry : blockedAssignments.entrySet()) {
      String employeeId = entry.getKey();
      List<String> preferredBlockedShiftIds = entry.getValue();

      // Trouver l'index de l'employé
      int employeeIndex = findEmployeeIndex(employees, employeeId);
      if (employeeIndex == -1) {
        continue; // Employé non trouvé, ignorer
      }

      // Pour chaque shift préférentiellement bloqué pour cet employé
      for (String shiftId : preferredBlockedShiftIds) {
        int shiftIndex = findShiftIndex(shifts, shiftId);
        if (shiftIndex == -1) {
          continue; // Shift non trouvé, ignorer
        }

        // SOFT : Pénaliser l'assignation de cet employé à ce shift
        // Plus l'employé est assigné, plus la pénalité est élevée
        ObjectiveWeight weight = config.getObjectiveWeight();
        collector.addTerm(context.getAssignments()[employeeIndex][shiftIndex], weight.getWeight());
      }
    }
  }

  @Override
  public String getName() {
    return "BlockedEmployee(" + nature + ")";
  }

  @Override
  public ConstraintNature getNature() {
    return nature;
  }

  /** Trouve l'index d'un employé par son ID. */
  private int findEmployeeIndex(List<Employee> employees, String employeeId) {
    for (int i = 0; i < employees.size(); i++) {
      if (employees.get(i).id().equals(employeeId)) {
        return i;
      }
    }
    return -1;
  }

  /** Trouve l'index d'un shift par son ID. */
  private int findShiftIndex(List<Shift> shifts, String shiftId) {
    for (int i = 0; i < shifts.size(); i++) {
      if (shifts.get(i).id().equals(shiftId)) {
        return i;
      }
    }
    return -1;
  }
}
