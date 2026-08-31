package com.kob.botrunningsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BotRunningSystemApplication {
    public static void main(String[] args) {
        // BotPool 由 Spring 管理（@Component），线程池随容器启动，无需手动 start
        SpringApplication.run(BotRunningSystemApplication.class, args);
    }
}
