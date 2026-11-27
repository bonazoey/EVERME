package com.example.everme.dto;

import lombok.Data;

@Data
public class ProfileRequest {
    private String name;
    private java.time.LocalDate birthDate;
    private String nationality;
    private String phone;
    private String gender;
}
