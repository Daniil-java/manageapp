package com.kuklin.manageapp.bots.channelposter.components.source;

import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import org.jsoup.HttpStatusException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PixabayClientTest {

    private final SourceHttpClient http = mock(SourceHttpClient.class);
    private final ChannelPosterBotKeyComponent keys = mock(ChannelPosterBotKeyComponent.class);
    private final PixabayClient client = new PixabayClient(http, keys);

    @Test
    void searchesFoodPhotosInRussianAndParsesHits() throws IOException {
        when(keys.getPixabayApiKey()).thenReturn("k1");
        when(http.get(anyString(), anyString(), anyMap())).thenReturn("""
                {"total": 2, "totalHits": 2, "hits": [
                  {"id": 11, "pageURL": "https://pixabay.com/photos/pasta-11/", "user": "Anna",
                   "largeImageURL": "https://pixabay.com/get/11_1280.jpg", "webformatURL": "w"},
                  {"id": 12, "pageURL": "u", "user": "B"}
                ]}""");

        assertThat(client.search("паста карбонара")).containsExactly(new PixabayClient.StockPhoto(
                11L, "https://pixabay.com/get/11_1280.jpg", "https://pixabay.com/photos/pasta-11/", "Anna"));

        verify(http).get(eq(PixabayClient.SEARCH_URL + "?key=k1"
                        + "&q=%D0%BF%D0%B0%D1%81%D1%82%D0%B0+%D0%BA%D0%B0%D1%80%D0%B1%D0%BE%D0%BD%D0%B0%D1%80%D0%B0"
                        + "&lang=ru&image_type=photo&orientation=horizontal&category=food&safesearch=true&per_page=20"),
                anyString(), eq(Map.of()));
    }

    @Test
    void searchesByKeywordsInEnglishWithoutCategory() throws IOException {
        when(keys.getPixabayApiKey()).thenReturn("k1");
        when(http.get(anyString(), anyString(), anyMap())).thenReturn("{\"hits\": []}");

        assertThat(client.searchByKeywords("oatmeal berries")).isEmpty();

        verify(http).get(eq(PixabayClient.SEARCH_URL + "?key=k1&q=oatmeal+berries"
                        + "&lang=en&image_type=photo&orientation=horizontal&safesearch=true&per_page=20"),
                anyString(), eq(Map.of()));
    }

    @Test
    void errorDoesNotLeakKey() throws IOException {
        when(keys.getPixabayApiKey()).thenReturn("secret-key");
        when(http.get(anyString(), anyString(), anyMap()))
                .thenThrow(new HttpStatusException("HTTP error", 429, "https://pixabay.com/api/?key=secret-key&q=x"));

        assertThatThrownBy(() -> client.search("x"))
                .isInstanceOf(IOException.class)
                .hasMessage("Pixabay: HTTP 429");
    }

    @Test
    void noHits() throws IOException {
        assertThat(client.parse("{\"hits\": []}")).isEmpty();
        assertThat(client.parse("{}")).isEmpty();
    }
}
