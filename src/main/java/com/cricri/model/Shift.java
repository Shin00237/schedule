package com.cricri.model;

import lombok.Data;

public record Shift(
    String id,                 // "Jour1-MATIN", "Jour15-SOIR", etc.
    int jour,                  // 1-31 pour un mois
    ShiftType type,            // référence au type
    int minEmployes,           // minimum requis
    int maxEmployes           // maximum autorisé
) {
}