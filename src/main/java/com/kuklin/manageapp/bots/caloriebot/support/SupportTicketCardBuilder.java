package com.kuklin.manageapp.bots.caloriebot.support;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.report.pdf.ReportHtml;
import com.kuklin.manageapp.aiconversation.usage.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.aiconversation.usage.repositories.MetricsAiInteractionRecordRepository;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.repositories.PaymentRepository;
import com.kuklin.manageapp.payment.repositories.PricingPlanRepository;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Карточка заявки — первое сообщение темы в группе поддержки, чтобы не переспрашивать пользователя.
 * Сверху — то, что читаем глазами: кто, подписка, платежи, сбои ИИ, прошлые заявки. Внизу, в свёрнутой
 * цитате, — то, что копируем: id пользователя, charge id платежей, команда возврата. Группа — по-русски.
 */
@Component
@RequiredArgsConstructor
public class SupportTicketCardBuilder {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int PREVIEW_LENGTH = 60;
    // Ссылка на тему: t.me/c/<id группы без -100>/<id темы>
    private static final long SUPERGROUP_ID_OFFSET = 1_000_000_000_000L;

    private final SupportProperties properties;
    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final UserSubscriptionService userSubscriptionService;
    private final PricingPlanRepository pricingPlanRepository;
    private final PaymentRepository paymentRepository;
    private final MetricsAiInteractionRecordRepository aiRecordRepository;
    private final CalorieTexts texts;

    /** Название темы: «#12 💳 Имя @username», у закрытой — с ✅ в начале. До 128 символов (лимит Telegram). */
    public String buildTopicName(SupportTicket ticket, TelegramUser user) {
        String name = (ticket.getStatus() == SupportTicket.Status.CLOSED ? "✅ " : "")
                + "#" + ticket.getId() + " " + ticket.getCategory().getEmoji() + " " + buildUserName(user);
        return name.length() > 128 ? name.substring(0, 128) : name;
    }

    public String buildCard(SupportTicket ticket, TelegramUser user) {
        Long appUserId = user.getAppUserId();
        List<Payment> payments = paymentRepository.findTop3ByAppUserIdAndBotIdentifierAndStatusInOrderByCreatedAtDesc(
                appUserId, BotIdentifier.CALORIE_BOT,
                List.of(Payment.PaymentStatus.SUCCESS, Payment.PaymentStatus.REFUNDED, Payment.PaymentStatus.FAILED));

        List<String> blocks = new ArrayList<>();
        blocks.add(ticket.getCategory().getEmoji() + " <b>" + getCategoryLabel(ticket.getCategory())
                + "</b> · заявка #" + ticket.getId());
        blocks.add(String.join("\n",
                buildUserLine(user),
                buildSubscriptionLine(appUserId),
                buildPaymentsLine(payments),
                buildAiLine(appUserId)));
        blocks.add(buildHistoryBlock(ticket));
        blocks.add(buildTechBlock(user, payments));
        return String.join("\n\n", blocks);
    }

    public String getCategoryLabel(SupportTicket.Category category) {
        return texts.get(AppLanguage.RU, BotTextKeys.buildSupportCategoryKey(category));
    }

    public String buildTopicLink(Integer topicId) {
        return "https://t.me/c/" + (-properties.getChatId() - SUPERGROUP_ID_OFFSET) + "/" + topicId;
    }

    private String buildUserLine(TelegramUser user) {
        String name = "<a href=\"tg://user?id=" + user.getTelegramId() + "\"><b>"
                + escape(buildDisplayName(user)) + "</b></a>";
        String username = user.getUsername() == null ? "" : " @" + escape(user.getUsername());
        return "👤 " + name + username + " " + texts.languageOf(user.getAppUserId()).getFlag();
    }

    private String buildSubscriptionLine(Long appUserId) {
        UserSubscription subscription = userSubscriptionService.getActiveSubscriptionOrNull(appUserId, BotIdentifier.CALORIE_BOT);
        if (subscription == null) return "💎 Без подписки";
        String plan = pricingPlanRepository.findById(subscription.getPricingPlanId())
                .map(PricingPlan::getTitle)
                .orElse("Подписка");
        long daysLeft = Duration.between(Instant.now(), subscription.getEndAt()).toDays();
        return "💎 " + escape(plan) + " — ещё " + daysLeft + " дн. (до " + formatDate(subscription.getEndAt()) + ")";
    }

    private String buildPaymentsLine(List<Payment> payments) {
        if (payments.isEmpty()) return "💳 Платежей не было";
        StringJoiner line = new StringJoiner(" · ", "💳 ", "");
        for (Payment payment : payments) {
            line.add(buildAmount(payment) + " " + formatDate(getPaymentTime(payment)) + " " + buildPaymentStatus(payment));
        }
        return line.toString();
    }

    private String buildAiLine(Long appUserId) {
        Instant from = Instant.now().minus(Duration.ofDays(1));
        long ok = aiRecordRepository.countByAppUserIdAndCreatedAfterAndErrorCodeIsNull(appUserId, from);
        long failed = aiRecordRepository.countByAppUserIdAndCreatedAfterAndErrorCodeIsNotNull(appUserId, from);
        String line = "🤖 ИИ за сутки: " + ok;
        if (failed == 0) return line;
        line += " · ⚠️ сбоев " + failed;
        MetricsAiInteractionRecord last = aiRecordRepository
                .findFirstByAppUserIdAndCreatedAfterAndErrorCodeIsNotNullOrderByCreatedDesc(appUserId, from)
                .orElse(null);
        if (last == null) return line;
        String code = last.getErrorCode() == 0 ? "нет ответа" : "HTTP " + last.getErrorCode();
        return line + " (последний " + last.getCreated().atZone(properties.getZone()).format(TIME) + ", " + code + ")";
    }

    private String buildHistoryBlock(SupportTicket ticket) {
        List<SupportTicket> previous = ticketRepository.findTop5ByAppUserIdAndStatusNotAndIdNotOrderByCreatedAtDesc(
                ticket.getAppUserId(), SupportTicket.Status.DRAFT, ticket.getId());
        if (previous.isEmpty()) return "🗂 Первое обращение";
        long total = ticketRepository.countByAppUserIdAndStatusNot(ticket.getAppUserId(), SupportTicket.Status.DRAFT) - 1;
        StringJoiner block = new StringJoiner("\n");
        block.add("🗂 <b>Прошлые заявки</b> (" + total + ")");
        previous.forEach(old -> block.add(buildHistoryLine(old)));
        return block.toString();
    }

    private String buildHistoryLine(SupportTicket ticket) {
        String status = ticket.getStatus() == SupportTicket.Status.CLOSED ? "✅" : "⏳";
        String rating = ticket.getRating() == null ? "" : ticket.getRating() > 0 ? "👍" : "👎";
        String number = ticket.getTopicId() == null
                ? "#" + ticket.getId()
                : "<a href=\"" + buildTopicLink(ticket.getTopicId()) + "\">#" + ticket.getId() + "</a>";
        String preview = messageRepository
                .findFirstByTicketIdAndDirectionOrderByCreatedAtAsc(ticket.getId(), SupportMessage.Direction.USER)
                .map(SupportMessage::getText)
                .map(text -> " — " + escape(shorten(text)))
                .orElse("");
        return status + rating + " " + number + " " + ticket.getCategory().getEmoji() + " "
                + formatDate(ticket.getCreatedAt()) + preview;
    }

    /** Свёрнутая цитата: id для поиска в базе, charge id платежей, готовая команда возврата последнего платежа. */
    private String buildTechBlock(TelegramUser user, List<Payment> payments) {
        StringJoiner block = new StringJoiner("\n");
        block.add("🔧 tg <code>" + user.getTelegramId() + "</code> · appUserId <code>" + user.getAppUserId() + "</code>");
        for (Payment payment : payments) {
            if (payment.getProviderPaymentId() == null) continue;
            block.add(buildAmount(payment) + " " + formatDate(getPaymentTime(payment))
                    + ": <code>" + escape(payment.getProviderPaymentId()) + "</code>");
        }
        payments.stream()
                .filter(payment -> payment.getStatus() == Payment.PaymentStatus.SUCCESS)
                .filter(payment -> payment.getStarsAmount() != null && payment.getProviderPaymentId() != null)
                .findFirst()
                .ifPresent(payment -> block.add("Вернуть в платёжном боте: <code>/refund "
                        + escape(payment.getProviderPaymentId()) + "</code>"));
        return "<blockquote expandable>" + block + "</blockquote>";
    }

    private static String buildAmount(Payment payment) {
        return payment.getStarsAmount() != null
                ? payment.getStarsAmount() + " ⭐"
                : payment.getAmount() + " " + payment.getCurrency();
    }

    private static String buildPaymentStatus(Payment payment) {
        return switch (payment.getStatus()) {
            case SUCCESS -> "✓";
            case REFUNDED -> "↩ возврат";
            case FAILED -> "✗ не прошёл";
            default -> payment.getStatus().name();
        };
    }

    private static Instant getPaymentTime(Payment payment) {
        return payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
    }

    private String formatDate(Instant instant) {
        return instant == null ? "?" : instant.atZone(properties.getZone()).format(DATE);
    }

    private static String buildUserName(TelegramUser user) {
        String name = buildDisplayName(user);
        return user.getUsername() == null ? name : name + " @" + user.getUsername();
    }

    private static String buildDisplayName(TelegramUser user) {
        String name = ((user.getFirstname() == null ? "" : user.getFirstname()) + " "
                + (user.getLastname() == null ? "" : user.getLastname())).trim();
        return name.isEmpty() ? "id " + user.getTelegramId() : name;
    }

    private static String shorten(String text) {
        String line = text.replace('\n', ' ');
        return line.length() > PREVIEW_LENGTH ? line.substring(0, PREVIEW_LENGTH) + "…" : line;
    }

    private static String escape(String text) {
        return ReportHtml.escapeHtml(text);
    }
}
