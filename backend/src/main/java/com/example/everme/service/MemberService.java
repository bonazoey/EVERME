package com.example.everme.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.everme.dao.MemberDao;
import com.example.everme.dto.LoginRequest;
import com.example.everme.dto.SignupRequest;
import com.example.everme.entity.Member;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MemberService {

    private final MemberDao memberDao;

    public MemberService(MemberDao memberDao) {
        this.memberDao = memberDao;
    }

    public Member getMember(String email){
        return memberDao.selectById(email);
    }
    public int signup(Member member) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        member.setPassword(encoder.encode(member.getPassword()));
        return memberDao.insert(member);
    }

    public String login(Member member) {
        Member db = memberDao.selectById(member.getEmail());
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean result = encoder.matches(member.getPassword(), db.getPassword());
        if(!result){
            return "wrongPassword";
        }
        return "success";
    }

}
