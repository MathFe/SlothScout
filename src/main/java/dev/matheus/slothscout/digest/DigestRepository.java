package dev.matheus.slothscout.digest;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DigestRepository extends JpaRepository<Digest, LocalDate> {

	Optional<Digest> findFirstByOrderByDayDesc();
}
