# 4. Design di Dettaglio

Questa sezione dettaglia le scelte di progettazione interne ai moduli del sistema DNTS, illustrando come i principi architetturali di alto livello siano stati declinati attraverso pattern object-oriented e funzionali.

## 4.1 Organizzazione del Codice (Package Structure)

Per riflettere rigorosamente la Separation of Concerns (SOC) delineata nell'architettura, la codebase è stata organizzata in namespace logici ben definiti:

* **`domain`:** Contiene il Pure Functional Core (algebra lineare, strutture della rete neaurale, logica di training, generazione dataset). Questo package non ha alcuna dipendenza da librerie di concorrenza o I/O.
* **`actors`:** Contiene il Distributed & State Layer. Definisce i protocolli, i comportamenti e la gerarchia del sistema ad Attori Akka.
* **`view`:** Costituiscono il Boundary Layer, deputato all'interazione con l'utente e al rendering grafico.
* **`config` e `cli`:** Gestiscono il bootstrap, i parametri da terminale e i file di configurazione.


## 4.2 Core Matematico e Astrazione della Rete Neurale

Questa sezione descrive la progettazione del livello base del sistema: l'algebra lineare e la topologia della rete neurale. L'obiettivo primario è stato garantire la purezza funzionale e l'immutabilità totale. Poiché lo stato del modello predittivo deve essere continuamente scambiato e aggiornato tra attori concorrenti, modellare queste entità come strutture dati immutabili rendendo la concorrenza sicura.

### 4.2.1 Astrazioni per l'Algebra Lineare
Per evitare l'anti-pattern della Primitive Obsession (ovvero l'abuso di array o collezioni generiche per rappresentare concetti complessi), è stato disegnato un dominio matematico dedicato a matrici e vettori. Il design adotta il principio dell'incapsulamento spinto: la reale rappresentazione in memoria dei dati viene nascosta, offrendo all'esterno unicamente un Domain-Specific Language (DSL) per le operazioni algebriche. Ogni operazione matematica non altera mai gli operandi, ma genera una nuova istanza.

### 4.2.2 Topologia della Rete Neurale
La rete neurale è modellata tramite un approccio composizionale gerarchico (il Modello aggrega la Rete, che a sua volta aggrega una sequenza di Layer). Tali entità sono puri aggregati strutturali deputati unicamente all'inferenza (forward pass).
Per garantire flessibilità matematica, il comportamento dei singoli strati è regolato dal pattern Strategy: le funzioni di attivazione sono ma modellate come interfacce intercambiabili. Questo permette di iniettare dinamicamente diverse logiche (es. Sigmoide, ReLU) senza dover modificare le classi strutturali della rete.

La creazione stessa della topologia della rete neurale è un processo a più step (definizione feature, aggiunta di N hidden layers, applicazione di complesse euristiche per l'inizializzazione pesi). Per isolare questa complessità, è stato utilizzato il pattern creazionale Builder, che accumula la configurazione internamente e restituisce infine una struttura dati immutabile corretta.

<div align="center">
  <img src="assets/diagramma-classi-4-2.png" width="40%" alt="Diagramma delle classi: Model, Network, Layer e Builder">
  <br>
  <em>Figura N: Struttura composizionale e pattern creazionali del Dominio e della Rete Neurale.</em>
</div>


## 4.3 Motore di Addestramento e Controllo
Questo modulo è responsabile dell'avanzamento della simulazione: processa il dataset, calcola l'errore del modello e ne aggiorna i pesi. Al fine di rispettare il requisito SOC, il suo design è stato nettamente diviso in due macro-aree: un nucleo matematico puro e un controllore reattivo ad attori.

### 4.3.1 Il Dominio Funzionale dell'Addestramento
Il calcolo matematico dell'addestramento (feed-forward, calcolo dell'errore e backpropagation) è stato modellato come una pipeline di funzioni pure. Invece di far mutare i pesi della rete, l'algoritmo di Backpropagation si limita a calcolare e restituire i gradienti (le variazioni da applicare) sotto forma di strutture dati immutabili.
Per gestire la variabilità degli iperparametri, si è fatto largo uso del pattern Strategy:
* **Euristiche Intercambiabili:** Componenti come la Loss Function (es. Mean Squared Error), gli Ottimizzatori (es. Stochastic Gradient Descent) e i meccanismi di Regolarizzazione (L1, L2, ElasticNet) sono modellati come interfacce funzionali. Le implementazioni concrete vengono iniettate a runtime in base alla configurazione iniziale, garantendo il Open/Closed Principle del core matematico rispetto a aggiunte future.
* **Facade Computazionale:** Per evitare che il livello ad attori debba orchestrare manualmente i passaggi matematici sui singoli punti del dataset, è stato introdotto un oggetto TrainingCore che funge da Facade. Questo componente riceve il batch di dati e il modello corrente, delega il calcolo della retropropagazione, e restituisce i gradienti mediati pronti per l'applicazione.

### 4.3.2 TrainerActor come Macchina a Stati (FSM)
Il ciclo di vita dell'addestramento (epoche e batch) nel TrainerActor non può essere implementato con loop tradizionali, poiché bloccherebbero l'attore impedendogli di gestire i messaggi di rete (come il protocollo Gossip) o i comandi utente.
Per ovviare a questo problema, il TrainerActor è stato progettato adottando il pattern architetturale delle Macchine a Stati Finiti (FSM), nativamente supportato dal paradigma Behavior di Akka Typed:
1. **Transizioni di Stato:** Il comportamento dell'attore muta attraversando stati ben definiti: Idle (attesa delle dipendenze), Ready (configurazione ricevuta), Training (loop di calcolo attivo) e Paused (sospensione su richiesta dell'utente). In ogni stato, l'attore reagisce solo ai messaggi pertinenti, scartando o ignorando comandi invalidi.
2. **Loop di Addestramento Asincrono:** Il ciclo sui batch di dati non è continuo, ma è scandito dall'invio asincrono di messaggi a se stesso e da un sistema di Timer. Al termine del calcolo di un batch, l'attore programma l'elaborazione del successivo. Questo design garantisce che, tra un batch e l'altro, la coda dei messaggi dell'attore sia libera di processare altri eventi prioritari (come le richieste di misurazione delle metriche i comandi utente) mantenendo il sistema altamente responsivo.

<div align="center">
  <img src="assets/sequence-diagram-4-3.png" width="65%" alt="Sequence Diagram che illustra il loop di addestramento asincrono">
  <br>
  <em>Figura N: Diagramma di sequenza che illustra il loop asincrono di addestramento.</em>
</div>


## 4.X Livello di Serializzazione

La natura P2P dell'architettura e l'algoritmo di Gossip Learning richiedono che i nodi si scambino ripetutamente lo stato dei propri modelli predittivi. Le entità scambiate possono essere di grandi dimensioni. Per ottimizzare le performance di rete e ridurre la latenza, il sistema adotta una serializzazione binaria custom, evitando formati verbosi o meccanismi di serializzazione standard inefficienti.
Il design di questo livello è stato guidato dalla necessità di preservare il pure functional core, che non deve avere conoscenza del fatto che i suoi oggetti verranno inviati su una rete, né  dipendere da librerie dell'infrastruttura.

### 4.X.1 Disaccoppiamento tramite Type Classes
Per evitare di accoppiare le classi di dominio con interfacce come dedicate o con metodi intrusivi, la logica di conversione è stata estratta utilizzando il pattern funzionale delle Type Classes.
Il design prevede la definizione di un'interfaccia generica Serializer[T] pura. Le implementazioni specifiche per i vari tipi di dato (es. LinearAlgebraSerializers, NetworkSerializers, GossipSerializers) sono fornite come istanze implicite in moduli separati dal dominio.
Questo approccio garantisce la massima flessibilità e componibilità: il serializzatore di una rete neurale non fa altro che comporre i serializzatori dei singoli layer, che a loro volta richiamano i serializzatori delle matrici, in modo totalmente modulare e dichiarativo.

### 4.X.2 Integrazione con l'Infrastruttura
L'integrazione della serializzazione personalizzata in Akka richiede l'estensione di classi proprietarie. Per evitare di accoppiare l'intera codebase a queste dipendenze è stato introdotto un Boundary Component basato sul pattern Adapter (AkkaSerializerAdapter).
Questo componente funge da ponte architetturale: implementa le interfacce richieste dal framework di rete, ma delega l'effettiva logica di (de)serializzazione a moduli indipendenti.

<div align="center">
  <img src="assets/diagramma-serializzazione-4-4.png" width="85%" alt="Diagramma strutturale della serializzazione.">
  <br>
  <em>Figura N: Disaccoppiamento della logica di serializzazione tramite Type Classes e Adapter.</em>
</div>


## 4.X Monitoraggio e Interazione con l'Utente

Questo modulo ha la responsabilità di estrarre le metriche (loss, consenso, andamento della rete) dal cluster distribuito e renderle disponibili all'utente, permettendo al contempo di inviare comandi di controllo (start, pause, stop). Il design è stato progettato tenendo conto di un netto disaccoppiamento tra l'infrastruttura reattiva di Akka e il livello di presentazione.

### 4.X.1 MonitorActor come Gateway
Per evitare che la logica della View debba comunicare direttamente con i singoli attori operativi è stato introdotto il MonitorActor. Questo componente agisce a tutti gli effetti come un Facade e un Bridge architetturale:
* **Aggregazione Event-Driven:** Il MonitorActor non effettua polling verso gli altri attori per ottenere le metriche. Al contrario, si iscrive al flusso di eventi del sistema e riceve aggiornamenti in modo totalmente asincrono (es. riceve la notifica di un nuovo calcolo dell'errore o di un aggiornamento del modello distribuito).
* **Inoltro dei Comandi:** Funge da singolo punto di ingresso per i comandi impartiti dall'utente, traducendoli in messaggi typed (MonitorCommand) e instradandoli ai corretti attori del dominio.

### 4.X.2 Disaccoppiamento della View
Per garantire che il livello ad attori rimanga separato dalla tecnologia di rendering utilizzata, l'interazione tra il MonitorActor e l'interfaccia grafica vera e propria è mediata da un Boundary Layer.
L'interazione è governata da un'astrazione dedicata (il trait ViewBoundary) che implementa una logica Observer: il MonitorActor funge da Publisher, notificando gli aggiornamenti di stato alla GUI, che agisce da Subscriber passivo.

<div align="center">
  <img src="assets/diagramma-monitor-4-5.png" width="70%" alt="Diagramma strutturale del livello di presentazione.">
  <br>
  <em>Figura N: Disaccoppiamento del livello di presentazione tramite ViewBoundary e MonitorActor.</em>
</div>


## 4.X Gestione della Configurazione

Questo sottomodulo gestisce la fase iniziale dell'applicazione, interfacciandosi con l'esterno per acquisire i parametri di simulazione tramite Command Line Interface (CLI) e file di configurazione. L'obiettivo di design principale in questa fase è stato isolare le operazioni di I/O dal resto del sistema, proteggendo il core matematico e il livello ad attori.

L'intero processo di bootstrap segue il principio architetturale Fail-Fast: se uno qualsiasi degli input (CLI o file) risulta invalido o incoerente, il sistema si interrompe prima ancora di inizializzare l'infrastruttura di rete, evitando la propagazione di configurazioni corrotte nel cluster P2P.

### 4.X.1 Incapsulamento e Validazione della CLI
La lettura e l'interpretazione degli argomenti da riga di comando sono state isolate in un componente dedicato (CliParser). Questo modulo si occupa esclusivamente di intercettare l'input grezzo, validarlo sintatticamente e tradurlo in una struttura dati tipizzata (CliOptions). Questo approccio sposta la validazione ai "margini" del sistema: comandi errati vengono intercettati e bloccati istantaneamente, proteggendo i livelli interni da stati anomali.

### 4.X.2 Parsing della Configurazione e Traduzione nel Dominio
Parallelamente alla CLI, la definizione della simulazione (topologia della rete, euristiche, dataset) risiede in un file di configurazione esterno. Per far sì che il core matematico e gli attori non debbano mai dipendere da librerie di parsing o manipolare formati testuali generici, è stato progettato un componente traduttore (ConfigLoader).
Questo modulo legge le stringhe di configurazione e si occupa di tradurle e istanziare direttamente i corretti oggetti di dominio immutabili.





















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