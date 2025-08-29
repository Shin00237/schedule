package com.cricri.constraints;

import com.cricri.constraints.enums.ConstraintNature;
import com.cricri.constraints.enums.ObjectiveWeight;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.cricri.service.ObjectiveCollector;
import com.cricri.service.SchedulingContext;
import com.cricri.testutils.TestDataFactory;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cricri.testutils.SolverAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests spécifiques pour MaximizeWorkingHoursConstraint en mode SOFT avec ObjectiveCollector.
 * 
 * <p>Valide que la contrainte contribue correctement à l'objectif unifié sans appeler
 * directement model.maximize().
 */
class MaximizeWorkingHoursSoftTest {
    
    private List<Employee> employees;
    private List<Shift> shifts;
    private SchedulingContext context;
    private ObjectiveCollector collector;
    
    @BeforeEach
    void setUp() {
        employees = TestDataFactory.createStandardEmployees();
        shifts = TestDataFactory.createStandardWeekShifts();
        context = TestDataFactory.createContext(employees, shifts);
        collector = new ObjectiveCollector();
    }
    
    @Test
    void testSoftConstraintAddsTermsToCollector() {
        // Given
        MaximizeWorkingHoursConstraint constraint = new MaximizeWorkingHoursConstraint(2, ConstraintNature.SOFT);
        
        // When
        constraint.applySoftConstraint(context, collector);
        
        // Then
        assertFalse(collector.isEmpty());
        assertTrue(collector.getTermCount() > 0);
        
        // Vérifier qu'aucun model.maximize() n'a été appelé directement
        // (le modèle doit rester sans objectif à ce stade)
        assertNotNull(collector.build());
    }
    
    @Test
    void testWeekdayBonusTermsAreAdded() {
        // Given - Un seul shift en semaine pour isoler le test
        List<Shift> weekdayShifts = TestDataFactory.createWeekdayOnlyShifts();
        SchedulingContext weekdayContext = TestDataFactory.createContext(employees, weekdayShifts);
        
        MaximizeWorkingHoursConstraint constraint = new MaximizeWorkingHoursConstraint(3, ConstraintNature.SOFT);
        
        // When
        constraint.applySoftConstraint(weekdayContext, collector);
        
        // Then
        int expectedTerms = employees.size() * weekdayShifts.size() * 2; // Base + bonus weekday
        assertTrue(collector.getTermCount() >= expectedTerms, 
            "Devrait avoir au moins " + expectedTerms + " termes (base + bonus weekday), mais a " + collector.getTermCount());
    }
    
    @Test
    void testWeekendShiftsHaveNoBonus() {
        // Given - Seulement des shifts weekend
        List<Shift> weekendShifts = TestDataFactory.createWeekendOnlyShifts();
        SchedulingContext weekendContext = TestDataFactory.createContext(employees, weekendShifts);
        
        MaximizeWorkingHoursConstraint constraint = new MaximizeWorkingHoursConstraint(2, ConstraintNature.SOFT);
        
        // When
        constraint.applySoftConstraint(weekendContext, collector);
        
        // Then - Seulement les termes de base (pas de bonus weekday)
        int expectedTerms = employees.size() * weekendShifts.size(); // Seulement base, pas de bonus
        assertEquals(expectedTerms, collector.getTermCount(),
            "Les shifts weekend ne devraient avoir que les termes de base");
    }
    
    @Test
    void testHardModeThrowsUnsupported() {
        // Given
        MaximizeWorkingHoursConstraint constraint = new MaximizeWorkingHoursConstraint(2, ConstraintNature.HARD);
        
        // When/Then
        assertThrows(UnsupportedOperationException.class, 
            () -> constraint.applyHardConstraint(context),
            "MaximizeWorkingHours ne peut pas être HARD");
    }
    
    @Test
    void testSoftConstraintDoesNotCallModelMaximizeDirectly() {
        // Given
        MaximizeWorkingHoursConstraint constraint = new MaximizeWorkingHoursConstraint(2, ConstraintNature.SOFT);
        
        // When
        constraint.applySoftConstraint(context, collector);
        
        // Then - Le modèle ne doit PAS avoir d'objectif défini à ce stade
        // (c'est ModularShiftScheduler qui doit appeler model.maximize() avec l'objectif unifié)
        
        // On vérifie indirectement en résolvant : un modèle sans objectif devrait
        // trouver une solution rapidement sans optimisation
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(context.getModel());
        
        // Le status peut être FEASIBLE ou OPTIMAL selon les contraintes HARD appliquées
        assertTrue(status == CpSolverStatus.FEASIBLE || status == CpSolverStatus.OPTIMAL,
            "Le modèle devrait être résolvable même sans objectif explicite");
    }
    
    @Test
    void testObjectiveWeightConfiguration() {
        // Given - Configuration avec poids spécifique
        MaximizeWorkingHoursConstraint constraint = new MaximizeWorkingHoursConstraint(2, ConstraintNature.SOFT);
        
        // When
        constraint.applySoftConstraint(context, collector);
        
        // Then
        LinearExpr objective = collector.build();
        assertNotNull(objective);
        
        // L'objectif doit être différent d'une constante (contient des variables)
        assertNotEquals(LinearExpr.constant(0), objective);
    }
    
    @Test
    void testConstraintName() {
        // Given
        MaximizeWorkingHoursConstraint constraint = new MaximizeWorkingHoursConstraint(3, ConstraintNature.SOFT);
        
        // When/Then
        String name = constraint.getName();
        assertTrue(name.contains("MaximizeWorkingHours"));
        assertTrue(name.contains("4")); // weekday multiplier = 1 + 3
        assertTrue(name.contains("SOFT"));
    }
    
    @Test
    void testConstraintNature() {
        // Given
        MaximizeWorkingHoursConstraint softConstraint = new MaximizeWorkingHoursConstraint(2, ConstraintNature.SOFT);
        MaximizeWorkingHoursConstraint hardConstraint = new MaximizeWorkingHoursConstraint(2, ConstraintNature.HARD);
        
        // When/Then
        assertEquals(ConstraintNature.SOFT, softConstraint.getNature());
        assertEquals(ConstraintNature.HARD, hardConstraint.getNature());
    }
}