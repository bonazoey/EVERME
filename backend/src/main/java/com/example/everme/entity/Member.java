package com.example.everme.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data 
public class Member {
    // 사용자 이름
    private String name;
    // 사용자 아이디
    private String email;
    // 사용자 비밀번호
    @lombok.ToString.Exclude
    private String password;
    // 생년월일
    private LocalDate birthDate;
    // 국적
    private String nationality;
    // 전화번호
    private String phone;
    // 성별
    private String gender;
    // 계정 활성 상태
    private boolean active = true;
    // 가입일자
    private LocalDateTime createdAt;
    
}
