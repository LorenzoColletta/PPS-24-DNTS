---
layout: default
title: Intro
nav_order: 1
---

# Distributed Neural Training Simulation (DNTS)

## Introduzione e Visione del Sistema

DNTS è un framework software progettato per la simulazione e l'analisi dell'addestramento di reti neurali profonde (Deep Neural Networks) in un ambiente distribuito P2P (Peer-to-Peer).

Il sistema realizza un ambiente di apprendimento completamente decentralizzato:

1. **Architettura Leaderless:** La simulazione modella il cluster come un insieme di nodi autonomi, dove ciascuno ospita una copia del modello e una partizione dei dati.
2. **Apprendimento Cooperativo:** Il training avviene tramite Gossip Learning, un protocollo asincrono in cui i nodi alternano cicli di calcolo locale (Backpropagation) a cicli di sincronizzazione asincrona (Model Averaging) con peer casuali. Questo processo permette al modello globale di raggiungere il consenso senza ricorrere a un server centrale.

La configurazione della rete viene definita tramite un file fornito dall’utente all’avvio dell’applicazione. L’interfaccia grafica consente di avviare, mettere in pausa e terminare l’addestramento, oltre a visualizzare in tempo reale la Decision Boundary del modello e le principali metriche quantitative (ad esempio Loss Function e Consensus Metric), permettendo un’analisi immediata del comportamento distribuito del sistema.

Il progetto sarà giudicato di successo non solo in base alla sua funzionalità, ma soprattutto sulla sua capacità di dimostrare l'efficacia e la robustezza dell'architettura distribuita. Le caratteristiche che decreteranno un buon risultato sono le seguenti:

* **Corretta Convergenza del Modello:** Il sistema deve dimostrare la capacità di apprendere e risolvere problemi di classificazione 2D complessi. Questa caratteristica è verificata graficamente dall'evoluzione della Decision Boundary in tempo reale e numericamente dalla riduzione della Loss Function.
* **Consenso e Qualità Distribuita:** Un buon risultato richiede che il sistema dimostri:
    * L'uniformità dei modelli sui diversi nodi, misurata dalla Consensus Metric, che deve tendere a zero.
    * L'efficacia del Model Averaging nel far apprendere al singolo nodo conoscenze che non erano presenti nella sua partizione locale di dati.
* **Robustezza Operativa (Fault Tolerance):** Il sistema deve essere resiliente. Un buon risultato è decretato dalla capacità del cluster di mantenere l'apprendimento e il consenso anche a fronte di un crash simulato o della disconnessione improvvisa di uno dei nodi.
* **Qualità Architetturale del Codice:** L'implementazione deve rispettare i principi di progettazione avanzata. Ciò include l'uso rigoroso del Modello ad Attori (Akka) per la gestione della concorrenza e l'adesione alla Programmazione Funzionale (Scala) nelle componenti numeriche dell'addestramento (calcolo dei gradienti, forward/backward propagation e aggiornamento dei pesi), garantendo l'immutabilità dei dati e la chiarezza del codice.


## Indice della Relazione

1. [Processo di Sviluppo](01-processo-di-sviluppo.md)
2. [Requirement Specification](02-requirement-specification.md)
3. [Design Architetturale](03-design-architetturale.md)
4. [Design di Dettaglio](04-design-di-dettaglio.md)
5. [Implementazione](05-implementazione.md)
6. [Testing](06-testing.md)
7. [Retrospettiva](07-retrospettiva.md)