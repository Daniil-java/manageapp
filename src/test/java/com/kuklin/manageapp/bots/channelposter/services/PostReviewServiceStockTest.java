package com.kuklin.manageapp.bots.channelposter.services;

import com.kuklin.manageapp.bots.channelposter.components.ArticleContentGenerator;
import com.kuklin.manageapp.bots.channelposter.components.PostPublisher;
import com.kuklin.manageapp.bots.channelposter.components.source.PixabayClient;
import com.kuklin.manageapp.bots.channelposter.configurations.ChannelPosterProperties;
import com.kuklin.manageapp.bots.channelposter.entities.PostImage;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Картинка поста со стока: по умолчанию фото с Pixabay (точный запрос, за ним запасной),
 * «🔁 Другое фото» — следующее из выдачи, фото других постов не повторяются.
 */
class PostReviewServiceStockTest {

    private static final long ADMIN = 1L;
    private static final long POST_ID = 5L;

    private final ChannelPosterTelegramBot bot = mock(ChannelPosterTelegramBot.class);
    private final ChannelPosterBotKeyComponent keys = mock(ChannelPosterBotKeyComponent.class);
    private final ArticleContentGenerator contentGenerator = mock(ArticleContentGenerator.class);
    private final PostImageService postImageService = mock(PostImageService.class);
    private final PostQueueService postQueueService = mock(PostQueueService.class);
    private final PixabayClient stock = mock(PixabayClient.class);
    private final PostReviewService service = new PostReviewService(
            bot, keys, contentGenerator, postImageService, postQueueService, mock(PostPublisher.class),
            new ChannelPosterProperties(), mock(TopicCategoryService.class), mock(ChannelPollService.class), stock);

    @BeforeEach
    void setUp() throws IOException {
        when(keys.hasPixabayKey()).thenReturn(true);
        when(stock.searchByKeywords("oatmeal berries")).thenReturn(List.of(photo(10), photo(11)));
        // запасной запрос: 11 уже есть в точной выдаче — второй раз не попадает
        when(stock.searchByKeywords("breakfast")).thenReturn(List.of(photo(11), photo(20), photo(21)));
        when(postQueueService.findStockPhotoIdsOfOtherPosts(anyCollection(), anyLong())).thenReturn(Set.of());
        when(postImageService.saveNewImage(any(), any(), anyString(), anyLong()))
                .thenAnswer(inv -> new PostImage().setTgFileId(inv.getArgument(2)));
        when(postQueueService.markReview(any())).thenAnswer(inv -> inv.getArgument(0));
        when(bot.sendPhotoByUrl(eq(ADMIN), anyString()))
                .thenAnswer(inv -> sentPhoto("file-" + inv.getArgument(1)));
    }

    @Test
    void newPostGetsFirstStockPhotoWithAuthorInsteadOfGeneration() throws IOException {
        PostQueue post = post(null);

        service.prepareAndSend(post, List.of(ADMIN));

        ArgumentCaptor<PostImage> image = ArgumentCaptor.forClass(PostImage.class);
        verify(postImageService).save(image.capture());
        assertThat(image.getValue().getTgFileId()).isEqualTo("file-img10");
        assertThat(image.getValue().getStockAuthor()).isEqualTo("author10");
        assertThat(image.getValue().getStockPageUrl()).isEqualTo("page10");
        assertThat(post.getStockPhotoId()).isEqualTo(10L);
        verify(contentGenerator, never()).generateImage(any());
    }

    @Test
    void broadQueryPhotosGoAfterPreciseWithoutDuplicates() throws IOException {
        assertThat(service.findStockPhotos(post(null)))
                .extracting(PixabayClient.StockPhoto::id)
                .containsExactly(10L, 11L, 20L, 21L);
    }

    @Test
    void photosOfOtherPostsAreSkipped() throws IOException {
        when(postQueueService.findStockPhotoIdsOfOtherPosts(anyCollection(), eq(POST_ID))).thenReturn(Set.of(10L, 20L));

        assertThat(service.findStockPhotos(post(null)))
                .extracting(PixabayClient.StockPhoto::id)
                .containsExactly(11L, 21L);
    }

    @Test
    void nextPhotoMovesIntoBroadResultsAndSkipsOneTelegramRejected() {
        PostQueue post = post(11L);
        when(bot.sendPhotoByUrl(ADMIN, "img20")).thenReturn(null);

        assertThat(service.attachNextStockPhoto(post, ADMIN)).isTrue();

        assertThat(post.getStockPhotoId()).isEqualTo(21L);
        verify(postImageService).saveNewImage(PostImage.ImageSource.STOCK, PostImage.ImageStatus.READY,
                "file-img21", POST_ID);
    }

    @Test
    void noMorePhotosKeepsCurrentImage() {
        PostQueue post = post(21L);

        assertThat(service.attachNextStockPhoto(post, ADMIN)).isFalse();

        assertThat(post.getStockPhotoId()).isEqualTo(21L);
        verify(postImageService, never()).deletePostImageByPostQueueId(anyLong());
        verify(bot, never()).sendPhotoByUrl(anyLong(), anyString());
    }

    @Test
    void removingImageReleasesStockPhoto() {
        PostQueue post = post(10L);

        service.removeImage(post);

        verify(postImageService).deletePostImageByPostQueueId(POST_ID);
        assertThat(post.getStockPhotoId()).isNull();
        verify(postQueueService).save(post);
    }

    @Test
    void withoutPixabayKeyImageIsGeneratedAsBefore() throws IOException {
        when(keys.hasPixabayKey()).thenReturn(false);

        service.prepareAndSend(post(null), List.of(ADMIN));

        verify(contentGenerator).generateImage(any());
        verify(stock, never()).searchByKeywords(anyString());
    }

    private static PostQueue post(Long stockPhotoId) {
        PostQueue post = new PostQueue().setImageQuery("oatmeal berries").setImageQueryBroad("breakfast")
                .setStockPhotoId(stockPhotoId);
        post.setId(POST_ID);
        return post;
    }

    private static PixabayClient.StockPhoto photo(long id) {
        return new PixabayClient.StockPhoto(id, "img" + id, "page" + id, "author" + id);
    }

    private static Message sentPhoto(String fileId) {
        PhotoSize size = new PhotoSize();
        size.setFileId(fileId);
        Message message = new Message();
        message.setMessageId(100);
        message.setPhoto(List.of(size));
        return message;
    }
}
