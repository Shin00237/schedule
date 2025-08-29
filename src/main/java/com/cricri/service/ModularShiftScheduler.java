package com.cricri.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cricri.constraints.Constraint;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import lombok.Getter;

@Getter
public class ModularShiftScheduler {

  private static final Logger logger = LoggerFactory.getLogger(ModularShiftScheduler.class);
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
    logger.info("\n=== Construction du modèle modulaire ===");

    // UN SEUL collecteur partagé pour TOUTES les contraintes SOFT
    ObjectiveCollector sharedCollector = new ObjectiveCollector();

    // Appliquer toutes les contraintes triées par priorité
    constraints.stream()
        .forEach(
            constraint -> {
              if (constraint.validate(context)) {
                if (constraint.getNature() == ConstraintNature.HARD) {
                  constraint.applyHardConstraint(context);
                } else {
                  // Chaque contrainte SOFT ajoute ses termes au collecteur partagé
                  constraint.applySoftConstraint(context, sharedCollector);
                }
                logger.info("✓ Appliqué: {}", constraint.getName());
              } else {
                logger.warn("✗ Ignoré: {} (validation échouée)", constraint.getName());
              }
            });

    // À la fin : un seul objectif unifié, pas d'état global !
    LinearExpr globalObjective = sharedCollector.build();
    if (!sharedCollector.isEmpty()) {
      context.getModel().maximize(globalObjective);
      logger.info("🎯 Objectif global unifié appliqué avec {} termes", sharedCollector.getTermCount());
    }

    logger.info("Modèle construit avec {} contraintes", constraints.size());
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
    logger.info("\n=== Contraintes configurées ===");
    constraints.forEach(c -> logger.info("  {} (nature: {})", c.getName(), c.getNature()));
  }
}
