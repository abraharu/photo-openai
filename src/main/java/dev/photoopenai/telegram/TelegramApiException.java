package dev.photoopenai.telegram;

public class TelegramApiException extends RuntimeException {
    public TelegramApiException(String message) {
        super(message);
    }
}
