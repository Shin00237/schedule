package com.cricri.service;

import java.util.List;
import java.util.Map;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import lombok.Data;

@Data
public class SchedulingContext {
  // Données business
  private final List<Employee> employees;
  private final List<Shift> shifts;
  private final Map<String, Integer> shiftIndexMap;
  private final SchedulingConfiguration config;

  // Modèle OR-Tools
  private final CpModel model;

  // Variables principales
  private BoolVar[][] assignments;
  private IntVar[][] actualHours;
  private IntVar[] employeesPerShift;

  // Variables temporelles
  private BoolVar[][][] workingDays;
  private IntVar[][] workingDaysPerWeek;
  private IntVar[][] hoursPerEmployeePerWeek;

  // État d'initialisation
  private boolean variablesInitialized = false;

  public SchedulingContext(
      List<Employee> employees, List<Shift> shifts, Map<String, Integer> shiftIndexMap, SchedulingConfiguration config) {
    this.employees = employees;
    this.shifts = shifts;
    this.shiftIndexMap = shiftIndexMap;
    this.config = config;
    this.model = new CpModel();
  }
  
  // Constructeur avec configuration par défaut pour compatibilité
  public SchedulingContext(
      List<Employee> employees, List<Shift> shifts, Map<String, Integer> shiftIndexMap) {
    this(employees, shifts, shiftIndexMap, SchedulingConfiguration.STANDARD_WEEK);
  }

  // Méthodes utilitaires
  public int getEmployeeCount() {
    return employees.size();
  }

  public int getShiftCount() {
    return shifts.size();
  }

  public int getWeekCount() {
    if (shifts.isEmpty()) return 1;
    return shifts.stream().mapToInt(shift -> shift.day().getWeekNumber()).max().orElse(0) + 1;
  }

  // Lazy initialization des variables
  public void ensureVariablesInitialized() {
    if (!variablesInitialized) {
      initializeVariables();
      variablesInitialized = true;
    }
  }

  private void initializeVariables() {
    // Variables d'assignation
    assignments = new BoolVar[getEmployeeCount()][getShiftCount()];
    actualHours = new IntVar[getEmployeeCount()][getShiftCount()];

    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int s = 0; s < getShiftCount(); s++) {
        assignments[e][s] = model.newBoolVar("assign_e" + e + "_s" + s);
        int maxShiftDuration = shifts.get(s).type().dureeEffectiveMinutes();
        actualHours[e][s] = model.newIntVar(0, maxShiftDuration, "hours_e" + e + "_s" + s);
      }
    }

    // Variables de comptage employés par shift
    employeesPerShift = new IntVar[getShiftCount()];
    for (int s = 0; s < getShiftCount(); s++) {
      employeesPerShift[s] = model.newIntVar(0, getEmployeeCount(), "nbEmployees_s" + s);
    }

    // Variables temporelles
    int nbWeeks = getWeekCount();
    
    // Variables pour les heures par employé par semaine
    hoursPerEmployeePerWeek = new IntVar[getEmployeeCount()][nbWeeks];
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        hoursPerEmployeePerWeek[e][w] =
            model.newIntVar(0, Integer.MAX_VALUE, "hours_e" + e + "_w" + w);
      }
    }

    // Variables pour les jours travaillés
    int daysPerCycle = config.getDaysPerCycle();
    workingDays = new BoolVar[getEmployeeCount()][nbWeeks][daysPerCycle];
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        for (int d = 0; d < daysPerCycle; d++) {
          workingDays[e][w][d] = model.newBoolVar("workDay_e" + e + "_w" + w + "_d" + d);
        }
      }
    }

    // Variables pour compter les jours travaillés par semaine
    workingDaysPerWeek = new IntVar[getEmployeeCount()][nbWeeks];
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        workingDaysPerWeek[e][w] = model.newIntVar(0, config.getDaysPerCycle(), "workDaysPerWeek_e" + e + "_w" + w);
      }
    }
  }
}