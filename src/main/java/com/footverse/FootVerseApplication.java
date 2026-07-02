package com.footverse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point for the FootVerse backend REST API.
 *
 * <p>Bootstraps the Spring Boot application context that hosts the FootVerse
 * e-commerce services (authentication, catalog, cart, and orders) consumed by
 * the Flutter mobile client.</p>
 */
@SpringBootApplication
public class FootVerseApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments forwarded to the Spring context
     */
    public static void main(String[] args) {
        SpringApplication.run(FootVerseApplication.class, args);
    }

}
