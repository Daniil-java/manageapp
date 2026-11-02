package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UserSettingsRepository;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserSettingsServiceLanguageCacheTest {
    private static final Long USER_ID = 7L;

    private final UserSettingsRepository repository = mock(UserSettingsRepository.class);
    private final UserSettingsService service =
            new UserSettingsService(repository, mock(PlatformTransactionManager.class));

    @Test
    void languageReadFromDbOnce() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(settingsWithLanguage("en")));

        assertThat(service.findLanguageCode(USER_ID)).isEqualTo("en");
        assertThat(service.findLanguageCode(USER_ID)).isEqualTo("en");

        verify(repository, times(1)).findById(USER_ID);
    }

    @Test
    void notChosenLanguageIsCachedToo() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.findLanguageCode(USER_ID)).isNull();
        assertThat(service.findLanguageCode(USER_ID)).isNull();

        verify(repository, times(1)).findById(USER_ID);
    }

    @Test
    void setLanguageEvictsCache() {
        UserSettings settings = settingsWithLanguage("ru");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(settings));
        assertThat(service.findLanguageCode(USER_ID)).isEqualTo("ru");

        service.setLanguageCode(USER_ID, "en");

        assertThat(service.findLanguageCode(USER_ID)).isEqualTo("en");
    }

    private static UserSettings settingsWithLanguage(String language) {
        UserSettings settings = UserSettings.createDefaultSettings(USER_ID);
        settings.setLanguage(language);
        return settings;
    }
}
