package dev.matheus.slothscout.parse;

import java.util.Comparator;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Escolhe o parser pelo remetente. Remetente desconhecido (ex.: alerta encaminhado)
 * tenta todos e fica com o que achou mais vagas.
 */
@Component
public class AlertParsing {

	private final List<AlertParser> parsers;

	public AlertParsing(List<AlertParser> parsers) {
		this.parsers = parsers;
	}

	public List<ParsedJob> parse(String from, String html) {
		Document doc = Jsoup.parse(html);
		String sender = from == null ? "" : from;
		return parsers.stream()
				.filter(p -> p.supports(sender))
				.findFirst()
				.map(p -> p.parse(doc))
				.orElseGet(() -> parsers.stream()
						.map(p -> p.parse(doc))
						.max(Comparator.comparingInt(List::size))
						.orElse(List.of()));
	}

	public String sourceFor(String from) {
		String sender = from == null ? "" : from;
		return parsers.stream().filter(p -> p.supports(sender)).map(AlertParser::source).findFirst().orElse("unknown");
	}
}
