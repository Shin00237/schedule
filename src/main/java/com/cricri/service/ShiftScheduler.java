package com.cricri.service;

import java.util.List;
import java.util.Map;
import com.cricri.model.Day;
import com.cricri.model.Employee;
import com.cricri.model.Shift;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import lombok.Data;

@Data
public class ShiftScheduler {
  private CpModel model;
  private List<Employee> employees;
  private List<Shift> shifts;
  private Map<String, Integer> shiftIndexMap; // pour retrouver facilement l'index d'un shift
  private int maxHoursPerWeek = 40 * 60; // 40h en minutes par défaut
  private int minRestHours = 11; // repos minimum entre shifts en heures
  private int minHoursPerShift = 5 * 60; // durée minimum par shift (5h par défaut)

  // Variables OR-Tools
  private BoolVar[][] assignments;
  private IntVar[][] actualHours; // [employé][shift] - durée réelle travaillée en minutes
  private IntVar[] employeesPerShift;
  private BoolVar[][][] workingDays; // [employé][semaine][jour_semaine] (0-6 pour lundi-dimanche)
  private IntVar[][] workingDaysPerWeek; // [employé][semaine] - nombre de jours travaillés
  private IntVar[] hoursPerEmployee;
  private IntVar[][] hoursPerEmployeePerWeek; // [employé][semaine]

  public void buildModel() {
    model = new CpModel();

    // Créer les variables
    assignments = new BoolVar[employees.size()][shifts.size()];
    actualHours = new IntVar[employees.size()][shifts.size()];
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        assignments[e][s] = model.newBoolVar("assign_e" + e + "_s" + s);
        // Durée réelle travaillée: 0 si non assigné, sinon entre 1 minute et durée complète du shift
        int maxShiftDuration = shifts.get(s).type().dureeMinutes();
        actualHours[e][s] = model.newIntVar(0, maxShiftDuration, "hours_e" + e + "_s" + s);
      }
    }

    // Variables pour compter les employés
    employeesPerShift = new IntVar[shifts.size()];
    for (int s = 0; s < shifts.size(); s++) {
      employeesPerShift[s] = model.newIntVar(0, employees.size(), "nbEmployees_s" + s);
    }

    // Variables pour les heures par employé par semaine
    int nbWeeks = calculateNumberOfWeeks();
    hoursPerEmployeePerWeek = new IntVar[employees.size()][nbWeeks];
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        hoursPerEmployeePerWeek[e][w] =
            model.newIntVar(0, maxHoursPerWeek, "hours_e" + e + "_w" + w);
      }
    }

    // Variables pour les jours travaillés
    workingDays = new BoolVar[employees.size()][nbWeeks][7]; // 0-6 pour lundi-dimanche
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        for (int d = 0; d < 7; d++) {
          workingDays[e][w][d] = model.newBoolVar("workDay_e" + e + "_w" + w + "_d" + d);
        }
      }
    }

    // Variables pour compter les jours travaillés par semaine
    workingDaysPerWeek = new IntVar[employees.size()][nbWeeks];
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        workingDaysPerWeek[e][w] = model.newIntVar(0, 7, "workDaysPerWeek_e" + e + "_w" + w);
      }
    }

    // workingDays = new BoolVar[employees.size()][nombreJoursDansMois];
    // for (int e = 0; e < employees.size(); e++) {
    // for (int d = 0; d < nombreJoursDansMois; d++) {
    // workingDays[e][d] = model.newBoolVar("workDay_e" + e + "_d" + (d+1));
    // }
    // }

    // Contraintes
    addMinimumEmployeesConstraint();
    addAssignmentHoursConstraint();
    addMaxHoursPerWeekConstraint();
    addMinimumRestConstraint();
    addWorkingDaysConstraints();
    addMinimumRestDaysConstraint();

    // Fonction objectif
    addWeekdayStaffingObjective();
  }

  public void addMinimumEmployeesConstraint() {
    // Pour chaque shift
    for (int s = 0; s < shifts.size(); s++) {
      Shift shift = shifts.get(s);

      // Compter le nombre d'employés assignés
      LinearExprBuilder sumEmployees = LinearExpr.newBuilder();
      for (int e = 0; e < employees.size(); e++) {
        sumEmployees.add(assignments[e][s]);
      }

      // Ajouter la contrainte : somme >= minimum requis
      model.addGreaterOrEqual(sumEmployees, shift.minEmployes());

      // Et aussi <= maximum si défini
      if (shift.maxEmployes() > 0) {
        model.addLessOrEqual(sumEmployees, shift.maxEmployes());
      }

      // Lier avec la variable employeesPerShift
      model.addEquality(employeesPerShift[s], sumEmployees);
    }
  }

  public void addAssignmentHoursConstraint() {
    // Si un employé n'est pas assigné à un shift, ses heures réelles doivent être 0
    // Si un employé est assigné à un shift, ses heures réelles doivent être > 0
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        int maxShiftDuration = shifts.get(s).type().dureeMinutes();

        // Contrainte de cohérence : si assigné, minimum minHoursPerShift, sinon 0
        // actualHours[e][s] >= assignments[e][s] * minHoursPerShift
        model.addGreaterOrEqual(actualHours[e][s],
            LinearExpr.newBuilder().addTerm(assignments[e][s], minHoursPerShift).build());

        // actualHours[e][s] <= assignments[e][s] * maxShiftDuration (si non assigné, alors 0)
        model.addLessOrEqual(actualHours[e][s],
            LinearExpr.newBuilder().addTerm(assignments[e][s], maxShiftDuration).build());
      }
    }
  }

  public void addMaxHoursPerWeekConstraint() {
    int nbWeeks = calculateNumberOfWeeks();

    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < nbWeeks; w++) {
        LinearExprBuilder hoursInWeek = LinearExpr.newBuilder();

        // Pour chaque shift de cette semaine
        for (int s = 0; s < shifts.size(); s++) {
          Shift shift = shifts.get(s);
          int shiftWeek = shift.day().getWeekNumber();

          if (shiftWeek == w) {
            // Ajouter les heures réelles travaillées pour ce shift
            hoursInWeek.add(actualHours[e][s]);
          }
        }

        // Lier avec la variable hoursPerEmployeePerWeek
        model.addEquality(hoursPerEmployeePerWeek[e][w], hoursInWeek);

        // Contrainte : ne pas dépasser le maximum d'heures par semaine
        model.addLessOrEqual(hoursPerEmployeePerWeek[e][w], maxHoursPerWeek);
      }
    }
  }

  public void addMinimumRestConstraint() {
    int minRestMinutes = minRestHours * 60; // convertir en minutes

    // Pour chaque employé
    for (int e = 0; e < employees.size(); e++) {
      // Pour chaque paire de shifts
      for (int s1 = 0; s1 < shifts.size(); s1++) {
        for (int s2 = 0; s2 < shifts.size(); s2++) {
          if (s1 != s2 && hasRestConflict(s1, s2, minRestMinutes)) {
            // Si les shifts sont en conflit, l'employé ne peut pas faire les deux
            model.addLessOrEqual(
                LinearExpr.newBuilder().add(assignments[e][s1]).add(assignments[e][s2]).build(), 1);
          }
        }
      }
    }
  }

  public void addWorkingDaysConstraints() {
    // Lier les assignments aux workingDays
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        Shift shift = shifts.get(s);
        int weekNumber = shift.day().getWeekNumber();
        int dayOfWeek = shift.day().getDayNumber(); // 0-6 pour lundi-dimanche

        // Si l'employé est assigné à ce shift, alors il travaille ce jour
        // workingDays[e][w][d] >= assignments[e][s]
        model.addGreaterOrEqual(workingDays[e][weekNumber][dayOfWeek], assignments[e][s]);
      }
    }

    // Lier workingDays à workingDaysPerWeek
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < workingDaysPerWeek[e].length; w++) {
        LinearExprBuilder sumDaysWorked = LinearExpr.newBuilder();
        for (int d = 0; d < 7; d++) {
          sumDaysWorked.add(workingDays[e][w][d]);
        }
        model.addEquality(workingDaysPerWeek[e][w], sumDaysWorked);
      }
    }
  }

  public void addMinimumRestDaysConstraint() {
    // Contrainte dure : au moins 1 jour de repos par semaine (max 6 jours travaillés)
    for (int e = 0; e < employees.size(); e++) {
      for (int w = 0; w < workingDaysPerWeek[e].length; w++) {
        model.addLessOrEqual(workingDaysPerWeek[e][w], 6);
      }
    }
  }

  public void addWeekdayStaffingObjective() {
    LinearExprBuilder objective = LinearExpr.newBuilder();

    // Objectif principal : maximiser les heures réelles travaillées
    for (int e = 0; e < employees.size(); e++) {
      for (int s = 0; s < shifts.size(); s++) {
        objective.add(actualHours[e][s]); // Encourager plus d'heures
      }
    }

    // Objectif secondaire : favoriser les jours de semaine
    for (int s = 0; s < shifts.size(); s++) {
      Shift shift = shifts.get(s);
      if (!shift.day().isWeekend()) {
        for (int e = 0; e < employees.size(); e++) {
          objective.addTerm(actualHours[e][s], 2); // Pondération × 2 pour semaine
        }
      }
    }

    model.maximize(objective); // Maximiser les heures totales
  }

  private int calculateNumberOfWeeks() {
    if (shifts.isEmpty()) return 1;

    int maxWeekNumber =
        shifts.stream().mapToInt(shift -> shift.day().getWeekNumber()).max().orElse(0);

    // Retourner le nombre total de semaines (0-indexé + 1)
    return maxWeekNumber + 1;
  }

  private boolean hasRestConflict(int shiftIndex1, int shiftIndex2, int minRestMinutes) {
    Shift shift1 = shifts.get(shiftIndex1);
    Shift shift2 = shifts.get(shiftIndex2);

    // Calculer les temps absolus en minutes depuis le début de la période
    int endTime1 = calculateAbsoluteTime(shift1.day(), shift1.type().heureFinMinutes());
    int startTime2 = calculateAbsoluteTime(shift2.day(), shift2.type().heureDebutMinutes());

    // Cas 1: Chevauchement - seulement si c'est le même jour absolu
    int day1 = shift1.day().getWeekNumber() * 7 + shift1.day().getDayNumber();
    int day2 = shift2.day().getWeekNumber() * 7 + shift2.day().getDayNumber();

    if (day1 == day2 && startTime2 < endTime1) {
      return true;
    }

    // Cas 2: Repos insuffisant - shift2 commence moins de minRestMinutes après la fin de shift1
    return startTime2 > endTime1 && startTime2 < endTime1 + minRestMinutes;
  }

  private int calculateAbsoluteTime(Day day, int heureMinutes) {
    // Convertir en temps absolu : (jour-1) * 24h * 60min + heureMinutes
    int absoluteDay = day.getWeekNumber() * 7 + day.getDayNumber();
    return absoluteDay * 24 * 60 + heureMinutes;
  }

  // Getter pour accéder aux heures réelles
  public IntVar[][] getActualHours() {
    return actualHours;
  }

  // Getter/Setter pour la durée minimum par shift
  public int getMinHoursPerShift() {
    return minHoursPerShift;
  }

  public void setMinHoursPerShift(int minHoursPerShift) {
    this.minHoursPerShift = minHoursPerShift;
  }
}
