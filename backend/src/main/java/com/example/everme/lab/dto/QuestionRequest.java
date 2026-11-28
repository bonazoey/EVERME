package com.example.everme.lab.dto;



import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 정보이용자가 지정한 클론 대화 세션에 보내는 질문 요청. */
public record QuestionRequest(
    @NotNull Long sessionId, @NotBlank @Size(max = 4000) String question, Long actorUserId) {}
