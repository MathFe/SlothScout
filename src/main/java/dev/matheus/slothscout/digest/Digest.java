package dev.matheus.slothscout.digest;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** O resumo de um dia: as duas linhas e os números da rodada. */
@Entity
public class Digest {

	@Id
	@Column(name = "digest_day")
	private LocalDate day;

	@Column(length = 2000)
	private String headline;

	private int alertsRead;

	private int jobsScored;

	private int shortlisted;

	private int cutoff;

	private String model;

	private Instant createdAt;

	protected Digest() {
	}

	public Digest(LocalDate day) {
		this.day = day;
	}

	public void update(String headline, int alertsRead, int jobsScored, int shortlisted, int cutoff, String model,
			Instant at) {
		this.headline = headline;
		this.alertsRead = alertsRead;
		this.jobsScored = jobsScored;
		this.shortlisted = shortlisted;
		this.cutoff = cutoff;
		this.model = model;
		this.createdAt = at;
	}

	public LocalDate getDay() { return day; }
	public String getHeadline() { return headline; }
	public int getAlertsRead() { return alertsRead; }
	public int getJobsScored() { return jobsScored; }
	public int getShortlisted() { return shortlisted; }
	public int getCutoff() { return cutoff; }
	public String getModel() { return model; }
	public Instant getCreatedAt() { return createdAt; }
}
