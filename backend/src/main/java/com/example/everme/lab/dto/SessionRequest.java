package com.example.everme.lab.dto;



/** 클론 대화 시작 요청. 참조할 소유자 대화와 정보이용자를 지정한다. */
public record SessionRequest(Long batchId, Long actorUserId) {}
