package br.edu.ifmt.tarefas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/")
    public String inicio() {
        return "redirect:/tarefas";
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }
}
