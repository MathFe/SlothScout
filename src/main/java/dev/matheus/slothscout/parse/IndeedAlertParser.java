package dev.matheus.slothscout.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Alertas do Indeed: o ID da vaga vem no parâmetro jk= (às vezes dentro de um redirect). */
@Component
class IndeedAlertParser extends LinkAnchoredParser {

	private static final Pattern LINK = Pattern.compile("indeed\\.com/.*?[?&]jk=(?<id>[0-9a-f]{16})");

	@Override
	public String source() {
		return "indeed";
	}

	@Override
	public boolean supports(String from) {
		return from.toLowerCase().contains("indeed");
	}

	@Override
	protected Pattern jobLink() {
		return LINK;
	}

	@Override
	protected String canonicalUrl(Matcher m) {
		return "https://br.indeed.com/viewjob?jk=" + m.group("id");
	}
}
