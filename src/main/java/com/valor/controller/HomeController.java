package com.valor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "Valor Backend is running.";
    }

    @GetMapping("/api/health")
    public String health() {
        return "OK";
    }
}
