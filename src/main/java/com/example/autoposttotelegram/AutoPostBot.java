package com.example.autoposttotelegram;

import com.example.autoposttotelegram.model.Channel;
import com.example.autoposttotelegram.model.PostMessage;
import com.example.autoposttotelegram.model.User;
import com.example.autoposttotelegram.repository.ChannelRepository;
import com.example.autoposttotelegram.repository.PostMessageRepository;
import com.example.autoposttotelegram.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@EnableScheduling
public class AutoPostBot extends TelegramLongPollingBot {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostMessageRepository postMessageRepository;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Override
    public String getBotUsername() {
        return "AutoPostBot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("TELEGRAM_BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();
        String telegramUsername = update.getMessage().getFrom().getUserName() != null
                ? update.getMessage().getFrom().getUserName().replace("@", "")
                : null;

        // Поиск или создание пользователя по telegramId
        User user = userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramId);
                    newUser.setTelegramUsername(telegramUsername != null ? telegramUsername : "unknown_" + telegramId);
                    return userRepository.save(newUser);
                });

        // Обновляем username, если он изменился
        if (telegramUsername != null && !telegramUsername.equals(user.getTelegramUsername())) {
            user.setTelegramUsername(telegramUsername);
            userRepository.save(user);
        }

        // Проверка на отсутствие username, если требуется
        if (telegramUsername == null && update.getMessage().hasText()) {
            try {
                sendMessage(chatId, "Внимание: ваш Telegram username не установлен. Некоторые функции могут быть ограничены. Установите username в настройках Telegram.", getMainMenuKeyboard());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();

            try {
                // Обработка команд и кнопок
                switch (messageText) {
                    case "/start":
                    case "Вернуться в меню":
                        sendMainMenu(chatId);
                        break;
                    case "Добавить канал":
                        sendMessage(chatId, "Введите ID канала в формате: @ChannelName", getMainMenuKeyboard());
                        break;
                    case "Удалить канал":
                        handleDeleteChannel(update, user);
                        break;
                    case "Создать пост":
                        sendMessage(chatId, "Введите текст поста и дату публикации в формате: <текст> dd.MM.yyyy HH:mm\nИли отправьте медиа с подписью в таком же формате.", getMainMenuKeyboard());
                        break;
                    case "Установить пароль":
                        sendMessage(chatId, "Введите пароль для входа на сайт.", getMainMenuKeyboard());
                        break;
                    default:
                        if (messageText.startsWith("/addchannel") || messageText.startsWith("@")) {
                            handleAddChannel(update, user);
                        } else if (messageText.startsWith("/del")) {
                            handleDeleteChannel(update, user);
                        } else if (messageText.startsWith("/createpost")) {
                            handleCreatePost(update, user);
                        } else if (messageText.startsWith("/password")) {
                            handleSetPassword(update, user);
                        } else {
                            if (isWaitingForPost(chatId, user)) {
                                handleCreatePostFromText(update, user, messageText);
                            } else if (isWaitingForPassword(chatId, user)) {
                                handleSetPasswordFromText(update, user, messageText);
                            } else {
                                sendMessage(chatId, "Неизвестная команда. Используйте кнопки меню или команду /start.", getMainMenuKeyboard());
                            }
                        }
                }
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else if (update.hasMessage()) {
            if (update.getMessage().hasPhoto()) {
                handleMediaMessage(update, "photo", user);
            } else if (update.getMessage().hasVideo()) {
                handleMediaMessage(update, "video", user);
            } else if (update.getMessage().hasSticker()) {
                handleMediaMessage(update, "sticker", user);
            } else if (update.getMessage().hasAudio()) {
                handleMediaMessage(update, "audio", user);
            } else if (update.getMessage().hasDocument()) {
                handleMediaMessage(update, "document", user);
            } else if (update.getMessage().hasAnimation()) {
                handleMediaMessage(update, "animation", user);
            } else if (update.getMessage().hasVoice()) {
                handleMediaMessage(update, "voice", user);
            } else if (update.getMessage().hasVideoNote()) {
                handleMediaMessage(update, "video_note", user);
            }
        }
    }

    private void sendMainMenu(Long chatId) throws TelegramApiException {
        String welcomeMessage = "Привет! Я бот для автоматизации постинга в Telegram-каналы.\n\n" +
                                "Что я умею:\n" +
                                "*• Добавить канал* — привяжите канал, где вы админ.\n" +
                                "*• Удалить канал* — отвяжите текущий канал.\n" +
                                "*• Создать пост* — запланируйте текстовый или медиа-пост (фото, видео и т.д.) с указанием даты и времени (dd.MM.yyyy HH:mm).\n" +
                                "*• Установить пароль* — настройте пароль для доступа к веб-интерфейсу.\n\n" +
                                "Выберите действие в меню ниже:";
        sendMessage(chatId, welcomeMessage, getMainMenuKeyboard(), ParseMode.MARKDOWN);
    }

    private ReplyKeyboardMarkup getMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("Добавить канал");
        row1.add("Удалить канал");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("Создать пост");
        row2.add("Установить пароль");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("Вернуться в меню");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private boolean isWaitingForPost(Long chatId, User user) {
        // Упрощённая логика, можно доработать с состоянием
        return true;
    }

    private boolean isWaitingForPassword(Long chatId, User user) {
        // Упрощённая логика, можно доработать с состоянием
        return true;
    }

    private void handleAddChannel(Update update, User user) throws TelegramApiException {
        String[] parts = update.getMessage().getText().split(" ");
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        if (parts.length < 2 && !update.getMessage().getText().startsWith("@")) {
            sendMessage(chatId, "Пожалуйста, укажите ID канала. Формат: @ChannelName", getMainMenuKeyboard());
            return;
        }

        String channelName = parts.length >= 2 ? parts[1] : update.getMessage().getText();

        List<ChatMember> admins = getChatAdministrators(channelName);
        boolean isAdmin = admins.stream()
                .anyMatch(admin -> admin.getUser().getId().equals(telegramId));

        if (!isAdmin) {
            sendMessage(chatId, "Вы должны быть администратором канала!", getMainMenuKeyboard());
            return;
        }

        Optional<Channel> existingChannel = channelRepository.findByUserId(user.getId());
        existingChannel.ifPresent(channelRepository::delete);

        Channel channel = new Channel();
        channel.setChannelId(channelName);
        channel.setUser(user);
        channelRepository.save(channel);
        sendMessage(chatId, "Канал " + channelName + " успешно добавлен! Предыдущий канал (если был) удалён.", getMainMenuKeyboard());
    }

    private void handleDeleteChannel(Update update, User user) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();

        Optional<Channel> channel = channelRepository.findByUserId(user.getId());
        if (channel.isPresent()) {
            channelRepository.delete(channel.get());
            sendMessage(chatId, "Канал успешно удалён!", getMainMenuKeyboard());
        } else {
            sendMessage(chatId, "У вас нет зарегистрированного канала!", getMainMenuKeyboard());
        }
    }

    private void handleSetPassword(Update update, User user) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        String[] parts = update.getMessage().getText().split(" ", 2);

        if (parts.length < 2) {
            sendMessage(chatId, "Пожалуйста, укажите пароль. Формат: /password <пароль>", getMainMenuKeyboard());
            return;
        }

        String password = parts[1];
        handleSetPasswordFromText(update, user, password);
    }

    private void handleSetPasswordFromText(Update update, User user, String password) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();

        user.setPassword(password);
        userRepository.save(user);
        sendMessage(chatId, "Пароль успешно установлен! Используйте его для входа на сайт.", getMainMenuKeyboard());
    }

    private void handleCreatePost(Update update, User user) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();

        if (messageText.length() < 16) {
            sendMessage(chatId, "Формат: /createpost <текст> dd.MM.yyyy HH:mm", getMainMenuKeyboard());
            return;
        }

        String dateTimeStr = messageText.substring(messageText.length() - 16);
        String content = messageText.substring("/createpost".length(), messageText.length() - 16).trim();
        handleCreatePostFromText(update, user, content + " " + dateTimeStr);
    }

    private void handleCreatePostFromText(Update update, User user, String messageText) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();

        if (messageText.length() < 16) {
            sendMessage(chatId, "Формат: <текст> dd.MM.yyyy HH:mm", getMainMenuKeyboard());
            return;
        }

        String dateTimeStr = messageText.substring(messageText.length() - 16);
        String content = messageText.substring(0, messageText.length() - 16).trim();

        try {
            LocalDateTime publishTime = LocalDateTime.parse(dateTimeStr, formatter);

            Optional<Channel> channel = channelRepository.findByUserId(user.getId());
            if (channel.isEmpty()) {
                sendMessage(chatId, "У вас нет зарегистрированного канала! Добавьте канал с помощью кнопки 'Добавить канал'.", getMainMenuKeyboard());
                return;
            }

            PostMessage post = new PostMessage();
            post.setChannel(channel.get());
            post.setContent(content);
            post.setPublishTime(publishTime);
            postMessageRepository.save(post);

            sendMessage(chatId, "Пост успешно создан и будет опубликован " + dateTimeStr, getMainMenuKeyboard());
        } catch (DateTimeParseException e) {
            sendMessage(chatId, "Неверный формат даты! Используйте: dd.MM.yyyy HH:mm в конце текста", getMainMenuKeyboard());
        }
    }

    private void handleMediaMessage(Update update, String mediaType, User user) {
        Long chatId = update.getMessage().getChatId();
        String fileId;
        String caption = update.getMessage().getCaption() != null ? update.getMessage().getCaption() : "";

        if (caption.length() < 16) {
            try {
                sendMessage(chatId, "Подпись должна заканчиваться датой и временем в формате: dd.MM.yyyy HH:mm", getMainMenuKeyboard());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        String dateTimeStr = caption.substring(caption.length() - 16);
        String content = caption.substring(0, caption.length() - 16).trim();

        try {
            LocalDateTime publishTime = LocalDateTime.parse(dateTimeStr, formatter);

            Optional<Channel> channel = channelRepository.findByUserId(user.getId());
            if (channel.isEmpty()) {
                try {
                    sendMessage(chatId, "У вас нет зарегистрированного канала! Добавьте канал с помощью кнопки 'Добавить канал'.", getMainMenuKeyboard());
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            switch (mediaType) {
                case "photo":
                    fileId = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId();
                    break;
                case "video":
                    fileId = update.getMessage().getVideo().getFileId();
                    break;
                case "sticker":
                    fileId = update.getMessage().getSticker().getFileId();
                    break;
                case "audio":
                    fileId = update.getMessage().getAudio().getFileId();
                    break;
                case "document":
                    fileId = update.getMessage().getDocument().getFileId();
                    break;
                case "animation":
                    fileId = update.getMessage().getAnimation().getFileId();
                    break;
                case "voice":
                    fileId = update.getMessage().getVoice().getFileId();
                    break;
                case "video_note":
                    fileId = update.getMessage().getVideoNote().getFileId();
                    break;
                default:
                    return;
            }

            PostMessage post = new PostMessage();
            post.setChannel(channel.get());
            post.setContent(content);
            post.setPublishTime(publishTime);
            post.setMediaId(fileId);
            post.setMediaType(mediaType);
            postMessageRepository.save(post);

            try {
                sendMessage(chatId, "Медиа-пост (" + mediaType + ") успешно создан и будет опубликован " + dateTimeStr, getMainMenuKeyboard());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } catch (DateTimeParseException e) {
            try {
                sendMessage(chatId, "Неверный формат даты! Подпись должна заканчиваться: dd.MM.yyyy HH:mm", getMainMenuKeyboard());
            } catch (TelegramApiException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void publishScheduledPosts() {
        LocalDateTime now = LocalDateTime.now();
        List<PostMessage> posts = postMessageRepository.findByPublishTimeBeforeAndPublishedFalse(now);

        for (PostMessage post : posts) {
            try {
                if (post.getMediaId() != null) {
                    switch (post.getMediaType()) {
                        case "photo":
                            SendPhoto sendPhoto = new SendPhoto();
                            sendPhoto.setChatId(post.getChannel().getChannelId());
                            sendPhoto.setPhoto(new InputFile(post.getMediaId()));
                            sendPhoto.setCaption(post.getContent());
                            execute(sendPhoto);
                            break;
                        case "video":
                            SendVideo sendVideo = new SendVideo();
                            sendVideo.setChatId(post.getChannel().getChannelId());
                            sendVideo.setVideo(new InputFile(post.getMediaId()));
                            sendVideo.setCaption(post.getContent());
                            execute(sendVideo);
                            break;
                        case "sticker":
                            SendSticker sendSticker = new SendSticker();
                            sendSticker.setChatId(post.getChannel().getChannelId());
                            sendSticker.setSticker(new InputFile(post.getMediaId()));
                            execute(sendSticker);
                            break;
                        case "audio":
                            SendAudio sendAudio = new SendAudio();
                            sendAudio.setChatId(post.getChannel().getChannelId());
                            sendAudio.setAudio(new InputFile(post.getMediaId()));
                            sendAudio.setCaption(post.getContent());
                            execute(sendAudio);
                            break;
                        case "document":
                            SendDocument sendDocument = new SendDocument();
                            sendDocument.setChatId(post.getChannel().getChannelId());
                            sendDocument.setDocument(new InputFile(post.getMediaId()));
                            sendDocument.setCaption(post.getContent());
                            execute(sendDocument);
                            break;
                        case "animation":
                            SendAnimation sendAnimation = new SendAnimation();
                            sendAnimation.setChatId(post.getChannel().getChannelId());
                            sendAnimation.setAnimation(new InputFile(post.getMediaId()));
                            sendAnimation.setCaption(post.getContent());
                            execute(sendAnimation);
                            break;
                        case "voice":
                            SendVoice sendVoice = new SendVoice();
                            sendVoice.setChatId(post.getChannel().getChannelId());
                            sendVoice.setVoice(new InputFile(post.getMediaId()));
                            sendVoice.setCaption(post.getContent());
                            execute(sendVoice);
                            break;
                        case "video_note":
                            SendVideoNote sendVideoNote = new SendVideoNote();
                            sendVideoNote.setChatId(post.getChannel().getChannelId());
                            sendVideoNote.setVideoNote(new InputFile(post.getMediaId()));
                            execute(sendVideoNote);
                            break;
                    }
                } else {
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(post.getChannel().getChannelId());
                    sendMessage.setText(post.getContent());
                    execute(sendMessage);
                }
                post.setPublished(true);
                postMessageRepository.save(post);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private List<ChatMember> getChatAdministrators(String chatId) throws TelegramApiException {
        GetChatAdministrators getChatAdministrators = new GetChatAdministrators();
        getChatAdministrators.setChatId(chatId);
        return execute(getChatAdministrators);
    }

    private void sendMessage(Long chatId, String text, ReplyKeyboardMarkup keyboard) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        execute(message);
    }

    private void sendMessage(Long chatId, String text, ReplyKeyboardMarkup keyboard, String mode) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(mode);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        execute(message);
    }
}