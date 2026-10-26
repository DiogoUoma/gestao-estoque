# Gestão de Estoque

Aplicação full stack para cadastrar e gerenciar produtos de uma loja, incluindo bebidas, alimentos, alimentos preparados e lembrancinhas. O projeto reúne uma API REST em Java com Spring Boot e uma interface web em React com TypeScript.

## Funcionalidades

- Cadastro e listagem de produtos.
- Consulta de produto por ID pela API.
- Edição de nome, descrição, categoria, preço e quantidade.
- Exclusão de produtos.
- Importação em lote por arquivos CSV e Excel (`.xlsx`).
- Exibição de preços em reais e mensagens de sucesso ou erro na interface.
- Persistência em PostgreSQL e migrações de banco com Flyway.

## Tecnologias

| Camada | Tecnologias |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA, Lombok e Maven |
| Banco de dados | PostgreSQL e Flyway |
| Importação | Apache Commons CSV e Apache POI |
| Frontend | React, TypeScript, Vite e CSS |

## Organização do repositório

| Caminho | Conteúdo |
| --- | --- |
| `backend/gestaodeestoque/gestaodeestoque/` | Projeto Maven da API, incluindo `pom.xml`, Maven Wrapper e `src/` |
| `frontend/estoque-front/` | Interface web e configuração do Vite |

O backend está organizado em controller, service, repository, model e enums. As migrações ficam em `src/main/resources/db/migration` dentro do projeto Maven.

## Executando localmente

### Pré-requisitos

- JDK 25, conforme configurado no `pom.xml`.
- PostgreSQL em execução.
- Node.js compatível com a versão do Vite declarada no `package.json` e npm.
- Git.

O backend inclui Maven Wrapper, portanto não é necessário instalar o Maven separadamente.

### 1. Clonar o projeto

```bash
git clone https://github.com/DiogoUoma/gest-o-estoque.git
cd gest-o-estoque
```

### 2. Preparar o banco

Crie o banco no PostgreSQL:

```sql
CREATE DATABASE gestaoestoque;
```

A configuração atual usa `localhost:5432` e o banco `gestaoestoque`. Para outro endereço, ajuste `spring.datasource.url` em `backend/gestaodeestoque/gestaodeestoque/src/main/resources/application.properties`.

Configure as variáveis `DB_USER` e `DB_PASSWORD` com as credenciais do seu PostgreSQL no ambiente que iniciará a API.

**Windows — PowerShell:**

```powershell
$env:DB_USER="seu_usuario"
$env:DB_PASSWORD="sua_senha"
```

**Linux/macOS — Bash:**

```bash
export DB_USER="seu_usuario"
export DB_PASSWORD="sua_senha"
```

Esses comandos valem para a sessão atual do terminal. Se executar pela IDE, configure as variáveis na configuração de execução. Não inclua credenciais reais em arquivos versionados.

### 3. Iniciar o backend

A partir da raiz do repositório, no mesmo terminal em que configurou as variáveis:

```bash
cd backend/gestaodeestoque/gestaodeestoque
```

**Windows — PowerShell:**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux/macOS:**

```bash
sh mvnw spring-boot:run
```

O Flyway aplica as migrações ao iniciar. A API usa `http://localhost:8080`.

Para verificar a inicialização, acesse `http://localhost:8080/produtos/hello`. A resposta esperada é `API is working`.

### 4. Iniciar o frontend

Abra outro terminal na raiz do repositório:

```bash
cd frontend/estoque-front
npm ci
```

Confira o arquivo `.env` nessa pasta:

```dotenv
VITE_API_URL=http://localhost:8080
```

Inicie a interface:

```bash
npm run dev
```

Acesse `http://localhost:5173`. Mantenha o backend em execução para carregar e salvar os produtos.

O CORS da API está configurado para `http://localhost:5173`. Se mudar o endereço ou a porta do frontend, ajuste a origem permitida no `ProdutoController`. Reinicie o Vite após alterar o `.env`.

## Endpoints

URL base local: `http://localhost:8080`.

| Método | Rota | Descrição |
| --- | --- | --- |
| GET | `/produtos/hello` | Verificar se a API responde |
| GET | `/produtos` | Listar os produtos |
| GET | `/produtos/{id}` | Consultar um produto |
| POST | `/produtos` | Cadastrar um produto |
| PUT | `/produtos/{id}` | Atualizar um produto |
| DELETE | `/produtos/{id}` | Excluir um produto |
| POST | `/produtos/importar` | Importar produtos de CSV ou XLSX |

### Exemplo de cadastro

Envie um JSON para `POST /produtos` com o cabeçalho `Content-Type: application/json`:

```json
{
  "nome": "Água mineral 500 ml",
  "descricao": "Água sem gás",
  "categoria": "BEBIDA",
  "preco": 4.50,
  "quantidade": 30
}
```

O ID é gerado pelo banco. As categorias disponíveis são `BEBIDA`, `ALIMENTO`, `ALIMENTO_PREPARADO` e `LEMBRANCINHA`.

## Importação de produtos

A importação pode ser feita pela interface ou por `POST /produtos/importar`, usando `multipart/form-data` e o campo **`arquivo`**.

### CSV

Salve em **UTF-8**, com separador **ponto e vírgula (`;`)**. O cabeçalho deve conter exatamente estas cinco colunas, nesta ordem:

```csv
nome;descricao;categoria;preco;quantidade
Água mineral 500 ml;Água sem gás;BEBIDA;4.50;30
Biscoito;Pacote de biscoitos;ALIMENTO;6.00;20
Pastel de queijo;Preparado na hora;ALIMENTO_PREPARADO;10.00;15
Chaveiro;Lembrancinha da loja;LEMBRANCINHA;8.00;25
```

### Excel

Use um arquivo `.xlsx`. A primeira linha da primeira aba deve conter `nome`, `descricao`, `categoria`, `preco` e `quantidade`, em colunas separadas e nessa ordem. Os produtos começam na segunda linha. Apenas a primeira aba é lida; use texto ou números, sem fórmulas ou datas.

### Regras da importação

- Até **5.000 produtos** por arquivo e **5 MB** por arquivo.
- Nome obrigatório, com até 150 caracteres.
- Descrição opcional, com até 500 caracteres.
- Categoria deve corresponder a uma das opções disponíveis.
- Preço não negativo, com até dez dígitos inteiros e duas casas decimais. No CSV, ponto ou vírgula podem ser usados como separador decimal, sem separador de milhares.
- Quantidade inteira e não negativa.
- Linhas totalmente vazias são ignoradas.
- A importação cria novos produtos; não atualiza registros existentes nem verifica duplicidade por nome.
- Se houver erro na validação de uma linha, o lote não é salvo.

## Comandos adicionais

Na pasta do frontend:

```bash
npm run build
npm run lint
npm run preview
```

`build` gera os arquivos em `dist/`; `lint` executa a análise do código; `preview` permite visualizar o build localmente. O preview pode usar outra porta, exigindo ajuste do CORS para acessar a API pelo navegador.

Na pasta do backend, para executar os testes:

```powershell
# Windows — PowerShell
.\mvnw.cmd test
```

```bash
# Linux/macOS
sh mvnw test
```

O teste de contexto da aplicação depende da configuração do banco e das variáveis de ambiente.

## Estado do projeto

Projeto em desenvolvimento, com foco no cadastro e na manutenção das quantidades de produtos. O código atual não inclui autenticação, histórico de movimentações ou controle de vendas. A execução descrita neste README é voltada ao ambiente local.

## Autor

Desenvolvido por [Diogo Uoma](https://github.com/DiogoUoma).

