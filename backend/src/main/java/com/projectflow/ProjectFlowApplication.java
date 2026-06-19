package com.projectflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ProjectFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectFlowApplication.class, args);
    }
}
