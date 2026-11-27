package com.example.everme.dto;

import lombok.Data;

@Data
public class ConsentSelection {
    private String consentType;
    private String documentVersion;
    private Boolean agreed;
}
