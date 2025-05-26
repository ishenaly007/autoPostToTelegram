package com.example.autoposttotelegram.model;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Data
public class PostMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "channel_id")
    private Channel channel;

    private String content;
    private LocalDateTime publishTime;
    private boolean published = false;
    private String mediaId;
    private String mediaType;
}