package com.example.everme.lab.dto;

import jakarta.validation.constraints.Size;

/** 대화 생성과 자동 실행에 사용할 주제 요청. */
public record TopicRequest(@Size(max = 1000) String topic) {}
