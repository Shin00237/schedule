package com.cricri.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.cricri.constraints.AssignmentHoursConstraint;
import com.cricri.constraints.Constraint;
import com.cricri.constraints.MaxHoursPerWeekConstraint;
import com.cricri.constraints.MinimumCoverageConstraint;
import com.cricri.constraints.MinimumRestConstraint;
import com.cricri.constraints.MinimumRestDaysConstraint;
import com.cricri.constraints.WeekdayPreferenceConstraint;
import com.cricri.constraints.WorkingDaysConstraint;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.IntVar;
import lombok.Getter;

@Getter
public class ModularShiftScheduler {
  private final SchedulingContext context;
  private final List<Constraint> constraints = new ArrayList<>();

  public ModularShiftScheduler(List<Employee> employees, List<Shift> shifts) {
    if (employees == null) {
      throw new IllegalArgumentException("La liste des employés ne peut pas être null");
    }
    if (shifts == null) {
      throw new IllegalArgumentException("La liste des shifts ne peut pas être null");
    }
    
    Map<String, Integer> shiftIndexMap = new HashMap<>();
    for (int i = 0; i < shifts.size(); i++) {
      shiftIndexMap.put(shifts.get(i).id(), i);
    }
    this.context = new SchedulingContext(employees, shifts, shiftIndexMap);
  }

  // API fluide pour ajouter contraintes
  public ModularShiftScheduler withConstraint(Constraint constraint) {
    constraints.add(constraint);
    return this;
  }


  public void buildModel() {
    System.out.println("\n=== Construction du modèle modulaire ===");

    // Appliquer contraintes par priorité
    constraints.stream()
        .sorted(Comparator.comparingInt(constraint -> constraint.getPriority().getValue()))
        .forEach(
            constraint -> {
              if (constraint.validate(context)) {
                constraint.apply(context);
                System.out.println("✓ Appliqué: " + constraint.getName());
              } else {
                System.out.println("✗ Ignoré: " + constraint.getName() + " (validation échouée)");
              }
            });

    System.out.println("Modèle construit avec " + constraints.size() + " contraintes");
  }

  // Méthodes de compatibilité avec l'ancien code
  public CpModel getModel() {
    return context.getModel();
  }

  public List<Employee> getEmployees() {
    return context.getEmployees();
  }

  public List<Shift> getShifts() {
    return context.getShifts();
  }

  // Accès aux variables pour l'affichage des résultats
  public BoolVar[][] getAssignments() {
    return context.getAssignments();
  }

  public IntVar[][] getActualHours() {
    return context.getActualHours();
  }

  public BoolVar[][][] getWorkingDays() {
    return context.getWorkingDays();
  }

  public IntVar[][] getWorkingDaysPerWeek() {
    return context.getWorkingDaysPerWeek();
  }

  // Méthodes utilitaires
  public void printConstraints() {
    System.out.println("\n=== Contraintes configurées ===");
    constraints.stream()
        .sorted(Comparator.comparingInt(constraint -> constraint.getPriority().getValue()))
        .forEach(c -> System.out.println("  " + c.getName() + " (priorité: " + c.getPriority() + ", nature: " + c.getNature() + ")"));
  }
}