package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteAccessServiceTest {

    private final SiteAccessService restricted =
            new SiteAccessService(true, "first@example.com, Second@Example.com", "111, 222");

    @Test
    void allowsListedEmailIgnoringCaseAndSpaces() {
        assertDoesNotThrow(() -> restricted.checkEmail("first@example.com"));
        assertDoesNotThrow(() -> restricted.checkEmail(" SECOND@example.com "));
    }

    @Test
    void rejectsUnlistedOrMissingEmail() {
        assertThrows(ErrorResponseException.class, () -> restricted.checkEmail("other@example.com"));
        assertThrows(ErrorResponseException.class, () -> restricted.checkEmail(null));
    }

    @Test
    void allowsOnlyListedTelegramIds() {
        assertDoesNotThrow(() -> restricted.checkTelegramId(222L));
        assertThrows(ErrorResponseException.class, () -> restricted.checkTelegramId(333L));
    }

    @Test
    void emptyListsRejectEveryoneWhileRestricted() {
        SiteAccessService empty = new SiteAccessService(true, "", "");
        assertThrows(ErrorResponseException.class, () -> empty.checkEmail("first@example.com"));
        assertThrows(ErrorResponseException.class, () -> empty.checkTelegramId(111L));
    }

    @Test
    void allowsEveryoneWhenNotRestricted() {
        SiteAccessService open = new SiteAccessService(false, "", "");
        assertDoesNotThrow(() -> open.checkEmail("anyone@example.com"));
        assertDoesNotThrow(() -> open.checkTelegramId(999L));
    }
}
