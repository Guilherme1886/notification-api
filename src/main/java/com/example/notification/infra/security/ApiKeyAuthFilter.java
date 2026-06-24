package com.example.notification.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica chamadas internas por meio do header {@code X-Internal-Api-Key}.
 *
 * <p>Todas as requisições são interceptadas, exceto {@code /actuator/**}. Quando o
 * header está ausente ou difere da chave configurada, a resposta é 401. Quando a
 * chave é válida, a requisição segue autenticada no contexto de segurança.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final String configuredApiKey;

    public ApiKeyAuthFilter(@Value("${internal.api.key}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey == null || !configuredApiKey.equals(providedKey)) {
            unauthorized(response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                "internal-client", null, AuthorityUtils.createAuthorityList("ROLE_INTERNAL"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
    }
}
