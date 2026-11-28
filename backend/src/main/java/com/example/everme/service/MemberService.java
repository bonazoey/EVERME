package com.example.everme.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import com.example.everme.dao.MemberDao;
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
        if (memberDao.selectById(member.getEmail()) != null) {
            throw new DuplicateKeyException("이미 가입된 이메일입니다.");
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        member.setPassword(encoder.encode(member.getPassword()));
        return memberDao.insert(member);
    }

    public String login(Member member) {
        if (member.getEmail() == null || member.getEmail().isBlank()
                || member.getPassword() == null || member.getPassword().isEmpty()) {
            return "wrongPassword";
        }
        Member db = memberDao.selectById(member.getEmail());
        if (db == null || !db.isActive() || db.getPassword() == null) {
            return "wrongPassword";
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean result = encoder.matches(member.getPassword(), db.getPassword());
        if(!result){
            return "wrongPassword";
        }
        return "success";
    }

}
