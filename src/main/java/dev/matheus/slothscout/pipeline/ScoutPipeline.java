package dev.matheus.slothscout.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import dev.matheus.slothscout.config.SlothScoutProperties;
import dev.matheus.slothscout.digest.Digest;
import dev.matheus.slothscout.digest.DigestService;
import dev.matheus.slothscout.job.JobPosting;
import dev.matheus.slothscout.job.JobRepository;
import dev.matheus.slothscout.job.JobStatus;
import dev.matheus.slothscout.job.ProcessedEmail;
import dev.matheus.slothscout.job.ProcessedEmailRepository;
import dev.matheus.slothscout.mail.AlertMailbox;
import dev.matheus.slothscout.mail.RawAlert;
import dev.matheus.slothscout.mail.ReplayInbox;
import dev.matheus.slothscout.parse.AlertParsing;
import dev.matheus.slothscout.parse.ParsedJob;
import dev.matheus.slothscout.score.JobScorer;

/**
 * A rodada da madrugada: coleta → extração → deduplicação → pontuação → resumo.
 * Cada vaga é salva assim que pontuada; se cair no meio, a próxima rodada continua de onde parou.
 */
@Service
public class ScoutPipeline {

	private static final Logger log = LoggerFactory.getLogger(ScoutPipeline.class);

	private final AlertMailbox mailbox;

	private final ReplayInbox replay;

	private final AlertParsing parsing;

	private final JobRepository jobs;

	private final ProcessedEmailRepository processed;

	private final JobScorer scorer;

	private final DigestService digests;

	private final SlothScoutProperties props;

	private final AtomicBoolean running = new AtomicBoolean();

	private volatile String lastMessage = "Ainda não rodou desde que o app subiu.";

	public ScoutPipeline(AlertMailbox mailbox, ReplayInbox replay, AlertParsing parsing, JobRepository jobs,
			ProcessedEmailRepository processed, JobScorer scorer, DigestService digests, SlothScoutProperties props) {
		this.mailbox = mailbox;
		this.replay = replay;
		this.parsing = parsing;
		this.jobs = jobs;
		this.processed = processed;
		this.scorer = scorer;
		this.digests = digests;
		this.props = props;
	}

	@Scheduled(cron = "${slothscout.schedule.cron}", zone = "${slothscout.zone}")
	public void nightly() {
		run();
	}

	public boolean isRunning() {
		return running.get();
	}

	public String lastMessage() {
		return lastMessage;
	}

	/** @return false se já havia uma rodada em andamento. */
	public boolean run() {
		if (!running.compareAndSet(false, true)) {
			log.info("Rodada já em andamento, ignorando.");
			return false;
		}
		Instant started = Instant.now();
		try {
			int alerts = collect();
			int scored = scorePending();
			Digest d = digests.build(LocalDate.now(ZoneId.of(props.zone())), alerts);
			lastMessage = "Rodada concluída: %d alertas lidos, %d vagas pontuadas, %d acima do corte (%ds)."
					.formatted(alerts, scored, d.getShortlisted(), java.time.Duration.between(started, Instant.now()).toSeconds());
			log.info(lastMessage);
		}
		catch (RuntimeException e) {
			lastMessage = "Rodada falhou: " + e.getMessage();
			log.error("Rodada falhou", e);
		}
		finally {
			running.set(false);
		}
		return true;
	}

	/** Etapas 2 a 4: IMAP, extração e deduplicação. Devolve quantos alertas novos foram lidos. */
	int collect() {
		List<RawAlert> alerts;
		try {
			if (replay.configured()) {
				log.info("Modo replay: lendo alertas de {}", props.run().replayDir().toAbsolutePath());
				alerts = replay.fetch(processed::existsById);
			}
			else if (mailbox.configured()) {
				alerts = mailbox.fetch(processed::existsById);
			}
			else {
				log.warn("IMAP não configurado (SLOTHSCOUT_IMAP_USER / SLOTHSCOUT_IMAP_PASSWORD). Pulando a coleta.");
				return 0;
			}
		}
		catch (Exception e) {
			log.error("Falha lendo os alertas; seguindo com o que já está no banco", e);
			return 0;
		}

		int newJobs = 0;
		for (RawAlert alert : alerts) {
			dump(alert);
			List<ParsedJob> parsed = parsing.parse(alert.from(), alert.html());
			for (ParsedJob p : parsed) {
				if (jobs.existsById(p.key())) {
					continue;
				}
				jobs.save(new JobPosting(p.key(), p.source(), p.title(), p.company(), p.location(), p.workMode(),
						p.url(), p.snippet(), alert.receivedAt()));
				newJobs++;
			}
			processed.save(new ProcessedEmail(alert.messageId(), alert.from(), alert.subject(), parsed.size(),
					Instant.now()));
			if (parsed.isEmpty()) {
				log.warn("Nenhuma vaga extraída de '{}' ({}). Ligue slothscout.run.dump-html para ajustar o parser.",
						alert.subject(), alert.from());
			}
		}
		log.info("{} alertas novos, {} vagas novas após deduplicação", alerts.size(), newJobs);
		return alerts.size();
	}

	/** Etapa 5: uma chamada por vaga, em lotes pequenos, salvando cada resultado na hora. */
	int scorePending() {
		SlothScoutProperties.Run cfg = props.run();
		List<JobPosting> pending = jobs.findByStatusOrderByFirstSeenAtAsc(JobStatus.NEW,
				PageRequest.of(0, cfg.maxJobsPerRun()));
		if (pending.isEmpty()) {
			return 0;
		}
		String resume = scorer.loadResume();
		log.info("Pontuando {} vagas com {} (lotes de {})", pending.size(), scorer.model(), cfg.batchSize());

		int scored = 0;
		for (int i = 0; i < pending.size(); i++) {
			JobPosting job = pending.get(i);
			try {
				JobScorer.ScoredJob s = scorer.score(resume, job);
				job.markScored(s.score(), s.reasons(), s.flags(), scorer.model(), Instant.now());
				scored++;
			}
			catch (ResourceAccessException e) {
				// Ollama fora do ar não é culpa da vaga: não gasta tentativa, só para a rodada.
				throw new IllegalStateException("Ollama inacessível. Ele está rodando? (" + e.getMessage() + ")", e);
			}
			catch (RuntimeException e) {
				log.warn("Falha pontuando {}: {}", job.getId(), e.getMessage());
				job.markAttemptFailed(e.toString(), cfg.maxAttempts());
			}
			jobs.save(job);
			if ((i + 1) % cfg.batchSize() == 0) {
				log.info("  {}/{} pontuadas", i + 1, pending.size());
			}
		}
		return scored;
	}

	private void dump(RawAlert alert) {
		if (!props.run().dumpHtml()) {
			return;
		}
		try {
			Files.createDirectories(props.run().dumpDir());
			String name = parsing.sourceFor(alert.from()) + "-" + alert.receivedAt().toEpochMilli() + ".html";
			Files.writeString(props.run().dumpDir().resolve(name), alert.html());
		}
		catch (IOException e) {
			log.warn("Não consegui salvar o HTML do alerta", e);
		}
	}
}
