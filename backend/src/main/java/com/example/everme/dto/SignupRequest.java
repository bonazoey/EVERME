package com.example.everme.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SignupRequest {
    //사용자 이름
    private String name;
    //사용자 아이디 
    private String email;
    // 생년월일
    private LocalDate birthDate;
    // 국적
    private String nationality;
    // 전화번호
    private String phone;
    // 성별
    private String gender;
    //사용자 비밀번호
    @lombok.ToString.Exclude
    private String password;

    
}
