# Roteiro para aprender com o projeto

## 1. Siga uma requisição simples

Abra `HorarioController.java` e encontre `GET /horarios/disponiveis`. O Spring converte `data` em `LocalDate`, o ID em `UUID` e o tipo de sessão no enum `TipoSessao`.

O controller chama `AgendaService.disponiveis`. O service aplica a regra de datas passadas e chama `HorarioRepository.disponiveis`. A consulta JPQL usa nomes das classes Java, enquanto o Hibernate traduz isso para SQL. O retorno é convertido em `HorarioResposta`, um record que define o contrato JSON sem expor entidades JPA.

Experimento: use o Swagger para consultar a mesma data com e sem filtro de profissional. Depois, envie uma data inválida e observe o status 400.

## 2. Entenda cadastro, BCrypt e JWT

`AuthController` recebe um DTO com `@Valid`. `AuthService` normaliza o e-mail, valida o limite do BCrypt, gera o hash e salva o usuário. `TokenService` cria o JWT assinado.

Hash não é criptografia reversível. No login, `PasswordEncoder.matches` verifica a senha enviada contra o hash armazenado. O salt já faz parte do formato BCrypt.

JWT é um token assinado, não um local para guardar informações secretas. Neste projeto contém identidade, versão da autenticação e informações de validade. O `SecurityFilterChain` usa o filtro Bearer fornecido pelo Spring Security; esse filtro cumpre a função de middleware de autenticação.

Experimento: copie o token retornado no login e use-o em `GET /agendamentos/meus`. Altere um caractere do token e observe o 401.

## 3. Veja por que a regra precisa de transação

Sem bloqueio, duas requisições poderiam ler o mesmo horário como disponível antes de qualquer uma salvar. Em `AgendaService.criar`, `findLockedById` usa `PESSIMISTIC_WRITE`: a segunda requisição espera a primeira terminar e então vê o horário reservado.

O índice parcial em `V1__estrutura_inicial.sql` é uma proteção adicional: para cada horário, só pode existir uma linha de agendamento com status `CONFIRMADO`. Os cancelados continuam no histórico.

`@Transactional` torna reserva e atualização de status uma operação única. Na remarcação, se o novo horário estiver ocupado, a transação falha sem perder a sessão original. Os testes concorrentes usam PostgreSQL real para exercitar esse comportamento.

## 4. Diferencie autenticação de autorização

Autenticação responde “quem está fazendo a requisição?”. Autorização responde “essa pessoa pode alterar este agendamento?”.

O controller obtém o cliente do JWT. `findOwnedLocked` procura pelo ID do agendamento **e** pelo ID do cliente. Saber o UUID de um agendamento alheio não permite lê-lo nem cancelá-lo.

Experimento: crie duas contas, reserve com a primeira e tente cancelar com o token da segunda. A API retorna 404.

## 5. Observe o ciclo de recuperação

O token enviado por e-mail tem alta entropia, expira em 15 minutos e é de uso único. No banco fica somente seu SHA-256. Como não é uma senha escolhida por uma pessoa, não exige um hash lento como BCrypt.

Na redefinição, o serviço bloqueia o usuário, verifica novamente o token, altera a senha e remove o registro de recuperação. Incrementar `tokenVersion` faz com que sessões anteriores sejam recusadas. Um novo login gera um token com a versão atual.

## 6. Ligue o React à API

Em `api.js`, observe a montagem dos headers, o `JSON.stringify` e a leitura dos erros `ProblemDetail`. O token fica em uma variável em memória. Em `App.jsx`, as consultas usam `AbortController`, evitando que a resposta de um filtro antigo substitua a seleção mais recente.

O navegador não escolhe qual cliente será dono da reserva. Envia apenas o ID do horário, e a API determina o cliente pelo token.

Experimento: desligue o backend e atualize os horários. A tela mostra um erro de conexão e permite tentar novamente. Reinicie o backend e repita a busca.

## 7. Próximos exercícios

1. Adicionar a regra de cancelamento com antecedência mínima, escrevendo primeiro testes para os limites de horário.
2. Criar papéis `CLIENTE` e `ADMIN` e endpoints administrativos para profissionais/horários.
3. Impedir intervalos sobrepostos durante o provisionamento da agenda, com uma constraint de exclusão PostgreSQL e a migração correspondente.
4. Criar notificações de confirmação assíncronas com uma tabela outbox para envio confiável após o commit.
5. Implementar paginação também na busca de horários e filtros de situação nos agendamentos.

Faça cada exercício em uma mudança pequena. Preserve DTOs separados das entidades, deixe regras de negócio nos serviços e escreva migrações novas para evoluir o banco.
