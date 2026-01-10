package com.cos.fairbid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class FairBidApplication {

    public static void main(String[] args) {
        SpringApplication.run(FairBidApplication.class, args);
    }

}
