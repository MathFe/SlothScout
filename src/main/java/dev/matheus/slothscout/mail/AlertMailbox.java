package dev.matheus.slothscout.mail;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.matheus.slothscout.config.SlothScoutProperties;

/**
 * Lê só a pasta/marcador dos alertas, em modo somente leitura: não marca nada como lido.
 * O controle do que já foi processado fica no banco local (por Message-ID).
 */
@Component
public class AlertMailbox {

	private static final Logger log = LoggerFactory.getLogger(AlertMailbox.class);

	private final SlothScoutProperties.Imap imap;

	public AlertMailbox(SlothScoutProperties props) {
		this.imap = props.imap();
	}

	public boolean configured() {
		return imap.configured();
	}

	public List<RawAlert> fetch(Predicate<String> alreadyProcessed) throws MessagingException {
		Properties p = new Properties();
		p.put("mail.store.protocol", "imaps");
		p.put("mail.imaps.host", imap.host());
		p.put("mail.imaps.port", String.valueOf(imap.port()));
		p.put("mail.imaps.ssl.enable", "true");
		p.put("mail.imaps.connectiontimeout", "15000");
		p.put("mail.imaps.timeout", "30000");

		Session session = Session.getInstance(p);
		List<RawAlert> alerts = new ArrayList<>();
		try (Store store = session.getStore("imaps")) {
			store.connect(imap.host(), imap.port(), imap.username(), imap.password());
			Folder folder = store.getFolder(imap.folder());
			if (!folder.exists()) {
				throw new MessagingException("Pasta IMAP não encontrada: " + imap.folder());
			}
			folder.open(Folder.READ_ONLY);
			try {
				Date since = Date.from(Instant.now().minus(Duration.ofDays(imap.lookbackDays())));
				Message[] messages = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, since));
				log.info("{} mensagens em '{}' nos últimos {} dias", messages.length, imap.folder(), imap.lookbackDays());
				for (Message m : messages) {
					if (alerts.size() >= imap.maxMessages()) {
						break;
					}
					String id = messageId(m);
					if (alreadyProcessed.test(id)) {
						continue;
					}
					String html = htmlOf(m);
					if (html == null) {
						log.debug("Sem corpo HTML: {}", m.getSubject());
						continue;
					}
					Date received = m.getReceivedDate();
					alerts.add(new RawAlert(id, fromOf(m), m.getSubject(),
							received != null ? received.toInstant() : Instant.now(), html));
				}
			}
			finally {
				folder.close(false);
			}
		}
		return alerts;
	}

	private static String messageId(Message m) throws MessagingException {
		String[] ids = m.getHeader("Message-ID");
		if (ids != null && ids.length > 0) {
			return ids[0].trim();
		}
		// Fallback estável quando o remetente não manda Message-ID.
		return "no-id:" + fromOf(m) + ":" + m.getReceivedDate() + ":" + m.getSubject();
	}

	private static String fromOf(Message m) throws MessagingException {
		Address[] from = m.getFrom();
		if (from == null || from.length == 0) {
			return "";
		}
		return from[0] instanceof InternetAddress ia ? ia.getAddress() : from[0].toString();
	}

	/** Procura a parte text/html, descendo em multiparts. */
	static String htmlOf(Part part) throws MessagingException {
		try {
			if (part.isMimeType("text/html")) {
				return (String) part.getContent();
			}
			if (part.isMimeType("multipart/*")) {
				Multipart mp = (Multipart) part.getContent();
				for (int i = 0; i < mp.getCount(); i++) {
					String html = htmlOf(mp.getBodyPart(i));
					if (html != null) {
						return html;
					}
				}
			}
			return null;
		}
		catch (IOException e) {
			throw new MessagingException("Falha lendo corpo do e-mail", e);
		}
	}
}
