package dev.matheus.slothscout.score;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Saída estruturada da pontuação; o Spring AI gera o JSON Schema a partir deste record.
 * Os campos de verificação vêm antes da nota de propósito: o modelo escreve o que leu na
 * vaga primeiro e só depois dá a nota, o que segura um modelo pequeno de chutar.
 */
public record JobScore(
		@JsonPropertyDescription("Nível exigido conforme escrito no título ou no texto (ex.: o título diz Júnior); vazio se o texto não disser")
		String levelInText,
		@JsonPropertyDescription("Cidade e modalidade conforme escritas no texto; vazio se o texto não disser")
		String locationInText,
		@JsonPropertyDescription("true se o cargo é de tecnologia: desenvolvimento, dados, infraestrutura, QA ou suporte técnico")
		boolean techRole,
		@JsonPropertyDescription("Nota de 0 a 100 de aderência da vaga ao candidato e aos critérios")
		int score,
		@JsonPropertyDescription("2 a 3 motivos curtos; cada um cita entre aspas duplas uma palavra ou expressão literal da vaga")
		List<String> reasons,
		@JsonPropertyDescription("Problemas concretos desta vaga frente aos critérios. Lista vazia se não houver nenhum")
		List<String> flags) {

	public JobScore {
		score = Math.max(0, Math.min(100, score));
		reasons = reasons == null ? List.of() : reasons;
		flags = flags == null ? List.of() : flags;
	}
}
