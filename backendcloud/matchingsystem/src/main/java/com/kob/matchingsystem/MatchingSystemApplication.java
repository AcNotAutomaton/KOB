package com.kob.matchingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MatchingSystemApplication {
    public static void main(String[] args) {
        // MatchingPool 由 Spring 管理（@Component），匹配线程随容器启动，无需手动 start
        SpringApplication.run(MatchingSystemApplication.class, args);
    }
}
