package com.MBQ.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY security setup until JWT login (FR9 / NFR1) is built.
 *
 * Without this class, Spring Security blocks every request, so the frontend
 * could not call the patient and queue endpoints at all.
 *
 * TODO(auth): when JWT login lands, replace permitAll() with role checks, e.g.
 *   /api/patients/**              -> RECEPTIONIST, ADMIN
 *   PATCH .../priority, call-next -> NURSE
 *   PATCH .../status              -> NURSE, DOCTOR
 * See docs/PATIENT_QUEUE_API.md for the intended role per endpoint.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                // REST API with tokens, not browser sessions/cookies -> CSRF protection not needed
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/patients/**", "/api/queue/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
