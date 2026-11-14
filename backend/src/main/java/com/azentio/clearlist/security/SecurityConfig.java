package com.azentio.clearlist.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless HTTP Basic API. CSRF protection is disabled because no session cookie is issued, so there is
 * no ambient browser credential for a cross-site request to ride on.
 *
 * <p>Endpoint-level rules live here; the state-dependent disposition rules (which role may move a hit
 * from which status) live in {@code HitTransitionPolicy}, because they need the hit's current status.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/watchlists/versions").hasRole(Role.SUPERVISOR.name())
                        .requestMatchers("/api/**").hasAnyRole(Role.ANALYST.name(), Role.SUPERVISOR.name())
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(errorHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // bcrypt by default
    }

    /** Two demo users; passwords come from the environment and are hashed at startup. */
    @Bean
    public UserDetailsService userDetailsService(UserCredentialsProperties credentials, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("analyst").password(encoder.encode(credentials.analystPassword()))
                        .roles(Role.ANALYST.name()).build(),
                User.withUsername("supervisor").password(encoder.encode(credentials.supervisorPassword()))
                        .roles(Role.SUPERVISOR.name()).build());
    }
}
