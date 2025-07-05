package dev.byteworks.indezy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class IndezyApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndezyApplication.class, args);
    }
}
