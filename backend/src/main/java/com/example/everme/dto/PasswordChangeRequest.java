package com.example.everme.dto;

import lombok.Data;

@Data
public class PasswordChangeRequest {
    @lombok.ToString.Exclude
    private String token;
    @lombok.ToString.Exclude
    private String newPassword;
}
