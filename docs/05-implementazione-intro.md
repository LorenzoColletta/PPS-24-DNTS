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
L'implementazione del motore di addestramento ha richiesto di coniugare la flessibilità matematica con l'esecuzione asincrona distribuita.

Per quanto concerne il dominio matematico (es. TrainingCore), l'iniezione delle diverse euristiche (come la LossFunction o la definizione dello Space 2D) non è stata realizzata tramite la classica Dependency Injection basata sui costruttori (tipica del mondo Java), bensì sfruttando le Contextual Abstractions di Scala 3. I metodi di calcolo, come computeBatchGradients, definiscono le dipendenze strategiche tramite clausole using (es. using lossFn: LossFunction). Le implementazioni concrete (es. la Mean Squared Error) sono definite come istanze given all'interno dell'oggetto Strategies o iniettate al momento della configurazione. Questo costrutto ha permesso di disaccoppiare totalmente l'algoritmo di Backpropagation dalla specifica metrica di errore, rendendo il codice estremamente pulito e idiomatico.

Sul fronte concorrente, la gestione del ciclo di vita dell'addestramento è stata implementata nel TrainerActor realizzando una Macchina a Stati Finiti (FSM) nativa tramite l'API di Akka Typed. Invece di mantenere uno stato mutabile interno (es. var currentState), i diversi stati (idle, ready, training, paused) sono stati codificati come metodi mutuamente ricorsivi che restituiscono un Behavior[TrainerMessage]. Ogni metodo definisce tramite Behaviors.receive solo le transizioni di stato valide per quel contesto, ignorando in modo sicuro (con Behaviors.same o unhandled) i messaggi non pertinenti.

Inoltre, per evitare il blocco del thread dell'attore durante il partizionamento del dataset in epoche e batch (che impedirebbe la reattività ai comandi dell'utente), il loop di training è stato srotolato in modo asincrono. È stato impiegato il TimerScheduler fornito da Akka (timers.startSingleTimer): al termine del calcolo di un batch, l'attore autoprogramma l'invio di un messaggio privato (PrivateTrainerCommand.NextBatch) a se stesso. Questo approccio garantisce che la mailbox dell'attore possa processare richieste prioritarie (es. calcolo metriche o pausa) tra l'esecuzione di due iterazioni sequenziali.

## 5.3 Implementazione a cura di Lorenzo Colletta

### 5.3.1 Package `pattern`: Modellazione Geometrica e Distribuzioni Spaziali
Il package è strutturato attorno a due trait fondamentali, `PointDistribution` e `ParametricCurve`, che definiscono i contratti per la generazione di punti. Le implementazioni concrete sono modellate prevalentemente tramite `case class`, garantendo immutabilità e facilitando la manipolazione dei parametri attraverso il metodo copy.

#### Implementazioni di PointDistribution
Le classi che estendono il trait `PointDistribution` incapsulano la logica necessaria a produrre un `Point2D` seguendo una specifica distribuzione spaziale.
* `GaussianCluster`: Questa `case class` modella un raggruppamento di punti basato sulla distribuzione normale. In fase di costruzione, l'integrità del modello è garantita da clausole `require` che verificano la vicinanza della media al dominio ammissibile. Internamente, la classe gestisce due istanze private di `NormalDistribution` per le coordinate X e Y. Il cuore dell'implementazione risiede nel metodo `sample`, metodo tail-recursive per implementare un campionamento con rigetto: il punto viene restituito solo se ricade all'interno di un dato raggio.
* `CircleRingPattern`: Definita come `case class`, questa implementazione genera punti uniformemente distribuiti all'interno di un'area circolare o anulare. Anche qui sample è tail-recursive.
* `UniformSquare`: Questa classe implementa la generazione di punti all'interno di quadranti specifici del piano cartesiano. L'implementazione si appoggia all'`enum` Quadrant, che definisce in modo tipizzato i quattro settori del piano cartesiano (I, II, III, IV).

#### Implementazioni di ParametricCurve
Il trait `ParametricCurve` definisce un contratto basato sulla funzione `at(x: Double)`, che mappa un parametro reale in un punto nello spazio.
* `SpiralCurve`: Implementata come `case class`, modella una spirale archimedea sulla base dei parametri di distanza dall'origine, distanza tra bracci e rotazione.

---

### 5.3.2 Package `sampling`: Astrazione e Meccanismi di Campionamento
L'elemento centrale del package è il `trait PointSampler`, che definisce un contratto basato sul metodo `sample(): Point2D`. Funge da interfaccia unificata per i livelli superiori del sistema, nascondendo la complessità della logica di generazione sottostante.
L'architettura del package implementa due modalità distinte per assolvere il contratto di campionamento:
* `DistributionSampler`: Implementato come final case class, agisce come un Adapter tra PointDistribution e PointSampler. Il suo compito è puramente delegativo: il metodo `sample()` richiama direttamente la logica di generazione della distribuzione incapsulata.
* `CurveSampler`: Realizza una composizione tra una ParametricCurve e una funzione di distribuzione dei parametri `(() => Double)`. A differenza dell'adapter precedente, il `CurveSampler` non si limita a delegare, ma definisce come una curva viene percorsa. Per gestire la creazione dei campionatori, è stato introdotto l'oggetto companion `CurveSampler`, il quale funge da factory specializzata.

Oltre ai campionatori basati su singole geometrie, il package include lo `XorSampler`, un'implementazione specializzata di PointSampler che realizza la distribuzione XOR attraverso la composizione di due pattern UniformSquare. 
A supporto della precisione del campionamento, il sistema adotta il pattern Type Class per il calcolo dei domini parametrici. 

Il trait `CurveDomainCalculator[C]` definisce il contratto per determinare l'intervallo di valori ammissibili per una curva di tipo C all'interno di uno Space. L'oggetto SpiralDomain concretizza questa type class per la SpiralCurve, effettuando calcoli analitici per troncare il parametro di crescita della spirale in base alle dimensioni dello spazio fornite. 

---

### 5.3.3 Package `dataset`

Il cuore dell'implementazione risiede nella classe `BinaryDataset`, che estende `LabeledDatasetModel`. Questa classe non è astratta ma fornisce la logica operativa per tutti i modelli binari: il metodo sample sfrutta il pattern matching sull'`enum Label` per delegare la generazione del punto al corrispondente `PointSampler` (positivo o negativo).
Le specializzazioni concrete sono implementate come `final class` che estendono `BinaryDataset`, configurando i campionatori nel costruttore:
* `DoubleGaussianDataset`: Realizza la specializzazione per cluster gaussiani. L'implementazione calcola i centri in modo simmetrico rispetto all'origine e istanzia due `DistributionSampler` che avvolgono oggetti `GaussianCluster`.
* `DoubleRingDataset`: Questa classe specializza il modello per distribuzioni circolari concentriche. Configura il sampler positivo come un'area circolare centrata nell'origine e quello negativo come un anello esterno `CircleRingPattern`.
* `DoubleSpiralDataset`: Rappresenta la specializzazione più complessa. Utilizza la factory `CurveSampler.spiralSampler` per creare i generatori basati su `SpiralCurve`. 
* `DoubleXorDataset`: Specializza il modello XOR associando coppie di quadranti alle etichette. Implementa la logica passando al XorSampler le tuple di `Quadrant` specifiche: (I, III) per la classe positiva e (II, IV) per quella negativa.

---

### 5.3.4 ClusterManager

L’attore `ClusterManager` è implementato in conformità con il **modello funzionale raccomandato da Akka Typed**, che prevede la definizione del comportamento come funzione parametrizzata dallo stato corrente. In tale modello:

* lo stato è esplicito e immutabile;
* ogni messaggio produce un nuovo comportamento;
* non vengono utilizzate variabili mutabili condivise;
* gli effetti collaterali sono separati dalla logica decisionale.

L’attore è definito mediante `Behaviors.setup` e `Behaviors.withTimers`, strumenti idiomatici di Akka Typed per l’inizializzazione controllata del comportamento e per la gestione del tempo logico.

La funzione principale:

```scala
private def runningBehavior(
  context: ActorContext[ClusterMemberCommand],
  timers: TimerScheduler[ClusterMemberCommand],
  state: ClusterState,
  ...
): Behavior[ClusterMemberCommand]
```

rappresenta un **behavior parametrico sullo stato**, che viene ricorsivamente restituito ad ogni elaborazione di messaggio. Lo stato non è mantenuto internamente come variabile mutabile, ma passato come parametro nella nuova istanza del comportamento:

```scala
runningBehavior(context, timers, newState, ...)
```

Questa scelta implementativa favorisce:
* assenza di race condition (lo stato è confinato nel thread dell’attore);
* testabilità della logica di trasformazione dello stato;
* aderenza al modello attore funzionale di Akka Typed.

#### Struttura del ciclo di elaborazione dei messaggi

L’elaborazione dei messaggi nel ClusterManager segue una pipeline deterministica articolata in fasi logicamente distinte. Tale struttura non è meramente organizzativa, ma riflette una precisa separazione tra:
* aggiornamento dello stato di dominio,
* calcolo decisionale,
* applicazione delle transizioni,
* esecuzione degli effetti collaterali.

Il sistema modella le conseguenze dell’elaborazione di un messaggio tramite una gerarchia di tipi algebrici:

```scala
sealed trait Effect
sealed trait StateTransition extends Effect
sealed trait Action extends Effect
```

Le tre categorie hanno ruoli distinti:
* **Effect:** rappresenta l’esito dichiarativo di una decisione. È un tipo base che descrive cosa deve accadere, senza specificare come.
* **StateTransition:** sottotipo di Effect che descrive una trasformazione dello stato interno dell’attore. Non produce effetti collaterali esterni.
* **Action**: sottotipo di Effect che descrive un’interazione con l’esterno (invio messaggi, avvio timer, operazioni su Akka Cluster, arresto dell’attore).

Questa modellazione consente di trattare le decisioni come dati immutabili, differendo l’esecuzione concreta a una fase successiva.

##### Funzione `handle` come core funzionale

La funzione:
```scala
private def handle(
    state: ClusterState,
    message: ClusterMemberCommand
): (ClusterState, List[Effect])
```
incapsula le prime due fasi della pipeline, occupandosi dell’aggiornamento della membership locale e del calcolo della lista di Effect tramite la DecisionPolicy.
Al suo interno non viene eseguita alcuna azione imperativa, non viene modificato lo stato dell’attore e non vengono invocati servizi di Akka. Di conseguenza, la funzione è puramente funzionale.

Se il messaggio rappresenta un evento proveniente dal cluster (ad esempio NodeUp, NodeRemoved, NodeUnreachable), la vista locale (ClusterMembership) viene aggiornata tramite una funzione pura:

```scala
val newView = message match
    case e: NodeEvent =>
        MembershipPolicy.update(state.view, e)
    case _ => state.view
```

L’aggiornamento:
* non modifica lo stato esistente;
* produce una nuova istanza immutabile della membership;
* mantiene coerenza rispetto all’evento osservato.

Si ottiene quindi uno stato intermedio:

```scala
val stateAfterHandle = state.copy(view = newView)
```

Successivamente, la policy attiva (in funzione della fase corrente) viene invocata ricevendo esclusivamente lo stato aggiornato, il messaggio corrente e restituisce una lista dichiarativa di Effect.

```scala
val effects =
currentPolicy.decide(stateAfterHandle, message)
```

La funzione handle conclude restituendo:

```scala
(stateAfterHandle, effects)
```

##### Applicazione delle `StateTransition`

Dopo l’invocazione di handle, il ClusterManager procede all’applicazione delle eventuali StateTransition contenute nella lista di Effect.

Esempi concreti di StateTransition sono:
* ChangePhase(newPhase) — rappresenta il cambio di fase della FSM;
* RemoveNodeFromMembership(node) — rappresenta la rimozione logica di un nodo dalla membership.

Questi elementi non sono metodi che modificano direttamente lo stato, bensì valori immutabili che descrivono una trasformazione. Ciò significa che la policy non esegue il cambio di fase, ma produce un dato che dichiara tale necessità.

L’applicazione avviene mediante una riduzione funzionale:

```scala
val newState =
effects.foldLeft(stateAfterHandle) {
case (currentState, transition: StateTransition) =>
applyTransition(currentState, transition)
case (currentState, _) =>
currentState
}
```

##### Interpretazione delle `Action`

Una volta ottenuto il nuovo stato, le Action vengono estratte e interpretate:

```scala
effects.collect { case a: Action => a }
.foreach(action => ClusterEffects.interpret(...))
```

Le Action rappresentano effetti collaterali quali:
* invio di messaggi ad altri attori;
* avvio o cancellazione di timer;
* invocazione di operazioni sul cluster;
* arresto dell’attore (StopBehavior).

L’interpretazione è delegata a ClusterEffects, che costituisce la shell imperativa del sistema.

#### Aspetti implementativi delle `DecisionPolicy`

Le `DecisionPolicy` sono già state descritte nel capitolo di design, in questa sede si evidenziano gli aspetti implementativi.

Ogni policy implementa:

```scala
def decide(state: ClusterState, msg: ClusterMemberCommand): List[Effect]
```

Dal punto di vista tecnico:

* la funzione è pura;
* il dominio dei messaggi è chiuso (sealed trait);
* il pattern matching è esaustivo;
* non vi è accesso a componenti infrastrutturali.

Le policy sfruttano pattern matching con guardie:

```scala
case NodeUnreachable(node) if node.roles.contains(NodeRole.Seed.id) =>
```

Questo utilizzo consente discriminazione semantica basata sul ruolo del nodo senza introdurre accoppiamento con il runtime.

#### ClusterNode e Adapter: isolamento del dominio da Akka

##### Necessità di `ClusterNode`

Il runtime del cluster fornisce il tipo `Member`, appartenente all’API di Akka. L’utilizzo diretto di tale tipo nella logica applicativa avrebbe introdotto:

* accoppiamento infrastrutturale;
* dipendenza da dettagli interni del runtime;
* riduzione della testabilità delle policy;
* contaminazione del dominio con concetti di basso livello.

Per questo motivo è stato introdotto il tipo:

```scala
final case class ClusterNode(
  address: Address,
  roles: Set[String]
)
```

`ClusterNode` rappresenta un **modello di dominio minimale**, contenente esclusivamente le informazioni semanticamente rilevanti per il sistema.

Questa astrazione consente di:

* rendere la logica indipendente da Akka;
* facilitare test unitari puri;
* garantire stabilità del dominio rispetto a possibili evoluzioni dell’API.


##### Implementazione dell’Adapter con Typeclass

La conversione tra gli eventi generati da Akka Cluster e il modello di dominio interno è realizzata attraverso una combinazione del pattern Adapter e del meccanismo delle typeclass di Scala 3. L’obiettivo implementativo è disaccoppiare completamente il dominio applicativo dalle classi infrastrutturali fornite dal runtime, in particolare dal tipo Member e dagli eventi di cluster che lo incapsulano.

A tal fine viene definita la seguente typeclass:

```scala
trait HasMember[E]:
def member(e: E): Member
```

Essa esprime, a livello di tipo, la capacità di estrarre un’istanza di Member da un evento di tipo E. In altri termini, non si stabilisce una gerarchia di ereditarietà tra gli eventi, ma si definisce un vincolo comportamentale: un tipo E può essere adattato se esiste un’istanza implicita di HasMember[E]. Tali istanze sono dichiarate mediante given. Ogni given HasMember[SpecificEvent] fornisce un’implementazione concreta del metodo member per uno specifico tipo di evento di Akka (ad esempio MemberUp, MemberRemoved, UnreachableMember).

##### Implicazioni sulla purezza e testabilità

Grazie all’introduzione di `ClusterNode` e alla separazione operata dall’Adapter:

* le policy operano esclusivamente su tipi di dominio;
* non esistono dipendenze da `Cluster`, `Member`, `ActorContext`;
* il comportamento decisionale può essere testato come funzione pura.

In termini di programmazione funzionale, il sistema realizza:

* isolamento degli effetti;
* dominio chiuso tramite ADT;
* composizione funzionale delle trasformazioni;
* utilizzo avanzato del sistema dei tipi di Scala 3.
