# Monitoring et Optimisation des Contraintes OR-Tools

## Vue d'ensemble

Ce guide décrit comment implémenter un système de monitoring des contraintes pour optimiser les performances de votre moteur de règles basé sur OR-Tools.

## Architecture du système de monitoring

### Structure des classes

```
src/main/java/com/cricri/monitoring/
├── ConstraintMonitor.java          // Classe principale de monitoring
├── ConstraintProfiler.java         // Profilage des contraintes individuelles
├── ConstraintMetrics.java          // Métriques et seuils
├── ConstraintCouplingAnalyzer.java // Analyse des couplages variables-contraintes
└── PerformanceReporter.java        // Génération de rapports
```

## 1. Implémentation du monitoring de base

### ConstraintMonitor.java

```java
package com.cricri.monitoring;

import com.google.ortools.sat.CpSolverResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConstraintMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ConstraintMonitor.class);

    private final Map<String, ConstraintThresholds> thresholds;
    private final Map<String, ConstraintMetrics> metricsHistory;

    public ConstraintMonitor() {
        this.thresholds = initializeThresholds();
        this.metricsHistory = new ConcurrentHashMap<>();
    }

    private Map<String, ConstraintThresholds> initializeThresholds() {
        return Map.of(
            "MinimumCoverage", new ConstraintThresholds(0.1, 50, 5, 10),
            "AssignmentHours", new ConstraintThresholds(0.3, 100, 15, 50),
            "MaxHoursPerWeek", new ConstraintThresholds(0.5, 200, 20, 100),
            "MinimumRest", new ConstraintThresholds(1.0, 500, 50, 200),
            "MinimumRestDays", new ConstraintThresholds(0.8, 300, 30, 150),
            "MaximizeWorkingHours", new ConstraintThresholds(0.2, 80, 10, 30)
        );
    }

    public void monitorConstraint(String constraintName, CpSolverResponse response) {
        ConstraintMetrics metrics = extractMetrics(constraintName, response);
        metricsHistory.put(constraintName, metrics);

        checkThresholds(constraintName, metrics);
        reportPerformance(constraintName, metrics);
    }

    private ConstraintMetrics extractMetrics(String constraintName, CpSolverResponse response) {
        return ConstraintMetrics.builder()
            .constraintName(constraintName)
            .wallTime(response.getWallTime())
            .branches(response.getNumBranches())
            .conflicts(response.getNumConflicts())
            .propagations(response.getNumBooleanPropagations())
            .variablesAfterPresolve(response.getNumBooleans())
            .status(response.getStatus())
            .build();
    }

    private void checkThresholds(String constraintName, ConstraintMetrics metrics) {
        ConstraintThresholds threshold = thresholds.get(constraintName);
        if (threshold == null) return;

        if (metrics.getWallTime() > threshold.getMaxTime()) {
            logger.warn("[PERFORMANCE] Contrainte '{}' trop lente: {:.3f}s (seuil: {:.3f}s)",
                constraintName, metrics.getWallTime(), threshold.getMaxTime());
        }

        if (metrics.getBranches() > threshold.getMaxBranches()) {
            logger.warn("[COMPLEXITY] Contrainte '{}' génère trop de branches: {} (seuil: {})",
                constraintName, metrics.getBranches(), threshold.getMaxBranches());
        }

        if (metrics.getConflicts() > threshold.getMaxConflicts()) {
            logger.error("[CONFLICT] Contrainte '{}' génère beaucoup de conflits: {} (seuil: {})",
                constraintName, metrics.getConflicts(), threshold.getMaxConflicts());
        }

        if (metrics.getPropagations() > threshold.getMaxPropagations()) {
            logger.info("[PROPAGATION] Contrainte '{}' nécessite beaucoup de propagations: {} (seuil: {})",
                constraintName, metrics.getPropagations(), threshold.getMaxPropagations());
        }
    }

    private void reportPerformance(String constraintName, ConstraintMetrics metrics) {
        logger.info("[METRICS] {} - Temps: {:.3f}s, Branches: {}, Conflits: {}, Propagations: {}",
            constraintName, metrics.getWallTime(), metrics.getBranches(),
            metrics.getConflicts(), metrics.getPropagations());
    }

    public Map<String, ConstraintMetrics> getMetricsHistory() {
        return Map.copyOf(metricsHistory);
    }
}
```

### ConstraintMetrics.java

```java
package com.cricri.monitoring;

import com.google.ortools.sat.CpSolverStatus;

public class ConstraintMetrics {

    private final String constraintName;
    private final double wallTime;
    private final long branches;
    private final long conflicts;
    private final long propagations;
    private final int variablesAfterPresolve;
    private final CpSolverStatus status;
    private final ComplexityLevel complexity;

    public enum ComplexityLevel {
        TRIVIAL(0, "Résolu au presolve"),
        LOW(1, "Résolution simple"),
        MEDIUM(100, "Résolution modérée"),
        HIGH(1000, "Résolution complexe"),
        CRITICAL(10000, "Résolution très complexe");

        private final int branchThreshold;
        private final String description;

        ComplexityLevel(int branchThreshold, String description) {
            this.branchThreshold = branchThreshold;
            this.description = description;
        }

        public static ComplexityLevel fromBranches(long branches) {
            if (branches == 0) return TRIVIAL;
            if (branches <= LOW.branchThreshold) return LOW;
            if (branches <= MEDIUM.branchThreshold) return MEDIUM;
            if (branches <= HIGH.branchThreshold) return HIGH;
            return CRITICAL;
        }
    }

    private ConstraintMetrics(Builder builder) {
        this.constraintName = builder.constraintName;
        this.wallTime = builder.wallTime;
        this.branches = builder.branches;
        this.conflicts = builder.conflicts;
        this.propagations = builder.propagations;
        this.variablesAfterPresolve = builder.variablesAfterPresolve;
        this.status = builder.status;
        this.complexity = ComplexityLevel.fromBranches(branches);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String constraintName;
        private double wallTime;
        private long branches;
        private long conflicts;
        private long propagations;
        private int variablesAfterPresolve;
        private CpSolverStatus status;

        public Builder constraintName(String constraintName) {
            this.constraintName = constraintName;
            return this;
        }

        public Builder wallTime(double wallTime) {
            this.wallTime = wallTime;
            return this;
        }

        public Builder branches(long branches) {
            this.branches = branches;
            return this;
        }

        public Builder conflicts(long conflicts) {
            this.conflicts = conflicts;
            return this;
        }

        public Builder propagations(long propagations) {
            this.propagations = propagations;
            return this;
        }

        public Builder variablesAfterPresolve(int variables) {
            this.variablesAfterPresolve = variables;
            return this;
        }

        public Builder status(CpSolverStatus status) {
            this.status = status;
            return this;
        }

        public ConstraintMetrics build() {
            return new ConstraintMetrics(this);
        }
    }

    // Getters
    public String getConstraintName() { return constraintName; }
    public double getWallTime() { return wallTime; }
    public long getBranches() { return branches; }
    public long getConflicts() { return conflicts; }
    public long getPropagations() { return propagations; }
    public int getVariablesAfterPresolve() { return variablesAfterPresolve; }
    public CpSolverStatus getStatus() { return status; }
    public ComplexityLevel getComplexity() { return complexity; }
}
```

### ConstraintThresholds.java

```java
package com.cricri.monitoring;

public class ConstraintThresholds {

    private final double maxTime;
    private final long maxBranches;
    private final long maxConflicts;
    private final long maxPropagations;

    public ConstraintThresholds(double maxTime, long maxBranches, long maxConflicts, long maxPropagations) {
        this.maxTime = maxTime;
        this.maxBranches = maxBranches;
        this.maxConflicts = maxConflicts;
        this.maxPropagations = maxPropagations;
    }

    // Getters
    public double getMaxTime() { return maxTime; }
    public long getMaxBranches() { return maxBranches; }
    public long getMaxConflicts() { return maxConflicts; }
    public long getMaxPropagations() { return maxPropagations; }
}
```

## 2. Profilage des contraintes individuelles

### ConstraintProfiler.java

```java
package com.cricri.monitoring;

import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ShiftScheduler;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ConstraintProfiler {

    private static final Logger logger = LoggerFactory.getLogger(ConstraintProfiler.class);

    private final ConstraintMonitor monitor;
    private final ShiftScheduler scheduler;

    public ConstraintProfiler(ConstraintMonitor monitor, ShiftScheduler scheduler) {
        this.monitor = monitor;
        this.scheduler = scheduler;
    }

    public ProfilingReport profileAllConstraints(List<Employee> employees, List<Shift> shifts) {
        logger.info("=== DÉBUT DU PROFILAGE DES CONTRAINTES ===");

        List<String> constraintTypes = Arrays.asList(
            "MinimumCoverage", "AssignmentHours", "MaxHoursPerWeek",
            "MinimumRest", "MinimumRestDays", "MaximizeWorkingHours"
        );

        Map<String, ConstraintMetrics> individualMetrics = new HashMap<>();
        Map<String, ConstraintMetrics> cumulativeMetrics = new HashMap<>();

        // Test de chaque contrainte individuellement
        for (String constraint : constraintTypes) {
            logger.info("Profilage de la contrainte: {}", constraint);
            ConstraintMetrics metrics = profileSingleConstraint(constraint, employees, shifts);
            individualMetrics.put(constraint, metrics);
        }

        // Test des combinaisons progressives
        List<String> activeConstraints = new ArrayList<>();
        for (String constraint : constraintTypes) {
            activeConstraints.add(constraint);
            logger.info("Profilage combiné: {}", String.join(" + ", activeConstraints));

            ConstraintMetrics metrics = profileConstraintCombination(
                new ArrayList<>(activeConstraints), employees, shifts);
            cumulativeMetrics.put(String.join("+", activeConstraints), metrics);
        }

        logger.info("=== FIN DU PROFILAGE DES CONTRAINTES ===");

        return new ProfilingReport(individualMetrics, cumulativeMetrics);
    }

    private ConstraintMetrics profileSingleConstraint(String constraintName,
            List<Employee> employees, List<Shift> shifts) {

        CpModel model = scheduler.buildModelWithConstraints(
            employees, shifts, Arrays.asList(constraintName));

        CpSolver solver = new CpSolver();
        CpSolverResponse response = solver.solve(model);

        monitor.monitorConstraint(constraintName, response);
        return extractMetricsFromResponse(constraintName, response);
    }

    private ConstraintMetrics profileConstraintCombination(List<String> constraints,
            List<Employee> employees, List<Shift> shifts) {

        CpModel model = scheduler.buildModelWithConstraints(employees, shifts, constraints);

        CpSolver solver = new CpSolver();
        CpSolverResponse response = solver.solve(model);

        String combinationName = String.join("+", constraints);
        return extractMetricsFromResponse(combinationName, response);
    }

    private ConstraintMetrics extractMetricsFromResponse(String name, CpSolverResponse response) {
        return ConstraintMetrics.builder()
            .constraintName(name)
            .wallTime(response.getWallTime())
            .branches(response.getNumBranches())
            .conflicts(response.getNumConflicts())
            .propagations(response.getNumBooleanPropagations())
            .variablesAfterPresolve(response.getNumBooleans())
            .status(response.getStatus())
            .build();
    }

    public static class ProfilingReport {
        private final Map<String, ConstraintMetrics> individualMetrics;
        private final Map<String, ConstraintMetrics> cumulativeMetrics;

        public ProfilingReport(Map<String, ConstraintMetrics> individual,
                             Map<String, ConstraintMetrics> cumulative) {
            this.individualMetrics = Map.copyOf(individual);
            this.cumulativeMetrics = Map.copyOf(cumulative);
        }

        public void printReport() {
            logger.info("\n=== RAPPORT DE PROFILAGE ===");

            logger.info("\nMétriques individuelles:");
            individualMetrics.entrySet().stream()
                .sorted(Map.Entry.<String, ConstraintMetrics>comparingByValue(
                    Comparator.comparing(ConstraintMetrics::getWallTime)).reversed())
                .forEach(entry -> {
                    ConstraintMetrics m = entry.getValue();
                    logger.info("  {}: {:.3f}s, {} branches, {} conflits ({})",
                        entry.getKey(), m.getWallTime(), m.getBranches(),
                        m.getConflicts(), m.getComplexity());
                });

            logger.info("\nImpact cumulatif:");
            cumulativeMetrics.entrySet()
                .forEach(entry -> {
                    ConstraintMetrics m = entry.getValue();
                    logger.info("  {}: {:.3f}s, {} branches",
                        entry.getKey(), m.getWallTime(), m.getBranches());
                });
        }

        public Map<String, ConstraintMetrics> getIndividualMetrics() { return individualMetrics; }
        public Map<String, ConstraintMetrics> getCumulativeMetrics() { return cumulativeMetrics; }
    }
}
```

## 3. Analyse du couplage des variables

### ConstraintCouplingAnalyzer.java

```java
package com.cricri.monitoring;

import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.IntVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ConstraintCouplingAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ConstraintCouplingAnalyzer.class);

    private final Map<String, Set<String>> variableToConstraints;
    private final Map<String, Set<String>> constraintToVariables;

    public ConstraintCouplingAnalyzer() {
        this.variableToConstraints = new HashMap<>();
        this.constraintToVariables = new HashMap<>();
    }

    public void recordVariableConstraintRelation(String variableName, String constraintName) {
        variableToConstraints.computeIfAbsent(variableName, k -> new HashSet<>()).add(constraintName);
        constraintToVariables.computeIfAbsent(constraintName, k -> new HashSet<>()).add(variableName);
    }

    public CouplingReport analyzeCoupling() {
        logger.info("=== ANALYSE DU COUPLAGE VARIABLES-CONTRAINTES ===");

        // Variables sur-contraintes (dans beaucoup de contraintes)
        Map<String, Integer> overConstrainedVariables = variableToConstraints.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 3)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().size(),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));

        // Contraintes complexes (touchent beaucoup de variables)
        Map<String, Integer> complexConstraints = constraintToVariables.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().size(),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));

        // Matrice d'interaction entre contraintes
        Map<String, Map<String, Integer>> interactionMatrix = buildInteractionMatrix();

        return new CouplingReport(overConstrainedVariables, complexConstraints, interactionMatrix);
    }

    private Map<String, Map<String, Integer>> buildInteractionMatrix() {
        Map<String, Map<String, Integer>> matrix = new HashMap<>();

        List<String> constraints = new ArrayList<>(constraintToVariables.keySet());

        for (int i = 0; i < constraints.size(); i++) {
            String constraint1 = constraints.get(i);
            Map<String, Integer> row = new HashMap<>();

            for (int j = 0; j < constraints.size(); j++) {
                String constraint2 = constraints.get(j);

                if (i == j) {
                    row.put(constraint2, constraintToVariables.get(constraint1).size());
                } else {
                    // Nombre de variables partagées
                    Set<String> vars1 = constraintToVariables.get(constraint1);
                    Set<String> vars2 = constraintToVariables.get(constraint2);

                    Set<String> intersection = new HashSet<>(vars1);
                    intersection.retainAll(vars2);

                    row.put(constraint2, intersection.size());
                }
            }
            matrix.put(constraint1, row);
        }

        return matrix;
    }

    public static class CouplingReport {
        private final Map<String, Integer> overConstrainedVariables;
        private final Map<String, Integer> complexConstraints;
        private final Map<String, Map<String, Integer>> interactionMatrix;

        public CouplingReport(Map<String, Integer> overConstrained,
                            Map<String, Integer> complex,
                            Map<String, Map<String, Integer>> matrix) {
            this.overConstrainedVariables = overConstrained;
            this.complexConstraints = complex;
            this.interactionMatrix = matrix;
        }

        public void printReport() {
            logger.info("\n=== RAPPORT D'ANALYSE DU COUPLAGE ===");

            logger.info("\nVariables sur-contraintes (>3 contraintes):");
            overConstrainedVariables.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry ->
                    logger.info("  {} : {} contraintes", entry.getKey(), entry.getValue())
                );

            logger.info("\nContraintes complexes (par nombre de variables):");
            complexConstraints.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry ->
                    logger.info("  {} : {} variables", entry.getKey(), entry.getValue())
                );

            logger.info("\nMatrice d'interaction (variables partagées):");
            interactionMatrix.forEach((constraint1, interactions) -> {
                logger.info("  {}:", constraint1);
                interactions.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0 && !entry.getKey().equals(constraint1))
                    .forEach(entry ->
                        logger.info("    avec {} : {} variables communes",
                            entry.getKey(), entry.getValue())
                    );
            });
        }

        // Getters
        public Map<String, Integer> getOverConstrainedVariables() { return overConstrainedVariables; }
        public Map<String, Integer> getComplexConstraints() { return complexConstraints; }
        public Map<String, Map<String, Integer>> getInteractionMatrix() { return interactionMatrix; }
    }
}
```

## 4. Intégration dans votre ShiftScheduler

### Modification de ShiftScheduler.java

```java
// Ajoutez ces champs à votre classe ShiftScheduler
private final ConstraintMonitor constraintMonitor;
private final ConstraintProfiler constraintProfiler;
private final ConstraintCouplingAnalyzer couplingAnalyzer;

public ShiftScheduler() {
    this.constraintMonitor = new ConstraintMonitor();
    this.constraintProfiler = new ConstraintProfiler(constraintMonitor, this);
    this.couplingAnalyzer = new ConstraintCouplingAnalyzer();
}

// Modifiez votre méthode de résolution pour inclure le monitoring
public CpSolverResponse solve(List<Employee> employees, List<Shift> shifts) {
    logger.info("\n=== Construction du modèle modulaire ===");

    CpModel model = new CpModel();

    // Construction progressive avec monitoring
    if (isConstraintEnabled("MinimumCoverage")) {
        addMinimumCoverageConstraint(model, employees, shifts);
        recordConstraintVariableRelations("MinimumCoverage", getAssignmentVariables());
    }

    if (isConstraintEnabled("AssignmentHours")) {
        addAssignmentHoursConstraint(model, employees, shifts);
        recordConstraintVariableRelations("AssignmentHours", getHourVariables());
    }

    // ... autres contraintes

    logger.info("Modèle construit avec {} contraintes", getEnabledConstraintsCount());

    // Résolution avec monitoring
    CpSolver solver = new CpSolver();
    CpSolverResponse response = solver.solve(model);

    // Monitoring post-résolution
    monitorSolutionQuality(response);

    return response;
}

private void recordConstraintVariableRelations(String constraintName, List<String> variableNames) {
    for (String varName : variableNames) {
        couplingAnalyzer.recordVariableConstraintRelation(varName, constraintName);
    }
}

private void monitorSolutionQuality(CpSolverResponse response) {
    // Monitoring global
    constraintMonitor.monitorConstraint("GLOBAL_MODEL", response);

    // Log des métriques importantes
    logger.info("\n=== MÉTRIQUES DE PERFORMANCE ===");
    logger.info("Status: {}", response.getStatus());
    logger.info("Temps de résolution: {:.4f}s", response.getWallTime());
    logger.info("Branches explorées: {}", response.getNumBranches());
    logger.info("Conflits rencontrés: {}", response.getNumConflicts());
    logger.info("Variables après presolve: {}", response.getNumBooleans());

    // Alerte si performance dégradée
    if (response.getWallTime() > 5.0) {
        logger.warn("ATTENTION: Temps de résolution élevé ({}s)", response.getWallTime());
    }

    if (response.getNumBranches() > 1000) {
        logger.warn("ATTENTION: Nombre de branches élevé ({})", response.getNumBranches());
    }
}

// Méthode pour lancer un profilage complet
public void runFullProfilingAnalysis(List<Employee> employees, List<Shift> shifts) {
    logger.info("=== LANCEMENT DE L'ANALYSE COMPLÈTE ===");

    // 1. Profilage des contraintes
    ConstraintProfiler.ProfilingReport profilingReport =
        constraintProfiler.profileAllConstraints(employees, shifts);
    profilingReport.printReport();

    // 2. Analyse du couplage
    ConstraintCouplingAnalyzer.CouplingReport couplingReport =
        couplingAnalyzer.analyzeCoupling();
    couplingReport.printReport();

    // 3. Recommandations basées sur l'analyse
    generateOptimizationRecommendations(profilingReport, couplingReport);
}

private void generateOptimizationRecommendations(
        ConstraintProfiler.ProfilingReport profilingReport,
        ConstraintCouplingAnalyzer.CouplingReport couplingReport) {

    logger.info("\n=== RECOMMANDATIONS D'OPTIMISATION ===");

    // Identifier les contraintes problématiques
    profilingReport.getIndividualMetrics().entrySet().stream()
        .filter(entry -> entry.getValue().getComplexity() == ConstraintMetrics.ComplexityLevel.HIGH ||
                        entry.getValue().getComplexity() == ConstraintMetrics.ComplexityLevel.CRITICAL)
        .forEach(entry -> {
            logger.warn("RECOMMANDATION: Optimiser la contrainte '{}' ({})",
                entry.getKey(), entry.getValue().getComplexity());
        });

    // Identifier les variables sur-contraintes
    couplingReport.getOverConstrainedVariables().entrySet().stream()
        .filter(entry -> entry.getValue() > 4)
        .forEach(entry -> {
            logger.warn("RECOMMANDATION: Variable '{}' impliquée dans {} contraintes - " +
                "considérer une décomposition", entry.getKey(), entry.getValue());
        });

    // Suggestions d'amélioration
    logger.info("\nSUGGESTIONS:");
    logger.info("- Tester la décomposition des contraintes complexes");
    logger.info("- Ajouter des contraintes de symétrie si applicable");
    logger.info("- Ajuster les paramètres du solveur selon les métriques observées");
    logger.info("- Considérer l'ajout de contraintes redondantes pour améliorer la propagation");
}
```

## 5. Configuration et utilisation

### application.yml

```yaml
# Configuration du monitoring des contraintes
constraint-monitoring:
  enabled: true
  profiling:
    enabled: true
    log-individual-constraints: true
    log-cumulative-metrics: true
  thresholds:
    MinimumCoverage:
      max-time: 0.1
      max-branches: 50
      max-conflicts: 5
    AssignmentHours:
      max-time: 0.3
      max-branches: 100
      max-conflicts: 15
    MaxHoursPerWeek:
      max-time: 0.5
      max-branches: 200
      max-conflicts: 20
```

### Utilisation dans votre application

```java
@Component
public class SchedulingService {

    private final ShiftScheduler scheduler;

    public SchedulingService(ShiftScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void generateOptimalSchedule() {
        List<Employee> employees = getEmployees();
        List<Shift> shifts = getShifts();

        // Option 1: Résolution normale avec monitoring
        CpSolverResponse response = scheduler.solve(employees, shifts);

        // Option 2: Analyse complète de performance (à faire périodiquement)
        scheduler.runFullProfilingAnalysis(employees, shifts);
    }
}
```

## 6. Interprétation des résultats

### Seuils d'alerte recommandés

| Métrique | Bon | Acceptable | Problématique |
|----------|-----|------------|---------------|
| Temps résolution | < 0.5s | < 2s | > 5s |
| Branches | < 100 | < 1000 | > 10000 |
| Conflits | < 10 | < 50 | > 100 |
| Variables éliminées | 10-50% | 50-80% | > 90% |

### Actions correctives

1. **Temps élevé**: Décomposer les contraintes complexes
2. **Beaucoup de branches**: Ajouter des contraintes de guidage
3. **Beaucoup de conflits**: Vérifier la cohérence des contraintes
4. **Trop de variables éliminées**: Le problème est peut-être sur-contraint

## 7. Tests et validation

### Test unitaire pour le monitoring

```java
@Test
public void testConstraintMonitoring() {
    ConstraintMonitor monitor = new ConstraintMonitor();

    // Simuler une réponse OR-Tools
    CpSolverResponse mockResponse = createMockResponse(0.5, 100, 5, 50);

    // Tester le monitoring
    monitor.monitorConstraint("TestConstraint", mockResponse);

    // Vérifier que les métriques sont correctement collectées
    Map<String, ConstraintMetrics> history = monitor.getMetricsHistory();
    assertThat(history).containsKey("TestConstraint");
    assertThat(history.get("TestConstraint").getWallTime()).isEqualTo(0.5);
}
```

Ce système de monitoring vous permettra d'identifier précisément les contraintes problématiques et d'optimiser votre moteur de règles de manière ciblée.

## 8. Exemples de logs et leur interprétation

### Log de contrainte performante
```
[METRICS] MinimumCoverage - Temps: 0.025s, Branches: 0, Conflits: 0, Propagations: 0
```
**Interprétation**: Contrainte résolue au presolve, très efficace.

### Log de contrainte problématique
```
[COMPLEXITY] MaxHoursPerWeek génère trop de branches: 2500 (seuil: 200)
[CONFLICT] MaxHoursPerWeek génère beaucoup de conflits: 150 (seuil: 20)
[METRICS] MaxHoursPerWeek - Temps: 2.145s, Branches: 2500, Conflits: 150, Propagations: 8900
```
**Interprétation**: Cette contrainte nécessite une refactorisation urgente.

## 9. Intégration avec les logs existants

### Mapping avec vos logs OR-Tools actuels

Vos logs montrent déjà ces informations - voici comment les exploiter :

```java
// Parsing automatique de vos logs OR-Tools existants
public class OrToolsLogParser {

    public ConstraintMetrics parseFromSolverLog(String solverLog) {
        // Extraire les métriques de vos logs existants
        Pattern wallTimePattern = Pattern.compile("walltime: ([0-9.]+)");
        Pattern branchesPattern = Pattern.compile("branches: ([0-9]+)");
        Pattern conflictsPattern = Pattern.compile("conflicts: ([0-9]+)");

        Matcher wallTimeMatcher = wallTimePattern.matcher(solverLog);
        Matcher branchesMatcher = branchesPattern.matcher(solverLog);
        Matcher conflictsMatcher = conflictsPattern.matcher(solverLog);

        return ConstraintMetrics.builder()
            .wallTime(wallTimeMatcher.find() ? Double.parseDouble(wallTimeMatcher.group(1)) : 0.0)
            .branches(branchesMatcher.find() ? Long.parseLong(branchesMatcher.group(1)) : 0L)
            .conflicts(conflictsMatcher.find() ? Long.parseLong(conflictsMatcher.group(1)) : 0L)
            .build();
    }
}
```

## 10. Dashboard de monitoring en temps réel

### PerformanceReporter.java

```java
package com.cricri.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PerformanceReporter {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceReporter.class);
    private final Queue<ConstraintMetrics> recentMetrics = new ConcurrentLinkedQueue<>();
    private final Map<String, List<Double>> timeSeriesData = new HashMap<>();

    public void addMetrics(ConstraintMetrics metrics) {
        recentMetrics.offer(metrics);

        // Garder seulement les 100 dernières métriques
        while (recentMetrics.size() > 100) {
            recentMetrics.poll();
        }

        // Ajouter aux données de série temporelle
        timeSeriesData.computeIfAbsent(metrics.getConstraintName(), k -> new ArrayList<>())
                     .add(metrics.getWallTime());
    }

    public void generatePerformanceReport() {
        logger.info("\n" + "=".repeat(60));
        logger.info("RAPPORT DE PERFORMANCE - {}",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        logger.info("=".repeat(60));

        generateSummaryStatistics();
        generateTrendAnalysis();
        generateRecommendations();

        logger.info("=".repeat(60));
    }

    private void generateSummaryStatistics() {
        logger.info("\n📊 STATISTIQUES GÉNÉRALES:");

        Map<String, DoubleSummaryStatistics> statsByConstraint = new HashMap<>();

        recentMetrics.stream()
            .collect(Collectors.groupingBy(
                ConstraintMetrics::getConstraintName,
                Collectors.summarizingDouble(ConstraintMetrics::getWallTime)
            ))
            .forEach((constraint, stats) -> {
                logger.info("  {}: avg={:.3f}s, min={:.3f}s, max={:.3f}s, count={}",
                    constraint, stats.getAverage(), stats.getMin(), stats.getMax(), stats.getCount());
                statsByConstraint.put(constraint, stats);
            });
    }

    private void generateTrendAnalysis() {
        logger.info("\n📈 ANALYSE DES TENDANCES:");

        timeSeriesData.forEach((constraint, times) -> {
            if (times.size() >= 3) {
                double trend = calculateTrend(times);
                String trendSymbol = trend > 0.01 ? "📈" : trend < -0.01 ? "📉" : "➡️";
                logger.info("  {} {}: tendance {:.4f}s/exécution",
                    trendSymbol, constraint, trend);
            }
        });
    }

    private double calculateTrend(List<Double> values) {
        if (values.size() < 2) return 0.0;

        int n = values.size();
        double sumX = n * (n - 1) / 2.0;
        double sumY = values.stream().mapToDouble(Double::doubleValue).sum();
        double sumXY = 0.0;
        double sumXX = 0.0;

        for (int i = 0; i < n; i++) {
            sumXY += i * values.get(i);
            sumXX += i * i;
        }

        return (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    }

    private void generateRecommendations() {
        logger.info("\n💡 RECOMMANDATIONS AUTOMATIQUES:");

        List<ConstraintMetrics> recentList = new ArrayList<>(recentMetrics);

        // Contraintes lentes
        recentList.stream()
            .filter(m -> m.getWallTime() > 1.0)
            .map(ConstraintMetrics::getConstraintName)
            .distinct()
            .forEach(constraint ->
                logger.warn("  ⚠️  Optimiser '{}' - temps de résolution élevé", constraint));

        // Contraintes avec beaucoup de branches
        recentList.stream()
            .filter(m -> m.getBranches() > 1000)
            .map(ConstraintMetrics::getConstraintName)
            .distinct()
            .forEach(constraint ->
                logger.warn("  🌳 Ajouter des contraintes de guidage pour '{}'", constraint));

        // Contraintes avec beaucoup de conflits
        recentList.stream()
            .filter(m -> m.getConflicts() > 50)
            .map(ConstraintMetrics::getConstraintName)
            .distinct()
            .forEach(constraint ->
                logger.error("  💥 Vérifier la cohérence de '{}'", constraint));
    }

    public Map<String, Object> getMetricsForApi() {
        Map<String, Object> apiData = new HashMap<>();

        // Métriques actuelles
        apiData.put("recent_metrics", new ArrayList<>(recentMetrics));

        // Statistiques par contrainte
        Map<String, Map<String, Double>> constraintStats = recentMetrics.stream()
            .collect(Collectors.groupingBy(
                ConstraintMetrics::getConstraintName,
                Collectors.collectingAndThen(
                    Collectors.summarizingDouble(ConstraintMetrics::getWallTime),
                    stats -> Map.of(
                        "avg_time", stats.getAverage(),
                        "min_time", stats.getMin(),
                        "max_time", stats.getMax(),
                        "count", (double) stats.getCount()
                    )
                )
            ));

        apiData.put("constraint_statistics", constraintStats);
        apiData.put("time_series", timeSeriesData);

        return apiData;
    }
}
```

## 11. Automatisation et intégration continue

### Test de régression des performances

```java
@Component
public class PerformanceRegressionTest {

    private final ConstraintMonitor monitor;
    private final PerformanceBaseline baseline;

    public PerformanceRegressionTest(ConstraintMonitor monitor) {
        this.monitor = monitor;
        this.baseline = loadBaseline();
    }

    @EventListener
    @Async
    public void onScheduleSolved(ScheduleSolvedEvent event) {
        // Test automatique après chaque résolution
        checkForPerformanceRegression(event.getResponse(), event.getConstraints());
    }

    private void checkForPerformanceRegression(CpSolverResponse response, List<String> constraints) {
        for (String constraint : constraints) {
            ConstraintMetrics current = extractMetrics(constraint, response);
            ConstraintMetrics baseline = this.baseline.getBaselineFor(constraint);

            if (baseline != null) {
                double performanceRatio = current.getWallTime() / baseline.getWallTime();

                if (performanceRatio > 2.0) { // Plus de 2x plus lent
                    logger.error("RÉGRESSION DÉTECTÉE: {} est {}x plus lent que la baseline",
                        constraint, String.format("%.2f", performanceRatio));

                    // Notification ou alerte selon votre système
                    notifyPerformanceRegression(constraint, performanceRatio);
                }
            }
        }
    }

    private void notifyPerformanceRegression(String constraint, double ratio) {
        // Implémentez votre système de notification
        logger.error("🚨 ALERTE PERFORMANCE: Contrainte '{}' dégradée de {:.1f}x",
            constraint, ratio);
    }
}
```

### Configuration de monitoring par environnement

```yaml
# application-dev.yml
constraint-monitoring:
  enabled: true
  profiling:
    enabled: true
    full-analysis-on-startup: true
  alerting:
    enabled: false

# application-prod.yml
constraint-monitoring:
  enabled: true
  profiling:
    enabled: false
    full-analysis-on-startup: false
  alerting:
    enabled: true
    thresholds:
      critical-time: 5.0
      warning-time: 2.0
```

## 12. Métriques avancées et ML

### Prédiction de performance

```java
@Component
public class PerformancePredictor {

    private final List<PerformanceDataPoint> trainingData = new ArrayList<>();

    public static class PerformanceDataPoint {
        public final int employeeCount;
        public final int shiftCount;
        public final int constraintCount;
        public final double actualTime;

        public PerformanceDataPoint(int employees, int shifts, int constraints, double time) {
            this.employeeCount = employees;
            this.shiftCount = shifts;
            this.constraintCount = constraints;
            this.actualTime = time;
        }
    }

    public void recordDataPoint(int employees, int shifts, int constraints, double actualTime) {
        trainingData.add(new PerformanceDataPoint(employees, shifts, constraints, actualTime));

        // Garder seulement les 1000 derniers points
        while (trainingData.size() > 1000) {
            trainingData.remove(0);
        }
    }

    public double predictSolutionTime(int employees, int shifts, int constraints) {
        if (trainingData.size() < 10) {
            return -1.0; // Pas assez de données
        }

        // Modèle de régression linéaire simple
        double complexity = employees * shifts * Math.log(constraints + 1);

        // Trouver les 5 cas les plus similaires
        return trainingData.stream()
            .sorted((a, b) -> Double.compare(
                Math.abs((a.employeeCount * a.shiftCount * Math.log(a.constraintCount + 1)) - complexity),
                Math.abs((b.employeeCount * b.shiftCount * Math.log(b.constraintCount + 1)) - complexity)
            ))
            .limit(5)
            .mapToDouble(dp -> dp.actualTime)
            .average()
            .orElse(-1.0);
    }

    public void logPredictionAccuracy(int employees, int shifts, int constraints, double actualTime) {
        double predicted = predictSolutionTime(employees, shifts, constraints);

        if (predicted > 0) {
            double accuracy = Math.abs(predicted - actualTime) / actualTime * 100;
            logger.info("Prédiction: {:.3f}s, Réel: {:.3f}s, Précision: {:.1f}%",
                predicted, actualTime, 100 - accuracy);
        }

        recordDataPoint(employees, shifts, constraints, actualTime);
    }
}
```

## 13. Actions recommandées pour votre projet

### Phase 1: Implémentation de base (Semaine 1)
1. Ajoutez `ConstraintMonitor` et `ConstraintMetrics` à votre projet
2. Intégrez le monitoring dans votre `ShiftScheduler` existant
3. Configurez les seuils d'alerte appropriés pour vos contraintes

### Phase 2: Analyse approfondie (Semaine 2)
1. Implémentez `ConstraintProfiler` pour analyser chaque contrainte
2. Ajoutez `ConstraintCouplingAnalyzer` pour comprendre les interactions
3. Lancez une analyse complète de votre modèle actuel

### Phase 3: Optimisation (Semaine 3)
1. Utilisez les résultats pour optimiser les contraintes problématiques
2. Implémentez les recommandations du système
3. Mesurez l'impact des optimisations

### Phase 4: Automatisation (Semaine 4)
1. Ajoutez les tests de régression automatiques
2. Configurez le monitoring en production
3. Implémentez le système d'alertes

## 14. Checklist de mise en œuvre

- [ ] Créer le package `com.cricri.monitoring`
- [ ] Implémenter les classes de base (`ConstraintMonitor`, `ConstraintMetrics`)
- [ ] Modifier `ShiftScheduler` pour intégrer le monitoring
- [ ] Configurer les seuils dans `application.yml`
- [ ] Tester avec un jeu de données simple
- [ ] Lancer une analyse complète sur vos données réelles
- [ ] Optimiser les contraintes identifiées comme problématiques
- [ ] Mettre en place le monitoring en production
- [ ] Configurer les alertes et notifications
- [ ] Documenter les résultats et bonnes pratiques

Ce système vous donnera une visibilité complète sur les performances de votre moteur OR-Tools et vous permettra d'optimiser de manière ciblée et mesurable.
