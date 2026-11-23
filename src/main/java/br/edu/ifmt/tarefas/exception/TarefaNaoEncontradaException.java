package br.edu.ifmt.tarefas.exception;

public class TarefaNaoEncontradaException extends RuntimeException {

    public TarefaNaoEncontradaException() {
        super("A tarefa solicitada não foi encontrada.");
    }
}
