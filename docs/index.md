# Distributed Neural Training Simulation (DNTS)

## Introduzione e Visione del Sistema

DNTS è un framework software progettato per la simulazione e l'analisi dell'addestramento di reti neurali profonde (Deep Neural Networks) in un ambiente distribuito P2P (Peer-to-Peer).

Il sistema realizza un ambiente di apprendimento completamente decentralizzato:

1. **Architettura Leaderless:** La simulazione modella il cluster come un insieme di nodi autonomi (Attori in Akka), dove ciascuno ospita una copia del modello e una partizione dei dati.
2. **Apprendimento Cooperativo:** Il training avviene tramite Gossip Learning, un protocollo asincrono in cui i nodi alternano cicli di calcolo locale (Backpropagation) a cicli di sincronizzazione asincrona (Model Averaging) con peer casuali. Questo processo permette al modello globale di raggiungere il consenso senza ricorrere a un server centrale.

Il controllo operativo del sistema (configurazione della topologia, avvio del training) avviene tramite CLI, mentre l'analisi e la verifica dei risultati sono gestiti da finestre grafiche dinamiche che visualizzano l'evoluzione del modello in tempo reale (Decision Boundary e metriche).

Il progetto sarà giudicato di successo non solo in base alla sua funzionalità, ma soprattutto sulla sua capacità di dimostrare l'efficacia e la robustezza dell'architettura distribuita. Le caratteristiche che decreteranno un buon risultato sono le seguenti:

* **Corretta Convergenza del Modello:** Il sistema deve dimostrare la capacità di apprendere e risolvere problemi di classificazione 2D complessi (es. XOR, Spiral). Questa caratteristica è verificata graficamente dall'evoluzione della Decision Boundary in tempo reale e numericamente dalla riduzione della Loss Function.
* **Consenso e Qualità Distribuita:** Un buon risultato richiede che il sistema dimostri:
    * L'uniformità dei modelli sui diversi nodi, misurata dalla Consensus Metric (la deviazione standard dei pesi), che deve tendere a zero.
    * L'efficacia del Model Averaging nel far apprendere al singolo nodo conoscenze che non erano presenti nella sua partizione locale di dati.
* **Robustezza Operativa (Fault Tolerance):** Il sistema deve essere resiliente. Un buon risultato è decretato dalla capacità del cluster di mantenere l'apprendimento e il consenso anche a fronte di un crash simulato o della disconnessione improvvisa di uno dei nodi.
* **Qualità Architetturale del Codice:** L'implementazione deve rispettare i principi di progettazione avanzata. Ciò include l'uso rigoroso del Modello ad Attori (Akka) per la gestione della concorrenza e l'adesione alla Programmazione Funzionale (Scala) per le componenti computazionali (matematica), garantendo l'immutabilità dei dati e la chiarezza del codice.