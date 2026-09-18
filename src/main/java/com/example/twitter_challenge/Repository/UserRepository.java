package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByUserName(String userName);
    Page<User> findByUserNameContaining(String userName, Pageable pageable);
}
