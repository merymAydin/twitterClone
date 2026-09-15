package com.example.twitter_challenge.security.filter;

import com.example.twitter_challenge.security.service.CustomUserDetailsService;
import com.example.twitter_challenge.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private JwtService jwtService;
    private CustomUserDetailsService customUserDetailsService;
    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService customUserDetailsService) {
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if(authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring("Bearer ".length());
                String username = jwtService.extractUsername(token);
                UserDetails userDetails =customUserDetailsService.loadUserByUsername(username);
                if(!userDetails.isEnabled()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username,null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

        }
        filterChain.doFilter(request, response);
    }
}

