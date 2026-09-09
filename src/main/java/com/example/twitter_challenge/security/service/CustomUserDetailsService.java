package com.example.twitter_challenge.security.service;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.security.config.CustomUserDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {


        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException(""));



        return new CustomUserDetails(user);
    }
}
