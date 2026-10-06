package com.example.student_api;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class StudentController {

    private final StudentRepository repo;

    public StudentController(StudentRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/")
    public String home() {
        return "Hello from Spring Boot behind Nginx!";
    }

    @GetMapping("/students")
    public List<Student> all() {
        return repo.findAll();
    }

    @PostMapping("/students")
    public Student add(@RequestBody Student s) {
        return repo.save(s);
    }
}