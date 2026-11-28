package com.example.everme.lab.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.everme.lab.dto.OwnerMessageRequest;
import com.example.everme.lab.dto.QuestionRequest;
import com.example.everme.lab.dto.SessionRequest;
import com.example.everme.lab.dto.TopicRequest;
import com.example.everme.lab.dto.UserRequest;
import com.example.everme.lab.service.LabCloneService;
import com.example.everme.lab.service.LabRunService;
import com.example.everme.lab.service.LabService;
import com.example.everme.lab.service.OpenAiLabClient;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;

@RestController
@Profile("clone-lab")
@RequestMapping("/api/lab")
public class LabController {

  // ##### 필드 #####
  private final LabService labService;
  private final LabRunService labRunService;
  private final LabCloneService labCloneService;
  private final OpenAiLabClient openAiLabClient;

  // ##### 생성자 #####
  public LabController(
      LabService labService,
      LabRunService labRunService,
      LabCloneService labCloneService,
      OpenAiLabClient openAiLabClient) {
    this.labService = labService;
    this.labRunService = labRunService;
    this.labCloneService = labCloneService;
    this.openAiLabClient = openAiLabClient;
  }

  // ##### 분석 요소 카탈로그를 조회하는 메소드 #####
  @GetMapping("/catalog")
  public JsonNode catalog() {
    return labService.catalog();
  }

  // ##### 모델 설정을 조회하는 메소드 #####
  @GetMapping("/config")
  public JsonNode config() {
    return labService.config();
  }

  // ##### 모델 접근 권한을 확인하는 메소드 #####
  @PostMapping("/models/check")
  public JsonNode checkModels() {
    return openAiLabClient.checkModels();
  }

  // ##### 테스트 사용자 목록을 조회하는 메소드 #####
  @GetMapping("/users")
  public JsonNode users() {
    return labService.users();
  }

  // ##### 테스트 사용자 생성 요청 메소드 #####
  @PostMapping("/users")
  public JsonNode create(@Valid @RequestBody UserRequest request) {
    return labService.createUser(request.name(), request.seed());
  }

  // ##### 분석 프로필을 조회하는 메소드 #####
  @GetMapping("/users/{user}/profile")
  public JsonNode profile(@PathVariable Long user) {
    return labService.profile(user);
  }

  // ##### 실험 결과를 내보내는 메소드 #####
  @GetMapping("/users/{user}/export")
  public JsonNode export(@PathVariable Long user) {
    return labService.export(user);
  }

  // ##### 대화 배치 목록을 조회하는 메소드 #####
  @GetMapping("/users/{user}/batches")
  public JsonNode batches(@PathVariable Long user) {
    return labService.batches(user);
  }

  // ##### 테스트 대화 10쌍을 생성하는 메소드 #####
  @PostMapping("/users/{user}/batches")
  public JsonNode generate(@PathVariable Long user, @Valid @RequestBody TopicRequest request) {
    return labService.generate(user, request.topic(), false);
  }

  // ##### 대화 배치를 조회하는 메소드 #####
  @GetMapping("/users/{user}/batches/{batch}")
  public JsonNode batch(@PathVariable Long user, @PathVariable Long batch) {
    return labService.batch(user, batch);
  }

  // ##### 같은 주제의 대화를 추가하는 메소드 #####
  @PostMapping("/users/{user}/batches/{batch}/turns")
  public JsonNode append(@PathVariable Long user, @PathVariable Long batch) {
    return labService.append(user, batch);
  }

  // ##### 사용자 직접 입력을 처리하는 메소드 #####
  @PostMapping("/users/{user}/messages")
  public JsonNode message(
      @PathVariable Long user, @Valid @RequestBody OwnerMessageRequest request) {
    return labService.message(user, request.batchId(), request.topic(), request.content());
  }

  // ##### 대화 분석을 실행하는 메소드 #####
  @PostMapping("/users/{user}/batches/{batch}/analysis")
  public JsonNode analyze(@PathVariable Long user, @PathVariable Long batch) {
    return labService.analyze(user, batch, false);
  }

  // ##### 분석 결과를 저장하는 메소드 #####
  @PostMapping("/users/{user}/batches/{batch}/save")
  public JsonNode save(@PathVariable Long user, @PathVariable Long batch) {
    return labService.save(user, batch, false);
  }

  // ##### 자동 실행 시작 요청 메소드 #####
  @PostMapping({"/users/{user}/runs", "/users/{user}/labRunService"})
  public JsonNode run(@PathVariable Long user, @Valid @RequestBody TopicRequest request) {
    return labRunService.start(user, request.topic(), false);
  }

  // ##### 자동 실행 상태 조회 요청 메소드 #####
  @GetMapping({"/users/{user}/runs/current", "/users/{user}/labRunService/current"})
  public JsonNode runState(@PathVariable Long user) {
    return labRunService.state(user);
  }

  // ##### 현재 작업 후 중단을 요청하는 메소드 #####
  @PostMapping({"/users/{user}/runs/current/stop", "/users/{user}/labRunService/current/stop"})
  public JsonNode stop(@PathVariable Long user) {
    return labRunService.stop(user);
  }

  // ##### 자동 실행 재개 요청 메소드 #####
  @PostMapping({"/users/{user}/runs/current/resume", "/users/{user}/labRunService/current/resume"})
  public JsonNode resume(@PathVariable Long user) {
    return labRunService.start(user, null, true);
  }

  // ##### 클론 대화 시작 요청 메소드 #####
  @PostMapping("/users/{user}/sessions")
  public JsonNode session(@PathVariable Long user, @RequestBody SessionRequest request) {
    return labCloneService.start(
        user, request.actorUserId() == null ? user : request.actorUserId(), request.batchId());
  }

  // ##### 정보이용자 대화 이력을 조회하는 메소드 #####
  @GetMapping("/users/{user}/sessions/current")
  public JsonNode history(
      @PathVariable Long user, @RequestParam(required = false) Long actorUserId) {
    return labCloneService.history(user, actorUserId == null ? user : actorUserId);
  }

  // ##### 클론 질문 요청 메소드 #####
  @PostMapping("/users/{user}/questions")
  public JsonNode question(@PathVariable Long user, @Valid @RequestBody QuestionRequest request) {
    return labCloneService.answer(
        user,
        request.actorUserId() == null ? user : request.actorUserId(),
        request.sessionId(),
        request.question());
  }
}
