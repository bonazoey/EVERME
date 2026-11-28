package com.example.everme.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 테스트 사용자 생성 요청. 표시 이름과 평가용 인물 설명을 받는다. */
public record UserRequest(
    @NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 3000) String seed) {}
