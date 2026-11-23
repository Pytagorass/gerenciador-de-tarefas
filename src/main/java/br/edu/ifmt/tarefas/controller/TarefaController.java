package br.edu.ifmt.tarefas.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.edu.ifmt.tarefas.exception.TarefaNaoEncontradaException;
import br.edu.ifmt.tarefas.model.Prioridade;
import br.edu.ifmt.tarefas.model.StatusTarefa;
import br.edu.ifmt.tarefas.model.Tarefa;
import br.edu.ifmt.tarefas.service.TarefaService;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/tarefas")
public class TarefaController {

    private final TarefaService tarefaService;

    public TarefaController(TarefaService tarefaService) {
        this.tarefaService = tarefaService;
    }

    // Define quais campos podem ser recebidos pelo formulário.
    @InitBinder("tarefa")
    public void configurarFormulario(WebDataBinder binder) {
        binder.setAllowedFields(
                "nome",
                "descricao",
                "dataLimite",
                "prioridade",
                "status");
    }

    // Disponibiliza as prioridades para as páginas.
    @ModelAttribute("prioridades")
    public Prioridade[] prioridades() {
        return Prioridade.values();
    }

    // Disponibiliza os status para as páginas.
    @ModelAttribute("statusDisponiveis")
    public StatusTarefa[] statusDisponiveis() {
        return StatusTarefa.values();
    }

    // Lista todas as tarefas ou pesquisa pelo nome.
    @GetMapping
    public String listar(
            @RequestParam(name = "nome", defaultValue = "") String nome,
            Model model) {

        model.addAttribute("tarefas", tarefaService.listar(nome));
        model.addAttribute("nome", nome);

        return "tarefas/lista";
    }

    // Abre a página de cadastro.
    @GetMapping("/nova")
    public String mostrarCadastro(Model model) {
        model.addAttribute("tarefa", new Tarefa());
        model.addAttribute("edicao", false);

        return "tarefas/cadastro";
    }

    // Valida e cadastra a tarefa.
    @PostMapping
    public String cadastrar(
            @Valid @ModelAttribute("tarefa") Tarefa tarefa,
            BindingResult resultado,
            Model model,
            RedirectAttributes atributos) {

        if (resultado.hasErrors()) {
            model.addAttribute("edicao", false);
            return "tarefas/cadastro";
        }

        tarefaService.cadastrar(tarefa);

        atributos.addFlashAttribute(
                "mensagem",
                "Tarefa cadastrada com sucesso!");

        return "redirect:/tarefas";
    }

    @GetMapping("/{id}/editar")
    public String mostrarEdicao(@PathVariable("id") Long id, Model model) {
        Tarefa tarefa = tarefaService.buscarPorId(id);
        model.addAttribute("tarefa", tarefa);
        prepararEdicao(model, tarefa);

        return "tarefas/cadastro";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("tarefa") Tarefa tarefa,
            BindingResult resultado,
            Model model,
            RedirectAttributes atributos) {

        if (resultado.hasErrors()) {
            // Mantém os valores digitados e usa o banco apenas para os dados de leitura.
            prepararEdicao(model, tarefaService.buscarPorId(id));
            return "tarefas/cadastro";
        }

        tarefaService.atualizar(id, tarefa);
        atributos.addFlashAttribute("mensagem", "Tarefa atualizada com sucesso!");

        return "redirect:/tarefas";
    }

    @ExceptionHandler(TarefaNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String tarefaNaoEncontrada() {
        return "error/404";
    }

    private void prepararEdicao(Model model, Tarefa tarefa) {
        model.addAttribute("edicao", true);
        model.addAttribute("idTarefa", tarefa.getId());
        model.addAttribute("dataFinalizacao", tarefa.getDataFinalizacao());
    }
}
