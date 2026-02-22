# Distributed Neural Training Simulation (DNTS)

**DNTS** è un framework software progettato per la simulazione e l'analisi dell'addestramento di reti neurali in un ambiente distribuito P2P (Peer-to-Peer). 

Il sistema realizza un ambiente di apprendimento decentralizzato: i nodi autonomi apprendono localmente dal proprio frammento di dati e utilizzano il **Gossip Learning** per scambiarsi e fondere i modelli in modo asincrono, raggiungendo il consenso globale senza alcun server centrale.

Sviluppato interamente in **Scala 3** e **Akka Cluster**, il progetto applica rigorosamente i principi della Programmazione Funzionale e del Modello ad Attori.


## Compilazione

Il progetto utilizza **sbt** (Scala Build Tool). Per generare l'eseguibile multipiattaforma (Fat JAR) contenente l'applicativo e tutte le sue dipendenze, esegui dalla root del progetto:

`sbt assembly`

Nota: per comodità nei comandi successivi, rinomineremo il file generato in `dnts.jar`

## Esecuzione del Sistema
L'applicazione è cross-platform (Windows, Linux, macOS) e richiede unicamente l'installazione di Java (JRE/JDK 11 o superiore). L'intero sistema può essere avviato tramite il JAR generato, senza dipendenze esterne.

### 1. Configurazione della Simulazione
La topologia della rete neurale, il dataset da generare e gli iperparametri di addestramento sono da definire in un file di configurazione `.conf`.
Assicurati che il file sia presente nella stessa cartella in cui stai lanciando il JAR del nodo Seed.

### 2. Avvio del Nodo Seed
Il primo nodo da lanciare è il seed. Questo nodo ha il compito di leggere il file di configurazione, avviare il cluster e fare da punto di ingresso per gli altri peer.

Apri un terminale ed esegui:
```console
java -jar dnts.jar --role seed --config simulation.conf
```

### 3. Avvio dei Nodi Client
Una volta avviato il nodo Seed, puoi lanciare quanti nodi client desideri. Ogni nodo si unirà al cluster, riceverà la sua porzione di dati e parteciperà il ciclo di Gossip Learning.
Una volta avviata una istanza di simulazione non sarà più possibile aggiungere nodi al cluster creato.

Apri un terminale ed esegui:
```console
# Avvio del primo client (es. sulla porta 2500)
java -jar dnts.jar --role client --ip 127.0.0.1 --port 2500

# Avvio del secondo client (es. sulla porta 2700)
java -jar dnts.jar --role client --ip 127.0.0.1 --port 2700
```
(Puoi aggiungere ulteriori client semplicemente specificando una --port diversa per ciascuno).

## Argomenti da CLI
L'eseguibile accetta i seguenti parametri:

| Parametro          | Descrizione                                          | Obbligatorietà                    |
| ------------------ | ---------------------------------------------------- | ----------------------------------|
| `--role <ruolo>`   | Ruolo del nodo nel cluster: `seed` o `client`        | Obbligatorio                      |
| `--config <path>`  | Percorso file configurazione                         | Obbligatorio se `--role seed`     |
| `--ip <indirizzo>` | IP di binding (default: `127.0.0.1`)                 | Opzionale                         |
| `--port <porta>`   | Porta di binding                                     | Obbligatorio se `--role client`   |
| `--help`           | Mostra menu di aiuto                                 | Opzionale                         |
