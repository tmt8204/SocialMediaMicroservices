package com.socialmedia.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import com.socialmedia.auth.entities.TokenType;
import com.socialmedia.auth.repositories.UserTokenRepository;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserTokenRepository userTokenRepository;

    public JwtAuthFilter(JwtService jwtService, UserTokenRepository userTokenRepository) {
        this.jwtService = jwtService;
        this.userTokenRepository = userTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Extract JWT from Authorization header
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate JWT and set authentication context
        String token = authHeader.substring(7);

        boolean isActiveAccessToken = userTokenRepository
            .findByTokenAndTokenTypeAndRevokedFalse(token, TokenType.ACCESS)
            .isPresent();

        if (jwtService.isTokenValid(token)
            && jwtService.isAccessToken(token)
            && isActiveAccessToken
            && SecurityContextHolder.getContext().getAuthentication() == null) {
            String role = jwtService.extractClaims(token).get("role", String.class);

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    null, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
