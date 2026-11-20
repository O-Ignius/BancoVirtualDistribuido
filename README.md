# README

## 1. Requisitos

Para compilar e executar os programas, é necessário possuir:

- Sistema operacional compatível com POSIX (GNU/Linux ou macOS).
- Python 3 instalado (caso execute o frontend).
- Java JDK instalado (caso execute os componentes Java).
- Apache Maven instalado e configurado no PATH.
- Bash ou shell compatível (para executar os scripts automatizados).
- Permissão de execução nos arquivos `.sh`.

As dependências Python são instaladas automaticamente pelos scripts de execução, utilizando o arquivo `requirements.txt` e o ambiente virtual (`venv`) criado pelo próprio projeto.

As dependências e os plugins dos projetos Java são gerenciados pelo Maven, por meio dos arquivos `pom.xml`.

## 2. Estrutura dos scripts

Os scripts `.sh` disponibilizados automatizam a compilação e/ou execução dos componentes do projeto.

Cada script possui uma finalidade específica:

| Script                    | Descrição                                                                                                                                       |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `executarFrontEnd.sh`     | Executa a interface web do banco virtual.                                                                                                       |
| `executarControlNodes.sh` | Executa os nós de controle.                                                                                                                     |
| `executarDataNodes.sh`    | Executa os nós de dados.                                                                                                                        |
| `executarGateway.sh`      | Executa o Gateway.                                                                                                                              |
| `executaTudoLocal.sh`     | Automatiza a inicialização dos componentes do sistema em ambiente local para testes, incluindo os nós de dados, os nós de controle e o Gateway. |

## 3. Permissões de execução

Antes da primeira execução, conceda permissão de execução aos scripts:

```bash
chmod +x client/*.sh && chmod +x server/*.sh
```

## 4. Compilação

A compilação dos componentes é realizada pelos próprios scripts de execução.

Para solicitar a compilação, basta utilizar o parâmetro `compile` ao executar o script desejado.

Exemplos:

```bash
./executarControlNodes.sh compile
./executarDataNodes.sh compile
./executarGateway.sh compile
./executarFrontEnd.sh compile
```

A compilação é realizada conforme as configurações de cada componente.

## 5. Execução

Os componentes podem ser executados individualmente ou em conjunto, conforme a necessidade.

### 5.1. Execução dos componentes individualmente

**Nós de controle:**

```bash
./executarControlNodes.sh
```

**Nós de dados:**

```bash
./executarDataNodes.sh
```

**Gateway:**

```bash
./executarGateway.sh
```

**Frontend:**

```bash
./executarFrontEnd.sh
```

### 5.2. Execução completa em ambiente local

Para iniciar os componentes do sistema em ambiente local por meio de um único script:

```bash
./executaTudoLocal.sh
```

Esse script automatiza a inicialização dos componentes necessários para a execução local do sistema.

### 5.3. Execução do frontend

O frontend pode ser executado em dois modos:

**Modo Mock**

Executa a interface utilizando um banco de dados em memória, sem necessidade de conexão com o Gateway.

```bash
./executarFrontEnd.sh mock
```

**Modo gRPC**

Executa a interface conectada ao Gateway por meio de comunicação gRPC.

```bash
./executarFrontEnd.sh
```

Nesse modo, o Gateway deve estar em execução e acessível pelo frontend.

## 6. Ajuda dos scripts

Todos os scripts disponibilizam opções de ajuda para consultar suas funcionalidades e parâmetros de execução.

Para visualizar as instruções de utilização de um script, utilize:

```bash
./nomeDoScript.sh -h
```

ou:

```bash
./nomeDoScript.sh --help
```

## 7. Observações

- Os scripts automatizam as etapas necessárias para compilação e execução dos componentes.
- O parâmetro `compile` solicita a compilação do componente correspondente.
- As dependências Python são instaladas automaticamente quando necessário.
- As dependências e os plugins Java são gerenciados pelo Maven.
- Os componentes podem estabelecer ou encerrar suas conexões durante a execução, não sendo obrigatória uma ordem rígida de inicialização, desde que os serviços necessários estejam disponíveis quando solicitados.
- Para a execução completa do sistema, deve haver pelo menos uma instância de cada componente necessário em funcionamento simultaneamente.
- Os comandos devem ser executados a partir do diretório correspondente ao script, salvo indicação contrária no próprio script.
