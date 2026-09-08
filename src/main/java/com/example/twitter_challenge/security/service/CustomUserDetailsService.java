package com.example.twitter_challenge.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CustomUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return null;
    }
}
UserRepository üzerinden userName ile kullanıcıyı bul.
Bulamazsan UserNotFoundException fırlat.
Bulduğun User nesnesini new CustomUserDetails(user) ile dön.