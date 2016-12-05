package com.riz.exercise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Created by rizjoj on 12/1/16.
 */
@SpringBootApplication
// @EnableScheduling <-- Do not enable Scheduling for Test context
public class TestApplication {
    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(TestApplication.class, args);
    }
}
