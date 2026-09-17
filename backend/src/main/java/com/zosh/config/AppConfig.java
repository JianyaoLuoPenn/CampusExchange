package com.zosh.config;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class AppConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, @Value("${campus.frontend-url}") String origin)
      throws Exception {
    CorsConfiguration cors = new CorsConfiguration();
    java.net.URI uri = java.net.URI.create(origin);
    java.util.ArrayList<String> allowed = new java.util.ArrayList<>(List.of(origin));
    // Only add the equivalent local development host; never permit arbitrary origins.
    String alias =
        "localhost".equals(uri.getHost())
            ? "127.0.0.1"
            : "127.0.0.1".equals(uri.getHost()) ? "localhost" : null;
    if (alias != null)
      allowed.add(
          new java.net.URI(uri.getScheme(), null, alias, uri.getPort(), null, null, null)
              .toString());
    cors.setAllowedOrigins(allowed);
    cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(s -> s.disable())
        .cors(s -> s.configurationSource(source))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/campus/listings",
                        "/api/campus/listings/*",
                        "/api/campus/config")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/campus/auth/login",
                        "/api/campus/auth/signup",
                        "/api/campus/webhooks/stripe")
                    .permitAll()
                    .requestMatchers("/api/campus/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
        .addFilterBefore(new JwtTokenValidator(), BasicAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
