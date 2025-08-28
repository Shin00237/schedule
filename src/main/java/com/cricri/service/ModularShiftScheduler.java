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
import com.cricri.constraints.WorkingDaysConstraint;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.objectives.ObjectiveFunction;
import com.cricri.objectives.WeekdayPreferenceObjective;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.IntVar;
import lombok.Getter;

@Getter
public class ModularShiftScheduler {
  private final SchedulingContext context;
  private final List<Constraint> constraints = new ArrayList<>();
  private final List<ObjectiveFunction> objectives = new ArrayList<>();

  public ModularShiftScheduler(List<Employee> employees, List<Shift> shifts) {
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

  // Contraintes prédéfinies avec API fluide
  public ModularShiftScheduler withMinimumCoverage() {
    return withConstraint(new MinimumCoverageConstraint());
  }

  public ModularShiftScheduler withMaxHoursPerWeek(int hoursInMinutes) {
    return withConstraint(new MaxHoursPerWeekConstraint(hoursInMinutes));
  }

  public ModularShiftScheduler withMinimumRest(int restHours) {
    return withConstraint(new MinimumRestConstraint(restHours));
  }

  public ModularShiftScheduler withMinimumRestDays(int restDaysPerWeek) {
    return withConstraint(new MinimumRestDaysConstraint(restDaysPerWeek));
  }

  public ModularShiftScheduler withAssignmentHours(int minHoursPerShift) {
    return withConstraint(new AssignmentHoursConstraint(minHoursPerShift));
  }

  public ModularShiftScheduler withWorkingDays() {
    return withConstraint(new WorkingDaysConstraint());
  }

  // API fluide pour les objectifs
  public ModularShiftScheduler withObjective(ObjectiveFunction objective) {
    objectives.add(objective);
    return this;
  }

  public ModularShiftScheduler withWeekdayPreference() {
    return withObjective(new WeekdayPreferenceObjective());
  }

  public ModularShiftScheduler withWeekdayPreference(int multiplier) {
    return withObjective(new WeekdayPreferenceObjective(multiplier));
  }

  // Configuration par défaut commune
  public ModularShiftScheduler withStandardConstraints(int maxHoursPerWeek, int minRestHours, int minHoursPerShift) {
    return this
        .withMinimumCoverage()
        .withAssignmentHours(minHoursPerShift)
        .withMaxHoursPerWeek(maxHoursPerWeek)
        .withMinimumRest(minRestHours)
        .withWorkingDays()
        .withMinimumRestDays(1)
        .withWeekdayPreference();
  }

  public void buildModel() {
    System.out.println("\n=== Construction du modèle modulaire ===");

    // Appliquer contraintes par priorité
    constraints.stream()
        .sorted(Comparator.comparingInt(Constraint::getPriority))
        .forEach(
            constraint -> {
              if (constraint.validate(context)) {
                constraint.apply(context);
                System.out.println("✓ Appliqué: " + constraint.getName());
              } else {
                System.out.println("✗ Ignoré: " + constraint.getName() + " (validation échouée)");
              }
            });

    // Appliquer objectifs par poids (plus élevé en premier)
    objectives.stream()
        .sorted(Comparator.comparingInt(ObjectiveFunction::getWeight).reversed())
        .forEach(
            objective -> {
              objective.apply(context);
              System.out.println("✓ Objectif: " + objective.getName());
            });

    System.out.println("Modèle construit avec " + constraints.size() + " contraintes et " + objectives.size() + " objectifs");
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
        .sorted(Comparator.comparingInt(Constraint::getPriority))
        .forEach(c -> System.out.println("  " + c.getName() + " (priorité: " + c.getPriority() + ")"));
  }

  public void printObjectives() {
    System.out.println("\n=== Objectifs configurés ===");
    objectives.stream()
        .sorted(Comparator.comparingInt(ObjectiveFunction::getWeight).reversed())
        .forEach(o -> System.out.println("  " + o.getName() + " (poids: " + o.getWeight() + ")"));
  }
}