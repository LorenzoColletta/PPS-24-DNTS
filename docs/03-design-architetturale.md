---
layout: default
title: Design Architetturale
nav_order: 3
---

# 3. Design Architetturale

Questa sezione descrive l'architettura complessiva del sistema Distributed Neural Training Simulation (DNTS). La progettazione è stata guidata dai requisiti di resilienza, concorrenza distribuita e rigorosa separazione delle responsabilità, adottando un approccio decentralizzato fondato sui paradigmi della programmazione funzionale e del modello ad attori.

## 3.1 Architettura Complessiva e Stile Architetturale

L'architettura adottata si basa sul modello Peer-to-Peer: il cluster è composto da un insieme dinamico di nodi autonomi, senza la presenza di un server centrale (Single Point of Failure). Il consenso sul modello globale viene sviluppato attraverso la comunicazione asincrona diretta tra i peer (*Gossip Learning*).

A livello di singolo nodo, l'architettura separa in modo netto le responsabilità. Per non mescolare il calcolo matematico puro con la gestione della comunicazione di rete, la struttura interna è organizzata in livelli distinti:

1. **Layer Computazionale (Pure Functional Core):** Uno strato deterministico e privo di side effect che gestisce unicamente la logica matematica della rete neurale.
2. **Layer di Stato e Distribuzione (Actor System):** Uno strato concorrente che orchestra il flusso di esecuzione, detiene lo stato mutabile e gestisce la comunicazione tra nodi.
3. **Layer di I/O e Presentazione:** Il confine esterno del sistema che interagisce con l'utente (GUI).

## 3.2 Pattern Architetturali Utilizzati

Per soddisfare i requisiti, la struttura si appoggia ai seguenti pattern architetturali:

* **Actor Model:** Pattern fondamentale utilizzato per modellare l'intera infrastruttura concorrente. Garantisce l'incapsulamento dello stato e l'elaborazione asincrona basata su messaggi, eliminando alla radice la necessità di lock espliciti o memoria condivisa.
* **Design Basato sui Ruoli:** In aderenza al principio di Separation of Concerns (SOC), l'organizzazione della topologia degli attori riflette una divisione in base al loro scopo all'interno del nodo, evitando la commistione tra logica, stato mutabile e I/O: 
  * **Stato (State Keepers):** Il gestore isolato del modello locale, dedicato esclusivamente alla custodia e all'aggiornamento atomico dei dati di dominio (Model Actor).
  * **Controllo (Coordinators):** Gli orchestratori della logica di business, dei calcoli e dei protocolli di rete (Trainer Actor, Gossip Actor, Consensus Actor).
  * **Infrastruttura e Supervisione (Infrastructure Managers):** Componenti dedicati al ciclo di vita del nodo e alla tolleranza ai guasti. Includono la gestione dinamica della Membership e il rilevamento dei fallimenti (es. Cluster Manager), la scoperta dei peer nel cluster (es. Discovery Actor tramite pattern Receptionist) e il bootstrap gerarchico (Root Actor).
  * **Confine (Gateways):** L'interfaccia verso l'esterno per tradurre i comandi utente e trasmettere i dati da visualizzare (Monitor Actor). 
* **Gossip Protocol:** Pattern architetturale di rete utilizzato per la diffusione epidemica delle informazioni. I nodi scelgono iterativamente e casualmente dei peer con cui sincronizzare il proprio stato, garantendo tolleranza ai guasti ed eventuale consistenza.

## 3.3 Componenti del Sistema Distribuito

L'unità fondamentale del sistema distribuito è il **Nodo**. Ogni nodo ospita un sistema ad attori gerarchico:

* **RootActor:** avvia, inizializza e orchestra gli altri attori.
* **Cluster Manager (Membership & Failure Detector):** Gestisce la visione che il nodo ha dell'intera topologia distribuita. Interpreta gli eventi di membership nativi (es. nodi aggiunti, rimossi o irraggiungibili) su cui si basa per determinare lo stato di salute del cluster e applicare dinamicamente le policy decisionali per la tolleranza ai guasti (come la riconfigurazione o lo spegnimento di emergenza in caso di perdita del nodo Seed pre simulazione).
* **Discovery Actor (Peer Locator):** Sfrutta il Receptionist di Akka per indicizzare e scoprire dinamicamente i servizi esposti dagli altri nodi. Mantiene e fornisce agli orchestratori di rete (come il Gossip Actor e il Consensus Actor) la lista costantemente aggiornata dei peer attivi e raggiungibili con cui scambiare i modelli.
* **Gossip Actor:** È il componente deputato all'interazione P2P. Gestisce il protocollo di rete, preleva snapshot del modello locale e li invia ai peer, ricevendo a sua volta i modelli remoti da fondere.
* **Sub-Controller di Dominio (Consensus & Dataset Distribution):** Componenti specializzati nello smistamento asincrono iniziale dei dati dal Master verso i Client (*Dataset Distribution*) e nel calcolo distribuito delle metriche di deviazione della rete (*Consensus Actor*).
* **Trainer Actor:** Esecutore logico del ciclo di addestramento. Preleva snapshot del modello locale e batch di dati, delega i calcoli di addestramento al Layer Computazionale (Backpropagation) e notifica i gradienti risultanti al Model Actor.
* **Model Actor (Single Source of Truth):** Rappresenta il cuore dello stato mutabile del nodo. Incapsula i pesi e i bias della rete neurale, esponendo interfacce per aggiornamenti atomici sequenziali (sia derivanti dal calcolo locale che dai merge di rete).
* **Monitor Actor:** Agisce come ponte tra gli attori e le interfacce I/O. Aggrega periodicamente le metriche e aggiorna i grafici.



## 3.4 Scelte Tecnologiche Cruciali ai Fini Architetturali

Le tecnologie sono state selezionate per supportare l'architettura sopra descritta:

1. **Akka Cluster Typed:** L'infrastruttura di distribuzione si basa interamente su Akka. Akka Typed garantisce la correttezza dei protocolli di messaggistica tra i componenti. Akka Cluster fornisce internamente le funzionalità di Membership, Discovery (tramite Receptionist) e Failure Detection, scaricando il codice applicativo dalla gestione del networking di basso livello e garantendo la tolleranza ai guasti.
3. **Serializzazione Custom Binaria:** Poiché l'architettura Gossip richiede il continuo scambio dell'intero stato del modello (matrici e vettori) sulla rete, è stato progettato un Adapter integrato nell'infrastruttura Akka che utilizza buffer binari per serializzare le primitive di algebra lineare, riducendo drasticamente l'overhead di rete e la latenza della sincronizzazione.

---
[Vai al Capitolo 4: Design di Dettaglio -->](04-design-di-dettaglio.md)