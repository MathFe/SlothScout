package dev.matheus.slothscout.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tudo que o usuário ajusta sem mexer no código. Os critérios moram aqui, não no prompt.
 */
@ConfigurationProperties("slothscout")
public record SlothScoutProperties(
		@DefaultValue("config/resume.md") Path resumePath,
		@DefaultValue("reports") Path reportsDir,
		@DefaultValue("America/Sao_Paulo") String zone,
		@DefaultValue Imap imap,
		@DefaultValue Criteria criteria,
		@DefaultValue Run run) {

	public record Imap(
			@DefaultValue("imap.gmail.com") String host,
			@DefaultValue("993") int port,
			String username,
			String password,
			@DefaultValue("Vagas") String folder,
			@DefaultValue("3") int lookbackDays,
			@DefaultValue("200") int maxMessages) {

		public boolean configured() {
			return username != null && !username.isBlank() && password != null && !password.isBlank();
		}
	}

	public record Criteria(
			@DefaultValue({"Júnior", "Estágio"}) List<String> levels,
			@DefaultValue({"São José dos Campos", "Taubaté", "Caçapava", "Jacareí"}) List<String> onSiteCities,
			@DefaultValue("presencial ou híbrido") String onSiteModes,
			@DefaultValue("remoto no Brasil ou internacional") String remote,
			@DefaultValue({"Java", "Python", "JavaScript", ".NET"}) List<String> stacks,
			@DefaultValue("2") int maxYearsExperience,
			@DefaultValue({"senioridade alta (pleno, sênior, especialista, lead)", "viagem obrigatória para São Paulo"}) List<String> exclusions,
			@DefaultValue("70") int cutoff) {
	}

	public record Run(
			@DefaultValue("5") int batchSize,
			@DefaultValue("150") int maxJobsPerRun,
			@DefaultValue("3") int maxAttempts,
			@DefaultValue("false") boolean dumpHtml,
			@DefaultValue("data/samples") Path dumpDir,
			Path replayDir) {
	}
}
