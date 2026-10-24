package dev.matheus.slothscout.digest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.matheus.slothscout.config.SlothScoutProperties;
import dev.matheus.slothscout.job.JobPosting;
import dev.matheus.slothscout.job.JobRepository;
import dev.matheus.slothscout.job.JobStatus;
import dev.matheus.slothscout.score.JobScorer;
import dev.matheus.slothscout.score.Prompts;

/**
 * Segunda chamada ao modelo: recebe só as vagas acima do corte e escreve as duas linhas.
 * Rodar de novo no mesmo dia refaz o resumo com tudo que entrou no dia.
 */
@Service
public class DigestService {

	private static final Logger log = LoggerFactory.getLogger(DigestService.class);

	private final JobRepository jobs;

	private final DigestRepository digests;

	private final SlothScoutProperties props;

	private final JobScorer scorer;

	private final ChatClient chat;

	public DigestService(JobRepository jobs, DigestRepository digests, SlothScoutProperties props, JobScorer scorer,
			ChatClient.Builder builder) {
		this.jobs = jobs;
		this.digests = digests;
		this.props = props;
		this.scorer = scorer;
		this.chat = builder.build();
	}

	@Transactional
	public Digest build(LocalDate day, int alertsRead) {
		for (JobPosting j : jobs.findByStatusAndDigestDateIsNull(JobStatus.SCORED)) {
			j.assignDigest(day);
		}
		List<JobPosting> all = jobs.findByDigestDateOrderByScoreDesc(day);
		int cutoff = props.criteria().cutoff();
		List<JobPosting> shortlist = all.stream().filter(j -> j.getScore() >= cutoff).toList();
		List<JobPosting> ignored = all.stream().filter(j -> j.getScore() < cutoff).toList();

		String headline = headline(shortlist, ignored, cutoff, topFlags(ignored));

		Digest digest = digests.findById(day).orElseGet(() -> new Digest(day));
		int previousAlerts = digest.getCreatedAt() == null ? 0 : digest.getAlertsRead();
		digest.update(headline, previousAlerts + alertsRead, all.size(), shortlist.size(), cutoff, scorer.model(),
				Instant.now());
		digests.save(digest);

		writeMarkdown(digest, shortlist, ignored);
		return digest;
	}

	private String headline(List<JobPosting> shortlist, List<JobPosting> ignored, int cutoff, List<String> flags) {
		int total = shortlist.size() + ignored.size();
		if (total == 0) {
			return "Nenhuma vaga nova nos alertas de hoje.\nPode voltar a dormir.";
		}
		String fallback = fallbackHeadline(shortlist, ignored, cutoff, flags);
		if (shortlist.isEmpty()) {
			return fallback;
		}
		String text = firstTwoLines(chat.prompt()
				.system(Prompts.DIGEST_SYSTEM)
				.user(Prompts.digestUser(shortlist, ignored, cutoff, flags))
				.call()
				.content());
		if (!mentionsAll(text, shortlist)) {
			// O modelo esqueceu ou trocou vaga da lista curta: melhor um texto sem graça do que um errado.
			log.warn("Resumo do modelo não cita todas as vagas da lista curta, usando o texto padrão: {}", text);
			return fallback;
		}
		if (text.lines().count() < 2) {
			return text + "\n" + fallback.lines().skip(1).findFirst().orElse("");
		}
		return text;
	}

	/** A linha 1 cita cada empresa da lista curta (basta o primeiro nome: "Acme" vale por "Acme Tech"). */
	static boolean mentionsAll(String text, List<JobPosting> shortlist) {
		String first = text.lines().findFirst().orElse("").toLowerCase();
		return shortlist.stream()
				.map(JobPosting::getCompany)
				.filter(Objects::nonNull)
				.map(c -> c.strip().split("\\s+")[0].toLowerCase())
				.allMatch(first::contains);
	}

	static String fallbackHeadline(List<JobPosting> shortlist, List<JobPosting> ignored, int cutoff,
			List<String> flags) {
		String line1 = shortlist.isEmpty()
				? "Nenhuma das " + ignored.size() + " vagas passou do corte de " + cutoff + "."
				: "Aplica nessas " + shortlist.size() + ": " + shortlist.stream()
						.map(j -> j.getTitle() + " (" + nz(j.getCompany()) + ")")
						.collect(Collectors.joining(", ")) + ".";
		String line2 = ignored.isEmpty() ? "Nada para ignorar hoje."
				: "Ignora as outras " + ignored.size() + (flags.isEmpty() ? "." : ": " + String.join(", ", flags) + ".");
		return line1 + "\n" + line2;
	}

	static String firstTwoLines(String text) {
		return text == null ? "" : text.lines()
				.map(String::strip)
				.filter(l -> !l.isEmpty())
				.limit(2)
				.collect(Collectors.joining("\n"));
	}

	private static List<String> topFlags(List<JobPosting> ignored) {
		return ignored.stream()
				.map(JobPosting::getFlags)
				.flatMap(Collection::stream)
				.filter(Objects::nonNull)
				.map(f -> f.strip().toLowerCase())
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
				.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(3)
				.map(Map.Entry::getKey)
				.toList();
	}

	private void writeMarkdown(Digest d, List<JobPosting> shortlist, List<JobPosting> ignored) {
		StringBuilder md = new StringBuilder();
		md.append("# SlothScout · ").append(d.getDay()).append("\n\n");
		d.getHeadline().lines().forEach(l -> md.append("> ").append(l).append("\n"));
		md.append("\n").append(d.getJobsScored()).append(" vagas avaliadas · corte ").append(d.getCutoff())
				.append(" · modelo `").append(d.getModel()).append("`\n\n");
		md.append("## Aplica nessas\n\n");
		if (shortlist.isEmpty()) {
			md.append("_Nenhuma hoje._\n");
		}
		for (JobPosting j : shortlist) {
			md.append("### ").append(j.getScore()).append(" · [").append(j.getTitle()).append("](").append(j.getUrl())
					.append(")\n");
			md.append(nz(j.getCompany())).append(" · ").append(nz(j.getLocation()));
			if (j.getWorkMode() != null) {
				md.append(" · ").append(j.getWorkMode());
			}
			md.append("\n\n");
			j.getReasons().forEach(r -> md.append("- ").append(r.grounded() ? "✓ " : "? ").append(r.text()).append("\n"));
			if (!j.getFlags().isEmpty()) {
				md.append("- ⚠ ").append(String.join(" · ", j.getFlags())).append("\n");
			}
			md.append("\n");
		}
		md.append("## Ignora o resto (").append(ignored.size()).append(")\n\n");
		for (JobPosting j : ignored) {
			md.append("- ").append(j.getScore()).append(" · ").append(j.getTitle()).append(" · ").append(nz(j.getCompany()));
			if (!j.getFlags().isEmpty()) {
				md.append(" — ").append(String.join(", ", j.getFlags()));
			}
			md.append("\n");
		}
		try {
			Files.createDirectories(props.reportsDir());
			Path file = props.reportsDir().resolve(d.getDay() + ".md");
			Files.writeString(file, md);
			log.info("Resumo salvo em {}", file.toAbsolutePath());
		}
		catch (IOException e) {
			log.warn("Não consegui salvar o .md do dia", e);
		}
	}

	private static String nz(String s) {
		return s == null || s.isBlank() ? "?" : s;
	}
}
