package dev.matheus.slothscout.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Alertas do LinkedIn: links do tipo linkedin.com/comm/jobs/view/1234567890. */
@Component
class LinkedInAlertParser extends LinkAnchoredParser {

	private static final Pattern LINK = Pattern.compile("linkedin\\.com/(?:comm/)?jobs/view/(?:[\\w-]*?-)?(?<id>\\d{6,})");

	@Override
	public String source() {
		return "linkedin";
	}

	@Override
	public boolean supports(String from) {
		return from.toLowerCase().contains("linkedin.com");
	}

	@Override
	protected Pattern jobLink() {
		return LINK;
	}

	@Override
	protected String canonicalUrl(Matcher m) {
		return "https://www.linkedin.com/jobs/view/" + m.group("id") + "/";
	}
}
