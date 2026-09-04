package com.example.twitter_challenge.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

@MappedSuperclass
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
