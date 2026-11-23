package br.edu.ifmt.tarefas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifmt.tarefas.model.Prioridade;
import br.edu.ifmt.tarefas.model.StatusTarefa;
import br.edu.ifmt.tarefas.model.Tarefa;
import br.edu.ifmt.tarefas.repository.TarefaRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GerenciadorTarefasApplicationTests {

    private static final String EMAIL = "aluno@example.test";
    private static final String SENHA = "SenhaFicticiaApenasParaTestes123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TarefaRepository tarefaRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void visitanteDeveSerRedirecionadoAoLogin() throws Exception {
        mvc.perform(get("/tarefas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void cadastroTambemDeveExigirAutenticacao() throws Exception {
        mvc.perform(get("/tarefas/nova"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void demaisPaginasDevemExigirAutenticacao() throws Exception {
        mvc.perform(get("/pagina-protegida"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void raizDeveRedirecionarParaTarefas() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tarefas"));
    }

    @Test
    void loginDeveSerPublicoEConterTokenCsrfAutomatico() throws Exception {
        var resultado = mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(containsString("Entre para organizar suas atividades")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("autocomplete=\"current-password\"")))
                .andReturn();

        tokenDoFormulario(resultado, "/login");
    }

    @Test
    void cssDeveSerAcessivelSemSessao() throws Exception {
        mvc.perform(get("/css/estilo.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString(".login-cartao")));
    }

    @Test
    void loginValidoDeveUsarTokenDoFormularioEPermitirAcessoAsTarefas() throws Exception {
        var sessao = fazerLogin();

        mvc.perform(get("/tarefas").session(sessao))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername(EMAIL))
                .andExpect(view().name("tarefas/lista"));
    }

    @Test
    void senhaInvalidaDeveRedirecionarComErro() throws Exception {
        mvc.perform(formLogin("/login").user(EMAIL).password("SenhaIncorretaFicticia"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void emailDesconhecidoDeveRedirecionarComErro() throws Exception {
        mvc.perform(formLogin("/login").user("inexistente@example.test").password(SENHA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void loginDeveSempreRedirecionarParaListagemMesmoComRequisicaoSalva() throws Exception {
        var resultado = mvc.perform(get("/tarefas/nova"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        mvc.perform(post("/login")
                        .session((MockHttpSession) resultado.getRequest().getSession())
                        .with(csrf())
                        .param("username", EMAIL)
                        .param("password", SENHA))
                .andExpect(redirectedUrl("/tarefas"))
                .andExpect(authenticated().withUsername(EMAIL));
    }

    @Test
    void mensagensDeErroESaidaDevemSerExibidas() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("E-mail ou senha inválidos")));

        mvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Você saiu do sistema")));
    }

    @Test
    void loginSemCsrfDeveSerRejeitado() throws Exception {
        mvc.perform(post("/login").param("username", EMAIL).param("password", SENHA))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void cadastroSemCsrfDeveSerRejeitadoSemSalvar() throws Exception {
        mvc.perform(post("/tarefas").with(usuarioAutenticado())
                        .param("nome", "Tarefa bloqueada"))
                .andExpect(status().isForbidden());

        assertThat(tarefaRepository.count()).isZero();
    }

    @Test
    void logoutSemCsrfDeveSerRejeitadoEPreservarSessao() throws Exception {
        var sessao = fazerLogin();

        mvc.perform(post("/logout").session(sessao))
                .andExpect(status().isForbidden())
                .andExpect(authenticated().withUsername(EMAIL));

        assertThat(sessao.isInvalid()).isFalse();
        mvc.perform(get("/tarefas").session(sessao)).andExpect(status().isOk());
    }

    @Test
    void logoutDeveInvalidarSessaoEExigirNovoLogin() throws Exception {
        var sessao = fazerLogin();
        var pagina = mvc.perform(get("/tarefas").session(sessao))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(post("/logout").session(sessao)
                        .param("_csrf", tokenDoFormulario(pagina, "/logout")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        assertThat(sessao.isInvalid()).isTrue();
        mvc.perform(get("/tarefas"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());
    }

    @Test
    void cadastroAutenticadoDeveConterCsrfNosDoisFormulariosPost() throws Exception {
        var pagina = mvc.perform(get("/tarefas/nova").with(usuarioAutenticado()))
                .andExpect(status().isOk())
                .andExpect(view().name("tarefas/cadastro"))
                .andReturn();

        tokenDoFormulario(pagina, "/tarefas");
        tokenDoFormulario(pagina, "/logout");
    }

    @Test
    void deveCadastrarTarefaAutenticadaComTokenGeradoPeloThymeleaf() throws Exception {
        var sessao = fazerLogin();
        var pagina = mvc.perform(get("/tarefas/nova").session(sessao))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(post("/tarefas").session(sessao)
                        .param("_csrf", tokenDoFormulario(pagina, "/tarefas"))
                        .param("nome", "  Estudar Spring Security  ")
                        .param("descricao", "  Revisar login e CSRF  ")
                        .param("dataLimite", "2030-12-01")
                        .param("prioridade", "ALTA")
                        .param("status", "A_FAZER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tarefas"))
                .andExpect(flash().attribute("mensagem", "Tarefa cadastrada com sucesso!"));

        assertThat(tarefaRepository.findAll()).singleElement().satisfies(tarefa -> {
            assertThat(tarefa.getNome()).isEqualTo("Estudar Spring Security");
            assertThat(tarefa.getDescricao()).isEqualTo("Revisar login e CSRF");
            assertThat(tarefa.getDataLimite()).isEqualTo(LocalDate.of(2030, 12, 1));
            assertThat(tarefa.getPrioridade()).isEqualTo(Prioridade.ALTA);
            assertThat(tarefa.getStatus()).isEqualTo(StatusTarefa.A_FAZER);
        });
    }

    @Test
    void cadastroInvalidoDevePreservarValidacaoESegurancaDoFormulario() throws Exception {
        var resultado = mvc.perform(post("/tarefas").with(usuarioAutenticado()).with(csrf())
                        .param("nome", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("tarefas/cadastro"))
                .andExpect(model().attributeHasFieldErrors("tarefa", "nome", "descricao",
                        "dataLimite", "prioridade"))
                .andReturn();

        tokenDoFormulario(resultado, "/tarefas");
        assertThat(tarefaRepository.count()).isZero();
    }

    @Test
    void pesquisaDeveContinuarFiltrandoTarefasParaUsuarioAutenticado() throws Exception {
        var tarefaJava = tarefaRepository.save(novaTarefa("Estudar Java"));
        tarefaRepository.save(novaTarefa("Preparar apresentação"));

        var resultado = mvc.perform(get("/tarefas").with(usuarioAutenticado()).param("nome", "java"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("nome", "java"))
                .andReturn();

        assertThat(resultado.getModelAndView().getModel().get("tarefas"))
                .isEqualTo(List.of(tarefaJava));
        tokenDoFormulario(resultado, "/logout");
    }

    @Test
    void tratamentoDeErrosDeveSerPublicoSemRedirecionamento() throws Exception {
        mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound());

        mvc.perform(get("/error").with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        })
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void senhaDeveEstarCodificadaComBCryptNoUsuarioSemPerfis() {
        var usuario = userDetailsService.loadUserByUsername(EMAIL);

        assertThat(usuario.getPassword()).startsWith("$2").isNotEqualTo(SENHA);
        assertThat(passwordEncoder.matches(SENHA, usuario.getPassword())).isTrue();
        assertThat(usuario.getAuthorities()).isEmpty();
    }

    private MockHttpSession fazerLogin() throws Exception {
        var pagina = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var sessao = (MockHttpSession) pagina.getRequest().getSession();

        mvc.perform(post("/login").session(sessao)
                        .param("username", EMAIL)
                        .param("password", SENHA)
                        .param("_csrf", tokenDoFormulario(pagina, "/login")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tarefas"))
                .andExpect(authenticated().withUsername(EMAIL));

        return sessao;
    }

    private RequestPostProcessor usuarioAutenticado() {
        return user(userDetailsService.loadUserByUsername(EMAIL));
    }

    private static String tokenDoFormulario(MvcResult resultado, String acao) throws Exception {
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        var formulario = Pattern.compile("<form\\b(?=[^>]*action=\"" + Pattern.quote(acao)
                + "\")(?=[^>]*method=\"post\")[^>]*>(.*?)</form>", Pattern.DOTALL).matcher(html);

        assertThat(formulario.find()).as("Formulário POST para %s", acao).isTrue();
        String conteudo = formulario.group(1);
        assertThat(conteudo).containsOnlyOnce("name=\"_csrf\"");

        var token = Pattern.compile("<input\\b[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(conteudo);
        assertThat(token.find()).as("Token CSRF automático para %s", acao).isTrue();
        assertThat(token.group(1)).isNotBlank();
        return token.group(1);
    }

    private static Tarefa novaTarefa(String nome) {
        var tarefa = new Tarefa();
        tarefa.setNome(nome);
        tarefa.setDescricao("Descrição fictícia de teste");
        tarefa.setDataLimite(LocalDate.of(2030, 12, 1));
        tarefa.setPrioridade(Prioridade.MEDIA);
        tarefa.setStatus(StatusTarefa.A_FAZER);
        return tarefa;
    }

}
