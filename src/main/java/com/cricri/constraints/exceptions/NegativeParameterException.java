package com.cricri.constraints.exceptions;

public class NegativeParameterException extends IllegalArgumentException {

  public NegativeParameterException(String parameterName) {
    super("Le paramètre '" + parameterName + "' ne peut pas être négatif");
  }
}
