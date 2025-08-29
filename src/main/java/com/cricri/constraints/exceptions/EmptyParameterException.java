package com.cricri.constraints.exceptions;

public class EmptyParameterException extends IllegalArgumentException {
  public EmptyParameterException(String parameterName) {
    super("Le paramètre '" + parameterName + "' ne peut pas être vide");
  }
}
