# 3. Design Architetturale

Questa sezione descrive l'architettura complessiva del sistema Distributed Neural Training Simulation (DNTS). La progettazione è stata guidata dai requisiti di resilienza, concorrenza distribuita e rigorosa separazione delle responsabilità, adottando un approccio decentralizzato fondato sui paradigmi della programmazione funzionale e del modello ad attori.

## 3.1 Architettura Complessiva e Stile Architetturale

Il sistema adotta a livello macroscopico uno stile architetturale **Peer-to-Peer (P2P) Strutturato Leaderless**. Il cluster è composto da un insieme dinamico di Nodi autonomi, senza la presenza di un server centrale (Single Point of Failure) o di un Parameter Server dedicato. Il consenso sul modello globale emerge in modo organico attraverso la comunicazione asincrona diretta tra i peer (*Gossip Learning*).

A livello del singolo nodo, l'architettura segue rigorosamente il principio della **Separation of Concerns (SOC)**. Per gestire la complessità derivante dall'unione di calcolo matematico intensivo e comunicazione distribuita, l'architettura interna del nodo è basata su un pattern **Layered (a strati) ibrido**:

1. **Layer Computazionale (Pure Functional Core):** Uno strato deterministico e privo di effetti collaterali che gestisce unicamente la logica matematica delle reti neurali.
2. **Layer di Stato e Distribuzione (Actor System):** Uno strato concorrente che orchestra il flusso di esecuzione, detiene lo stato mutabile e gestisce la rete.
3. **Layer di I/O e Presentazione:** Il confine esterno del sistema che interagisce con l'utente (CLI/GUI).

## 3.2 Pattern Architetturali Utilizzati

Per soddisfare i requisiti, la struttura si appoggia ai seguenti pattern architetturali:

* **Actor Model:** Pattern fondamentale utilizzato per modellare l'intera infrastruttura concorrente e di rete. Garantisce l'incapsulamento dello stato e l'elaborazione asincrona basata su messaggi, eliminando alla radice la necessità di lock espliciti o memoria condivisa tra i thread.
* **Entity-Control-Boundary (ECB):** L'organizzazione degli attori riflette questo pattern:
    * *Entity:* Il gestore dello stato del modello locale (Model Actor).
    * *Control:* Gli orchestratori dei cicli operativi locali e di rete (Trainer Actor, Gossip Actor, Consensus Actor).
    * *Boundary:* L'interfaccia verso l'esterno per l'acquisizione dei comandi e la visualizzazione delle metriche (Monitor Actor).
* **Gossip Protocol:** Pattern architetturale di rete utilizzato per la disseminazione epidemica delle informazioni. I nodi scelgono iterativamente e casualmente dei peer con cui sincronizzare il proprio stato, garantendo tolleranza ai guasti ed eventuale consistenza.

## 3.3 Componenti del Sistema Distribuito

L'unità fondamentale del sistema distribuito è il **Nodo**. Ogni nodo ospita un sistema ad attori gerarchico supervisionato da un *Root Actor*, che instrada l'inizializzazione, e da un *Cluster Manager*, responsabile della topology di rete.

All'interno di ogni istanza della JVM (il Nodo), operano i seguenti macro-componenti logici e distribuiti:

* **Gossip Actor (Controller di Rete):** È il componente deputato all'interazione P2P. Gestisce il protocollo di rete, preleva snapshot del modello locale e li invia ai peer, ricevendo a sua volta i modelli remoti da fondere.
* **Model Actor (Stateful Entity - Single Source of Truth):** Rappresenta il cuore dello stato mutabile del nodo. Incapsula i Pesi e i Bias della rete neurale, esponendo interfacce per aggiornamenti atomici sequenziali (sia derivanti dal calcolo locale che dai merge di rete).
* **Trainer Actor (Controller di Calcolo):** Esecutore logico del ciclo di addestramento. Preleva batch di dati locali, delega i calcoli intensivi al Pure Functional Core (Backpropagation) e notifica i gradienti risultanti al gestore del modello.
* **Sub-Controller di Dominio (Consensus & Dataset Distribution):** Componenti specializzati nello smistamento asincrono iniziale dei dati dal Master verso i Client (*Dataset Distribution*) e nel calcolo distribuito delle metriche di deviazione della rete (*Consensus Actor*).
* **Monitor Actor (Boundary/Presenter):** Agisce come ponte tra l'infrastruttura distribuita e le interfacce I/O (GUI/CLI). Aggrega periodicamente le metriche e aggiorna i grafici senza bloccare l'Actor System sottostante.

L'isolamento di queste responsabilità fa sì che il fallimento della rete (es. crash di un nodo simulato) impatti solo il livello Gossip/Cluster, permettendo al resto del sistema di riorganizzarsi e continuare l'addestramento senza corrompere i dati.


*(Figura: Diagramma dei componenti che mostra il Nodo DNTS, i Worker Actors nel livello Akka, le interazioni con il Domain Core Layer puro e i collegamenti verso il P2P Cluster esterno)*

## 3.4 Scelte Tecnologiche Cruciali ai Fini Architetturali

Le tecnologie sono state selezionate per supportare in modo nativo l'architettura sopra descritta:

1. **Scala 3:** Scelto come linguaggio portante per il suo supporto nativo al paradigma ibrido (Object-Oriented e Functional Programming). Scala permette di modellare l'infrastruttura di routing tramite l'OOP e contemporaneamente confinare l'algoritmica matematica in costrutti puramente funzionali (immutabili e dichiarativi), soddisfacendo la necessità di disaccoppiare gli strati.
2. **Akka Cluster & Akka Typed:** L'infrastruttura di distribuzione si basa interamente su Akka. Akka Typed garantisce a *compile-time* la correttezza dei protocolli di messaggistica tra i componenti. Akka Cluster fornisce internamente le funzionalità di Membership, Discovery (tramite Receptionist) e Failure Detection (Split Brain Resolver), scaricando il codice applicativo dalla gestione del networking di basso livello e garantendo la tolleranza ai guasti.
3. **Serializzazione Custom Binaria (AkkaSerializerAdapter):** Poiché l'architettura Gossip richiede il continuo scambio dell'intero stato del modello (matrici e vettori) sulla rete, la serializzazione standard (es. JSON o Java nativa) è stata scartata per problemi di performance. È stato progettato un Adapter integrato nell'infrastruttura Akka che utilizza buffer binari (`ByteBuffer`) per serializzare le primitive di algebra lineare, riducendo drasticamente l'overhead di rete e la latenza della sincronizzazione.