package com.example.autoposttotelegram.repository;

import com.example.autoposttotelegram.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findByChannelId(String channelId);
    Optional<Channel> findByUserId(Long userId);
}