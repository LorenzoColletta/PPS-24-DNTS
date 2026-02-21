# 1. Processo di Sviluppo Adottato

Il processo di sviluppo per il progetto Distributed Neural Training Simulation (DNTS) segue una metodologia incrementale e iterativa aderente al framework Scrum.

## Struttura del Team e Ritmo di Lavoro

  - **Ruoli:** Un membro del team, nel nostro caso Giorgio Fantilli ricopre il ruolo di Product Owner (PO), gestendo la visione e la priorità del Product Backlog.
  - **Durata Sprint:** Ogni Sprint ha una durata fissa di una settimana (sei giorni lavorativi).
  - **Numero Totale:** Sono previsti quattro Sprint totali per coprire l'intera implementazione (Core Funzionale, Architettura Distribuita, Testing, GUI) e la documentazione finale.
  - **Modalità di Lavoro:** La fase iniziale di Analisi dei Requisiti e Design Architetturale è stata svolta congiuntamente. La successiva implementazione è condotta primariamente per via telematica.

## 1.1 Meeting e Interazioni Pianificate

L'interazione è scandita da meeting frequenti, volti a mantenere l'allineamento del team e l'organizzazione del lavoro da svolgere:

  - **Stand-up Meeting (Giornaliero, 15 min):** Incontri rapidi e puntuali. I membri comunicano: 1) Progressi effettuati; 2) Piano di sviluppo per la giornata; 3) Eventuali blocchi o dipendenze non risolte.
  - **Problem Resolution Meeting:** Riunioni ad hoc convocate immediatamente (o schedulate a breve) quando emergono problematiche strutturali complesse che richiedono il coinvolgimento diretto solo di specifici sviluppatori. La soluzione viene poi comunicata all'intero team.
  - **Sprint Planning (Max 1 ora):** Riunione effettuata all'inizio di ogni nuovo Sprint. Si decide quali elementi del Product Backlog verranno inclusi nel nuovo Sprint, si dettagliano gli Sprint Task da svolgere e si assegnano le responsabilità.
  - **Retrospective (Max 1 ora):** Incontro tenuto alla fine di ogni Sprint. L'obiettivo è analizzare cosa ha funzionato e cosa può essere migliorato/riprogettato per far fronte alle problematiche eventualmente emerse.
  - **Sprint Review (Max 1 ora):** Riunione condotta in itinere, tipicamente a metà Sprint, o quando si nota uno scostamento dal piano di sviluppo. Serve per rivedere i progressi, dimostrare le funzionalità completate e, se necessario, riassegnare task o rinegoziare lo scope dello Sprint.

## 1.2 Revisione e Qualità dei Task

Il progetto pone un'enfasi particolare sulla qualità e la robustezza del codice, data l'architettura distribuita.

  - **Revisione Individuale:** L'implementazione procede individualmente, ma ogni commit significativo viene inviato al repository GitHub.
  - **Quality Check (Peer Review):** Saranno svolti controlli di qualità occasionali in cui i membri non coinvolti direttamente nell'implementazione di un modulo critico valuteranno il codice prodotto, proponendo miglioramenti in termini di stile Scala e aderenza al modello FP.
  - **Mob Programming:** Task di fondamentale importanza e alta interdipendenza verranno realizzati dall’intero team in modalità Mob Programming, garantendo la condivisione della conoscenza e l'immediatezza della revisione. Qualora alcuni componenti non fossero direttamente coinvolti o interessati al task specifico, è prevista la modalità Pair Programming, permettendo a due membri di collaborare in modo più mirato senza impegnare l’intero gruppo.

## 1.3 Tool e Infrastruttura di Sviluppo

  - **Build Automation & Dependency Management:** SBT (Scala Build Tool) viene utilizzato per gestire tutte le dipendenze esterne e automatizzare la compilazione e la creazione dei JAR eseguibili.
  - **Versionamento:** Git è lo standard per il controllo versione, con GitHub utilizzato come repository centralizzato.
  - Testing: Verrà utilizzato ScalaTest come framework principale per la stesura dei test unitari.
  - **Continuous Integration (CI):** Il team utilizzerà lo strumento GitHub Actions per garantire che ogni nuovo codice integrato nel repository venga automaticamente compilato e sottoposto a tutti i test ScalaTest, preservando la stabilità del branch principale.