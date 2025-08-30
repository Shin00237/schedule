package com.cricri.model;

import java.time.Duration;
import java.time.LocalTime;

public record ShiftType(
    String id, // "MATIN", "APRES_MIDI", "SOIR"
    LocalTime heureDebut,
    LocalTime heureFin,
    Duration pause) {

  // Durée effective de travail (sans les pauses)
  public Duration duree() {
    Duration d = Duration.between(heureDebut, heureFin);
    // Gérer traversée minuit
    return d.isNegative() ? d.plusDays(1) : d;
  }

  public Duration dureeEffective() {
    return duree().minus(pause);
  }

  public int dureeMinutes() {
    return (int) duree().toMinutes();
  }

  public int dureeEffectiveMinutes() {
    return (int) dureeEffective().toMinutes();
  }
}
