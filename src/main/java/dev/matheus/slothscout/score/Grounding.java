package dev.matheus.slothscout.score;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Confere se o motivo cita algo que existe de verdade na vaga. Não corrige o modelo,
 * só mostra na página quais motivos se sustentam no texto.
 */
public final class Grounding {

	private static final Pattern QUOTED = Pattern.compile("[\"“”'‘’«»]([^\"“”'‘’«»]{2,120})[\"“”'‘’«»]");

	private Grounding() {
	}

	public static List<String> quotes(String reason) {
		List<String> out = new ArrayList<>();
		Matcher m = QUOTED.matcher(reason);
		while (m.find()) {
			out.add(m.group(1).trim());
		}
		return out;
	}

	/**
	 * Verdadeiro se todas as citações aparecem no texto da vaga. Sem aspas, vale o motivo
	 * inteiro: modelos pequenos às vezes devolvem só o trecho, sem as aspas.
	 */
	public static boolean isGrounded(String reason, String jobText) {
		String haystack = normalize(jobText);
		List<String> quotes = quotes(reason);
		if (quotes.isEmpty()) {
			String whole = normalize(reason);
			return whole.length() >= 2 && haystack.contains(whole);
		}
		return quotes.stream().allMatch(q -> haystack.contains(normalize(q)));
	}

	static String normalize(String s) {
		String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}
}
