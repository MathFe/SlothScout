package dev.matheus.slothscout.job;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Marca um e-mail como processado pelo agente, sem tocar no "lido" do Gmail. */
@Entity
public class ProcessedEmail {

	@Id
	@Column(length = 1000)
	private String messageId;

	private String sender;

	@Column(length = 1000)
	private String subject;

	private int jobsFound;

	private Instant processedAt;

	protected ProcessedEmail() {
	}

	public ProcessedEmail(String messageId, String sender, String subject, int jobsFound, Instant processedAt) {
		this.messageId = messageId;
		this.sender = sender;
		this.subject = subject;
		this.jobsFound = jobsFound;
		this.processedAt = processedAt;
	}

	public String getMessageId() { return messageId; }
	public int getJobsFound() { return jobsFound; }
}
