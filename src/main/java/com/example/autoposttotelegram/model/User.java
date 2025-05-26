package com.example.autoposttotelegram.model;

import lombok.Data;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;

    private String password;

    @OneToMany(mappedBy = "user")
    private List<Channel> channels;
}