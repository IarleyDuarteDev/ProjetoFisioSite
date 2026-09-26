# Fisio Clínica — Spring Boot + React + PostgreSQL

Aplicação de estudo para agendamento de uma clínica de fisioterapia. O **Java é responsável pela autenticação e por todas as regras de negócio**. O Supabase fornece somente o PostgreSQL: não há Supabase Auth, SDK do Supabase no navegador nem acesso direto do React ao banco.

O projeto inclui cadastro, login, recuperação de senha por e-mail, pesquisa de horários, reserva, cancelamento e remarcação, além de interface responsiva em português, Swagger e testes automatizados.

## Tecnologias e organização

- Java 21, Spring Boot 3.5.16 e Maven Wrapper.
- Spring Security, JWT HS256 via Nimbus e BCrypt com custo 12.
- Spring Data JPA, PostgreSQL e migrações Flyway.
- Bean Validation e respostas de erro `application/problem+json`.
- React 19, Vite, `fetch`, CSS e ícones Lucide.
- JUnit 5, Mockito, MockMvc e PostgreSQL temporário nos testes; Vitest no frontend.

```text
backend/
  src/main/java/br/com/fisioclinica/
    config/        Segurança, CORS, OpenAPI e dados de demonstração
    controller/    HTTP, parâmetros, validação e identidade do cliente
    dto/           Contratos de entrada e saída (records Java)
    exception/     Erros de negócio e @RestControllerAdvice
    model/         Entidades JPA e enums
    repository/    Consultas e bloqueios no PostgreSQL
    service/       Autenticação, JWT, e-mail e regras de agendamento
  src/main/resources/db/migration/
  src/test/java/br/com/fisioclinica/
frontend/
  src/App.jsx           Agenda e área do cliente
  src/AuthDialog.jsx    Cadastro, login e recuperação
  src/api.js            Cliente HTTP e Bearer token
  src/format.js         Datas no fuso da clínica
compose.yaml            Ambiente local completo
docs/aprendizado.md     Roteiro para estudar o código
```

## Opção 1: executar tudo com Docker

Requer Docker Desktop em execução com containers Linux. Na raiz do projeto:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

O Compose inicia PostgreSQL local, Mailpit, backend e frontend. O `.env.example` da raiz contém valores **exclusivos de desenvolvimento**. Os serviços ficam acessíveis apenas no computador local.

| Serviço | Endereço |
| --- | --- |
| Site | http://localhost:5173 |
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| E-mails de recuperação no Mailpit | http://localhost:8025 |

São criados três profissionais fictícios e horários nos próximos 14 dias, a partir de amanhã, exceto domingos. Cadastre seu próprio cliente pela interface. Não existe senha padrão de cliente.

Para parar: `docker compose down`. O volume do PostgreSQL preserva os dados. A carga de demonstração só roda quando não há profissionais nem horários; ela não renova a agenda a cada inicialização.

## Opção 2: Java local e banco no Supabase

Requisitos: JDK 21 ou 25 e Node.js 22.12+ (Node 24 recomendado). Configure `JAVA_HOME` para a pasta do JDK. Não é necessário instalar Maven: use `mvnw.cmd` no Windows ou `sh ./mvnw` no Linux/macOS.

1. No projeto Supabase, abra **Connect** e copie host, usuário e senha de **Session pooler**, porta **5432**. Use conexão direta se seu ambiente tiver acesso IPv6 ou IPv4 compatível.
2. Copie `backend/.env.example` para `backend/.env`.
3. Preencha `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `JWT_SECRET`. Gere uma chave aleatória; não use a chave `anon`, a `service_role` nem o segredo JWT do Supabase.

```dotenv
DB_URL=jdbc:postgresql://SEU-HOST-DO-POOLER:5432/postgres?sslmode=require
DB_USERNAME=postgres.SEU_PROJECT_REF
DB_PASSWORD=sua-senha-do-banco
JWT_SECRET=sua-chave-aleatoria-com-pelo-menos-32-bytes
FRONTEND_URL=http://localhost:5173
CLINIC_ZONE=America/Rio_Branco
DEMO_DATA=true
```

Use o host exato fornecido pelo Supabase. A senha fica em variável separada, sem precisar codificá-la na URL. Para validação de certificado e hostname, configure `sslmode=verify-full` e o certificado raiz indicado pelo Supabase. `sslmode=require` habilita criptografia.

Exemplo para gerar uma chave no PowerShell, com Node instalado:

```powershell
node -e "console.log(require('node:crypto').randomBytes(48).toString('base64'))"
```

Inicie o backend, a partir da raiz:

```powershell
.\backend\run.ps1
```

O script carrega as variáveis do `backend/.env` no processo, sem executar o conteúdo do arquivo. Use `CHAVE=valor` sem aspas. Se a política local impedir scripts PowerShell, configure as variáveis no terminal/IDE e execute `backend\mvnw.cmd -f backend\pom.xml spring-boot:run`. O Spring Boot **não carrega arquivos `.env` automaticamente**; o script, a IDE ou o ambiente de execução precisam fornecer as variáveis.

Em outro terminal:

```powershell
cd frontend
npm ci
npm run dev
```

O Vite encaminha `/api` para `http://localhost:8080`, removendo esse prefixo. A API Java não tem prefixo `/api`. Para hospedar a API em outra origem, configure `VITE_API_URL` antes de compilar e ajuste `FRONTEND_URL` no backend para permitir essa origem no CORS.

O Flyway cria o schema **`fisio`** e suas tabelas. A conexão usada na primeira execução precisa poder criar schema e tabelas. O JPA usa `ddl-auto=validate`: alterações estruturais devem ser novas migrações, sem editar uma migração já aplicada.

**Não exponha o schema `fisio` na Data API do Supabase.** As tabelas pertencem à aplicação Java, e a autorização ocorre na API. Credenciais JDBC e o segredo JWT ficam exclusivamente no backend. Em uma implantação, separe o usuário de migrações do usuário de execução com permissões restritas.

Referências oficiais: [Spring Boot no Supabase](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot), [conexões PostgreSQL](https://supabase.com/docs/guides/database/connecting-to-postgres) e [Spring Security com JWT](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).

## Opção 3: demonstração descartável sem Docker ou Supabase

Para estudar a interface antes de configurar um banco, o código de teste contém um iniciador com PostgreSQL temporário. Requer Java e faz o download dos binários do PostgreSQL na primeira execução:

```powershell
cd backend
.\mvnw.cmd test-compile exec:java '-Dexec.mainClass=br.com.fisioclinica.LocalDemo' '-Dexec.classpathScope=test'
```

Em outro terminal, execute `npm ci` e `npm run dev` em `frontend`. O banco desta opção é descartável; os cadastros e agendamentos se perdem ao encerrar. O segredo JWT também é temporário. A classe `LocalDemo` está em `src/test` e não entra no JAR de produção. Para recuperação de senha nesta opção, disponibilize um SMTP em `localhost:1025`, por exemplo com `docker compose up mailpit`.

## Recuperação de senha

Configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` e `MAIL_FROM` em `backend/.env` ou no ambiente. Para um SMTP com STARTTLS, normalmente use porta 587, `SMTP_AUTH=true` e `SMTP_STARTTLS=true`, conforme o provedor. O remetente precisa ser autorizado pelo provedor.

1. O cliente envia seu e-mail a `POST /auth/forgot-password`.
2. A API sempre responde `202` com a mesma mensagem, exista ou não uma conta.
3. Um token aleatório de 256 bits é enviado por e-mail; apenas seu SHA-256 é armazenado, com validade de 15 minutos.
4. O link abre o formulário React. O token vem no fragmento `#redefinir=...`, que não é enviado ao servidor web, e é removido da barra de endereço ao abrir o formulário.
5. `POST /auth/reset-password` troca a senha, consome o token e incrementa a versão de autenticação do usuário, invalidando os JWTs anteriores.

Há intervalo de um minuto entre envios para a mesma conta. Falhas de SMTP geram aviso no backend sem expor e-mail ou token e mantêm a resposta genérica. A aplicação não devolve o token de recuperação na API nem o imprime nos logs. No Docker local, abra o Mailpit para consultar o e-mail.

## API REST

Datas: `YYYY-MM-DD`. IDs: UUID. Horários: hora local da clínica, definida por `CLINIC_ZONE`. Configure `VITE_CLINIC_ZONE` com o mesmo fuso caso altere o padrão. Tipos de sessão: `AVALIACAO`, `FISIOTERAPIA`, `PILATES`.

| Método e rota | Autenticação | Entrada / comportamento |
| --- | --- | --- |
| `POST /auth/register` | Pública | `nome`, `email`, `senha`; retorna sessão e usuário, HTTP 201 |
| `POST /auth/login` | Pública | `email`, `senha`; retorna sessão, HTTP 200 |
| `POST /auth/forgot-password` | Pública | `email`; resposta genérica, HTTP 202 |
| `POST /auth/reset-password` | Pública | `token`, `novaSenha` |
| `GET /profissionais` | Pública | Lista para preencher o filtro |
| `GET /horarios/disponiveis` | Pública | `data` obrigatória; `profissionalId` e `tipoSessao` opcionais; até 200 horários por data |
| `GET /agendamentos/meus` | Bearer JWT | `pagina=0`, `tamanho=20` (máximo 100) |
| `POST /agendamentos` | Bearer JWT | `horarioId`, `observacoes` opcional |
| `PUT /agendamentos/{id}` | Bearer JWT | `acao: CANCELAR` ou `acao: REMARCAR` com `novoHorarioId` |

Cadastro e login retornam:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "usuario": { "id": "uuid", "nome": "Ana Silva", "email": "ana@example.com" }
}
```

Nas rotas protegidas, envie `Authorization: Bearer SEU_TOKEN`. No Swagger, use **Authorize** com o token recebido. O cliente é sempre identificado pelo JWT; a API não aceita um `clienteId` escolhido pelo navegador.

```http
GET /horarios/disponiveis?data=2026-10-01&tipoSessao=FISIOTERAPIA

POST /agendamentos
Authorization: Bearer SEU_TOKEN
Content-Type: application/json

{"horarioId":"UUID_DO_HORARIO","observacoes":"Primeira sessão"}
```

```json
{"acao":"REMARCAR","novoHorarioId":"UUID_DO_NOVO_HORARIO"}
```

```json
{"acao":"CANCELAR"}
```

A listagem de agendamentos retorna `{ "itens": [], "pagina": 0, "tamanho": 20, "total": 0, "totalPaginas": 0 }`, ordenada por data/hora decrescente. Cada item contém `id`, `status`, `observacoes` e `horario`, incluindo o profissional e o tipo de sessão.

Erros usam `status`, `title`, `detail` e, em validações de formulário, `campos`. Exemplos: 400 para entrada inválida, 401 para credenciais/token inválidos, 404 para agendamento inexistente ou de outro cliente e 409 para horário ocupado ou sessão já iniciada.

## Regras implementadas

- E-mail é normalizado para minúsculas e tem unicidade no banco. Senhas exigem pelo menos oito caracteres no cadastro/redefinição e respeitam o limite de 72 bytes UTF-8 do BCrypt.
- JWT válido por 30 minutos, com assinatura, emissor e expiração verificados pelo filtro Bearer do Spring Security. A versão da autenticação é consultada no banco para permitir revogação após troca de senha.
- JWT fica apenas em memória no React. Atualizar a página ou sair da conta exige novo login; não há refresh token. Sair limpa a sessão deste navegador; um token já copiado continua válido até expirar ou até uma redefinição de senha.
- Reserva usa transação e bloqueio pessimista do horário. Um índice parcial PostgreSQL garante no máximo um agendamento `CONFIRMADO` por horário, preservando os cancelados no histórico.
- Remarcação bloqueia os horários em ordem estável e libera o anterior na mesma transação. Se o novo horário estiver ocupado, a reserva original permanece intacta.
- Cancelamento libera o horário. Reservas canceladas ou sessões que já começaram não podem ser alteradas. Não há multa nem antecedência mínima adicional.
- O modelo inclui `tipo_sessao` em `Horario`, `token_version` em `Usuario` e a tabela `recuperacoes_senha`, além dos campos solicitados.

Profissionais e horários são provisionados pela carga opcional de demonstração ou por operações administrativas no banco. Não há painel administrativo neste escopo. Ao cadastrar uma agenda real, mantenha intervalos sem sobreposição para cada profissional; a reserva controla a exclusividade de cada horário cadastrado.

## Testes e build

```powershell
cd backend
.\mvnw.cmd verify
```

Os testes Java usam um PostgreSQL real, temporário e isolado, iniciado automaticamente pela biblioteca Embedded Postgres, sem credenciais do Supabase e sem Docker. É necessário acesso à internet no primeiro download. Em Linux, execute como usuário sem privilégios de root, pois o PostgreSQL não inicia como root. O envio de e-mail é substituído por um mock nos testes de integração.

A suíte cobre autenticação, BCrypt, JWT adulterado, acesso a dados de outro cliente, validações, filtros, reserva/remarcação/cancelamento, rollback, disputa concorrente pelo mesmo horário, recuperação com uso único, expiração de recuperação e revogação de JWT. Há também testes unitários de regras do serviço.

```powershell
cd frontend
npm ci
npm test
npm run build
```

O build Java gera `backend/target/fisio-api-0.0.1-SNAPSHOT.jar`; o build React gera `frontend/dist`. Os testes frontend verificam o cliente HTTP, expiração de sessão, tratamento de conflitos, cálculo de datas no fuso da clínica e o fluxo dos componentes de login, reserva, remarcação e cancelamento.

## Limites desta versão de estudo

Antes de disponibilizar para clientes reais, configure HTTPS e o SMTP, aplique limites de requisições nos endpoints de autenticação no proxy/gateway e defina a operação administrativa da agenda. A proteção contra abuso distribuído, confirmação de e-mail, papéis administrativos e trilha de auditoria ficam para uma próxima etapa. Observações não devem ser usadas como prontuário clínico.

Comece o estudo pelo [roteiro de aprendizado](docs/aprendizado.md). As credenciais reais do Supabase e do provedor de e-mail precisam ser fornecidas no seu ambiente; os arquivos de exemplo não contêm credenciais de produção.
