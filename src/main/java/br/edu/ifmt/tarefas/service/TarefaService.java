package br.edu.ifmt.tarefas.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifmt.tarefas.model.Tarefa;
import br.edu.ifmt.tarefas.repository.TarefaRepository;

@Service
@Transactional(readOnly = true)
public class TarefaService {

    private final TarefaRepository tarefaRepository;

    public TarefaService(TarefaRepository tarefaRepository) {
        this.tarefaRepository = tarefaRepository;
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

        return tarefaRepository.save(tarefa);
    }
}