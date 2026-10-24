package dev.matheus.slothscout.job;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Um motivo dado pelo modelo. {@code grounded} diz se o trecho citado entre aspas
 * existe de fato no texto da vaga: é o "confere aí" da demo.
 */
@Embeddable
public record Reason(@Column(length = 1000) String text, boolean grounded) {
}
