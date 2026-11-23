package br.edu.ifmt.tarefas.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifmt.tarefas.exception.TarefaNaoEncontradaException;
import br.edu.ifmt.tarefas.model.StatusTarefa;
import br.edu.ifmt.tarefas.model.Tarefa;
import br.edu.ifmt.tarefas.repository.TarefaRepository;

@Service
@Transactional(readOnly = true)
public class TarefaService {

    private final TarefaRepository tarefaRepository;
    private final Clock clock;

    public TarefaService(TarefaRepository tarefaRepository, Clock clock) {
        this.tarefaRepository = tarefaRepository;
        this.clock = clock;
    }

    public Tarefa buscarPorId(Long id) {
        return tarefaRepository.findById(id)
                .orElseThrow(TarefaNaoEncontradaException::new);
    }

    public List<Tarefa> listar(String nome) {

        if (nome == null || nome.isBlank()) {
            return tarefaRepository
                    .findAllByOrderByDataLimiteAscIdAsc();
        }

        return tarefaRepository
                .findByNomeContainingIgnoreCaseOrderByDataLimiteAscIdAsc(
                        nome.trim());
    }

    @Transactional
    public Tarefa cadastrar(Tarefa tarefa) {

        tarefa.setId(null);
        tarefa.setNome(tarefa.getNome().trim());
        tarefa.setDescricao(tarefa.getDescricao().trim());
        registrarFinalizacao(tarefa, null);

        return tarefaRepository.save(tarefa);
    }

    @Transactional
    public Tarefa atualizar(Long id, Tarefa dados) {
        Tarefa tarefa = buscarPorId(id);
        StatusTarefa statusAnterior = tarefa.getStatus();

        // O ID e a finalização pertencem à entidade persistida, nunca ao formulário.
        tarefa.setNome(dados.getNome().trim());
        tarefa.setDescricao(dados.getDescricao().trim());
        tarefa.setDataLimite(dados.getDataLimite());
        tarefa.setPrioridade(dados.getPrioridade());
        tarefa.setStatus(dados.getStatus());
        registrarFinalizacao(tarefa, statusAnterior);

        return tarefaRepository.save(tarefa);
    }

    private void registrarFinalizacao(Tarefa tarefa, StatusTarefa statusAnterior) {
        if (tarefa.getStatus() != StatusTarefa.CONCLUIDA) {
            tarefa.setDataFinalizacao(null);
        } else if (statusAnterior != StatusTarefa.CONCLUIDA) {
            tarefa.setDataFinalizacao(LocalDateTime.now(clock));
        }
        // Ao manter CONCLUIDA, preserva inclusive uma data histórica desconhecida.
    }
}
