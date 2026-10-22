package com.api.v1.bearerauth.config;

import com.api.v1.bearerauth.security.CustomAccessDeniedHandler;
import com.api.v1.bearerauth.security.JwtAuthEntrypoint;
import com.api.v1.bearerauth.security.JwtAuthFilter;
import com.api.v1.bearerauth.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppUserService appUserService;
    private final CustomAccessDeniedHandler customAccessDeniesHandler;
    private final JwtAuthEntrypoint jwtAuthEntrypoint;
    private final AppProperty appProperty;

    // Only the origins listed in app.cors.allowed-origins may call the API from a browser.
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperty.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        request -> request
                                .requestMatchers(
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/api/v1/auth/register",
                                        "/api/v1/auth/verify-email",
                                        "/api/v1/auth/resend-otp",
                                        "/api/v1/auth/login",
                                        "/api/v1/auth/login/verify-otp",
                                        "/api/v1/auth/refresh",
                                        "/api/v1/auth/forgot-password",
                                        "/api/v1/auth/reset-password"
                                ).permitAll()
                                .requestMatchers("/api/v1/users/me", "/api/v1/users/me/avatar").authenticated()
                                .requestMatchers("/api/v1/users/**", "/api/v1/roles/**").hasRole("ADMIN")
                                // system settings: everybody reads the public ones, only the admin reads all and changes them
                                .requestMatchers(HttpMethod.GET, "/api/v1/settings/public").permitAll()
                                .requestMatchers("/api/v1/settings/**").hasRole("ADMIN")
                                // notifications: any logged-in user, only their own (scoped in NotificationService)
                                .requestMatchers("/api/v1/notifications/**").authenticated()
                                // activity log: the editors read only the content feed, everything else is for the admin (read only)
                                .requestMatchers(HttpMethod.GET, "/api/v1/activity-logs/recent").hasAnyRole("EDITOR", "ADMIN")
                                .requestMatchers("/api/v1/activity-logs/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/v1/categories/admin").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/*",
                                        "/api/v1/tags", "/api/v1/tags/*").permitAll()
                                .requestMatchers("/api/v1/categories/**", "/api/v1/tags/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                                // link previews (Open Graph pages for Facebook, Telegram ...): HTML, no login
                                .requestMatchers(HttpMethod.GET, "/preview", "/preview/**").permitAll()
                                // article management; the public reading endpoints are added in a later step
                                // public reading: readers need no login
                                .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                                // reports: the author's own dashboard for AUTHOR; the lists, series, trends and activity for
                                // EDITOR and ADMIN (whole site) and AUTHOR (own articles, scoped in ReportService);
                                // everything else for EDITOR and ADMIN
                                .requestMatchers(HttpMethod.GET, "/api/v1/reports/author").hasRole("AUTHOR")
                                .requestMatchers(HttpMethod.GET, "/api/v1/reports/most-viewed", "/api/v1/reports/most-commented",
                                        "/api/v1/reports/published", "/api/v1/reports/views", "/api/v1/reports/trends",
                                        "/api/v1/reports/activity").hasAnyRole("EDITOR", "ADMIN", "AUTHOR")
                                .requestMatchers("/api/v1/reports/**").hasAnyRole("EDITOR", "ADMIN")
                                // bookmarks: any logged-in user (GET /api/v1/bookmarks needs a login like everything else)
                                .requestMatchers(HttpMethod.POST, "/api/v1/news/*/bookmark").authenticated()
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/news/*/bookmark").authenticated()
                                // comments: everybody reads, any logged-in user writes, editors/admins moderate
                                .requestMatchers(HttpMethod.GET, "/api/v1/news/*/comments").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/news/*/comments").authenticated()
                                .requestMatchers(HttpMethod.PUT, "/api/v1/news/*/comments/enable",
                                        "/api/v1/news/*/comments/disable").hasAnyRole("EDITOR", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/v1/comments").hasAnyRole("EDITOR", "ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/v1/comments/*/hide",
                                        "/api/v1/comments/*/unhide").hasAnyRole("EDITOR", "ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/comments/*").authenticated()
                                // editorial workflow: the author submits, the editor reviews, only the admin restores
                                .requestMatchers(HttpMethod.POST, "/api/v1/news/*/submit").hasAnyRole("AUTHOR", "ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/v1/news/*/publish", "/api/v1/news/*/reject",
                                        "/api/v1/news/*/archive").hasAnyRole("EDITOR", "ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/v1/news/*/restore").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/v1/news/review").hasAnyRole("EDITOR", "ADMIN")
                                // an editor also reads and edits articles (NewsService limits what each role may do)
                                .requestMatchers("/api/v1/news/**").hasAnyRole("AUTHOR", "EDITOR", "ADMIN")
                                .anyRequest().authenticated()
                )
                .userDetailsService(appUserService)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(customAccessDeniesHandler)
                        .authenticationEntryPoint(jwtAuthEntrypoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
