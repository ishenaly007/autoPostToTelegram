package com.example.autoposttotelegram.model;

import lombok.Data;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String telegramUsername; // Изменено с telegramId на telegramUsername

    private String password;

    @OneToMany(mappedBy = "user")
    @JsonIgnore // Игнорируем каналы при сериализации
    private List<Channel> channels;
}