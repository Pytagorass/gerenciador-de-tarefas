package br.edu.ifmt.tarefas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.HtmlUtils;

import br.edu.ifmt.tarefas.model.Prioridade;
import br.edu.ifmt.tarefas.model.StatusTarefa;
import br.edu.ifmt.tarefas.model.Tarefa;
import br.edu.ifmt.tarefas.repository.TarefaRepository;
import br.edu.ifmt.tarefas.service.TarefaService;
import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TarefaEdicaoFinalizacaoTests {

    private static final Instant INSTANTE = Instant.parse("2030-12-01T18:35:00Z");
    private static final LocalDateTime AGORA_CUIABA = LocalDateTime.of(2030, 12, 1, 14, 35);
    private static final LocalDateTime DATA_ORIGINAL = LocalDateTime.of(2029, 5, 10, 8, 15);
    private static final long ID_INEXISTENTE = Long.MAX_VALUE;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TarefaRepository tarefaRepository;

    @Autowired
    private TarefaService tarefaService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @MockitoBean(name = "clock")
    private Clock clock;

    @BeforeEach
    void controlarHorarioNoFusoDeCuiaba() {
        when(clock.getZone()).thenReturn(ZoneId.of("America/Cuiaba"));
        when(clock.instant()).thenReturn(INSTANTE);
    }

    @Test
    void deveAbrirEdicaoComDadosPreenchidosEAcaoCorreta() throws Exception {
        var tarefa = tarefaRepository.save(novaTarefa("Estudar edição", StatusTarefa.EM_ANDAMENTO));

        var resultado = mvc.perform(get("/tarefas/{id}/editar", tarefa.getId())
                        .with(usuarioAutenticado()))
                .andExpect(status().isOk())
                .andExpect(view().name("tarefas/cadastro"))
                .andExpect(model().attribute("edicao", true))
                .andExpect(model().attribute("idTarefa", tarefa.getId()))
                .andExpect(model().attribute("tarefa", tarefa))
                .andReturn();

        String html = html(resultado);
        assertThat(html).contains("Editar tarefa", "Salvar alterações", "Sair",
                "value=\"Estudar edição\"", "Descrição original", "value=\"2030-12-01\"");
        assertThat(html).containsPattern("<option(?=[^>]*value=\"ALTA\")(?=[^>]*selected)[^>]*>");
        assertThat(html).containsPattern("<option(?=[^>]*value=\"EM_ANDAMENTO\")(?=[^>]*selected)[^>]*>");
        assertThat(html).containsPattern("(?s)<a\\b[^>]*href=\"/tarefas\"[^>]*>\\s*Cancelar\\s*</a>");
        tokenDoFormulario(resultado, "/tarefas/" + tarefa.getId() + "/editar");
        tokenDoFormulario(resultado, "/logout");
        assertThat(tarefaRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void finalizacaoNaEdicaoDeveSerSomenteInformativa(boolean registrada) throws Exception {
        var tarefa = novaTarefa("Consultar finalização", StatusTarefa.CONCLUIDA);
        tarefa.setDataFinalizacao(registrada ? DATA_ORIGINAL : null);
        tarefaRepository.save(tarefa);

        var resultado = mvc.perform(get("/tarefas/{id}/editar", tarefa.getId())
                        .with(usuarioAutenticado()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(html(resultado)).contains(registrada ? "10/05/2029 08:15" : "Não registrada")
                .contains("A data é preenchida automaticamente ao concluir a tarefa.")
                .doesNotContain("name=\"dataFinalizacao\"", "name=\"id\"");
    }

    @Test
    void listagemDeveExibirFinalizacaoOuTracoELinkDeEdicao() throws Exception {
        var concluida = novaTarefa("Atividade concluída", StatusTarefa.CONCLUIDA);
        concluida.setDataFinalizacao(DATA_ORIGINAL);
        tarefaRepository.save(concluida);
        tarefaRepository.save(novaTarefa("Atividade aberta", StatusTarefa.A_FAZER));

        var resultado = mvc.perform(get("/tarefas").with(usuarioAutenticado()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(html(resultado)).contains("Finalização", "Ações", "10/05/2029 08:15", "—",
                "href=\"/tarefas/" + concluida.getId() + "/editar\"", "status-CONCLUIDA");
    }

    @Test
    void deveAtualizarCincoCamposMantendoIdEQuantidadeComCsrfDoFormulario() throws Exception {
        var tarefa = tarefaRepository.save(novaTarefa("Nome original", StatusTarefa.A_FAZER));
        var outra = tarefaRepository.save(novaTarefa("Outra tarefa", StatusTarefa.A_FAZER));
        Long id = tarefa.getId();
        Long outroId = outra.getId();
        var pagina = mvc.perform(get("/tarefas/{id}/editar", id).with(usuarioAutenticado()))
                .andExpect(status().isOk()).andReturn();
        var dados = dadosValidos(StatusTarefa.EM_ANDAMENTO);
        dados.set("nome", "  Nome atualizado  ");
        dados.set("descricao", "  Descrição atualizada  ");
        dados.set("dataLimite", "2031-02-15");
        dados.set("prioridade", "BAIXA");

        mvc.perform(post("/tarefas/{id}/editar", id).with(usuarioAutenticado())
                        .session((MockHttpSession) pagina.getRequest().getSession())
                        .params(dados)
                        .param("_csrf", tokenDoFormulario(pagina, "/tarefas/" + id + "/editar")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tarefas"))
                .andExpect(flash().attribute("mensagem", "Tarefa atualizada com sucesso!"));

        var atualizada = recarregar(id);
        assertThat(atualizada.getId()).isEqualTo(id);
        assertThat(atualizada.getNome()).isEqualTo("Nome atualizado");
        assertThat(atualizada.getDescricao()).isEqualTo("Descrição atualizada");
        assertThat(atualizada.getDataLimite()).isEqualTo(LocalDate.of(2031, 2, 15));
        assertThat(atualizada.getPrioridade()).isEqualTo(Prioridade.BAIXA);
        assertThat(atualizada.getStatus()).isEqualTo(StatusTarefa.EM_ANDAMENTO);
        assertThat(atualizada.getDataFinalizacao()).isNull();
        assertThat(tarefaRepository.count()).isEqualTo(2);
        assertThat(tarefaRepository.findById(outroId).orElseThrow().getNome()).isEqualTo("Outra tarefa");
    }

    @Test
    void dadosInvalidosDevemManterValoresModoAcaoEDadosPersistidos() throws Exception {
        var tarefa = novaTarefa("Nome preservado", StatusTarefa.CONCLUIDA);
        tarefa.setDataFinalizacao(DATA_ORIGINAL);
        tarefaRepository.saveAndFlush(tarefa);
        Long id = tarefa.getId();
        var dados = dadosValidos(StatusTarefa.A_FAZER);
        dados.set("nome", "");
        dados.set("descricao", "Descrição digitada para corrigir");

        var resultado = mvc.perform(post("/tarefas/{id}/editar", id)
                        .with(usuarioAutenticado()).with(csrf()).params(dados))
                .andExpect(status().isOk())
                .andExpect(view().name("tarefas/cadastro"))
                .andExpect(model().attributeHasFieldErrors("tarefa", "nome"))
                .andExpect(model().attribute("edicao", true))
                .andExpect(model().attribute("idTarefa", id))
                .andExpect(model().attribute("dataFinalizacao", DATA_ORIGINAL))
                .andReturn();

        assertThat(html(resultado)).contains("Editar tarefa", "Salvar alterações",
                "Descrição digitada para corrigir", "10/05/2029 08:15");
        tokenDoFormulario(resultado, "/tarefas/" + id + "/editar");
        var preenchida = (Tarefa) resultado.getModelAndView().getModel().get("tarefa");
        assertThat(preenchida.getNome()).isEmpty();
        assertThat(preenchida.getStatus()).isEqualTo(StatusTarefa.A_FAZER);

        var persistida = recarregar(id);
        assertThat(persistida.getNome()).isEqualTo("Nome preservado");
        assertThat(persistida.getDescricao()).isEqualTo("Descrição original");
        assertThat(persistida.getStatus()).isEqualTo(StatusTarefa.CONCLUIDA);
        assertThat(persistida.getDataFinalizacao()).isEqualTo(DATA_ORIGINAL);
        assertThat(tarefaRepository.count()).isEqualTo(1);
    }

    @Test
    void erroDeConversaoTambemDevePreservarEdicaoEValorRejeitado() throws Exception {
        var tarefa = tarefaRepository.save(novaTarefa("Tarefa válida", StatusTarefa.A_FAZER));
        var dados = dadosValidos(StatusTarefa.A_FAZER);
        dados.set("dataLimite", "data-invalida");
        dados.set("prioridade", "INEXISTENTE");

        var resultado = mvc.perform(post("/tarefas/{id}/editar", tarefa.getId())
                        .with(usuarioAutenticado()).with(csrf()).params(dados))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("tarefa", "dataLimite", "prioridade"))
                .andExpect(model().attribute("edicao", true))
                .andReturn();

        assertThat(html(resultado)).contains("value=\"data-invalida\"", "Salvar alterações");
        tokenDoFormulario(resultado, "/tarefas/" + tarefa.getId() + "/editar");
        var persistida = recarregar(tarefa.getId());
        assertThat(persistida.getDataLimite()).isEqualTo(LocalDate.of(2030, 12, 1));
        assertThat(persistida.getPrioridade()).isEqualTo(Prioridade.ALTA);
    }

    @Test
    void getDeIdInexistenteDeveRetornar404ComPaginaAmigavel() throws Exception {
        mvc.perform(get("/tarefas/{id}/editar", ID_INEXISTENTE).with(usuarioAutenticado()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(containsString("Tarefa não encontrada")))
                .andExpect(content().string(containsString("Voltar para tarefas")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void postDeIdInexistenteDeveRetornar404MesmoComDadosInvalidos(boolean valido) throws Exception {
        var dados = dadosValidos(StatusTarefa.A_FAZER);
        if (!valido) {
            dados.set("nome", "");
        }

        mvc.perform(post("/tarefas/{id}/editar", ID_INEXISTENTE)
                        .with(usuarioAutenticado()).with(csrf()).params(dados))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));

        assertThat(tarefaRepository.count()).isZero();
    }

    @Test
    void visitanteNaoDeveAbrirEdicao() throws Exception {
        mvc.perform(get("/tarefas/1/editar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void visitanteNaoDeveAtualizarMesmoComCsrf() throws Exception {
        mvc.perform(post("/tarefas/1/editar").with(csrf()).params(dadosValidos(StatusTarefa.A_FAZER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(tarefaRepository.count()).isZero();
    }

    @Test
    void postSemCsrfOuComTokenInvalidoNaoDeveModificarTarefa() throws Exception {
        var tarefa = tarefaRepository.save(novaTarefa("Nome preservado", StatusTarefa.A_FAZER));

        mvc.perform(post("/tarefas/{id}/editar", tarefa.getId()).with(usuarioAutenticado())
                        .params(dadosValidos(StatusTarefa.CONCLUIDA)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/tarefas/{id}/editar", tarefa.getId()).with(usuarioAutenticado())
                        .with(csrf().useInvalidToken()).params(dadosValidos(StatusTarefa.CONCLUIDA)))
                .andExpect(status().isForbidden());

        var persistida = recarregar(tarefa.getId());
        assertThat(persistida.getNome()).isEqualTo("Nome preservado");
        assertThat(persistida.getStatus()).isEqualTo(StatusTarefa.A_FAZER);
        assertThat(persistida.getDataFinalizacao()).isNull();
    }

    @ParameterizedTest
    @EnumSource(StatusTarefa.class)
    void edicaoDeveIgnorarIdEFinalizacaoDoCorpo(StatusTarefa statusNovo) throws Exception {
        var tarefa = novaTarefa("Editar esta tarefa", StatusTarefa.CONCLUIDA);
        tarefa.setDataFinalizacao(DATA_ORIGINAL);
        tarefaRepository.save(tarefa);
        var outra = tarefaRepository.save(novaTarefa("Não modificar esta tarefa", StatusTarefa.A_FAZER));
        Long id = tarefa.getId();
        Long outroId = outra.getId();
        var dados = dadosValidos(statusNovo);
        dados.add("id", outroId.toString());
        dados.add("dataFinalizacao", "valor-invalido-que-nao-deve-ser-convertido");

        mvc.perform(post("/tarefas/{id}/editar", id).with(usuarioAutenticado())
                        .with(csrf()).params(dados))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tarefas"));

        var persistida = recarregar(id);
        assertThat(persistida.getId()).isEqualTo(id);
        assertThat(persistida.getDataFinalizacao())
                .isEqualTo(statusNovo == StatusTarefa.CONCLUIDA ? DATA_ORIGINAL : null);
        assertThat(tarefaRepository.count()).isEqualTo(2);
        assertThat(tarefaRepository.findById(outroId).orElseThrow().getNome())
                .isEqualTo("Não modificar esta tarefa");
    }

    @ParameterizedTest
    @EnumSource(StatusTarefa.class)
    void cadastroTambemDeveIgnorarIdEFinalizacaoDoFormulario(StatusTarefa statusNovo) throws Exception {
        var existente = tarefaRepository.save(novaTarefa("Tarefa existente", StatusTarefa.A_FAZER));
        Long idExistente = existente.getId();
        var dados = dadosValidos(statusNovo);
        dados.add("id", idExistente.toString());
        dados.add("dataFinalizacao", "valor-invalido-que-nao-deve-ser-convertido");

        mvc.perform(post("/tarefas").with(usuarioAutenticado()).with(csrf()).params(dados))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tarefas"));

        tarefaRepository.flush();
        entityManager.clear();
        assertThat(tarefaRepository.count()).isEqualTo(2);
        assertThat(tarefaRepository.findById(idExistente).orElseThrow().getNome())
                .isEqualTo("Tarefa existente");
        var cadastrada = tarefaRepository.findAll().stream()
                .filter(tarefa -> !tarefa.getId().equals(idExistente)).findFirst().orElseThrow();
        assertThat(cadastrada.getDataFinalizacao())
                .isEqualTo(statusNovo == StatusTarefa.CONCLUIDA ? AGORA_CUIABA : null);
    }

    @ParameterizedTest
    @EnumSource(StatusTarefa.class)
    void cadastroNoServiceDeveCalcularFinalizacaoPeloStatusEClock(StatusTarefa statusNovo) {
        var dados = novaTarefa("Nova tarefa", statusNovo);
        dados.setId(ID_INEXISTENTE);
        dados.setDataFinalizacao(DATA_ORIGINAL);

        var cadastrada = recarregar(tarefaService.cadastrar(dados).getId());

        assertThat(cadastrada.getId()).isNotEqualTo(ID_INEXISTENTE);
        assertThat(cadastrada.getDataFinalizacao())
                .isEqualTo(statusNovo == StatusTarefa.CONCLUIDA ? AGORA_CUIABA : null);
    }

    @ParameterizedTest
    @CsvSource({
            "A_FAZER, A_FAZER", "A_FAZER, EM_ANDAMENTO", "A_FAZER, CONCLUIDA",
            "EM_ANDAMENTO, A_FAZER", "EM_ANDAMENTO, EM_ANDAMENTO", "EM_ANDAMENTO, CONCLUIDA",
            "CONCLUIDA, A_FAZER", "CONCLUIDA, EM_ANDAMENTO", "CONCLUIDA, CONCLUIDA"
    })
    void atualizacaoDeveAplicarTodasAsTransicoes(StatusTarefa anterior, StatusTarefa novo) {
        var tarefa = novaTarefa("Tarefa original", anterior);
        tarefa.setDataFinalizacao(anterior == StatusTarefa.CONCLUIDA ? DATA_ORIGINAL : null);
        tarefaRepository.saveAndFlush(tarefa);
        Long id = tarefa.getId();
        entityManager.clear();
        var dados = novaTarefa("Tarefa atualizada", novo);
        dados.setId(ID_INEXISTENTE);
        dados.setDataFinalizacao(LocalDateTime.of(1999, 1, 1, 0, 0));

        tarefaService.atualizar(id, dados);

        LocalDateTime esperada = novo != StatusTarefa.CONCLUIDA ? null
                : anterior == StatusTarefa.CONCLUIDA ? DATA_ORIGINAL : AGORA_CUIABA;
        var persistida = recarregar(id);
        assertThat(persistida.getId()).isEqualTo(id);
        assertThat(persistida.getStatus()).isEqualTo(novo);
        assertThat(persistida.getDataFinalizacao()).isEqualTo(esperada);
        assertThat(tarefaRepository.count()).isEqualTo(1);
    }

    @Test
    void tarefaAntigaConcluidaSemDataDeveManterDataDesconhecida() {
        var antiga = tarefaRepository.save(novaTarefa("Conclusão antiga", StatusTarefa.CONCLUIDA));
        Long id = antiga.getId();

        tarefaService.atualizar(id, novaTarefa("Nome alterado", StatusTarefa.CONCLUIDA));

        var persistida = recarregar(id);
        assertThat(persistida.getNome()).isEqualTo("Nome alterado");
        assertThat(persistida.getStatus()).isEqualTo(StatusTarefa.CONCLUIDA);
        assertThat(persistida.getDataFinalizacao()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = StatusTarefa.class, names = {"A_FAZER", "EM_ANDAMENTO"})
    void novaConclusaoAposReaberturaDeveRegistrarNovaData(StatusTarefa statusReaberto) {
        var tarefa = tarefaService.cadastrar(novaTarefa("Concluir e reabrir", StatusTarefa.CONCLUIDA));
        Long id = tarefa.getId();
        assertThat(tarefa.getDataFinalizacao()).isEqualTo(AGORA_CUIABA);

        when(clock.instant()).thenReturn(INSTANTE.plusSeconds(3600));
        tarefaService.atualizar(id, novaTarefa("Reaberta", statusReaberto));
        assertThat(recarregar(id).getDataFinalizacao()).isNull();

        when(clock.instant()).thenReturn(INSTANTE.plusSeconds(7200));
        tarefaService.atualizar(id, novaTarefa("Concluída novamente", StatusTarefa.CONCLUIDA));
        var concluida = recarregar(id);
        assertThat(concluida.getDataFinalizacao()).isEqualTo(AGORA_CUIABA.plusHours(2));
        assertThat(concluida.getDataFinalizacao()).isAfter(AGORA_CUIABA);
        assertThat(tarefaRepository.count()).isEqualTo(1);
    }

    private Tarefa recarregar(Long id) {
        tarefaRepository.flush();
        entityManager.clear();
        return tarefaRepository.findById(id).orElseThrow();
    }

    private RequestPostProcessor usuarioAutenticado() {
        return user(userDetailsService.loadUserByUsername("aluno@example.test"));
    }

    private static MultiValueMap<String, String> dadosValidos(StatusTarefa status) {
        var dados = new LinkedMultiValueMap<String, String>();
        dados.add("nome", "Tarefa atualizada");
        dados.add("descricao", "Descrição atualizada");
        dados.add("dataLimite", "2030-12-01");
        dados.add("prioridade", "MEDIA");
        dados.add("status", status.name());
        return dados;
    }

    private static Tarefa novaTarefa(String nome, StatusTarefa status) {
        var tarefa = new Tarefa();
        tarefa.setNome(nome);
        tarefa.setDescricao("Descrição original");
        tarefa.setDataLimite(LocalDate.of(2030, 12, 1));
        tarefa.setPrioridade(Prioridade.ALTA);
        tarefa.setStatus(status);
        return tarefa;
    }

    private static String html(MvcResult resultado) throws Exception {
        return HtmlUtils.htmlUnescape(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String tokenDoFormulario(MvcResult resultado, String acao) throws Exception {
        var formulario = Pattern.compile("<form\\b(?=[^>]*action=\"" + Pattern.quote(acao)
                + "\")(?=[^>]*method=\"post\")[^>]*>(.*?)</form>", Pattern.DOTALL)
                .matcher(html(resultado));
        assertThat(formulario.find()).as("Formulário POST para %s", acao).isTrue();
        String conteudo = formulario.group(1);
        assertThat(conteudo).containsOnlyOnce("name=\"_csrf\"");
        var token = Pattern.compile("<input\\b[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(conteudo);
        assertThat(token.find()).as("Token CSRF automático para %s", acao).isTrue();
        assertThat(token.group(1)).isNotBlank();
        return token.group(1);
    }
}
