package com.socialmedia.admin.entities.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "roles")
public class RoleDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String roleName;
}
