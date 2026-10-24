package dev.matheus.slothscout.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Alertas da Gupy: cada empresa tem um subdomínio, empresa.gupy.io/jobs/123456. */
@Component
class GupyAlertParser extends LinkAnchoredParser {

	private static final Pattern LINK = Pattern.compile("(?<sub>[\\w-]+)\\.gupy\\.io/(?:[\\w-]+/)?jobs?/(?<id>\\d+)");

	@Override
	public String source() {
		return "gupy";
	}

	@Override
	public boolean supports(String from) {
		return from.toLowerCase().contains("gupy");
	}

	@Override
	protected Pattern jobLink() {
		return LINK;
	}

	@Override
	protected String canonicalUrl(Matcher m) {
		return "https://" + m.group("sub") + ".gupy.io/jobs/" + m.group("id");
	}
}
