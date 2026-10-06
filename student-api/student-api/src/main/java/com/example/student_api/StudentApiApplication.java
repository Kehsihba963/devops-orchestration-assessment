package com.example.student_api;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StudentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentApiApplication.class, args);
    }

    @Bean
    CommandLineRunner seed(StudentRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(new Student("Alice", "alice@example.com"));
                repo.save(new Student("Bob", "bob@example.com"));
            }
        };
    }
}