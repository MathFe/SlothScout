package dev.matheus.slothscout.parse;

/** Uma vaga extraída de um e-mail de alerta, antes de ir para o banco. */
public record ParsedJob(
		String source,
		String externalId,
		String title,
		String company,
		String location,
		String workMode,
		String url,
		String snippet) {

	/** Chave de deduplicação: a mesma vaga em alertas diferentes cai na mesma chave. */
	public String key() {
		return source + ":" + externalId;
	}
}
