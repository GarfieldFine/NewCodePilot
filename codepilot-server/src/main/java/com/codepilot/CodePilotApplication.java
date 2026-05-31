package com.codepilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.codepilot.mapper")
public class CodePilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodePilotApplication.class, args);
    }
}
