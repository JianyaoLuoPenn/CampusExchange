package com.zosh.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import java.time.Clock;
import java.util.List;
@Configuration
public class AppConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, @Value("${campus.frontend-url}") String origin) throws Exception {
        CorsConfiguration cors=new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin)); cors.setAllowedMethods(List.of("GET","POST","OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization","Content-Type"));
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors);
        return http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(s->s.disable()).cors(s->s.configurationSource(source))
            .authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/campus/listings","/api/campus/listings/*","/api/campus/config").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/campus/auth/login","/api/campus/auth/signup","/api/campus/webhooks/stripe").permitAll()
                .requestMatchers("/api/campus/**").authenticated().anyRequest().denyAll())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(401)))
            .addFilterBefore(new JwtTokenValidator(),BasicAuthenticationFilter.class).build();
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
