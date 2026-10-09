package com.kuklin.manageapp.bots.channelposter.components;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostPublisherTest {

    @Test
    void appendsEscapedSourceLink() {
        PostQueue post = new PostQueue()
                .setTextContent("<b>Белок</b> на завтрак\n")
                .setSourceUrl("https://example.com/a?x=1&y=\"2\"")
                .setSourceName("R&D <Lab>");

        assertThat(PostPublisher.renderText(post)).isEqualTo(
                "<b>Белок</b> на завтрак\n\n"
                        + "<a href=\"https://example.com/a?x=1&amp;y=&quot;2&quot;\">Источник: R&amp;D &lt;Lab&gt;</a>");
    }

    @Test
    void manualPostWithoutSource() {
        assertThat(PostPublisher.renderText(new PostQueue().setTextContent("Текст"))).isEqualTo("Текст");
    }
}
