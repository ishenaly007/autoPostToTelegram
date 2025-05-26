package com.example.autoposttotelegram.repository;

import com.example.autoposttotelegram.model.PostMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface PostMessageRepository extends JpaRepository<PostMessage, Long> {
    List<PostMessage> findByPublishTimeBeforeAndPublishedFalse(LocalDateTime publishTime);
    List<PostMessage> findByChannelIdAndPublishedFalse(Long channelId);
}