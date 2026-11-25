package dev.swirlit.indezy.push;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushServiceTest {

    @Test
    void reachesOnlyThePushServicesOfKnownBrowsers() {
        assertThat(PushService.allowed("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(PushService.allowed("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(PushService.allowed("https://web.push.apple.com/QGuQ")).isTrue();
        assertThat(PushService.allowed("https://wns2-par02p.notify.windows.com/w/?token=abc")).isTrue();
        assertThat(PushService.allowed("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
        assertThat(PushService.allowed("https://fcm.googleapis.com:8443/fcm/send/abc")).isFalse();
        assertThat(PushService.allowed("https://evil.example/fcm.googleapis.com")).isFalse();
        assertThat(PushService.allowed("https://fcm.googleapis.com.evil.example/x")).isFalse();
        assertThat(PushService.allowed("https://user@fcm.googleapis.com/x")).isFalse();
        assertThat(PushService.allowed("https://169.254.169.254/latest")).isFalse();
        assertThat(PushService.allowed("not a url at all")).isFalse();
    }

    @Test
    void saysPushIsOffWithoutBothKeys() {
        assertThat(new PushProperties(null, null).configured()).isFalse();
        assertThat(new PushProperties("public", " ").configured()).isFalse();
        assertThat(new PushProperties("public", "private").configured()).isTrue();
        assertThat(new PushProperties("public", "private").toString()).doesNotContain("private");
    }

    @Test
    void readsTheEventsAnAccountTurnedOff() {
        dev.swirlit.indezy.model.User user = new dev.swirlit.indezy.model.User();
        org.assertj.core.api.Assertions.assertThat(PushService.eventsOff(user)).isEmpty();
        user.setPushEventsOff("MESSAGES, GONE_EVENT,FOLLOW_UPS");
        org.assertj.core.api.Assertions.assertThat(PushService.eventsOff(user)).containsExactlyInAnyOrder(PushEvent.MESSAGES, PushEvent.FOLLOW_UPS);
    }

    private static dev.swirlit.indezy.model.User account(long id, String language, String eventsOff) {
        dev.swirlit.indezy.model.User user = new dev.swirlit.indezy.model.User();
        user.setId(id);
        user.setLanguagePreference(language);
        user.setPushEventsOff(eventsOff);
        return user;
    }

    private static dev.swirlit.indezy.model.PushSubscription browser(long userId, String endpoint) {
        dev.swirlit.indezy.model.PushSubscription subscription = new dev.swirlit.indezy.model.PushSubscription();
        subscription.setUserId(userId);
        subscription.setEndpoint(endpoint);
        return subscription;
    }

    @Test
    void tellsEachAccountsBrowsersInItsLanguageAndNoneWhoTurnedTheEventOff() {
        dev.swirlit.indezy.repository.PushSubscriptionRepository subscriptions =
            org.mockito.Mockito.mock(dev.swirlit.indezy.repository.PushSubscriptionRepository.class);
        dev.swirlit.indezy.repository.UserRepository users = org.mockito.Mockito.mock(dev.swirlit.indezy.repository.UserRepository.class);
        PushService service = org.mockito.Mockito.spy(new PushService(new PushProperties("public", "private"), subscriptions, users,
            Runnable::run, tools.jackson.databind.json.JsonMapper.builder().build(), java.time.Clock.systemUTC(),
            new dev.swirlit.indezy.mail.MailProperties("re_x", "Indezy <noreply@swirlit.dev>", "https://indezy.example", "https://api.resend.com")));
        org.mockito.Mockito.doNothing().when(service).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.when(users.findById(1L)).thenReturn(java.util.Optional.of(account(1L, "en-GB", null)));
        org.mockito.Mockito.when(users.findById(2L)).thenReturn(java.util.Optional.of(account(2L, null, "NETWORK")));
        org.mockito.Mockito.when(users.findById(3L)).thenReturn(java.util.Optional.of(account(3L, "fr", "MESSAGES, UNKNOWN")));
        org.mockito.Mockito.when(users.findById(4L)).thenReturn(java.util.Optional.empty());
        dev.swirlit.indezy.model.PushSubscription laptop = browser(1L, "https://fcm.googleapis.com/a");
        dev.swirlit.indezy.model.PushSubscription phone = browser(1L, "https://web.push.apple.com/b");
        dev.swirlit.indezy.model.PushSubscription other = browser(3L, "https://fcm.googleapis.com/c");
        org.mockito.Mockito.when(subscriptions.findByUserId(1L)).thenReturn(java.util.List.of(laptop, phone));
        org.mockito.Mockito.when(subscriptions.findByUserId(3L)).thenReturn(java.util.List.of(other));
        java.util.Map<dev.swirlit.indezy.model.enums.DocumentLanguage, String[]> texts = java.util.Map.of(
            dev.swirlit.indezy.model.enums.DocumentLanguage.EN, new String[] {"New message", "Léa wrote to you"},
            dev.swirlit.indezy.model.enums.DocumentLanguage.FR, new String[] {"Nouveau message", "Léa vous a écrit"});

        service.notify(PushEvent.MESSAGES, java.util.List.of(1L, 2L, 3L, 4L), texts, "/network/messages/7");

        // Alice in English on both her browsers; the account that turned messages off and the one gone are skipped.
        org.mockito.Mockito.verify(service).send(laptop, "New message", "Léa wrote to you", "/network/messages/7");
        org.mockito.Mockito.verify(service).send(phone, "New message", "Léa wrote to you", "/network/messages/7");
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.eq(other),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(subscriptions, org.mockito.Mockito.never()).findByUserId(4L);

        // An event left on reaches the French account in French; nothing goes without the VAPID keys or recipients.
        service.notify(PushEvent.NETWORK, java.util.List.of(3L), texts, "/network");
        org.mockito.Mockito.verify(service).send(other, "Nouveau message", "Léa vous a écrit", "/network");
        service.notify(PushEvent.NETWORK, java.util.List.of(), texts, "/network");
        PushService off = new PushService(new PushProperties(null, null), subscriptions, users, Runnable::run,
            tools.jackson.databind.json.JsonMapper.builder().build(), java.time.Clock.systemUTC(), null);
        off.notify(PushEvent.NETWORK, java.util.List.of(3L), texts, "/network");
        org.mockito.Mockito.verify(users, org.mockito.Mockito.times(2)).findById(3L);
    }
}
