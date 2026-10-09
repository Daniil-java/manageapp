package com.kuklin.manageapp.common.configurations;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BotSchedulerAspectTest {

    @Test
    void disabledBotSchedulerIsSkipped() {
        // поля читаем у исходного объекта: у CGLIB-прокси свои, пустые
        HhJobs target = new HhJobs();
        HhJobs jobs = proxy(target, Set.of(BotIdentifier.HH_BOT));

        jobs.scheduled();

        assertThat(target.calls.get()).isZero();
    }

    @Test
    void enabledBotSchedulerRuns() {
        // поля читаем у исходного объекта: у CGLIB-прокси свои, пустые
        HhJobs target = new HhJobs();
        HhJobs jobs = proxy(target, Set.of(BotIdentifier.KWORK));

        jobs.scheduled();

        assertThat(target.calls.get()).isEqualTo(1);
    }

    @Test
    void notScheduledMethodOfDisabledBotRuns() {
        // поля читаем у исходного объекта: у CGLIB-прокси свои, пустые
        HhJobs target = new HhJobs();
        HhJobs jobs = proxy(target, Set.of(BotIdentifier.HH_BOT));

        jobs.manual();

        assertThat(target.calls.get()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void disabledListFromProperty() {
        // Так Spring превращает bots.disabled в Set<BotIdentifier>
        DefaultConversionService conversion = new DefaultConversionService();
        TypeDescriptor target = TypeDescriptor.collection(Set.class, TypeDescriptor.valueOf(BotIdentifier.class));

        EnabledBots none = new EnabledBots((Set<BotIdentifier>) conversion.convert("", TypeDescriptor.valueOf(String.class), target));
        EnabledBots two = new EnabledBots((Set<BotIdentifier>) conversion.convert("HH_BOT, KWORK", TypeDescriptor.valueOf(String.class), target));

        assertThat(none.isEnabled(BotIdentifier.HH_BOT)).isTrue();
        assertThat(two.isEnabled(BotIdentifier.HH_BOT)).isFalse();
        assertThat(two.isEnabled(BotIdentifier.KWORK)).isFalse();
        assertThat(two.isEnabled(BotIdentifier.CALORIE_BOT)).isTrue();
    }

    private static <T> T proxy(T target, Set<BotIdentifier> disabled) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new BotSchedulerAspect(new EnabledBots(disabled)));
        return factory.getProxy();
    }

    @BotScheduler(BotIdentifier.HH_BOT)
    static class HhJobs {
        final AtomicInteger calls = new AtomicInteger();

        @Scheduled(cron = "0 0 * * * *")
        public void scheduled() {
            calls.incrementAndGet();
        }

        public void manual() {
            calls.incrementAndGet();
        }
    }
}
