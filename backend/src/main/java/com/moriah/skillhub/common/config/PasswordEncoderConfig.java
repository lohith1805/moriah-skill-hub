package com.moriah.skillhub.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Split out of {@code SecurityConfig} (feature 05): {@code PasswordEncoder} has no relationship
 * to the HTTP filter chain — it was only ever there by convention. That mattered once feature 05
 * added OAuth2 wiring to {@code SecurityConfig}: {@code AuthService} needs a {@code
 * PasswordEncoder}, and a {@code @Bean} method defined on {@code SecurityConfig} itself requires
 * Spring to fully construct {@code SecurityConfig} first — which by then also needs {@code
 * OAuth2AuthenticationSuccessHandler} → {@code OAuth2Service} → {@code AuthService}, an
 * unresolvable circular dependency with constructor injection. Moving this one {@code @Bean} to
 * its own dependency-free class breaks the cycle without touching the actual login/2FA
 * relationship between {@code AuthService} and {@code OAuth2Service} (`/architect feature 05`'s
 * design, already confirmed).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
