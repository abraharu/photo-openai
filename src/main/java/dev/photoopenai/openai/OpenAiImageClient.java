package dev.photoopenai.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.photoopenai.config.OpenAiProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;

@Component
public class OpenAiImageClient {
    private static final int MAX_IN_MEMORY_SIZE = 25 * 1024 * 1024;

    private final OpenAiProperties properties;
    private final WebClient openAiClient;

    public OpenAiImageClient(OpenAiProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.openAiClient = webClientBuilder
                .clone()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }

    public Mono<byte[]> editImage(byte[] imageBytes, String contentType, String prompt) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("model", properties.imageModel());
        body.part("prompt", prompt);
        body.part("size", properties.imageSize());
        body.part("input_fidelity", properties.inputFidelity());
        body.part("image", new NamedByteArrayResource(imageBytes, "telegram-input"))
                .filename("telegram-input." + extensionFrom(contentType))
                .contentType(MediaType.parseMediaType(contentType));

        return openAiClient.post()
                .uri("/images/edits")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty(response.statusCode().toString())
                                .flatMap(bodyText -> Mono.error(new OpenAiImageException(
                                        "OpenAI " + response.statusCode().value() + ": " + bodyText
                                )));
                    }
                    return response.bodyToMono(ImageEditResponse.class);
                })
                .flatMap(this::extractImageBytes);
    }

    private Mono<byte[]> extractImageBytes(ImageEditResponse response) {
        if (response.data() == null || response.data().isEmpty()) {
            return Mono.error(new OpenAiImageException("OpenAI returned no image data."));
        }

        ImageData first = response.data().getFirst();
        if (first.b64Json() != null && !first.b64Json().isBlank()) {
            return Mono.just(Base64.getDecoder().decode(first.b64Json()));
        }

        if (first.url() != null && !first.url().isBlank()) {
            return WebClient.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                    .baseUrl(first.url())
                    .build()
                    .get()
                    .retrieve()
                    .bodyToMono(byte[].class);
        }

        return Mono.error(new OpenAiImageException("OpenAI image response did not include b64_json or url."));
    }

    private String extensionFrom(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ImageEditResponse(List<ImageData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ImageData(
            @JsonProperty("b64_json") String b64Json,
            String url
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
