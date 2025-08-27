package com.cricri.model;

import lombok.Data;

public record Employee(
    String id,
    String nom
) {
    // Plus tard: préférences, disponibilités, etc.
}
