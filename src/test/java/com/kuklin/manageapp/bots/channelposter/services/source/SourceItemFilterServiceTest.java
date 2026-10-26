package com.kuklin.manageapp.bots.channelposter.services.source;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceItemFilterServiceTest {

    private final SourceItemFilterService service =
            new SourceItemFilterService(null, null, null, null, null, null);

    @Test
    void parsesItemsObject() throws Exception {
        Map<Long, SourceItemFilterService.Verdict> verdicts = service.parseVerdicts("""
                {"items":[{"id":12,"score":8,"reason":"практичное РКИ"},{"id":13,"score":15,"reason":"x"},{"id":14,"score":-2}]}
                """);

        assertThat(verdicts).hasSize(3);
        assertThat(verdicts.get(12L)).isEqualTo(new SourceItemFilterService.Verdict(8, "практичное РКИ"));
        // оценка зажимается в 0–10
        assertThat(verdicts.get(13L).score()).isEqualTo(10);
        assertThat(verdicts.get(14L).score()).isZero();
    }

    @Test
    void toleratesMarkdownFenceAndBareArray() throws Exception {
        assertThat(service.parseVerdicts("```json\n[{\"id\":1,\"score\":7,\"reason\":\"ok\"}]\n```"))
                .containsKey(1L);
    }

    @Test
    void brokenResponseThrowsSoItemsStayNew() {
        assertThatThrownBy(() -> service.parseVerdicts("10423 10425")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> service.parseVerdicts("")).isInstanceOf(IllegalArgumentException.class);
    }
}
