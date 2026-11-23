# Gerenciador de Tarefas

Projeto acadêmico com Java 17, Spring Boot 4.1.2, Maven, Spring MVC, Thymeleaf,
Spring Data JPA, Hibernate, MySQL e Spring Security. Permite cadastrar tarefas,
listar e pesquisar pelo nome após autenticação.

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

## Verificações

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

`verify` também executa os testes e gera o JAR em `target/`.

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
