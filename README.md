# Gerenciador de Tarefas

Projeto acadêmico com Java 17, Spring Boot 4.1.2, Maven, Spring MVC, Thymeleaf,
Spring Data JPA, Hibernate, MySQL e Spring Security. Permite cadastrar, listar,
pesquisar e editar tarefas após autenticação, com registro automático de finalização.

## Configuração e execução no PowerShell

É necessário ter JDK 17, a variável `JAVA_HOME` configurada e MySQL em execução.
O Maven Wrapper baixa a distribuição do Maven e as dependências na primeira execução,
portanto precisa de acesso à internet. Não é necessário instalar Maven separadamente.

Use um banco MySQL existente chamado `gerenciador_tarefas`, em `localhost:3306`,
ou prepare esse banco antes de iniciar. A URL pode ser ajustada em
`src/main/resources/application.properties`. A configuração `ddl-auto=update`
existente foi preservada: o Hibernate atualiza o esquema sem recriar as tabelas.
Nenhuma tabela de usuários é criada pela autenticação.

Na raiz do projeto, defina as quatro variáveis no mesmo terminal em que executará o
Wrapper. Os valores abaixo são **fictícios**; substitua-os pelos seus valores locais:

```powershell
$env:DB_USERNAME = 'usuario_mysql_ficticio'
$env:DB_PASSWORD = 'SenhaMySQLFicticia123!'
$env:APP_LOGIN_EMAIL = 'aluno@example.test'
$env:APP_LOGIN_PASSWORD = 'SenhaLoginFicticia123!'

.\mvnw.cmd spring-boot:run
```

| Variável | Finalidade |
| --- | --- |
| `DB_USERNAME` | Usuário com acesso ao banco MySQL da aplicação. |
| `DB_PASSWORD` | Senha desse usuário do MySQL. |
| `APP_LOGIN_EMAIL` | E-mail do único usuário que poderá entrar no sistema. |
| `APP_LOGIN_PASSWORD` | Senha de login, codificada com BCrypt ao iniciar. |

As credenciais da aplicação não têm valores padrão. As duas variáveis de login
devem ser preenchidas; a inicialização falha se estiverem ausentes ou em branco.
Não salve senhas reais no repositório. Reinicie a aplicação após alterar o usuário
ou a senha nas variáveis de ambiente.

Acesse <http://localhost:8080/>. O endereço inicial redireciona para `/tarefas`;
visitantes sem sessão são encaminhados para `/login`. Entre com os valores definidos
em `APP_LOGIN_EMAIL` e `APP_LOGIN_PASSWORD`.

## Autenticação e componentes

As versões das dependências são gerenciadas pelo parent do Spring Boot 4.1.2.

- `spring-boot-starter-security`: integra filtros, autenticação, sessão e proteção CSRF.
- `SecurityConfig`: declara `SecurityFilterChain`, `BCryptPasswordEncoder` e
  `InMemoryUserDetailsManager`. A senha recebida é codificada antes de registrar o
  usuário; os controllers não conferem credenciais.
- `AuthController`: atende apenas os GETs `/` e `/login`.
- `templates/auth/login.html`: formulário de login com Bootstrap 5.3.8 e o CSS
  compartilhado em `static/css/estilo.css`, publicado em `/css/estilo.css`.
- Os cabeçalhos de listagem e cadastro oferecem **Sair** por formulário POST.

| Rota | Comportamento |
| --- | --- |
| `GET /` | Redireciona para `/tarefas`. |
| `GET /login` | Exibe a página pública de login. |
| `POST /login` | Spring Security autentica e redireciona para `/tarefas`, ou `/login?error`. |
| `GET /tarefas` | Lista ou pesquisa tarefas; exige autenticação. |
| `GET /tarefas/nova` | Exibe o cadastro; exige autenticação. |
| `POST /tarefas` | Valida e cadastra uma tarefa; exige autenticação e CSRF. |
| `GET /tarefas/{id}/editar` | Exibe o formulário preenchido; exige autenticação. |
| `POST /tarefas/{id}/editar` | Valida e atualiza a tarefa; exige autenticação e CSRF. |
| `POST /logout` | Invalida a sessão e redireciona para `/login?logout`; exige CSRF. |
| `/css/**`, `/error` e despachos de erro | Acesso público para estilos e tratamento de erros. |

As demais rotas exigem autenticação. Mesmo quando há uma requisição anterior salva,
o login bem-sucedido sempre leva à listagem.

CSRF permanece habilitado. Todos os formulários POST usam `th:action`, permitindo
que a integração do Thymeleaf com o Spring Security insira automaticamente um campo
oculto `_csrf`. Não há tokens manuais duplicados. O campo E-mail é enviado como
`username`, e o campo Senha como `password`, conforme os parâmetros do Spring Security.
Referências: [form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
e [integração CSRF com formulários](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-form).

O usuário em memória é uma **simplificação acadêmica**. Ele é recriado a cada
inicialização, não possui tabela no banco nem perfis. Não há cadastro de usuário,
recuperação de senha ou login social. As tarefas continuam **compartilhadas**, sem
associação individual ao usuário. Os modelos, repositório e serviço de tarefas
continuam usando o MySQL configurado.

## Edição e finalização

O botão **Editar** na coluna **Ações** abre o mesmo template usado no cadastro.
O título passa a **Editar tarefa** e o botão a **Salvar alterações**. É possível
alterar nome, descrição, data limite, prioridade e status. **Cancelar** retorna à
listagem sem gravar. Dados inválidos mantêm os valores preenchidos, as mensagens,
o modo de edição e o endereço correto do formulário.

Ao salvar, `TarefaService.atualizar()` busca a entidade pelo ID da URL e copia apenas
os cinco campos editáveis. O ID persistido é preservado, sem cadastrar outra tarefa.
Os campos `id` e `dataFinalizacao` continuam fora da lista de binding permitida.
IDs inexistentes retornam HTTP 404 com uma página amigável. Após atualizar, a
listagem apresenta **Tarefa atualizada com sucesso!**.

`ClockConfig` fornece um `Clock` no fuso **America/Cuiaba**, injetado pelo construtor
em `TarefaService`. A data é calculada no serviço com `LocalDateTime.now(clock)`:

| Operação ou transição | `data_finalizacao` |
| --- | --- |
| Cadastro como A Fazer ou Em Andamento | `NULL`. |
| Cadastro como Concluída | Data e hora atuais de Cuiabá. |
| A Fazer ou Em Andamento → Concluída | Data e hora atuais de Cuiabá. |
| Concluída → Concluída | Preserva a data original, mesmo ao alterar outros campos. |
| Concluída → A Fazer ou Em Andamento | Limpa a data. |
| Nova conclusão após reabrir | Registra uma nova data e hora. |
| Registro antigo Concluído sem data, mantendo o status | Mantém `NULL`; a data histórica permanece desconhecida. |

A listagem apresenta **Finalização** no formato `dd/MM/yyyy HH:mm`, ou **—** quando
não houver data. Na edição, a informação é somente de leitura, com **Não registrada**
quando nula. A data é preenchida automaticamente ao concluir; o formulário não a
recebe como campo editável. `LocalDateTime` não armazena o fuso na coluna: os valores
são gravados usando o horário local de Cuiabá. O uso de `Clock` permite controlar o
horário nos testes sem depender do relógio da máquina.
Referência: [Clock do Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html).

## Atualização do banco

O MySQL continua com `spring.jpa.hibernate.ddl-auto=update`. Ao iniciar a nova versão,
o Hibernate pode adicionar a coluna nullable `data_finalizacao` à tabela `tarefas`,
preservando os registros existentes. Não troque essa configuração por `create` ou
`create-drop` no banco real. A coluna não tem preenchimento retroativo nem valor
padrão de data atual: tarefas antigas continuam com finalização desconhecida.

Também está disponível a migração manual `database/adicionar-data-finalizacao.sql`.
**Execute-a somente se a coluna ainda não existir**, antes de iniciar a aplicação,
como alternativa à adição automática pelo Hibernate. Se o Hibernate já adicionou a
coluna, não execute o `ALTER TABLE` novamente.

Para conferir, abra seu cliente MySQL e execute:

```sql
SELECT COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'gerenciador_tarefas'
  AND TABLE_NAME = 'tarefas'
  AND COLUMN_NAME = 'data_finalizacao';
```

Se não retornar nenhuma linha, você pode executar o script manual no banco existente.
Com o cliente de linha de comando instalado, abra-o a partir da raiz do projeto:

```powershell
mysql --user=$env:DB_USERNAME --password gerenciador_tarefas
```

Informe a senha do MySQL quando solicitada. No prompt do MySQL, somente se a coluna
estiver ausente:

```sql
SOURCE database/adicionar-data-finalizacao.sql;
```

O script adiciona apenas `data_finalizacao DATETIME(6) NULL`, sem excluir dados ou
alterar status existentes. Não é executado automaticamente pelos testes nem pela
aplicação.

## Dicionário da tabela `tarefas`

| Coluna | Tipo no MySQL | Aceita NULL | Finalidade |
| --- | --- | --- | --- |
| `id` | `BIGINT`, chave primária, auto incremento | Não | Identificador da tarefa. |
| `nome` | `VARCHAR(100)` | Não | Nome da atividade. |
| `descricao` | `VARCHAR(1000)` | Não | Descrição da atividade. |
| `data_limite` | `DATE` | Não | Prazo informado pelo usuário. |
| `prioridade` | `ENUM('ALTA', 'MEDIA', 'BAIXA')` | Não | Prioridade, persistida pelo nome do enum Java. |
| `status` | `ENUM('A_FAZER', 'EM_ANDAMENTO', 'CONCLUIDA')` | Não | Situação atual, persistida pelo nome do enum Java. |
| `data_finalizacao` | `DATETIME(6)` | Sim | Data/hora de conclusão no horário de Cuiabá; corresponde a `LocalDateTime`. |

## Dependências

Nenhuma dependência adicional foi necessária para edição ou finalização.
As versões continuam gerenciadas pelo Spring Boot 4.1.2.

| Dependência | Escopo | Finalidade |
| --- | --- | --- |
| `spring-boot-starter-webmvc` | Aplicação | Controllers, rotas e processamento HTTP. |
| `spring-boot-starter-thymeleaf` | Aplicação | Renderização dos templates e formulários. |
| `spring-boot-starter-data-jpa` | Aplicação | Repositórios e persistência com Hibernate. |
| `spring-boot-starter-validation` | Aplicação | Validação de campos com Jakarta Validation. |
| `spring-boot-starter-security` | Aplicação | Login, sessão, autorização, BCrypt e CSRF. |
| `mysql-connector-j` | Runtime | Driver JDBC do MySQL. |
| `spring-boot-starter-webmvc-test` | Teste | MockMvc e testes da camada web. |
| `spring-boot-starter-data-jpa-test` | Teste | Suporte aos testes de persistência. |
| `spring-boot-starter-thymeleaf-test` | Teste | Suporte aos testes com templates Thymeleaf. |
| `spring-boot-starter-validation-test` | Teste | Suporte aos testes de validação. |
| `spring-boot-starter-security-test` | Teste | Autenticação simulada, CSRF e verificações de segurança. |
| `h2` | Teste | Banco em memória, sem acesso ao MySQL real. |

## Verificações

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

`clean verify` limpa os artefatos anteriores, executa os testes e gera o JAR em `target/`.

Os testes ativam exclusivamente o perfil `test`, em `src/test/resources`, com H2
em memória e credenciais fictícias próprias. Não precisam das quatro variáveis de
ambiente nem de MySQL. A dependência H2 tem escopo `test`, e a configuração de teste
não é incluída no JAR da aplicação. Não utilize `ddl-auto=create-drop` no MySQL real.

`spring-boot-starter-security-test` fornece a integração do Spring Security com
MockMvc. Os testes carregam os controllers, filtros, templates, serviço e repositório
reais, com transações revertidas ao final de cada caso. Cobrem redirecionamentos,
login válido e inválido, CSS público, tokens CSRF gerados pelo Thymeleaf, rejeição
de POSTs sem token, logout com invalidação de sessão, cadastro e pesquisa de tarefas,
validação de formulário e tratamento público de erros.

Os testes existentes de autenticação foram preservados. `TarefaEdicaoFinalizacaoTests`
adiciona cobertura para o formulário preenchido, edição sem duplicar registros,
rejeição de dados inválidos, erros de conversão, página 404, binding restrito,
autenticação e CSRF nas novas rotas. Também verifica todos os pares de transições
de status, a data desconhecida de registros antigos e novas conclusões após reabrir.
O `Clock` é controlado nos testes, com instantes conhecidos e fuso de Cuiabá.

## Checklist manual

- [ ] Entrar com as credenciais definidas nas variáveis de ambiente.
- [ ] Cadastrar uma tarefa como A Fazer e conferir finalização **—** na listagem.
- [ ] Pesquisar pelo nome e conferir os resultados.
- [ ] Abrir **Editar**, conferir os dados preenchidos e cancelar sem gravar.
- [ ] Editar os campos e conferir a mensagem de sucesso sem duplicar a tarefa.
- [ ] Enviar um campo obrigatório vazio e conferir que os demais valores são mantidos.
- [ ] Concluir a tarefa e conferir a data/hora de Cuiabá na listagem e na edição.
- [ ] Editar uma tarefa já concluída e conferir que a data original foi preservada.
- [ ] Reabrir como A Fazer ou Em Andamento e conferir a remoção da data.
- [ ] Concluir novamente em outro momento e conferir a nova data de finalização.
- [ ] Acessar a edição de um ID inexistente e conferir a página 404 amigável.
- [ ] Sair e conferir que as páginas de tarefas voltam a exigir login.
