package com.mydata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MyDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyDataApplication.class, args);
    }
}
