package dev.matheus.slothscout.job;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, String> {

	long countByProcessedAtAfter(Instant since);
}
