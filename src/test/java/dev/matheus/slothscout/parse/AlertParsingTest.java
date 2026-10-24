package dev.matheus.slothscout.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class AlertParsingTest {

	private final AlertParsing parsing = new AlertParsing(
			List.of(new LinkedInAlertParser(), new IndeedAlertParser(), new GupyAlertParser()));

	@Test
	void linkedinExtractsAndDedupesWithinTheEmail() {
		List<ParsedJob> jobs = parsing.parse("jobalerts-noreply@linkedin.com", fixture("linkedin.html"));

		assertThat(jobs).hasSize(2);
		ParsedJob java = jobs.get(0);
		assertThat(java.key()).isEqualTo("linkedin:4012345678");
		assertThat(java.title()).isEqualTo("Desenvolvedor Java Júnior");
		assertThat(java.company()).isEqualTo("Embraer");
		assertThat(java.location()).isEqualTo("São José dos Campos, SP (Híbrido)");
		assertThat(java.workMode()).isEqualTo("Híbrido");
		assertThat(java.url()).isEqualTo("https://www.linkedin.com/jobs/view/4012345678/");

		ParsedJob python = jobs.get(1);
		assertThat(python.title()).isEqualTo("Estágio em Desenvolvimento Python");
		assertThat(python.company()).isEqualTo("Acme Tech");
		assertThat(python.location()).isEqualTo("Brasil (Remoto)");
		assertThat(python.workMode()).isEqualTo("Remoto");
	}

	@Test
	void indeedFindsJobIdEvenInsideEncodedRedirect() {
		List<ParsedJob> jobs = parsing.parse("alert@indeed.com", fixture("indeed.html"));

		assertThat(jobs).extracting(ParsedJob::externalId).containsExactly("0a1b2c3d4e5f6a7b", "fedcba9876543210");
		ParsedJob first = jobs.get(0);
		assertThat(first.title()).isEqualTo("Analista de Suporte .NET Jr");
		assertThat(first.company()).isEqualTo("Prefeitura Tech");
		assertThat(first.location()).isEqualTo("Taubaté, SP");
		assertThat(first.workMode()).isEqualTo("Presencial");
		assertThat(first.snippet()).contains("C# e SQL Server");
	}

	@Test
	void gupyKeepsCompanySubdomainInUrl() {
		List<ParsedJob> jobs = parsing.parse("noreply@gupy.io", fixture("gupy.html"));

		assertThat(jobs).hasSize(2);
		assertThat(jobs.get(0).url()).isEqualTo("https://vale.gupy.io/jobs/8123456");
		assertThat(jobs.get(0).company()).isEqualTo("Vale");
		assertThat(jobs.get(0).workMode()).isEqualTo("Híbrido");
		assertThat(jobs.get(1).title()).isEqualTo("Assistente de Vendas");
	}

	@Test
	void unknownSenderTriesEveryParser() {
		List<ParsedJob> jobs = parsing.parse("eu@meudominio.com", fixture("linkedin.html"));

		assertThat(jobs).extracting(ParsedJob::source).containsOnly("linkedin");
	}

	private static String fixture(String name) {
		try (InputStream in = AlertParsingTest.class.getResourceAsStream("/alerts/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
