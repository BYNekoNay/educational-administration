package com.pzhu.eduadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EduAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduAdminApplication.class, args);
    }
}
