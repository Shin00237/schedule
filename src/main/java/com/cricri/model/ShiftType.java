package com.cricri.model;

public record ShiftType(
    String id, // "MATIN", "APRES_MIDI", "SOIR"
    int heureDebutMinutes, // 390 pour 6h30 (6*60 + 30)
    int heureFinMinutes, // 990 pour 16h30 (16*60 + 30)
    int dureeMinutes, // heureFinMinutes - heureDebutMinutes
    int pauseMinutes // durée de pause incluse dans le shift
    ) {

  // Durée effective de travail (sans les pauses)
  public int dureeEffectiveMinutes() {
    return dureeMinutes - pauseMinutes;
  }
}
