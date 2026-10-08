package com.ricardomukeka.incidentsentinel.model;

import java.time.Instant;
import java.time.LocalDate;

import com.ricardomukeka.incidentsentinel.enums.AvaliacaoCausa;
import com.ricardomukeka.incidentsentinel.enums.AvaliacaoSolucao;
import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Tíquete interno gerado pela investigação (docs/ARCHITECTURE.md). A unicidade
 * (app, tipo_erro, chave_dedup, data) sustenta a regra de dedup — a
 * gravação/associação passa por INSERT ... ON DUPLICATE KEY UPDATE nativo
 * (TicketRepository), nunca por capturar violação de constraint na mesma
 * transação Spring/JPA (docs/ARCHITECTURE.md).
 *
 * <p>Dois grupos de campos com donos diferentes:
 * <ul>
 * <li><b>Saída da investigação</b> ({@code causa*}, {@code solucao_proposta},
 * {@code confianca_*}, {@code investigado_em}, {@code fontes_consultadas}) —
 * o sistema escreve uma vez, é sempre hipótese.</li>
 * <li><b>Registro de resolução</b> ({@code resolvido_*}, {@code avaliacao_*},
 * {@code *_efetiva}) — o humano escreve depois, é autoritativo. Na v1 esses
 * campos são gravados mas não lidos: são o corpus do Failure Book RAG da v2
 * (docs/ARCHITECTURE.md).</li>
 * </ul>
 */
@Entity
@Table(name = "ticket",
		uniqueConstraints = @UniqueConstraint(name = "uk_ticket_dedup", columnNames = { "app", "tipo_erro", "chave_dedup", "data" }),
		indexes = {
				@Index(name = "idx_ticket_status", columnList = "status"),
				@Index(name = "idx_ticket_data", columnList = "data")
		})
public class Ticket {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String app;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_erro", nullable = false)
	private TipoErro tipoErro;

	@Column(name = "chave_dedup", length = 64, nullable = false)
	private String chaveDedup;

	@Column(name = "trace_id", length = 128)
	private String traceId;

	@Column(nullable = false)
	private LocalDate data;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketStatus status;

	// --- Saída da investigação (sistema escreve, uma vez, hipótese) ---

	@Column(name = "causa_raiz", columnDefinition = "TEXT")
	private String causaRaiz;

	@Enumerated(EnumType.STRING)
	@Column(name = "causa_categoria", length = 30)
	private CausaCategoria causaCategoria;

	@Enumerated(EnumType.STRING)
	@Column(name = "confianca_causa", length = 10)
	private ConfiancaDiagnostico confiancaCausa;

	@Column(name = "solucao_proposta", columnDefinition = "TEXT")
	private String solucaoProposta;

	@Enumerated(EnumType.STRING)
	@Column(name = "confianca_solucao", length = 10)
	private ConfiancaDiagnostico confiancaSolucao;

	@Column(name = "investigado_em")
	private Instant investigadoEm;

	/** JSON bruto — shape documentado em docs/ARCHITECTURE.md ({"signoz_trace": "consultada"|"indisponivel"|"pulada", ...}). */
	@Column(name = "fontes_consultadas", columnDefinition = "JSON")
	private String fontesConsultadas;

	// --- Registro de resolução (humano escreve, depois, autoritativo — v1 grava, não lê) ---

	@Column(name = "resolvido_em")
	private Instant resolvidoEm;

	@Column(name = "resolvido_por")
	private String resolvidoPor;

	@Enumerated(EnumType.STRING)
	@Column(name = "avaliacao_causa", length = 20)
	private AvaliacaoCausa avaliacaoCausa;

	@Column(name = "causa_efetiva", columnDefinition = "TEXT")
	private String causaEfetiva;

	@Enumerated(EnumType.STRING)
	@Column(name = "avaliacao_solucao", length = 20)
	private AvaliacaoSolucao avaliacaoSolucao;

	@Column(name = "solucao_efetiva", columnDefinition = "TEXT")
	private String solucaoEfetiva;

	// --- Ocorrências e controle ---

	@Column(name = "primeira_ocorrencia_ts", nullable = false)
	private Instant primeiraOcorrenciaTs;

	@Column(name = "ultima_ocorrencia_ts", nullable = false)
	private Instant ultimaOcorrenciaTs;

	@Column(name = "contagem_ocorrencias", nullable = false)
	private Integer contagemOcorrencias;

	@Column(name = "orcamento_debitado", nullable = false)
	private Boolean orcamentoDebitado = Boolean.FALSE;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getApp() {
		return app;
	}

	public void setApp(String app) {
		this.app = app;
	}

	public TipoErro getTipoErro() {
		return tipoErro;
	}

	public void setTipoErro(TipoErro tipoErro) {
		this.tipoErro = tipoErro;
	}

	public String getChaveDedup() {
		return chaveDedup;
	}

	public void setChaveDedup(String chaveDedup) {
		this.chaveDedup = chaveDedup;
	}

	public String getTraceId() {
		return traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	public LocalDate getData() {
		return data;
	}

	public void setData(LocalDate data) {
		this.data = data;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public void setStatus(TicketStatus status) {
		this.status = status;
	}

	public String getCausaRaiz() {
		return causaRaiz;
	}

	public void setCausaRaiz(String causaRaiz) {
		this.causaRaiz = causaRaiz;
	}

	public CausaCategoria getCausaCategoria() {
		return causaCategoria;
	}

	public void setCausaCategoria(CausaCategoria causaCategoria) {
		this.causaCategoria = causaCategoria;
	}

	public ConfiancaDiagnostico getConfiancaCausa() {
		return confiancaCausa;
	}

	public void setConfiancaCausa(ConfiancaDiagnostico confiancaCausa) {
		this.confiancaCausa = confiancaCausa;
	}

	public String getSolucaoProposta() {
		return solucaoProposta;
	}

	public void setSolucaoProposta(String solucaoProposta) {
		this.solucaoProposta = solucaoProposta;
	}

	public ConfiancaDiagnostico getConfiancaSolucao() {
		return confiancaSolucao;
	}

	public void setConfiancaSolucao(ConfiancaDiagnostico confiancaSolucao) {
		this.confiancaSolucao = confiancaSolucao;
	}

	public Instant getInvestigadoEm() {
		return investigadoEm;
	}

	public void setInvestigadoEm(Instant investigadoEm) {
		this.investigadoEm = investigadoEm;
	}

	public String getFontesConsultadas() {
		return fontesConsultadas;
	}

	public void setFontesConsultadas(String fontesConsultadas) {
		this.fontesConsultadas = fontesConsultadas;
	}

	public Instant getResolvidoEm() {
		return resolvidoEm;
	}

	public void setResolvidoEm(Instant resolvidoEm) {
		this.resolvidoEm = resolvidoEm;
	}

	public String getResolvidoPor() {
		return resolvidoPor;
	}

	public void setResolvidoPor(String resolvidoPor) {
		this.resolvidoPor = resolvidoPor;
	}

	public AvaliacaoCausa getAvaliacaoCausa() {
		return avaliacaoCausa;
	}

	public void setAvaliacaoCausa(AvaliacaoCausa avaliacaoCausa) {
		this.avaliacaoCausa = avaliacaoCausa;
	}

	public String getCausaEfetiva() {
		return causaEfetiva;
	}

	public void setCausaEfetiva(String causaEfetiva) {
		this.causaEfetiva = causaEfetiva;
	}

	public AvaliacaoSolucao getAvaliacaoSolucao() {
		return avaliacaoSolucao;
	}

	public void setAvaliacaoSolucao(AvaliacaoSolucao avaliacaoSolucao) {
		this.avaliacaoSolucao = avaliacaoSolucao;
	}

	public String getSolucaoEfetiva() {
		return solucaoEfetiva;
	}

	public void setSolucaoEfetiva(String solucaoEfetiva) {
		this.solucaoEfetiva = solucaoEfetiva;
	}

	public Instant getPrimeiraOcorrenciaTs() {
		return primeiraOcorrenciaTs;
	}

	public void setPrimeiraOcorrenciaTs(Instant primeiraOcorrenciaTs) {
		this.primeiraOcorrenciaTs = primeiraOcorrenciaTs;
	}

	public Instant getUltimaOcorrenciaTs() {
		return ultimaOcorrenciaTs;
	}

	public void setUltimaOcorrenciaTs(Instant ultimaOcorrenciaTs) {
		this.ultimaOcorrenciaTs = ultimaOcorrenciaTs;
	}

	public Integer getContagemOcorrencias() {
		return contagemOcorrencias;
	}

	public void setContagemOcorrencias(Integer contagemOcorrencias) {
		this.contagemOcorrencias = contagemOcorrencias;
	}

	public Boolean getOrcamentoDebitado() {
		return orcamentoDebitado;
	}

	public void setOrcamentoDebitado(Boolean orcamentoDebitado) {
		this.orcamentoDebitado = orcamentoDebitado;
	}
}
