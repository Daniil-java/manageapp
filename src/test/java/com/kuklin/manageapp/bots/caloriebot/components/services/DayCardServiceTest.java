package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.services.DayCardService.DayCard;
import com.kuklin.manageapp.bots.caloriebot.components.services.DayCardService.Status;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DayCardServiceTest {

    private static final long USER_ID = 7L;
    private static final String REF_URL = "https://t.me/calorydairy_bot?start=ref7";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    private final DishService dishService = mock(DishService.class);
    private final UserNutritionProfileService profileService = mock(UserNutritionProfileService.class);
    private final UserSettingsService settingsService = mock(UserSettingsService.class);
    private final StreakService streakService = mock(StreakService.class);

    private DayCardService buildService(String language) {
        when(settingsService.findLanguageCode(USER_ID)).thenReturn(language);
        when(streakService.getStreakEndingAt(USER_ID, DAY)).thenReturn(5);
        when(profileService.getOrCreateProfile(USER_ID)).thenReturn(new UserNutritionProfile()
                .setCaloriesNormPerDay(2100).setProteinsNormGramsPerDay(120)
                .setFatsNormGramsPerDay(70).setCarbsNormGramsPerDay(240));
        return new DayCardService(dishService, profileService, streakService,
                new CalorieTexts(new I18nProperties(), settingsService));
    }

    private static Dish dish(Integer calories, Integer proteins, Integer fats, Integer carbs) {
        return new Dish().setUserId(USER_ID).setName("Блюдо").setCalories(calories)
                .setProteins(proteins).setFats(fats).setCarbohydrates(carbs);
    }

    @Test
    void noDishesTodayIsEmpty() {
        DayCardService service = buildService("ru");
        when(dishService.getDishesForDate(USER_ID, DAY)).thenReturn(List.of());

        assertThat(service.buildCard(USER_ID, DAY, REF_URL, 3).status()).isEqualTo(Status.EMPTY);
    }

    @Test
    void totalsSkipEmptyFields() {
        DayCardService.DayTotals totals = DayCardService.getTotals(
                List.of(dish(500, 30, 20, null), dish(null, 10, null, 60)),
                new UserNutritionProfile().setCaloriesNormPerDay(2000));

        assertThat(totals.calories()).isEqualTo(500);
        assertThat(totals.proteins()).isEqualTo(40);
        assertThat(totals.fats()).isEqualTo(20);
        assertThat(totals.carbs()).isEqualTo(60);
        assertThat(totals.dishes()).isEqualTo(2);
        assertThat(totals.proteinsNorm()).isNull();
    }

    @Test
    void barIsClampedToVisibleRange() {
        assertThat(DayCardService.getBarPercent(1050, 2100)).isEqualTo(50);
        assertThat(DayCardService.getBarPercent(3000, 2100)).isEqualTo(100);
        assertThat(DayCardService.getBarPercent(0, 2100)).isEqualTo(2);
        assertThat(DayCardService.getBarPercent(10, 0)).isZero();
    }

    @Test
    void templateGetsDataInUserLanguage() {
        DayCardService service = buildService("ru");
        DayCardService.DayTotals totals = DayCardService.getTotals(
                List.of(dish(1840, 95, 60, 210)), profileService.getOrCreateProfile(USER_ID));

        String html = service.fillTemplateOrNull(USER_ID, totals, LocalDate.of(2026, 10, 20), 5, REF_URL, 3);

        assertThat(html)
                .contains("Мой день", "20 октября", ">1840<", "100 ккал по норме",
                        "95 / 120", "5 дней подряд", "1 блюдо",
                        "t.me/calorydairy_bot?start=ref7", "По моей ссылке +3 дня Premium")
                .doesNotContain("{{", "https://t.me", "️");
    }

    @Test
    void rendersPngOfCardSize() throws IOException {
        DayCardService service = buildService("ru");
        when(dishService.getDishesForDate(USER_ID, DAY)).thenReturn(List.of(
                dish(520, 32, 18, 55), dish(780, 41, 30, 70), dish(540, 22, 12, 85)));

        DayCard card = service.buildCard(USER_ID, DAY, REF_URL, 3);

        assertThat(card.status()).isEqualTo(Status.OK);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(card.png()));
        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isEqualTo(1350);
        savePreview("daycard-ru.png", card.png());
    }

    @Test
    void rendersEnglishCard() throws IOException {
        DayCardService service = buildService("en");
        when(dishService.getDishesForDate(USER_ID, DAY)).thenReturn(List.of(dish(1840, 95, 60, 210)));

        DayCard card = service.buildCard(USER_ID, DAY, REF_URL, 3);

        assertThat(card.status()).isEqualTo(Status.OK);
        savePreview("daycard-en.png", card.png());
    }

    /** Не проверка, а превью: с -Dreport.preview.dir кладёт картинку в эту папку. */
    private static void savePreview(String name, byte[] png) throws IOException {
        String dir = System.getProperty("report.preview.dir");
        if (dir == null) return;
        Files.createDirectories(Path.of(dir));
        Files.write(Path.of(dir, name), png);
    }
}
