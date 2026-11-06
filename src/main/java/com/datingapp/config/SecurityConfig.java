package com.datingapp.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, WebSocketProperties.class})
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;

    /** H2 console is only reachable in the dev profile (and only when it is enabled). */
    @Bean
    @Order(1)
    @Profile("dev")
    SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API: no cookies, so CSRF protection is not applicable.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // Static test UI (src/main/resources/static). It holds no data; every API call it
                        // makes still needs a bearer token.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.js", "/app.css").permitAll()
                        // The WebSocket handshake is authenticated by JwtHandshakeInterceptor.
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(signingKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    private static SecretKey signingKey(JwtProperties props) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(props.secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret must be base64-encoded", e);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret must decode to at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
