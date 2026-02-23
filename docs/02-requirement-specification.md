# 2. Requirement specification

## 2.1 Requisiti di Business

I requisiti di business definiscono il valore strategico che il sistema deve fornire agli utilizzatori.

  - **BR1. Obiettivo Esplicativo:** Il sistema deve agire come uno strumento analitico e didattico primario, offrendo una visualizzazione chiara, dinamica e trasparente di concetti complessi come l'apprendimento delle reti neurali e la convergenza distribuita.
  - **BR2. Validità Architetturale:** Il sistema deve dimostrare un vantaggio operativo tangibile derivante dalla sua architettura P2P (Peer-to-Peer), garantendo resilienza operativa e la capacità di raggiungere il consenso del modello a partire da dati iniziali distribuiti.
  - **BR3. Completezza Funzionale:** La simulazione deve coprire l'intero ciclo di vita del modello, dalla sua configurazione all'inferenza finale, fornendo tutte le metriche necessarie per la validazione della sua performance e del protocollo distribuito.

## 2.2 Modello di Dominio

Il modello di dominio definisce gli elementi concettuali chiave del sistema.

| **Elemento di Dominio** |                                                **Descrizione**                                 |
| :-: |:----------------------------------------------------------------------------------------------:|
| Rete Neurale (Model) |                La struttura computazionale (Layer, Neuroni) replicata localmente su ogni nodo. |
| Nodo |                      Un'istanza del sistema in esecuzione, che partecipa al cluster P2P.       |
| Attore (Actor) |       L'unità fondamentale di calcolo e comunicazione (Pattern Actor Model) all'interno di ciascun Nodo. |
| Pesi e Bias |              I parametri del modello, dati che vengono scambiati e mediati dal protocollo Gossip. |
| Partizione del Dataset |          La porzione di dati 2D assegnata in esclusiva a un singolo Nodo per l'addestramento locale. |
| Gossip Protocol |   Il meccanismo P2P che garantisce lo scambio asincrono dei pesi tra i nodi per raggiungere la convergenza. |
| Decision Boundary | La regione nello spazio 2D che viene classificata dal modello. La sua evoluzione è l'output visivo principale. |
| Consensus Metric |                  Una misura aggregata che quantifica la differenza tra i pesi dei vari nodi.   |

## 2.3 Requisiti Funzionali

### 2.3.1. Requisiti Funzionali Utente

| **ID** |                                                         **Descrizione del Requisito**                                                          | **Modalità di Interazione** |
| :-: |:----------------------------------------------------------------------------------------------------------------------------------------------:|:-:|
| FRU1 |                             L'utente deve poter avviare e connettere nuovi nodi al cluster tramite parametri CLI.                              |             CLI             |
| FRU2 |    L'utente deve poter definire la topologia (layer, neuroni), funzioni di attivazione ed iperparametri prima dell'avvio della simulazione.    |      Config File / CLI      |
| FRU3 |      L'utente deve poter selezionare e partizionare un Dataset 2D specifico (XOR, Circle, Spiral, Gaussian) fornito per l'addestramento.       |         Config File         |
| FRU4 |                       L'utente deve poter avviare, sospendere e riprendere il processo di training tramite interfaccia.                        |             GUI             |
| FRU5 |        Il sistema deve visualizzare in una finestra grafica l'evoluzione della Decision Boundary come prova visiva dell'apprendimento.         |       GUI (Plotting)        |
| FRU6 |          Il sistema deve visualizzare in un grafico dedicato l'andamento in tempo reale della Loss Function e della Consensus Metric.          |       GUI (Plotting)        |
| FRU7 |               L'utente deve poter simulare la disconnessione (crash) di un nodo durante il training per osservare la resilienza.               |          CLI / SO           |
| FRU9 | L'utente deve poter richiedere l'output dettagliato dei pesi e bias di un nodo specifico in formato numerico, con destinazione un file di log. |          Log File           |

### 2.3.2. Requisiti Funzionali di Sistema

| **ID** |                                                                                      **Descrizione del Requisito**                                                                                      |           **Tecnologia Coinvolta**            |
|:------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------:|
|  FRS1  | Il sistema deve implementare un protocollo Gossip applicativo (custom) per la mediazione asincrona dei parametri del modello, la diffusione delle configurazioni e il calcolo del consenso distribuito. |                  Attori Akka                  |
|  FRS2  |  Il sistema deve gestire in modo decentralizzato la membership del cluster, occupandosi del join dei nodi, del rilevamento dei guasti (failure detection) e della rimozione dei nodi irraggiungibili.   |                 Akka Cluster                  |
|  FRS3  |                          La logica di training deve suddividersi in cicli locali (Backpropagation) e cicli di sincronizzazione (Gossip) eseguiti in modo asincrono tra i Nodi.                          |               Attori, Scala FP                |
|  FRS4  |                                       Il sistema deve essere in grado di calcolare e aggregare una Consensus Metric tra tutti i nodi attivi e inviarla alla GUI.                                        |                   Scala FP                    |
|  FRS5  |                         Il sistema deve supportare la serializzazione efficiente della struttura completa del modello (pesi e bias) per la trasmissione in rete tra gli attori.                         |              Akka Serialization               |
|  FRS6  |                                  Il sistema deve implementare l'algoritmo di Backpropagation e Gradient Descent utilizzando funzioni pure e strutture dati immutabili.                                  |                   Scala FP                    |
|  FRS7  |           Il sistema deve implementare un modulo generatore procedurale in grado di creare dinamicamente dataset 2D di varia forma da usare per il training (XOR, Circle, Spiral, Gaussian).            |         Scala FP        |

## 2.4. Requisiti Non Funzionali

|  **ID**  |                         **Categoria**                         |                                                                               **Descrizione del Requisito**                                                                                |
|:----:|:---------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| NFR1 |                        Resilienza                         |                            Il sistema deve garantire la Tollezanza ai Guasti. L'apprendimento non deve interrompersi in caso di caduta di un singolo nodo.                             |
| NFR2 | Separazione delle Responsabilità (Separation of Concerns) | La logica computazionale e di dominio (matematica e addestramento) deve essere funzionalmente pura e rigorosamente disaccoppiata dalla gestione dello stato concorrente e distribuito. |
| NFR3 |                  Qualità Architetturale                   |                        L'intero sistema deve aderire al modello ad attori, evitando l'uso di lock o meccanismi di sincronizzazione basati su memoria condivisa.                        |

## 2.5. Requisiti di Implementazione

| **ID** | **Categoria** | **Descrizione del Requisito** |
| :-: | :-: | :-: |
| IR1 | Linguaggio di Programmazione | Il sistema deve essere interamente sviluppato in Scala 3. |
| IR2 | Architettura Distribuita | La logica distribuita, la concorrenza e la gestione del cluster devono essere gestite tramite il framework Akka Cluster. |

---
[Vai al Capitolo 3: Design Architetturale -->](03-design-architetturale.md)