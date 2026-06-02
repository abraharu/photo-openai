package dev.photoopenai.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class TelegramModels {
    private TelegramModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramResponse<T>(boolean ok, T result, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(
            @JsonProperty("update_id") long updateId,
            Message message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") long messageId,
            Chat chat,
            String text,
            String caption,
            List<PhotoSize> photo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhotoSize(
            @JsonProperty("file_id") String fileId,
            @JsonProperty("file_unique_id") String fileUniqueId,
            int width,
            int height,
            @JsonProperty("file_size") Integer fileSize
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileInfo(
            @JsonProperty("file_id") String fileId,
            @JsonProperty("file_unique_id") String fileUniqueId,
            @JsonProperty("file_size") Integer fileSize,
            @JsonProperty("file_path") String filePath
    ) {
    }
}
