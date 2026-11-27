package com.example.everme.lab.controller;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("clone-lab")
public class LabPageController {
  // ##### 서버 도구 컨텍스트의 소유자를 확인하는 메소드 #####
  @GetMapping({"/lab", "/lab/"})
  public ResponseEntity<Resource> owner() {
    return file("owner.html", MediaType.TEXT_HTML);
  }

  @GetMapping("/lab/viewer")
  public ResponseEntity<Resource> viewer() {
    return file("viewer.html", MediaType.TEXT_HTML);
  }

  @GetMapping("/lab/assets/{asset}")
  public ResponseEntity<Resource> asset(@PathVariable String asset) {
    Map<String, MediaType> types =
        Map.of(
            "lab.css",
            MediaType.parseMediaType("text/css"),
            "lab.js",
            MediaType.parseMediaType("application/javascript"));
    if (!types.containsKey(asset)) {
      return ResponseEntity.notFound().build();
    }
    return file(asset, types.get(asset));
  }

  private ResponseEntity<Resource> file(String name, MediaType type) {
    return ResponseEntity.ok()
        .contentType(type)
        .header("Cache-Control", "no-store")
        .body(new ClassPathResource("clone-lab/" + name));
  }
}
