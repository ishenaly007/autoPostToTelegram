package com.example.autoposttotelegram.model;

import lombok.Data;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Data
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String channelId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore // Игнорируем пользователя при сериализации
    private User user;
}