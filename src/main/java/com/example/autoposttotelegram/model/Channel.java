package com.example.autoposttotelegram.model;

import lombok.Data;
import jakarta.persistence.*;

@Entity
@Data
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String channelId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}