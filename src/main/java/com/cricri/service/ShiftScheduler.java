package com.cricri.service;

import com.cricri.constraints.Constraint;
import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.exceptions.EmptyParameterException;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.monitoring.ConstraintMonitor;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class ShiftScheduler {

  private static final Logger logger = LoggerFactory.getLogger(ShiftScheduler.class);
  private final SchedulingContext context;
  private final List<Constraint> constraints = new ArrayList<>();
  private final ConstraintMonitor constraintMonitor;
  private final boolean monitoringEnabled;

  public ShiftScheduler(List<Employee> employees, List<Shift> shifts) {
    this(employees, shifts, false); // Monitoring désactivé par défaut
  }

  public ShiftScheduler(List<Employee> employees, List<Shift> shifts, boolean enableMonitoring) {
    verifyParameters(employees, shifts);

    Map<String, Integer> shiftIndexMap = new HashMap<>();
    for (int i = 0; i < shifts.size(); i++) {
      shiftIndexMap.put(shifts.get(i).id(), i);
    }
    this.context = new SchedulingContext(employees, shifts, shiftIndexMap);
    this.constraintMonitor = new ConstraintMonitor();
    this.monitoringEnabled = enableMonitoring;
  }

  private void verifyParameters(List<Employee> employees, List<Shift> shifts) {
    if (employees == null) {
      throw new EmptyParameterException("employees");
    }
    if (shifts == null) {
      throw new EmptyParameterException("shifts");
    }
  }

  // API fluide pour ajouter contraintes
  public ShiftScheduler withConstraint(Constraint constraint) {
    constraints.add(constraint);
    return this;
  }

  public void buildModel() {
    logger.info("\n=== Construction du modèle modulaire ===");

    // UN SEUL collecteur partagé pour TOUTES les contraintes SOFT
    ObjectiveCollector sharedCollector = new ObjectiveCollector();

    // Appliquer toutes les contraintes triées par priorité
    constraints.stream().forEach(addConstraint(sharedCollector));

    // À la fin : un seul objectif unifié, pas d'état global !
    LinearExpr globalObjective = sharedCollector.build();
    if (!sharedCollector.isEmpty()) {
      context.getModel().maximize(globalObjective);
      logger.info(
          "🎯 Objectif global unifié appliqué avec {} termes", sharedCollector.getTermCount());
    }

    logger.info("Modèle construit avec {} contraintes", constraints.size());
  }

  private Consumer<? super Constraint> addConstraint(ObjectiveCollector sharedCollector) {
    return constraint -> {
      if (constraint.getNature() == ConstraintNature.HARD) {
        constraint.applyHardConstraint(context);
      } else {
        // Chaque contrainte SOFT ajoute ses termes au collecteur partagé
        constraint.applySoftConstraint(context, sharedCollector);
      }
    };
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

  // Résolution avec monitoring simplifié
  public CpSolver solve() {
    buildModel();

    logger.info("\n=== Résolution du modèle avec monitoring ===");

    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(context.getModel());

    if (monitoringEnabled) {
      // IMPORTANT: Enregistrer les métriques dans le monitor
      constraintMonitor.monitorConstraint("GLOBAL_MODEL", solver, status);
    }

    // Monitoring post-résolution simplifié
    logSolutionStatus(status);

    return solver;
  }

  private void logSolutionStatus(CpSolverStatus status) {
    logger.info("\n=== RÉSULTAT DE LA RÉSOLUTION ===");
    logger.info("Status: {}", status);

    // Diagnostic basique en cas d'échec
    if (status.toString().contains("INFEASIBLE")) {
      logger.error("DIAGNOSTIC: Le modèle est impossible à satisfaire.");
      logger.error("Vérifiez la compatibilité entre vos contraintes.");
    } else if (status.toString().contains("OPTIMAL")) {
      logger.info("✓ Solution optimale trouvée");
    } else if (status.toString().contains("FEASIBLE")) {
      logger.info("✓ Solution réalisable trouvée");
    }
  }

  // Méthodes utilitaires
  public void printConstraints() {
    logger.info("\n=== Contraintes configurées ===");
    constraints.forEach(c -> logger.info("  {} (nature: {})", c.getName(), c.getNature()));
  }

  public ConstraintMonitor getConstraintMonitor() {
    return constraintMonitor;
  }
}
