package com.example.twitter_challenge.Repository;


import com.example.twitter_challenge.Entity.Comment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface CommentRepository extends JpaRepository<Comment,Long> {

}
