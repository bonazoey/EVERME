package com.example.everme.lab.dto;



import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 소유자가 새 대화 또는 기존 대화에 직접 입력하는 메시지 요청. */
public record OwnerMessageRequest(
    Long batchId, @Size(max = 1000) String topic, @NotBlank @Size(max = 8000) String content) {}
