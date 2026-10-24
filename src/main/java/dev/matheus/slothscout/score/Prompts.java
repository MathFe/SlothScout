package dev.matheus.slothscout.score;

import java.util.List;
import java.util.stream.Collectors;

import dev.matheus.slothscout.config.SlothScoutProperties.Criteria;
import dev.matheus.slothscout.job.JobPosting;

/**
 * Prompts montados por concatenação, sem template: currículo e vaga podem ter chaves
 * e outros caracteres que um motor de template interpretaria.
 */
public final class Prompts {

	private Prompts() {
	}

	public static final String SCORING_SYSTEM = """
			Você é um triador de vagas de emprego. Avalia UMA vaga por vez contra o currículo e os critérios do candidato.

			Regras:
			1. Use SOMENTE o texto da vaga fornecido, incluindo o título. Ele é um trecho de um e-mail de alerta e costuma estar incompleto.
			2. Leia o título: palavras como Júnior, Jr, Estágio, Estagiário, Pleno, Sênior, Sr definem o nível.
			3. Se uma informação realmente não aparece no texto, aponte em "flags" qual é. Não invente e não aponte falta do que está escrito.
			4. Cada motivo em "reasons" cita entre aspas duplas uma palavra ou expressão copiada literalmente do texto da vaga.
			5. "flags" lista só problemas desta vaga. Vaga sem problema tem "flags" vazia.
			6. Exclusão confirmada pelo texto deixa a nota abaixo de 40. Cargo fora de tecnologia (vendas, atendimento, administrativo) fica abaixo de 20.

			Guia de nota:
			- 85 a 100: nível, local/modalidade e stack batem com os critérios.
			- 70 a 84: bate no nível e no local, com alguma lacuna pequena (ex.: stack não citada no trecho).
			- 40 a 69: ambígua; falta informação importante como o nível ou o local.
			- 0 a 39: viola um critério ou uma exclusão.

			Responda apenas com o JSON pedido, em português.
			""";

	public static String scoringUser(String resume, Criteria c, JobPosting job, String formatInstructions) {
		return "## Critérios do candidato\n" + criteria(c)
				+ "\n## Currículo\n" + resume.strip()
				+ "\n\n## Vaga (trecho do alerta)\n" + job.promptText()
				+ "\n" + formatInstructions;
	}

	public static String criteria(Criteria c) {
		return "- Níveis aceitos: " + String.join(", ", c.levels()) + "\n"
				+ "- Local: " + String.join(", ", c.onSiteCities()) + " (" + c.onSiteModes() + "), ou " + c.remote() + "\n"
				+ "- Stacks aceitas: " + String.join(", ", c.stacks()) + "\n"
				+ "- Máximo de anos de experiência exigidos: " + c.maxYearsExperience() + "\n"
				+ "- Exclusões: " + String.join("; ", c.exclusions()) + "\n";
	}

	public static final String DIGEST_SYSTEM = """
			Você escreve o resumo matinal de um agente que triou alertas de vaga durante a noite.
			Escreva EXATAMENTE duas linhas curtas, em português, tom direto e um pouco bem-humorado (o agente é uma preguiça).
			Linha 1: quantas valem a pena e quais (empresa e cargo), no espírito de "aplica nessas".
			Linha 2: o que ficou de fora e por quê, em poucas palavras, no espírito de "ignora o resto".
			Use apenas as informações fornecidas. Sem markdown, sem listas, sem aspas em volta.
			""";

	public static String digestUser(List<JobPosting> shortlist, List<JobPosting> ignored, int cutoff,
			List<String> commonFlags) {
		return "Nota de corte: " + cutoff + "\n\n"
				+ "APLICAR (" + shortlist.size() + " vagas, todas devem aparecer na linha 1):\n" + list(shortlist) + "\n\n"
				+ "IGNORAR (" + ignored.size() + " vagas, resuma na linha 2):\n" + list(ignored) + "\n"
				+ "Motivos mais comuns para ignorar: "
				+ (commonFlags.isEmpty() ? "(nenhum)" : String.join("; ", commonFlags)) + "\n";
	}

	private static String list(List<JobPosting> jobs) {
		if (jobs.isEmpty()) {
			return "(nenhuma)";
		}
		return jobs.stream()
				.map(j -> "- " + j.getTitle() + " na " + j.getCompany() + " (nota " + j.getScore() + ")")
				.collect(Collectors.joining("\n"));
	}
}
