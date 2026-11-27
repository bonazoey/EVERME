package com.example.everme.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.everme.entity.Member;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey secretKey;

    public JwtService(@Value("${jwt.secret.key}") String jwtSecretKey) throws Exception {
        byte[] bytes = (jwtSecretKey).getBytes("UTF-8");

        secretKey = Keys.hmacShaKeyFor(bytes);
    }

    public String createToken(Member member) {
        // 유효시간 1시간
        Date date = new Date(new Date().getTime() + 1000 * 1 * 60 * 60);
        String jwt = Jwts.builder()
                // JWT의 subject에는 사용자의 식별값을 저장
                .subject(member.getEmail())
                .expiration(date)
                .signWith(secretKey)
                .compact();
        return jwt;
    }

    public String getEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey) // 발급할 때 사용한 키로 서명 검증
                .build()
                .parseSignedClaims(token) // 서명과 만료 시간 확인
                .getPayload();

        String email = claims.getSubject();

        if (email == null || email.isBlank() || claims.getExpiration() == null) {
            throw new JwtException("유효하지 않은 토큰입니다.");
        }

        return email;
    }
}
