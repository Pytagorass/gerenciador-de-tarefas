package br.edu.ifmt.tarefas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifmt.tarefas.model.Tarefa;

public interface TarefaRepository extends JpaRepository<Tarefa, Long> {

    List<Tarefa> findAllByOrderByDataLimiteAscIdAsc();

    List<Tarefa> findByNomeContainingIgnoreCaseOrderByDataLimiteAscIdAsc(
            String nome);
}