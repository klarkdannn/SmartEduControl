package com.dev.LMS.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.dev.LMS.util.JwtUtil;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtUtil);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // --------------------------
            // Отключаем CSRF на время теста
            // --------------------------
            .csrf(csrf -> csrf.disable())

            // --------------------------
            // Разрешаем все запросы без авторизации
            // --------------------------
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

            // --------------------------
            // Сессии делаем Stateless (можно оставить для JWT)
            // --------------------------
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        // --------------------------
        // JWT фильтр оставляем, но он не будет блокировать доступ
        // --------------------------
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /*
    // Для нормальной настройки безопасности потом использовать так:
    // .requestMatchers("/register", "/login").permitAll()
    // .requestMatchers("/profile").hasAnyRole("STUDENT", "INSTRUCTOR", "ADMIN")
    // .requestMatchers("/users/**").hasRole("ADMIN")
    // .anyRequest().authenticated()
    */
}
