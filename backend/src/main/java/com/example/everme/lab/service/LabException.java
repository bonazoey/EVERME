package com.example.everme.lab.service;

public class LabException extends RuntimeException {
  // ##### 필드 #####
  private final int status;

  // ##### 생성자 #####
  public LabException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static LabException bad(String message) {
    return new LabException(400, message);
  }

  public static LabException conflict(String message) {
    return new LabException(409, message);
  }

  public static LabException invalid(String message) {
    return new LabException(502, message);
  }
}
