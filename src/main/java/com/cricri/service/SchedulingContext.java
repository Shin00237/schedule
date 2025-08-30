package com.cricri.service;

import java.util.List;
import java.util.Map;
import com.cricri.constants.Constants;
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
      List<Employee> employees,
      List<Shift> shifts,
      Map<String, Integer> shiftIndexMap,
      SchedulingConfiguration config) {
    this.employees = employees;
    this.shifts = shifts;
    this.shiftIndexMap = shiftIndexMap;
    this.config = config;
    this.model = new CpModel();
  }

  public SchedulingContext(
      List<Employee> employees, List<Shift> shifts, Map<String, Integer> shiftIndexMap) {
    this(employees, shifts, shiftIndexMap, SchedulingConfiguration.STANDARD_WEEK);
  }

  public int getWeekCount() {
    if (shifts.isEmpty()) return 1;
    return shifts.stream().mapToInt(shift -> shift.day().getWeekNumber()).max().orElse(0) + 1;
  }

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

    employeeAssignationAndActualHoursWorksInit();
    employePerShiftInit();

    // Variables temporelles
    int nbWeeks = getWeekCount();
    hoursPerEmployePerWeekInit(nbWeeks);
    daysWorksInit(nbWeeks);
    countingDaysWorkPerWeekInit(nbWeeks);

    // Établir les liens logiques entre variables
    workingDaysLinksInit();
  }

  private void employeeAssignationAndActualHoursWorksInit() {
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int s = 0; s < getShiftCount(); s++) {
        assignments[e][s] = model.newBoolVar("assign_e" + e + "_s" + s);
        int maxShiftDuration = shifts.get(s).type().dureeEffectiveMinutes();
        actualHours[e][s] = model.newIntVar(0, maxShiftDuration, "hours_e" + e + "_s" + s);
      }
    }
  }

  private void employePerShiftInit() {
    employeesPerShift = new IntVar[getShiftCount()];
    for (int s = 0; s < getShiftCount(); s++) {
      employeesPerShift[s] = model.newIntVar(0, getEmployeeCount(), "nbEmployees_s" + s);
    }
  }

  private void hoursPerEmployePerWeekInit(int nbWeeks) {
    hoursPerEmployeePerWeek = new IntVar[getEmployeeCount()][nbWeeks];
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        hoursPerEmployeePerWeek[e][w] =
            model.newIntVar(0, Integer.MAX_VALUE, "hours_e" + e + "_w" + w);
      }
    }
  }

  private void daysWorksInit(int nbWeeks) {
    workingDays = new BoolVar[getEmployeeCount()][nbWeeks][Constants.DAYS_IN_A_WEEK];
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        for (int d = 0; d < Constants.DAYS_IN_A_WEEK; d++) {
          workingDays[e][w][d] = model.newBoolVar("workDay_e" + e + "_w" + w + "_d" + d);
        }
      }
    }
  }

  private void countingDaysWorkPerWeekInit(int nbWeeks) {
    workingDaysPerWeek = new IntVar[getEmployeeCount()][nbWeeks];
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        workingDaysPerWeek[e][w] =
            model.newIntVar(0, Constants.DAYS_IN_A_WEEK, "workDaysPerWeek_e" + e + "_w" + w);
      }
    }
  }

  /**
   * Initialise les liens logiques entre les variables d'assignation et les jours travaillés. Cette
   * logique était précédemment dans WorkingDaysConstraint mais appartient ici car elle représente
   * des contraintes techniques toujours nécessaires, pas des règles métier optionnelles.
   */
  private void workingDaysLinksInit() {
    // Lien 1: Si assigné à un shift → alors travaille ce jour
    // workingDays[e][w][d] >= assignments[e][s] pour chaque shift
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int s = 0; s < getShiftCount(); s++) {
        Shift shift = shifts.get(s);
        int weekNumber = shift.day().getWeekNumber();
        int dayOfWeek = shift.day().getDayInWeek(); // 0-6 pour lundi-dimanche

        // Si l'employé est assigné à ce shift, alors il travaille ce jour
        model.addGreaterOrEqual(workingDays[e][weekNumber][dayOfWeek], assignments[e][s]);
      }
    }

    // Lien 2: Calculer workingDaysPerWeek = somme des workingDays de la semaine
    for (int e = 0; e < getEmployeeCount(); e++) {
      for (int w = 0; w < workingDaysPerWeek[e].length; w++) {
        com.google.ortools.sat.LinearExprBuilder sumDaysWorked =
            com.google.ortools.sat.LinearExpr.newBuilder();
        int daysPerCycle = Constants.DAYS_IN_A_WEEK;
        for (int d = 0; d < daysPerCycle; d++) {
          sumDaysWorked.add(workingDays[e][w][d]);
        }
        model.addEquality(workingDaysPerWeek[e][w], sumDaysWorked);
      }
    }
  }

  public int getEmployeeCount() {
    return employees.size();
  }

  public int getShiftCount() {
    return shifts.size();
  }
}
