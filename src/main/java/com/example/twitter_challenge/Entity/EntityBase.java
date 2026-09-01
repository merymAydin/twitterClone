package com.example.twitter_challenge.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.Date;

@Data
public class EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status")
    private Integer status;

    private Date createdAt;

    private Date updatedAt;

    private Integer updatedBy;

    private Integer createdBy;

    private Boolean isDeleted;

}
