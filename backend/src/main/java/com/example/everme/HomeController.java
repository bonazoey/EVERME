package com.example.everme;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController  
public class HomeController {

    @GetMapping("/api/home")
    public String main(){
        return "안녕하세요";
    }
}
