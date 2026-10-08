package com.ricardomukeka.incidentsentinel.investigation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.model.AppRegistry;
import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.AppRegistryRepository;
import com.ricardomukeka.incidentsentinel.repository.InvestigationBudgetRepository;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;

/**
 * Exceção pontual à convenção "sem Testcontainers" dos repos-irmãos
 * (docs/ARCHITECTURE.md, Estratégia de teste) — só aqui e no dedup de
 * TicketRepository, porque é exatamente o ponto onde Mockito não captura a
 * race condition real contra banco real.
 *
 * <p><b>Não executado no ambiente onde este arquivo foi escrito</b> (sem
 * Docker disponível na sessão) — {@code disabledWithoutDocker = true} faz o
 * JUnit pular a classe inteira, não falhar, quando não há Docker. Rodar de
 * verdade (com Docker) antes de considerar o passo 6 fechado.
 *
 * <p>Sem {@code @AutoConfigureTestDatabase(replace=NONE)} — essa anotação
 * não existe mais nesse formato no Boot 4 (achado na implementação), mas
 * também não faz falta aqui: só existiria embedded DB pra substituir se H2
 * estivesse no classpath, e não está (o projeto não usa H2, docs/ARCHITECTURE.md).
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
class InvestigationBudgetConcurrencyTest {

	@Container
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> mysql.getJdbcUrl() + "?connectionTimeZone=UTC&useAffectedRows=true");
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
	}

	@Autowired
	private InvestigationBudgetRepository investigationBudgetRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private AppRegistryRepository appRegistryRepository;

	@Test
	void incrementoAtomico_sobConcorrenciaReal_contadorFinalIgualAN() throws InterruptedException {
		LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
		investigationBudgetRepository.garantirLinhaDoDia(hoje);

		int n = 20;
		ExecutorService executor = Executors.newFixedThreadPool(n);
		CountDownLatch latch = new CountDownLatch(n);
		for (int i = 0; i < n; i++) {
			executor.submit(() -> {
				try {
					investigationBudgetRepository.incrementarContador(hoje);
				} finally {
					latch.countDown();
				}
			});
		}
		assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
		executor.shutdown();

		long contadorFinal = investigationBudgetRepository.findById(hoje).orElseThrow().getContador();
		assertThat(contadorFinal).isEqualTo(n);
	}

	@Test
	void duasRedeliveries_soDebitamOrcamentoUmaVez() {
		AppRegistry app = new AppRegistry();
		app.setServiceName("app-teste-orcamento");
		app.setDisplayName("App Teste");
		app.setAtivo(true);
		app.setCriadoEm(Instant.now());
		appRegistryRepository.save(app);

		Ticket ticket = new Ticket();
		ticket.setApp("app-teste-orcamento");
		ticket.setTipoErro(TipoErro.HTTP_5XX);
		ticket.setChaveDedup("a".repeat(64));
		ticket.setData(LocalDate.now(ZoneOffset.UTC));
		ticket.setStatus(TicketStatus.EM_INVESTIGACAO);
		ticket.setPrimeiraOcorrenciaTs(Instant.now());
		ticket.setUltimaOcorrenciaTs(Instant.now());
		ticket.setContagemOcorrencias(1);
		ticket.setOrcamentoDebitado(false);
		ticket = ticketRepository.save(ticket);

		int primeiraTentativa = ticketRepository.marcarOrcamentoDebitado(ticket.getId());
		int segundaTentativa = ticketRepository.marcarOrcamentoDebitado(ticket.getId());

		assertThat(primeiraTentativa).isEqualTo(1);
		assertThat(segundaTentativa).isEqualTo(0);
	}
}
