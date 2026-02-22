# 5. Implementazione
Questo capitolo illustra i dettagli implementativi più rilevanti del progetto, evidenziando come le scelte di design discusse nel capitolo precedente siano state tradotte in codice. L'attenzione è posta sull'utilizzo dei paradigmi funzionali e dei costrutti avanzati offerti da Scala 3.

## 5.1 Implementazione a cura di Giorgio Fantilli
Il mio lavoro si è concentrato sulla realizzazione del dominio matematico, del motore di addestramento (concorrente e funzionale), dell'interfaccia verso l'utente e dei meccanismi di base dell'infrastruttura (serializzazione e configurazione). Di seguito vengono esplorati i dettagli tecnici più interessanti.

### 5.1.1 Core Matematico
L'implementazione dell'algebra lineare richiedeva alte performance e immutabilità. Per evitare l'overhead prestazionale derivante dall'istanziazione di classi wrapper, si è sfruttato il costrutto nativo di Scala 3 opaque type.
I tipi Vector e Matrix sono stati definiti come type alias opachi delle normali collezioni immutabili di Scala. A tempo di compilazione, il type checker garantisce la type safety, mentre a runtime i tipi opachi vengono completamente cancellati garantendo l'assenza di penalità prestazionali.

Per fornire un'interfaccia fluida e domain-specific (DSL matematico), le operazioni algebriche non sono state definite come metodi di istanza, ma iniettate sulle strutture dati tramite gli extension methods. Questo ha permesso di fare un overloading pulito degli operatori standard e di definire operatori custom efficienti.

All'interno della topologia della rete (Network), la computazione del Forward Pass è totalmente priva dall'uso di cicli iterativi imperativi e variabili mutabili. La propagazione dell'input attraverso i layer è stata risolta in modo puramente dichiarativo concatenando l'uso di funzioni come map, zip e in particolare foldLeft: l'operazione di fold accumula ricorsivamente le trasformazioni, passando l'output di un layer (layer.weights * x + layer.biases) come input per l'iterazione successiva.


### 5.1.2 Motore di Addestramento
L'implementazione del motore di addestramento ha richiesto di coniugare la massima flessibilità e purezza matematica con un'esecuzione asincrona e reattiva. A tal fine, il modulo è stato nettamente diviso tra il dominio funzionale (TrainingCore, Strategies, Consensus) e il controllore concorrente (TrainerActor).

#### Il Dominio Matematico
Per quanto concerne il calcolo puro, l'implementazione fa un uso estensivo dei costrutti avanzati di Scala 3 per garantire disaccoppiamento e manutenibilità:
* **Contextual Abstractions:** L'iniezione delle euristiche di base non è stata realizzata tramite la classica Dependency Injection basata sui costruttori, bensì sfruttando le astrazioni contestuali. Metodi centrali come TrainingCore.computeBatchGradients definiscono dipendenze strategiche tramite clausole using (es. using lossFn: LossFunction, space: Space). Le implementazioni concrete (come la Mean Squared Error) sono fornite come istanze given all'interno dell'oggetto Strategies. Questo disaccoppia totalmente l'algoritmo di Backpropagation dalla metrica di errore, rendendo il codice estremamente pulito e idiomatico.
* **Enum avanzati per le Attivazioni:** Le funzioni di attivazione sfruttano le potenzialità degli enum di Scala 3. L'enum Activations implementa il trait Activation, incapsulando direttamente nel tipo non solo la logica di forward (metodo apply), ma anche la sua derivata (fondamentale per la retropropagazione) e l'euristica per il calcolo della deviazione standard ottimale usata nell'inizializzazione dei pesi.
* **Strategy e Factory per l'Ottimizzazione:** Le regole di aggiornamento dei pesi e prevenzione dell'overfitting sono governate da interfacce generiche (Optimizer, RegularizationStrategy). L'oggetto Strategies agisce sia da contenitore che da Factory: ad esempio, traduce in tempo reale la configurazione dichiarativa dell'utente (modificata tramite l'enum Regularization in L1, L2 o ElasticNet) nella corrispondente funzione matematica pura applicabile alle matrici dei pesi. L'ottimizzatore concreto, come SGD (Stochastic Gradient Descent), orchestra questi calcoli per generare e restituire una copia interamente nuova e aggiornata della Rete.
* **Calcolo del Consenso e Aggregazione:** Il modulo Consensus funge da Facade per le primitive di sincronizzazione della rete. Sfruttando gli extension methods di Scala 3, operazioni come averageWith e divergenceFrom vengono "iniettate" direttamente sulla classe Network. Questo approccio permette di mascherare la complessità implementativa dietro un DSL estremamente leggibile (es. net1 averageWith net2). A livello operativo (ConsensusOps), sia la fusione dei pesi per il protocollo Gossip P2P (averageModels), sia l'aggregazione dei gradienti per il singolo mini-batch (averageGradients), sono realizzate senza alcuna mutazione di stato. Infine, la distanza matematica tra i modelli per valutare la convergenza distribuita è astratta dal trait ConsensusMetric. La sua implementazione di default (fornita come contesto given) calcola accuratamente il Mean Absolute Error (MAE): accumula le differenze assolute di ogni singolo parametro (tutti i pesi e i bias) scendendo ricorsivamente nei layer tramite l'uso di foldLeft.

#### Il TrainerActor: Reattività e Macchina a Stati
Il TrainerActor realizza il controllo operativo del ciclo di addestramento, attraverso un'implementazione basata su Akka Typed.

1. **FSM tramite Behaviors ricorsivi:** Per eliminare la necessità di variabili di stato mutabili, l'attore è implementato come una Macchina a Stati Finiti dove ogni stato (idle, ready, training, paused) è rappresentato da un metodo che ritorna un Behavior[TrainerMessage]. Il passaggio tra gli stati avviene tramite l'invocazione e la restituzione di un nuovo metodo (es. da ready a training alla ricezione del comando di Start), garantendo che l'attore risponda solo ai messaggi validi per il suo stato attuale ed eviti incongruenze (es. ignorando comandi di training se già in esecuzione).

2. **Loop Asincrono:** Loop Asincrono: Per soddisfare il requisito vitale di reattività, il loop iterativo di addestramento è stato scomposto impiegando il TimerScheduler di Akka. Invece di usare un ciclo while bloccante che saturerebbe il thread dell'attore, il sistema processa un singolo batch, aggiorna il modello locale e poi auto-invia a se stesso un messaggio NextBatch tramite timers.startSingleTimer. Questo meccanismo permette alla coda dei messaggi (mailbox) di processare, tra un batch e l'altro, eventi ad alta priorità (come richieste di pausa, stop o il calcolo delle metriche per il MonitorActor), garantendo che la GUI rimanga fluida e il nodo sempre responsivo al cluster.

3. **Integrazione Layered:** In ogni iterazione del loop, il TrainerActor agisce da coordinatore: richiede il modello al ModelActor, preleva il batch corrente, invoca il TrainingCore e reinvia i gradienti calcolati per l'applicazione. Questo flusso unidirezionale garantisce che la logica distribuita di Akka non "contamini" mai le funzioni matematiche sottostanti.


### 5.1.3 Serializzazione Custom Binaria e Akka Adapter
Come anticipato il sistema P2P necessita di scambiare continuamente l'intero stato dei modelli predittivi senza che il dominio matematico sia vincolato a librerie di rete. Per ottenere questo risultato massimizzando le performance, è stata implementata una serializzazione binaria custom basata sul pattern Type Class.
* **Serializzazione Binaria Componibile tramite Type Classes:** L'interfaccia generica Serializer[A] definisce il contratto funzionale per la conversione in array di byte, permettendo di evitare il pesante overhead della serializzazione Java nativa. Il vero vantaggio di questo approccio in Scala 3 risiede nella sua natura ricorsiva e componibile, garantita dalle contextual abstractions. Le implementazioni per i tipi base (es. Vector e Matrix) sono fornite come istanze given e fungono da blocchi base. Serializzatori più complessi, come il ModelSerializer delegano la logica dichiarando dipendenze implicite tramite clausole using. In questo modo, il compilatore risolve e inietta automaticamente l'albero delle dipendenze permettendo di costruire la serializzazione assemblando ricorsivamente i serializzatori più piccoli in modo pulito e dichiarativo.
* **Adapter per Akka Serialization:** Per collegare questa infrastruttura al framework di messaggistica, è stata implementata la classe AkkaSerializerAdapter, che estende il trait nativo SerializerWithStringManifest di Akka. L'adapter mantiene un registro interno (TypeBinding) che mappa costanti stringa (i "Manifest") alle rispettive classi e alle istanze del dominio recuperate tramite il costrutto summon[DomainSerializer[T]]. Quando Akka richiede la serializzazione di un messaggio l'adapter funge da dispatcher, delegando l'operazione sui byte al corretto Serializer di dominio.


### 5.1.4 Inizializzazione del Sistema
L'avvio dell'applicazione (Main) e la lettura dei parametri sono stati implementati mantenendo un rigoroso approccio funzionale e Fail-Fast, al fine di garantire che il nodo entri nella rete distribuita in uno stato perfettamente coerente.
* **Parsing della CLI:** L'elaborazione degli argomenti da riga di comando all'interno di CliParser evita completamente l'uso di cicli while o variabili mutabili. La logica è implementata tramite una funzione ricorsiva basata sul Pattern Matching. Per massimizzare le performance, la funzione è stata annotata con @tailrec, delegando al compilatore Scala l'ottimizzazione a basso livello. Il risultato è incapsulato in un ADT ParseResult (Success, Failure, Help) che permette la gestione esplicita di tutti i possibili esiti.
* **ConfigLoader e Data Transfer Object (DTO):** Per la definizione della topologia della simulazione (iperparametri, layer, euristiche) si è sfruttata la libreria Typesafe Config (HOCON). Il modulo ConfigLoader legge i file testuali e si occupa di validare e tradurre i dati grezzi in un'unica struttura dati immutabile fortemente tipizzata, FileConfig, che agisce da DTO per il resto del sistema.
* **Abstract Factory per la Generazione dei Dati:** La simulazione richiede la creazione procedurale di diverse distribuzioni di dati 2D. La configurazione testuale viene mappata su un ADT DatasetStrategyConfig (es. Gaussian, Ring, Spiral). Il componente DataModelFactory realizza il pattern Abstract Factory: tramite pattern matching sulla strategia richiesta, istanzia la corretta implementazione concreta di LabeledDatasetModel (es. DoubleSpiralDataset o DoubleXorDataset), calcolando inoltre in modo deterministico i sub-seed (positivi e negativi) necessari alla generazione matematica dei punti.
* **Dependency Injection tramite Contextual Abstractions:** Alcuni parametri globali di sistema (come la frequenza di sincronizzazione P2P, l'intervallo di render grafico o la LossFunction di default) sono stati estratti nel trait AppConfig. L'oggetto Main istanzia la configurazione di produzione e la dichiara come contesto implicito (given). Questo approccio permette una Dependency Injection nativa a tempo di compilazione lungo l'intero albero degli attori.


### 5.1.5 Monitoraggio Reattivo e Boundary Layer
L'interazione tra gli attori e l'interfaccia grafica (View) è stata progettata per garantire un disaccoppiamento totale, in modo che la logica di backend non conosca i dettagli sulla tecnologia di rendering (nel nostro caso Swing).
* **Disaccoppiamento tramite ViewBoundary:** Il MonitorActor non usa direttamente librerie grafiche, ma comunica con l'esterno unicamente tramite il trait ViewBoundary. Questa interfaccia astratta viene passata all'attore al momento della sua creazione. Ciò permette di testare il sistema isolatamente o di sostituire l'intera interfaccia grafica .
* **Gestione dei Comandi della UI:** Per consentire all'interfaccia di inviare comandi (Start, Pause, Stop) al sistema, ViewBoundary espone il metodo bindController. In fase di avvio, il MonitorActor vi collega una semplice funzione che inoltra i comandi ricevuti dalla UI direttamente alla propria mailbox, garantendo una comunicazione sicura tra i diversi thread.
* **Stato Grafico Immutabile:** Tutti i dati necessari per aggiornare lo schermo (epoca corrente, loss, metriche di consenso) sono raggruppati nella case class immutabile ViewStateSnapshot. Questo oggetto viene generato e passato di stato in stato all'interno del comportamento dell'attore, eliminando del tutto l'uso di variabili mutabili da sincronizzare.
* **Aggiornamento Asincrono dei Grafici:** Per aggiornare le metriche senza bloccare l'attore in cicli di attesa, è stato utilizzato il TimerScheduler di Akka. Il MonitorActor si auto-invia periodicamente un messaggio (TickMetrics) che fa scattare la richiesta dei dati al ModelActor. Appena riceve i dati aggiornati l'attore li inoltra alla View richiamando boundary.plotMetrics(...) in modo reattivo.


## 5.2 Implementazione a cura di Domenico Francesco Giacobbi
Il mio lavoro si è concentrato:
* sulla gestione del ModelActor e delle Monade State
* sullo sviluppo del GossipActor
* sul passaggio della configurazione iniziale ai peer
* sulla distribuzione del dataset
* sul calcolo del consensus

### 5.2.1 Integrazione delle Monade State

Per evitare l'uso di variabili mutabili e prevenire race conditions, la logica di aggiornamento dei pesi è stata delegata all'oggetto ModelTasks, che restituisce una monade State[Model, Unit].
L'implementazione segue questo flusso:
* **Ricezione Gradienti**: Quando il TrainerActor invia ApplyGradients(grads), il ModelActor non modifica i parametri esistenti.
* **Definizione della Transizione**: Viene richiamata ModelTasks.applyGradients(grads), che definisce come il modello deve cambiare in base all'ottimizzatore (es. SGD o Adam).
* **Esecuzione**: Tramite il metodo .run(currentModel), la monade produce una nuova istanza immutabile del modello (nextModel).
* **Ricorsione**: L'attore transita nuovamente nello stato active con il nuovo modello

### 5.2.2 Implementazione del Sottosistema Gossip
Il sottosistema Gossip costituisce la spina dorsale della comunicazione P2P del sistema distribuito. 
La sua implementazione traduce in codice le scelte architetturali descritte nella sezione 4.5, sfruttando in modo sistematico i costrutti di Scala 3 e il modello ad attori di Akka Typed per garantire sicurezza ai tipi, assenza di stato mutabile condiviso e resilienza ai guasti di rete.

### 5.2.3 Protocollo e Gerarchia dei Tipi (`GossipProtocol`)
Il punto di partenza dell'intera infrastruttura è la definizione del protocollo dei messaggi in `GossipProtocol`. 
La scelta progettuale fondamentale è stata modellare la gerarchia dei comandi come un'unica famiglia di tipi chiusa, sfruttando i `sealed trait` di Scala 3.
Il trait radice `GossipCommand` è volutamente non-sealed: questo permette ai sottomoduli (`ConfigurationProtocol`, `ConsensusProtocol`, `DatasetDistributionProtocol`) di estendere il tipo con i propri comandi specializzati. 
Il risultato è una gerarchia aperta verso l'esterno ma chiusa all'interno di ogni sottomodulo, che permette al compilatore di verificare la completezza del pattern matching a livello locale senza vincolare l'estensibilità del sistema.
I comandi di controllo globale sono separati semanticamente in una gerarchia distinta (`ControlCommand extends GossipCommand`). 
Questa scelta non è puramente estetica: consente al `GossipBehavior` di distinguere staticamente, a tempo di compilazione, i messaggi che devono essere instradati ai sottomoduli da quelli che richiedono la propagazione sull'intera rete, senza alcun casting a runtime.

### 5.2.4 Il Ciclo di Gossip

Il cuore dell'algoritmo di Gossip Learning è implementato come una pipeline asincrona a più stadi, orchestrata dal `TimerScheduler`.
Allo scattare del tick periodico (`TickGossip`), si avvia una catena di messaggi che attraversa tre stadi distinti.
Nel primo stadio, l'attore interroga il `DiscoveryActor` per ottenere la lista aggiornata dei peer attivi. 
Questa richiesta è non bloccante: l'attore rimane responsivo ad altri messaggi mentre attende la risposta. 
Nel secondo stadio, alla ricezione di `WrappedPeers`, viene selezionato un peer casuale tra quelli disponibili (escludendo se stesso) e viene richiesta al `ModelActor` una snapshot del modello locale tramite `GetModel`.
Nel terzo stadio, alla ricezione del modello tramite un nuovo `messageAdapter`, il messaggio `SendModelToPeer` viene recapitato al peer selezionato, che lo riceverà come `HandleRemoteModel` e delegherà al proprio `ModelActor` la fusione tramite `SyncModel`.

### 5.2.5 Propagazione dei Comandi di Controllo

La diffusione dei comandi globali (pausa, ripresa, stop) è implementata attraverso due varianti del pattern broadcast. 
`SpreadCommand` propaga il comando a tutti i peer incluso se stesso, mentre `SpreadCommandOther` lo propaga esclusivamente agli altri nodi. 
Questa distinzione è necessaria perché il nodo Seed, che emette il comando, ha già eseguito localmente l'azione corrispondente e non deve riceverla una seconda volta.
La gestione del comando `GlobalStop` merita particolare attenzione: oltre a propagare lo stop al `RootActor` locale, l'implementazione invoca `timers.cancelAll()` prima di restituire `Behaviors.stopped`. 
Questo garantisce che non rimangano timer orfani in esecuzione dopo che l'attore è terminato.

### 5.2.6 Bootstrap Distribuito: `ConfigurationBehavior`

L'implementazione del `ConfigurationBehavior` risolve un classico problema del distributed computing: come sincronizzare lo stato iniziale di un cluster in assenza di un coordinator centralizzato e affidabile.
La soluzione adottata sfrutta lo stato immutabile dell'attore, dove il metodo `active` accetta due parametri opzionali — `cachedConfig` e `gossip` — che rappresentano lo stato corrente. 
Ogni transizione di stato produce una nuova invocazione ricorsiva di `active` con i parametri aggiornati, seguendo il pattern di Akka Typed per evitare variabili mutabili.
Un aspetto sottile riguarda la gestione del riferimento al `GossipActor`. Poiché i due attori possono essere avviati in ordine non deterministico, la registrazione avviene tramite il messaggio `RegisterGossip`, ricevuto dopo l'avvio. 
Fino a quando questo messaggio non è arrivato, l'attore notifica un avviso ma non blocca il sistema: il polling continua e i peer vengono interrogati non appena il riferimento è disponibile.
La transizione dal ruolo di richiedente al ruolo di fornitore avviene automaticamente alla ricezione di `ShareConfig`: da quel momento `cachedConfig` è popolato e qualsiasi `RequestInitialConfig` ricevuto da un nodo verrà soddisfatto immediatamente, senza richiedere alcuna logica aggiuntiva.

### 5.2.7 Calcolo del Consensus: `ConsensusBehavior`

L'implementazione del `ConsensusBehavior` è la più sofisticata del sottosistema e traduce direttamente il pattern Scatter-Gather.
**Gestione del round tramite stato immutabile.** La case class `ConsensusRoundState` incapsula tutto il contesto di un round attivo: l'identificativo univoco (`roundId`), il numero di risposte attese (`expectedCount`), i modelli raccolti (`collected`) e la snapshot del modello locale al momento dell'avvio del round (`localModel`). 
La separazione tra `consensusRound` e `roundCounter` è intenzionale: il contatore viene incrementato alla ricezione dei peer nella fase di Scatter, mentre il round state viene aggiornato ad ogni risposta ricevuta nella fase di Gather.
**Filtraggio dei peer remoti.** `WrappedPeersForConsensus`  esclude i nodi locali dal calcolo. 
**Correlazione e scarto dei messaggi stale.** La gestione di `ConsensusModelReply` implementa un meccanismo di correlazione esplicita basato sul `roundId`.
**Completamento del round e timeout.** Il round può concludersi in due modi distinti. 
Nel caso nominale, quando il contatore `updated.collected.size >= updated.expectedCount + 1` viene raggiunto, il timer di timeout viene annullato e il consenso viene calcolato su tutti i modelli ricevuti. 
Nel caso di timeout, il messaggio `ConsensusRoundTimeout` viene processato solo se il `roundId` corrisponde al round attivo, dopodiché il sistema procede con i dati parziali disponibili.
Il calcolo finale del consenso è delegato alla funzione privata `computeNetworkConsensus`, definita a livello di package.


### 5.2.8 Distribuzione del Dataset: `DatasetDistributionBehavior`

L'implementazione del `DatasetDistributionBehavior` si occupa della suddivisione del train set.
Esso viene suddiviso in parti uguali e ogni parte viene affidata a un peer.
Prima di essere suddiviso in chunk, viene effetuato uno shuffle del train set basato sul seed che viene fornito mediante il metodo `RegisterSeed`.
Il test set viene passato interamente ad ogni peer senza essere suddiviso.