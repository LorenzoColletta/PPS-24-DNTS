# 4. Design di Dettaglio

Questa sezione dettaglia le scelte di progettazione interne ai moduli del sistema DNTS, illustrando come i principi architetturali di alto livello siano stati declinati attraverso pattern object-oriented e funzionali.

## 4.1 Organizzazione del Codice (Package Structure)

Per riflettere rigorosamente la Separation of Concerns (SOC) delineata nell'architettura, la codebase è stata organizzata in namespace logici ben definiti:

* **`domain`:** Contiene il Pure Functional Core (algebra lineare, strutture della rete neaurale, logica di training, generazione dataset). Questo package non ha alcuna dipendenza da librerie di concorrenza o I/O.
* **`actors`:** Contiene il Distributed & State Layer. Definisce i protocolli, i comportamenti e la gerarchia del sistema ad Attori Akka.
* **`view`:** Costituiscono il Boundary Layer, deputato all'interazione con l'utente e al rendering grafico.
* **`config`:** Gestisce l'importazione e la validazione dei file di configurazione della simulazione.

## 4.2 Design del Dominio e Core Funzionale

Il livello di dominio matematico è stato progettato massimizzando l'uso di astrazioni puramente funzionali per garantire testabilità e assenza di side effect.

* **Incapsulamento e DSL Matematico (Algebra Lineare):** Per garantire un forte incapsulamento le entità matematiche di base (`Vector`, `Matrix`) sono modellate come astrazioni leggere e immutabili. Il design adottato disaccoppia la rappresentazione dei dati dai comportamenti: le operazioni matematiche standard vengono "iniettate" sulle strutture dati, fornendo un'interfaccia fluida e domain-specific.
* **Builder Pattern (Costruzione Rete):** La creazione della topologia della rete neurale (`Model`) è un processo a più step (definizione feature, aggiunta di N hidden layers, inizializzazione pesi). Per isolare questa complessità, è stato utilizzato il pattern creazionale Builder (`ModelBuilder`), che accumula la configurazione internamente e restituisce infine una struttura dati immutabile (`Model`).
* **Strategy Pattern per le Dinamiche di Addestramento:** Il processo di training di una rete neurale richiede l'applicazione di diverse euristiche matematiche, come le formule per il calcolo dell'errore, le tecniche di ottimizzazione dei gradienti e i meccanismi di regolarizzazione dei pesi. Poiché queste logiche variano in base alla configurazione iniziale scelta dall'utente, sono state modellate adottando il pattern comportamentale Strategy. Questo design permette di definire famiglie di algoritmi intercambiabili garantendo un forte disaccoppiamento tra il motore di calcolo generale e le specifiche formule applicate.
* **Abstract Factory Pattern per la Generazione dei Dataset:** La simulazione richiede la creazione dinamica di diverse distribuzioni di dati 2D. Per isolare la complessità della generazione procedurale è stato adottato il pattern Abstract Factory (`DataModelFactory`). La factory interpreta la configurazione dell'utente e istanzia la corretta topologia restituendo un'interfaccia comune.
* **Type Classes per il Disaccoppiamento di I/O e Serializzazione:** Per la conversione delle complesse entità di dominio (reti neurali) in formati idonei alla trasmissione di rete o al logging si è optato per l`utilizzo del pattern funzionale delle Type Classes, al fine di preservare la totale purezza e isolamento del core matematico. Esportazione e serializzazione sono stati modellati come interfacce generiche esterne ai dati; le relative implementazioni specifiche per i vari tipi vengono definite in moduli separati e fornite contestualmente (in modo implicito) all'infrastruttura solo quando necessario. Questo design garantisce strategie di traduzione dei dati totalmente intercambiabili, azzerando l'accoppiamento tra il dominio e i livelli di comunicazione.
* **State Monad (Transizioni di Stato):** I processi centrali dell'addestramento, come l'applicazione dei gradienti matematici e la sincronizzazione dei pesi tramite, comportano una continua evoluzione dello stato della rete neurale. Invece di mutare gli oggetti direttamente, pratica rischiosa in un sistema concorrente e distribuito, si è adottato il pattern architetturale della State Monad. Le transizioni di stato sono incapsulate in funzioni pure e componibili: le operazioni non alterano mai le istanze esistenti, ma descrivono la trasformazione restituendo sempre una nuova copia dello stato aggiornato insieme al risultato del calcolo. Questo garantisce la totale assenza di side-effect.

## 4.3 Design del Livello Concorrente (Actor System)

La progettazione del livello di controllo distribuito si fonda sul Modello ad Attori tipizzato, scelto per garantire robustezza e per modellare i componenti come macchine a stati finiti.

* **Message Protocols tramite ADT (Algebraic Data Types):** Ogni attore espone una propria API pubblica definita in un modulo dedicato. I messaggi ammessi sono modellati come algebraic data types. Questo design, combinato con i meccanismi di pattern matching del linguaggio, sposta il controllo di correttezza a tempo di compilazione: il sistema garantisce l'esaustività nella gestione degli eventi, eliminando alla radice la possibilità di messaggi non riconosciuti a runtime.
* **Finite State Machines (FSM)per i Cicli di Vita:** Molti attori nel sistema non hanno un comportamento statico, ma attraversano diverse fasi di ciclo di vita. Questo è stato possibile sfruttando il pattern delle Macchine a Stati Finiti (FSM). Ad esempio, il Trainer Actor transita attraverso stati ben definiti (idle, ready, training, paused) e la semantica di ricezione e reazione ai messaggi dipende dallo stato corrente.
* **Service Registry Pattern per il Discovery:** Per garantire l'elasticità del cluster ed evitare configurazioni di rete statiche, la scoperta dinamica dei nodi è governata da un pattern di Service Discovery. Gli attori deputati alla comunicazione di rete (GossipActor) si registrano presso un servizio di directory distribuito comune a tutto il cluster. Questo meccanismo permette ai nodi di ricevere aggiornamenti asincroni e automatici ogni volta che un peer entra o esce dal cluster, rendendo il sistema capace di adattarsi ai cambiamenti e ai fallimenti senza bisogno di un coordinatore centrale.

## 4.4 Viste Architetturali di Dettaglio

**Diagramma Strutturale: Type Classes per Serializzazione Distribuita**

Questo diagramma mostra come la logica di serializzazione (necessaria al Gossip Protocol) sia disaccoppiata dal Core Dominio tramite Type Classes, permettendo all'`AkkaSerializerAdapter` di tradurre gli oggetti in array di byte senza che le classi matematiche ne siano a conoscenza.