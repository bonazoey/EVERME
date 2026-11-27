package com.example.everme.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    @lombok.ToString.Exclude
    private String password;
}
