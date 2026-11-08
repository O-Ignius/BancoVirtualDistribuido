# Requisitos para compilar e executar

## Servidor (Java) — pasta `server/`

| Ferramenta | Versão mínima | Para quê |
|---|---|---|
| JDK | 17 | Compilar e rodar os nós (`maven.compiler.release=17` no `pom.xml`) |
| Maven | 3.6 | Build, download das dependências e geração das classes gRPC a partir de `proto/` |

As bibliotecas Java (JGroups 5.0, gRPC 1.68.1, Protobuf 3.25.5, Jackson) e o
compilador `protoc` são baixados automaticamente pelo Maven na primeira
compilação — não precisam ser instalados à parte, mas é necessário acesso à
internet nessa primeira vez.

Instalação (Debian/Ubuntu/Parrot):

```bash
sudo apt install openjdk-17-jdk maven
```

Se o `apt` falhar (ex.: no Parrot, erro `Redirection from https to 'http://...' is forbidden`
por causa de um espelho HTTP), instale o Maven sem sudo a partir do binário oficial:

```bash
V=3.9.16
curl -fLO https://dlcdn.apache.org/maven/maven-3/$V/binaries/apache-maven-$V-bin.tar.gz
mkdir -p ~/.local/opt ~/.local/bin
tar -xzf apache-maven-$V-bin.tar.gz -C ~/.local/opt
ln -sf ~/.local/opt/apache-maven-$V/bin/mvn ~/.local/bin/mvn   # ~/.local/bin precisa estar no PATH
```

Verificação:

```bash
java -version
mvn -version
```

## Cliente (Python) — pasta `client/`

| Ferramenta | Versão mínima | Para quê |
|---|---|---|
| Python | 3.9 | Rodar o `Client.py` |
| pip / venv | — | Instalar as dependências do `requirements.txt` |

Em distribuições recentes (Debian 12+, Ubuntu 23.04+, Parrot) o `pip` não
instala pacotes no Python do sistema, então use um ambiente virtual:

```bash
sudo apt install python3 python3-venv python3-pip
cd client
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Outros

- `bash` e utilitários POSIX (`setsid`, `timeout`, `tail`, `grep`) — usados pelo `server/compilaExecuta.sh`.
- Rede com suporte a multicast UDP entre as máquinas — o JGroups (`jgroups-config.xml`) usa `UDP` + `PING` para descoberta dos membros.
