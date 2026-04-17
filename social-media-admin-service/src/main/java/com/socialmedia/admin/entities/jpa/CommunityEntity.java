package com.socialmedia.admin.entities.jpa;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "communities")
public class CommunityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    private String privacy; // PUBLIC or PRIVATE

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "member_count")
    private int memberCount;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
