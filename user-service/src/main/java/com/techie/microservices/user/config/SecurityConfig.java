package com.techie.microservices.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server. Registration is public (the visitor has no token yet); everything else
 * (e.g. GET /api/user/me) needs a valid Keycloak JWT. Swagger and actuator health are open.
 */
@Configuration
public class SecurityConfig {

    private static final String[] OPEN_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**",
            "/actuator/health/**", "/actuator/info"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/user/register").permitAll()
                        .requestMatchers(OPEN_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
