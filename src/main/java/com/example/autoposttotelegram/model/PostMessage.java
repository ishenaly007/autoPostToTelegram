package com.example.autoposttotelegram.model;

import lombok.Data;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Entity
@Data
public class PostMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "channel_id")
    @JsonIgnore // Игнорируем канал при сериализации
    private Channel channel;

    private String content;
    private LocalDateTime publishTime;
    private boolean published = false;
    private String mediaId;
    private String mediaType;
}