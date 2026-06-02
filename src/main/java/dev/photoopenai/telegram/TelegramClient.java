package dev.photoopenai.telegram;

import dev.photoopenai.config.TelegramProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static dev.photoopenai.telegram.TelegramModels.FileInfo;
import static dev.photoopenai.telegram.TelegramModels.TelegramResponse;
import static dev.photoopenai.telegram.TelegramModels.Update;

@Component
public class TelegramClient {
    private final TelegramProperties properties;
    private final WebClient apiClient;
    private final WebClient fileClient;

    public TelegramClient(TelegramProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.apiClient = webClientBuilder
                .clone()
                .baseUrl("https://api.telegram.org/bot" + properties.token())
                .build();
        this.fileClient = webClientBuilder
                .clone()
                .baseUrl("https://api.telegram.org/file/bot" + properties.token())
                .build();
    }

    public Mono<List<Update>> getUpdates(long offset) {
        return apiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getUpdates")
                        .queryParam("offset", offset)
                        .queryParam("timeout", properties.longPollTimeout().toSeconds())
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramResponse<List<Update>>>() {
                })
                .map(this::requireOk)
                .timeout(properties.longPollTimeout().plus(Duration.ofSeconds(10)));
    }

    public Mono<FileInfo> getFile(String fileId) {
        return apiClient.get()
                .uri(uriBuilder -> uriBuilder.path("/getFile").queryParam("file_id", fileId).build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramResponse<FileInfo>>() {
                })
                .map(this::requireOk);
    }

    public Mono<byte[]> downloadFile(String filePath) {
        return fileClient.get()
                .uri("/" + filePath)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    public Mono<Void> sendMessage(long chatId, String text) {
        return apiClient.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SendMessageRequest(chatId, text))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramResponse<Object>>() {
                })
                .map(this::requireOk)
                .then();
    }

    public Mono<Void> sendChatAction(long chatId, String action) {
        return apiClient.post()
                .uri("/sendChatAction")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SendChatActionRequest(chatId, action))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramResponse<Object>>() {
                })
                .map(this::requireOk)
                .then();
    }

    public Mono<Void> sendPhoto(long chatId, byte[] imageBytes, String filename, String caption) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("chat_id", Long.toString(chatId));
        body.part("caption", caption);
        body.part("photo", new NamedByteArrayResource(imageBytes, filename))
                .filename(filename)
                .contentType(MediaType.IMAGE_PNG);

        return apiClient.post()
                .uri("/sendPhoto")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TelegramResponse<Object>>() {
                })
                .map(this::requireOk)
                .then();
    }

    private <T> T requireOk(TelegramResponse<T> response) {
        if (!response.ok()) {
            throw new TelegramApiException(response.description());
        }
        return response.result();
    }

    private record SendMessageRequest(@com.fasterxml.jackson.annotation.JsonProperty("chat_id") long chatId, String text) {
    }

    private record SendChatActionRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("chat_id") long chatId,
            String action
    ) {
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
