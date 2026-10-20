#!/bin/bash
#

########################################
## 1) Necessário adicionar a pasta do .jar na lista de bibliotecas do CLASSPATH
LIBS=../libs/java/jgroups-5.0.0.Final.jar:./
export CLASSPATH=$CLASSPATH:$LIBS

## 2) Necessário forçar IPv4 para funcionar na rede cabeada dos laboratórios do IFMG
# IPV4="-Djgroups.bind_addr=172.16.2.222 -Djava.net.preferIPv4Stack=true"
IPV4="-Djava.net.preferIPv4Stack=true"

## 2.1) necessário caso existam muitas interfaces virtuais de rede (ex.: com nomes iniciados com br-, docker0, etc)
get_IP_of_interface() {
  # ifconfig 2>&1 1>/dev/null || sudo apt install net-tools # instala o ifconfig se não houver

  if [[ -n $1 ]]; then
    ifconfig | grep "$1" -A5 | grep 'inet ' | sed 's/ *inet //; s/\/.*//; s/ .*//; s/[ \t]*//' | tr -d '[:space:]'
    # ip addr | grep "$1" -A5 | grep 'inet ' | sed 's/ *inet //; s/\/.*//' | tr -d '[:space:]'
  else
    ifconfig | grep -E "(eno1|eth0|en0)" -A5 | grep 'inet ' | sed 's/ *inet //; s/\/.*//; s/ .*//; s/[ \t]*//' | tr -d '[:space:]'
    # ip addr | grep -E "(eno1|eth0|en0)" -A5 | grep 'inet ' | sed 's/ *inet //; s/\/.*//' | tr -d '[:space:]'
  fi
}
# MEU_IP="$(get_IP_of_interface)"
# IPV4="$IPV4 -Djgroups.bind_addr=$MEU_IP"
# echo $IPV4
########################################

## compila o código-fonte
#javac Pessoa.java

## executa 1 processo nesta janela
java $IPV4 ServidorInstancia #2>/dev/null

## executa outros 2 processos em janelas separadas
# COMANDO="java $IPV4 -cp $LIBS ExemploMeme # 2>/dev/null"
# echo "$COMANDO"
# sleep 2
# ( xterm -hold -e $COMANDO || x-terminal-emulator -e $COMANDO ) &
# ( xterm -hold -e $COMANDO || x-terminal-emulator -e $COMANDO ) &
