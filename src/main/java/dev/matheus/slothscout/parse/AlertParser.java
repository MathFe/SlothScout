package dev.matheus.slothscout.parse;

import java.util.List;

import org.jsoup.nodes.Document;

/** Um parser por remetente: cada site tem um layout de alerta diferente. */
public interface AlertParser {

	String source();

	boolean supports(String fromAddress);

	List<ParsedJob> parse(Document html);
}
