package dev.matheus.slothscout.mail;

import java.time.Instant;

/** Um e-mail de alerta de vaga, já com o HTML extraído. */
public record RawAlert(String messageId, String from, String subject, Instant receivedAt, String html) {
}
