# 7. Retrospettiva
Questo capitolo finale analizza l'andamento del processo di sviluppo, ripercorrendo le iterazioni del team, la gestione del Product Backlog e traendo le conclusioni sulle sfide affrontate e sui risultati ottenuti.

## 7.1 Gestione del Backlog e Metodologia
Lo sviluppo del sistema DNTS è stato orchestrato seguendo un approccio agile basato su Scrum. Il Product Backlog è stato suddiviso in 4 Sprint della durata di una settimana ciascuno (dal 5 Gennaio al 1 Febbraio).

Il team ha adottato un tracciamento rigoroso dell'effort: ad ogni task è stata assegnata una stima iniziale, scalata poi giornalmente per monitorare l'avanzamento effettivo. Questa visibilità quotidiana ha permesso di identificare tempestivamente eventuali colli di bottiglia, ricalibrare i carichi di lavoro (come avvenuto nelle fasi di refactoring finali) e mantenere un ritmo di sviluppo costante e sostenibile.

## 7.2 Analisi delle Iterazioni (Sprint)
L'evoluzione del codice ha seguito un rigoroso approccio bottom-up: dal dominio matematico puro, passando per l'infrastruttura di rete, fino ad arrivare all'interfaccia utente.

* **Sprint 1 (05 - 11 Gennaio) – Fondamenta Funzionali e Astrazioni:**
Il primo sprint si è concentrato sulla costruzione del core indipendente (Pure Functional Core). Fantilli ha guidato l'implementazione del motore matematico (Core Math Engine, Backpropagation, SGD) e la complessa architettura di serializzazione. Parallelamente, Colletta ha avviato l'ingegnerizzazione dei dati (generazione e partizionamento dei dataset), mentre Giacobbi ha delineato lo scheletro dei protocolli Akka e le interfacce base dei primi attori (ModelActor, TrainerActor).

* **Sprint 2 (12 - 18 Gennaio) – Concorrenza e Motore Reattivo:**
Il focus si è spostato sull'Actor System. Fantilli ha tradotto le funzioni di training in un motore asincrono implementando il TrainerActor e progettando il MonitorActor. Giacobbi si è concentrato sull'integrazione del modello e sull'avvio della componente chiave del P2P: il GossipActor e il relativo protocollo. Contestualmente, Colletta ha iniziato il task di progettazione del ClusterManager per la gestione della topologia di rete.

* **Sprint 3 (19 - 25 Gennaio) – Distribuzione e Bootstrap:**
Durante questa fase il sistema ha iniziato a "comunicare". Fantilli ha curato la complessa fase di bootstrap (configurazione della rete e generazione procedurale dei dataset in base al config). Giacobbi ha espanso l'infrastruttura creando attori dedicati alla distribuzione della configurazione e del dataset stesso tra i seed e i client. Colletta ha integrato il DiscoveryActor e rifinito le logiche del Cluster Manager.

* **Sprint 4 (26 Gennaio - 01 Febbraio) – UI, Refactoring e Chiusura:**
L'ultimo sprint è stato dedicato alla rifinitura e all'eccellenza tecnica. Fantilli si è dedicato alla implementazione della funzione Main e della View (GUI e controlli). Nel frattempo, Giacobbi ha eseguito un importante refactoring architetturale spezzando il monolitico GossipActor in attori più coesi e specializzati (come il ConsensusActor). Colletta ha concluso l'integrazione delle configurazioni Akka e guidato la fase vitale di debugging distribuito e fixing finale.

## 7.3 Commenti Finali
Il completamento del progetto DNTS ha permesso al team di trarre importanti conclusioni, sia a livello tecnologico che architetturale:

* **Il Valore della Separation of Concerns:** La scelta iniziale di disaccoppiare rigidamente la matematica pura (Scala) dalla gestione dello stato distribuito (Akka) si è rivelata la mossa vincente del progetto. Ha permesso a tre sviluppatori di lavorare in parallelo su componenti diversi minimizzando i conflitti su Git, ha reso possibile testare la matematica in totale isolamento e ha evitato che bug legati alla rete compromettessero la logica di addestramento neurale.

* **Potenza di Scala 3:** L'utilizzo intensivo di feature avanzate di Scala 3 (come le Contextual Abstractions, gli enum avanzati e gli opaque types) ha innalzato notevolmente l'espressività del codice. Pattern complessi come le Type Classes per la serializzazione hanno dimostrato come sia possibile scrivere codice altamente componibile ed estensibile senza ricorrere a framework pesanti.

* **Complessità del Modello ad Attori:** Progettare un sistema reattivo e distribuito non è stato privo di ostacoli. La gestione dei fallimenti, il debugging di messaggi asincroni "persi" nella rete e l'esigenza di spezzare cicli bloccanti tramite i Timer hanno richiesto un cambio di paradigma mentale rispetto alla classica programmazione imperativa. Il refactoring finale (eseguito nello Sprint 4) è stato la prova di una maturata consapevolezza del team verso il principio di singola responsabilità all'interno del Modello ad Attori.

In sintesi, il progetto non solo ha centrato tutti i requisiti di business e implementativi iniziali, ma ha fornito al team un'eccellente palestra per la progettazione e la validazione di architetture distribuite complesse, tolleranti ai guasti e pure functional.