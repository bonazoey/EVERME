package com.example.everme.lab.controller;

import com.example.everme.lab.service.LabException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.Map;

@RestControllerAdvice(basePackageClasses=LabController.class)
@Profile("clone-lab")
public class LabErrorHandler {
    @ExceptionHandler(LabException.class) ResponseEntity<?> error(LabException e){return ResponseEntity.status(e.status()).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class}) ResponseEntity<?> input(Exception e){return ResponseEntity.badRequest().body(Map.of("message","입력값을 확인해 주세요."));}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e){
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Clone lab request failed: {}",e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of("message","실험 처리에 실패했습니다. DB 연결과 실행 설정을 확인해 주세요."));
    }
}
