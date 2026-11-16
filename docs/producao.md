# Colocando o site na internet

Este roteiro cobre o que o código já faz e o que precisa ser feito fora dele. Não substitui uma revisão jurídica (LGPD).

## 1. O que você precisa
- Um servidor Linux (VPS) com Docker e Docker Compose, e um domínio apontando para o IP dele (registro A).
- As portas **80 e 443** abertas no firewall e **nenhuma outra** (o banco e o back-end não são publicados).
- Um servidor de e-mail de verdade (SMTP) para confirmação de conta e recuperação de senha. O Mailpit é só de desenvolvimento.

## 2. Segredos (gere novos, nunca reaproveite os de exemplo)
```
node -e "console.log(require('node:crypto').randomBytes(48).toString('base64'))"   # JWT_SECRET
node -e "console.log(require('node:crypto').randomBytes(48).toString('base64'))"   # CPF_KEY
node -e "console.log(require('node:crypto').randomBytes(24).toString('base64'))"   # POSTGRES_PASSWORD
```
Crie `.env.producao` (ele já é ignorado pelo Git) com: `DOMINIO`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `CPF_KEY`, `ADMIN_EMAIL`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` e, se usar login social, `GOOGLE_CLIENT_ID` (no Google Cloud, autorize `https://SEU-DOMINIO` em "Origens JavaScript autorizadas").

Guarde uma cópia da **CPF_KEY** em um cofre de senhas. Sem ela, os CPFs já cadastrados não podem ser lidos.

## 3. Subir
```
docker compose -f compose.prod.yaml --env-file .env.producao up -d --build
```
Com `APP_PRODUCAO=true` o back-end **se recusa a iniciar** se encontrar chave de exemplo, senha curta, `DEMO_DATA`, Swagger ligado, `FRONTEND_URL` sem https ou SMTP em localhost. A mensagem de erro lista o que corrigir, sem mostrar valores.

Depois do primeiro início, entre no site, use "Esqueci minha senha" com o `ADMIN_EMAIL` e defina a senha do administrador.

## 4. O que o código já faz
- Senhas com BCrypt, tokens de recuperação e de confirmação guardados só como hash, uso único e com validade.
- CPF cifrado (AES-GCM), único por conta, mascarado nas respostas.
- E-mail confirmado antes de agendar; login com Google com validação do token; vínculo pelo identificador do provedor.
- Bloqueio por e-mail após 5 erros de login e limite por IP nas rotas de autenticação (registro, login, recuperação, reenvio).
- "Sair" invalida o token no servidor (em todos os dispositivos da conta).
- Área do administrador protegida no servidor, com log de auditoria das alterações.
- HTTPS automático e HSTS (Caddy), CSP e demais cabeçalhos (Nginx), Swagger desligado.

## 5. O que ainda é com você
- **Backup do banco** (diário, guardado fora do servidor):
  ```
  docker compose -f compose.prod.yaml exec -T postgres pg_dump -U fisio fisio | gzip > backup-$(date +%F).sql.gz
  ```
  Teste a restauração pelo menos uma vez.
- **Atualizações:** refaça o build periodicamente (`up -d --build`) para receber correções das imagens base e das dependências.
- **LGPD:** o CPF é dado pessoal. Publique uma política de privacidade com a finalidade, o prazo de guarda e um canal para o titular pedir acesso, correção ou exclusão. Hoje a exclusão de conta é manual, no banco.
- **Melhorias futuras:** verificação em duas etapas para o administrador, senha mínima maior, exclusão de conta pelo próprio usuário e monitoramento de erros e de acessos.
