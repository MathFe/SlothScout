package dev.matheus.slothscout.job;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * Uma vaga vista em algum alerta. A chave é "fonte:id-externo", então a deduplicação
 * entre alertas diferentes sai de graça.
 */
@Entity
@Table(name = "job_posting", indexes = {
		@Index(columnList = "status"),
		@Index(columnList = "digestDate")
})
public class JobPosting {

	@Id
	private String id;

	private String source;

	@Column(length = 500)
	private String title;

	@Column(length = 300)
	private String company;

	@Column(length = 300)
	private String location;

	private String workMode;

	@Column(length = 2000)
	private String url;

	@Column(length = 4000)
	private String snippet;

	private Instant firstSeenAt;

	@Enumerated(EnumType.STRING)
	private JobStatus status = JobStatus.NEW;

	private int attempts;

	private Integer score;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "job_reason")
	@OrderColumn
	private List<Reason> reasons = new ArrayList<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "job_flag")
	@OrderColumn
	@Column(name = "flag", length = 500)
	private List<String> flags = new ArrayList<>();

	private String model;

	private Instant scoredAt;

	/** Dia do resumo em que esta vaga entrou (acima ou abaixo do corte). */
	private LocalDate digestDate;

	@Column(length = 2000)
	private String lastError;

	protected JobPosting() {
	}

	public JobPosting(String id, String source, String title, String company, String location, String workMode,
			String url, String snippet, Instant firstSeenAt) {
		this.id = id;
		this.source = source;
		this.title = title;
		this.company = company;
		this.location = location;
		this.workMode = workMode;
		this.url = url;
		this.snippet = snippet;
		this.firstSeenAt = firstSeenAt;
	}

	public void markScored(int score, List<Reason> reasons, List<String> flags, String model, Instant at) {
		this.status = JobStatus.SCORED;
		this.score = score;
		this.reasons = new ArrayList<>(reasons);
		this.flags = new ArrayList<>(flags);
		this.model = model;
		this.scoredAt = at;
		this.lastError = null;
	}

	public void markAttemptFailed(String error, int maxAttempts) {
		this.attempts++;
		this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 2000));
		if (attempts >= maxAttempts) {
			this.status = JobStatus.FAILED;
		}
	}

	public void assignDigest(LocalDate date) {
		this.digestDate = date;
	}

	/** O texto que o modelo enxerga, e contra o qual as citações são conferidas. */
	public String promptText() {
		return "Título: " + nz(title) + "\n"
				+ "Empresa: " + nz(company) + "\n"
				+ "Local: " + nz(location) + "\n"
				+ "Modalidade: " + nz(workMode) + "\n"
				+ "Trecho da descrição: " + nz(snippet) + "\n";
	}

	private static String nz(String s) {
		return s == null || s.isBlank() ? "(não informado)" : s;
	}

	public String getId() { return id; }
	public String getSource() { return source; }
	public String getTitle() { return title; }
	public String getCompany() { return company; }
	public String getLocation() { return location; }
	public String getWorkMode() { return workMode; }
	public String getUrl() { return url; }
	public String getSnippet() { return snippet; }
	public Instant getFirstSeenAt() { return firstSeenAt; }
	public JobStatus getStatus() { return status; }
	public int getAttempts() { return attempts; }
	public Integer getScore() { return score; }
	public List<Reason> getReasons() { return reasons; }
	public List<String> getFlags() { return flags; }
	public String getModel() { return model; }
	public Instant getScoredAt() { return scoredAt; }
	public LocalDate getDigestDate() { return digestDate; }
	public String getLastError() { return lastError; }
}
