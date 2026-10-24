package dev.matheus.slothscout.parse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Base dos parsers: e-mails de alerta mudam de layout o tempo todo, mas o link da vaga
 * (com o ID dela) é estável. Então ancoramos em cada link de vaga, subimos até o maior
 * bloco que só fala daquela vaga (o "card") e lemos as linhas de texto dele.
 */
abstract class LinkAnchoredParser implements AlertParser {

	private static final Pattern MODE_REMOTE = Pattern.compile("\\b(remot[oa]|remote|home ?office|100% remoto)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static final Pattern MODE_HYBRID = Pattern.compile("\\b(h[ií]brid[oa]|hybrid)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static final Pattern MODE_ONSITE = Pattern.compile("\\b(presencial|on-site|onsite|no local)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	/** Textos de botão/selo que não são título, empresa nem local. */
	private static final Set<String> NOISE = Set.of("ver vaga", "ver vagas", "candidatar-se", "candidatar",
			"candidatura simplificada", "easy apply", "apply now", "apply", "promovida", "promoted", "novo", "new",
			"ver todas as vagas", "see all jobs", "view job", "ver mais", "salvar", "save");

	/** Regex do link da vaga. O grupo "id" vira o ID externo. */
	protected abstract Pattern jobLink();

	protected abstract String canonicalUrl(Matcher match);

	@Override
	public List<ParsedJob> parse(Document doc) {
		Map<String, Card> cards = new LinkedHashMap<>();
		for (Element a : doc.select("a[href]")) {
			Optional<Matcher> m = match(a.attr("href"));
			if (m.isEmpty()) {
				continue;
			}
			String id = m.get().group("id");
			cards.computeIfAbsent(id, k -> new Card(m.get())).anchors.add(a);
		}

		List<ParsedJob> jobs = new ArrayList<>();
		for (Map.Entry<String, Card> e : cards.entrySet()) {
			jobs.add(toJob(e.getKey(), e.getValue()));
		}
		return jobs;
	}

	private ParsedJob toJob(String id, Card card) {
		Element block = cardBlock(card.anchors.getFirst(), id);
		List<String> lines = lines(block);

		String title = card.anchors.stream()
				.map(a -> clean(a.text()))
				.filter(t -> !t.isEmpty() && !isNoise(t))
				.findFirst()
				.orElse(lines.isEmpty() ? "" : lines.getFirst());

		List<String> rest = new ArrayList<>(lines);
		rest.removeIf(l -> l.equalsIgnoreCase(title));

		String company = null;
		String location = null;
		if (!rest.isEmpty() && rest.getFirst().contains(" · ")) {
			String[] parts = rest.removeFirst().split(" · ", 2);
			company = parts[0].trim();
			location = parts[1].trim();
		}
		else {
			if (!rest.isEmpty()) {
				company = rest.removeFirst();
			}
			if (!rest.isEmpty()) {
				location = rest.removeFirst();
			}
		}

		String snippet = String.join(" ", rest);
		if (snippet.length() > 1500) {
			snippet = snippet.substring(0, 1500) + "…";
		}
		String all = String.join(" ", lines);
		return new ParsedJob(source(), id, title, company, location, workMode(all), canonicalUrl(card.match), snippet);
	}

	private Optional<Matcher> match(String href) {
		for (String candidate : new String[] { href, decode(href), decode(decode(href)) }) {
			Matcher m = jobLink().matcher(candidate);
			if (m.find()) {
				return Optional.of(m);
			}
		}
		return Optional.empty();
	}

	/** Sobe enquanto o ancestral só tiver links desta mesma vaga. */
	private Element cardBlock(Element anchor, String id) {
		Element best = anchor;
		for (Element p = anchor.parent(); p != null && !"body".equals(p.tagName()); p = p.parent()) {
			Set<String> ids = jobIdsIn(p);
			if (ids.size() == 1 && ids.contains(id)) {
				best = p;
			}
			else {
				break;
			}
		}
		return best;
	}

	private Set<String> jobIdsIn(Element el) {
		Set<String> ids = new LinkedHashSet<>();
		for (Element a : el.select("a[href]")) {
			match(a.attr("href")).ifPresent(m -> ids.add(m.group("id")));
		}
		return ids;
	}

	/** Linhas de texto visíveis do card, sem repetição e sem texto de botão. */
	static List<String> lines(Element block) {
		Set<String> out = new LinkedHashSet<>();
		for (Element el : block.getAllElements()) {
			if ("style".equals(el.tagName()) || "script".equals(el.tagName())) {
				continue;
			}
			String own = clean(el.ownText());
			if (!own.isEmpty() && !isNoise(own) && own.length() > 1) {
				out.add(own);
			}
		}
		return new ArrayList<>(out);
	}

	static String workMode(String text) {
		if (MODE_HYBRID.matcher(text).find()) {
			return "Híbrido";
		}
		if (MODE_REMOTE.matcher(text).find()) {
			return "Remoto";
		}
		if (MODE_ONSITE.matcher(text).find()) {
			return "Presencial";
		}
		return null;
	}

	static String clean(String s) {
		return s == null ? "" : s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
	}

	private static boolean isNoise(String s) {
		return NOISE.contains(s.toLowerCase(Locale.ROOT));
	}

	private static String decode(String s) {
		try {
			return URLDecoder.decode(s, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException e) {
			return s;
		}
	}

	private static final class Card {

		final Matcher match;

		final List<Element> anchors = new ArrayList<>();

		Card(Matcher match) {
			this.match = match;
		}
	}
}
