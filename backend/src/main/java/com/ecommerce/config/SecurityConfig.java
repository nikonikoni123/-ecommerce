package com.ecommerce.config;

import com.ecommerce.common.ApiError;
import com.ecommerce.security.JwtAuthFilter;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
// Spring Boot 4 usa Jackson 3, cuyo paquete es tools.jackson (no com.fasterxml).
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {

    private final AppProperties properties;

    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                           ObjectMapper objectMapper) throws Exception {
        // Con la sesion en cookies, el navegador las adjunta solo a cada peticion, asi que CSRF si
        // aplica: sin esto, otro sitio podria provocar acciones en nombre del usuario. El token va
        // en una cookie legible (XSRF-TOKEN) que el cliente reenvia en la cabecera X-XSRF-TOKEN;
        // como un sitio ajeno no puede leer esa cookie, no puede falsificar la cabecera.
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Se opta por el token en claro (sin cifrado BREACH) para que el valor de la cookie sea el
        // mismo que el cliente debe enviar, y ademas se genere de forma temprana en cada peticion.
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        // El login y el registro no tienen sesion previa que proteger, y son el
                        // punto de entrada que aun no dispone del token.
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/login/2fa",
                                "/api/auth/register/**", "/api/auth/verify/**",
                                "/api/auth/password/**"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, objectMapper, 401, "UNAUTHENTICATED",
                                        "Necesitas iniciar sesion para acceder a este recurso."))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeError(response, objectMapper, 403, "FORBIDDEN",
                                        "No tienes permiso para realizar esta accion.")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper mapper,
                            int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), ApiError.of(code, message));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.frontendUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
