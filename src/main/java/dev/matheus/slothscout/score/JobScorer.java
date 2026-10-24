package dev.matheus.slothscout.score;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.matheus.slothscout.config.SlothScoutProperties;
import dev.matheus.slothscout.job.JobPosting;
import dev.matheus.slothscout.job.Reason;

/** Uma chamada ao modelo local por vaga, com saída estruturada mapeada para {@link JobScore}. */
@Component
public class JobScorer {

	private final ChatClient chat;

	private final SlothScoutProperties props;

	private final String model;

	private final BeanOutputConverter<JobScore> converter = new BeanOutputConverter<>(JobScore.class);

	public JobScorer(ChatClient.Builder builder, SlothScoutProperties props,
			@Value("${spring.ai.ollama.chat.model}") String model) {
		this.chat = builder.build();
		this.props = props;
		this.model = model;
	}

	public String model() {
		return model;
	}

	public String loadResume() {
		try {
			return Files.readString(props.resumePath());
		}
		catch (IOException e) {
			throw new IllegalStateException("Currículo não encontrado em " + props.resumePath().toAbsolutePath()
					+ ". Copie config/resume.example.md para config/resume.md e preencha.", e);
		}
	}

	public ScoredJob score(String resume, JobPosting job) {
		String raw = chat.prompt()
				.system(Prompts.SCORING_SYSTEM)
				.user(Prompts.scoringUser(resume, props.criteria(), job, converter.getFormat()))
				// Ollama restringe a geração ao schema: o JSON sempre volta parseável.
				.options(OllamaChatOptions.builder().format(converter.getJsonSchemaMap()))
				.call()
				.content();
		JobScore score = converter.convert(raw);

		String jobText = job.promptText();
		List<Reason> reasons = score.reasons().stream()
				.map(r -> new Reason(r, Grounding.isGrounded(r, jobText)))
				.toList();
		List<String> flags = score.flags();
		if (!score.techRole()) {
			flags = java.util.stream.Stream.concat(java.util.stream.Stream.of("não é TI de verdade"), flags.stream())
					.distinct().toList();
		}
		return new ScoredJob(score.score(), reasons, flags);
	}

	public record ScoredJob(int score, List<Reason> reasons, List<String> flags) {
	}
}
