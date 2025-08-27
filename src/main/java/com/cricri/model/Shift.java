package com.cricri.model;

public record Shift(
    String id, // "Jour1-MATIN", "Jour15-SOIR", etc.
    Day day, // référence au jour
    ShiftType type, // référence au type
    int minEmployes, // minimum requis
    int maxEmployes // maximum autorisé
    ) {
  // Méthode de compatibilité pour le code existant
  public int jour() {
    return day.dayNumber();
  }
}
