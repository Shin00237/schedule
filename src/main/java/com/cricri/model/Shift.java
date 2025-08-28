package com.cricri.model;

public record Shift(
    String id, // "Jour1-MATIN", "Jour15-SOIR", etc.
    Day day, // référence au jour
    ShiftType type, // référence au type
    int minEmployes, // minimum requis
    int maxEmployes // maximum autorisé
    ) {
  // Méthode pour récupérer le numéro de jour global (0-indexé)
  public int jour() {
    return day.dayNumber();
  }

  // Méthode pour récupérer le jour de la semaine (0-6)
  public int jourDansLaSemaine() {
    return day.getDayInWeek();
  }
}
