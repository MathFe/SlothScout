package dev.matheus.slothscout.mail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.matheus.slothscout.config.SlothScoutProperties;

/**
 * Lê alertas de arquivos .html em vez do IMAP. Os nomes seguem o padrão do dump-html
 * ("linkedin-123.html"), então dá para salvar os e-mails reais uma vez e reprocessar offline:
 * bom para ajustar parser, comparar modelos e gravar a demo.
 */
@Component
public class ReplayInbox {

	private final Path dir;

	public ReplayInbox(SlothScoutProperties props) {
		this.dir = props.run().replayDir();
	}

	public boolean configured() {
		return dir != null && Files.isDirectory(dir);
	}

	public List<RawAlert> fetch(Predicate<String> alreadyProcessed) {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(f -> f.toString().endsWith(".html"))
					.sorted()
					.map(this::toAlert)
					.filter(a -> !alreadyProcessed.test(a.messageId()))
					.toList();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private RawAlert toAlert(Path file) {
		try {
			String name = file.getFileName().toString();
			String source = name.contains("-") ? name.substring(0, name.indexOf('-')) : "unknown";
			return new RawAlert("replay:" + name, "alerts@" + source + ".com", name,
					Files.getLastModifiedTime(file).toInstant(), Files.readString(file));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
