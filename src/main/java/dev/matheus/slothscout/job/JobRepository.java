package dev.matheus.slothscout.job;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<JobPosting, String> {

	List<JobPosting> findByStatusOrderByFirstSeenAtAsc(JobStatus status, Pageable page);

	List<JobPosting> findByStatusAndDigestDateIsNull(JobStatus status);

	List<JobPosting> findByDigestDateOrderByScoreDesc(LocalDate date);

	long countByStatus(JobStatus status);
}
