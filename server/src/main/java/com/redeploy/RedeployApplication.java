package com.redeploy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RedeployApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedeployApplication.class, args);
    }
}
