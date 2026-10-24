package dev.matheus.slothscout.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroundingTest {

	private static final String JOB = "Título: Desenvolvedor Java Júnior\nLocal: São José dos Campos, SP (Híbrido)\n";

	@Test
	void quoteFoundInJobIsGroundedIgnoringCaseAndAccents() {
		assertThat(Grounding.isGrounded("Stack bate: \"java júnior\"", JOB)).isTrue();
		assertThat(Grounding.isGrounded("Fica em “Sao Jose dos Campos”", JOB)).isTrue();
	}

	@Test
	void bareSnippetCopiedFromJobIsGrounded() {
		assertThat(Grounding.isGrounded("Desenvolvedor Java", JOB)).isTrue();
	}

	@Test
	void inventedQuoteOrFreeTextIsNotGrounded() {
		assertThat(Grounding.isGrounded("Pede \"Kubernetes\"", JOB)).isFalse();
		assertThat(Grounding.isGrounded("Vaga boa para iniciante", JOB)).isFalse();
	}
}
