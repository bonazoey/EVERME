package com.example.everme.dto;

import lombok.Data;

@Data
public class InvitationRequest {
    private String email;
    private Integer maxAccessLevel;
    private java.util.UUID groupId;
}
