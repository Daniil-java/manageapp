package com.kuklin.manageapp.bots.channelposter.configurations;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelPosterPropertiesTest {

    @Test
    void bindsValuesInYamlFormat() {
        Map<String, String> yaml = Map.of(
                "channelposter.pipeline-target", "20",
                "channelposter.posts-per-run", "3",
                "channelposter.max-backlog", "150",
                "channelposter.approve-score", "7",
                "channelposter.autopilot-min-score", "8",
                "channelposter.autopilot-after", "12h",
                "channelposter.max-item-age", "30d",
                "channelposter.review-ttl", "3d"
        );

        ChannelPosterProperties p = new Binder(new MapConfigurationPropertySource(yaml))
                .bind("channelposter", ChannelPosterProperties.class)
                .get();

        assertThat(p.getPipelineTarget()).isEqualTo(20);
        assertThat(p.getPostsPerRun()).isEqualTo(3);
        assertThat(p.getMaxBacklog()).isEqualTo(150);
        assertThat(p.getApproveScore()).isEqualTo(7);
        assertThat(p.getAutopilotMinScore()).isEqualTo(8);
        assertThat(p.getAutopilotAfter()).isEqualTo(Duration.ofHours(12));
        assertThat(p.getMaxItemAge()).isEqualTo(Duration.ofDays(30));
        assertThat(p.getReviewTtl()).isEqualTo(Duration.ofDays(3));
    }

    @Test
    void zeroTurnsAutopilotOff() {
        ChannelPosterProperties p = new Binder(new MapConfigurationPropertySource(
                Map.of("channelposter.autopilot-after", "0")))
                .bind("channelposter", ChannelPosterProperties.class)
                .get();

        assertThat(p.getAutopilotAfter()).isZero();
    }
}
