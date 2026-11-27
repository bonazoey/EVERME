package com.example.everme.lab.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Profile("clone-lab")
public class LabPageController {
    @GetMapping({"/lab","/lab/"}) public ResponseEntity<Resource> owner(){return file("owner.html",MediaType.TEXT_HTML);}
    @GetMapping("/lab/viewer") public ResponseEntity<Resource> viewer(){return file("viewer.html",MediaType.TEXT_HTML);}
    @GetMapping("/lab/assets/{asset}") public ResponseEntity<Resource> asset(@PathVariable String asset){
        var types=Map.of("lab.css",MediaType.parseMediaType("text/css"),"lab.js",MediaType.parseMediaType("application/javascript"));
        if(!types.containsKey(asset))return ResponseEntity.notFound().build();return file(asset,types.get(asset));
    }
    private ResponseEntity<Resource> file(String name,MediaType type){return ResponseEntity.ok().contentType(type).header("Cache-Control","no-store").body(new ClassPathResource("clone-lab/"+name));}
}
