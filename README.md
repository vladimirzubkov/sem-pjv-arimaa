# Arimaa — B0B36PJV semester project

Desktop implementation of the **Arimaa** board game for the *Programování v Javě* course (B0B36PJV).

**Stack:** Java 21, Maven, JavaFX, JUnit 5, SLF4J/Logback.

**Highlights:** MVC architecture, full rule engine (push/pull, traps, freezing), Minimax + Alpha-Beta AI, TCP multiplayer over a custom NDJSON protocol, game save/load with official notation, unit tests.

```bash
mvn -f Arimaa/pom.xml javafx:run
```

---

# Semestrální práce B0B36PJV — Arimaa

Tento projekt obsahuje implementaci logické deskové hry **Arimaa** pro předmět B0B36PJV (Programování v Javě).

Aplikace je vytvořena v jazyce Java (verze 21) s grafickým uživatelským rozhraním v JavaFX. 
Architektura je navržena dle MVC návrhového vzoru. Byla implementována plná pravidla hry, včetně tahání, tlačení, mrznutí a pastí. 
Hra podporuje režimy pro dva hráče na jednom počítači, hru proti umělé inteligenci (využívající algoritmus Minimax s Alpha-Beta prořezáváním) a síťovou hru přes TCP s využitím vlastního NDJSON protokolu.

## Dokumentace k projektu

Kompletní dokumentace je uložena ve složce `Dokumentace/` a je rozdělena na dvě hlavní části:

- **[Uživatelský manuál](Dokumentace/Manual-hrace.md)**: návod k obsluze aplikace, popis ovládání, klávesových zkratek, nastavení AI a možností síťové hry.
- **[Technická dokumentace](Dokumentace/Technicka-dokumentace.md)**: popis vnitřní struktury programu, rozdělení do balíčků, klíčové třídy a diagramy (PlantUML) architektury a toků. 

## Splnění povinných požadavků kurzu

Následující tabulka obsahuje seznam povinných požadavků na semestrální práci a způsob jejich naplnění v projektu.

| # | Požadavek | Status <span style="color:#2e7d32">✓</span> = Splněno | Podrobnosti / Komentář |
|---|-----------|--------|------------------------|
| 1 | Java ≥ 21, projekt pod **Maven** | <span style="color:#2e7d32">✓</span> | Projekt používá Java 21 (`maven.compiler.release` 21). Sestavení probíhá přes `pom.xml`. |
| 2 | Průběžné commity na **GitLab**, rozumná historie | <span style="color:#2e7d32">✓</span> | Vývoj probíhal průběžně; oddělení práce větev `CP3` / `CP3-ext` a tagy verzí (`0.9.x`). |
| 3 | **JavaFX** GUI; netriviální část **bez Scene Builderu** | <span style="color:#2e7d32">✓</span> | Vůbec nebyl použit Scene Builder / FXML. Všechny komponenty (např. `BoardGridView`) a logika jsou tvořeny dynamicky v Javě. |
| 4 | **Vlákna** mimo triviální `Timer`; u JavaFX typicky **Task** / **Service** | <span style="color:#2e7d32">✓</span> | Použito pro síťovou komunikaci (TCP server/klient běží na pozadí), pro výpočet tahů AI a pro odpočítávání herních hodin (`PlayChessClockTicker`). |
| 5 | **Unit testy** nebo funkční testy (JUnit) | <span style="color:#2e7d32">✓</span> | JUnit 5. Testy pokrývají herní model, serializaci, parsování notace, algoritmy AI i validaci pravidel (např. `PlayStepValidationTest`). |
| 6 | **Loggery** (SLF4J/Logback), zap/vyp **bez editace kódu** | <span style="color:#2e7d32">✓</span> | Integrován Logback. Úroveň logování lze měnit přes CLI argumenty, JVM properties nebo přímo v menu aplikace za běhu. Lze zapnout i výpis do souboru. |
| 7 | **Uložení stavu** aplikace / partie | <span style="color:#2e7d32">✓</span> | Stav hry a historie se ukládá/načítá v textové notaci přes `GameSerializer`. |
| 8 | **Javadoc** u netriviálních public API | <span style="color:#2e7d32">✓</span> | Důležité veřejné třídy a metody mají JavaDoc dokumentaci popisující vstupy, výstupy a chování. |
| 9 | Komentáře u netriviálních částí | <span style="color:#2e7d32">✓</span> | Místa s náročnou logikou (např. `DefaultRuleEngine` nebo `SearchGrid`) jsou průběžně komentována. |
| 10 | **Uživatelský manuál** a **technická dokumentace** | <span style="color:#2e7d32">✓</span> | Uloženo v repozitáři viz výše, připraveno pro vložení do GitLab Wiki (požadavek CP3). |
| 11 | Kód a komentáře **anglicky**; uživ./tech. dokumentace **CS/SK** | <span style="color:#2e7d32">✓</span> | Zdrojový kód, komentáře v něm a commit zprávy většinou v AJ. Manuály a technická dokumentace jsou v češtině. |

### Téma Arimaa — specifické požadavky

| Funkce dle zadání | Status | Podrobnosti / Komentář |
|-------------------|--------|------------------------|
| Tahy 1–4, tlačení, tažení | <span style="color:#2e7d32">✓</span> | Implementováno plně v `DefaultRuleEngine` (router s rozpadem na separátní logiku a pravidla). |
| Pasti, zmrazení | <span style="color:#2e7d32">✓</span> | Implementováno, u vizualizace mají zmrazené figurky sníženou opacitu. |
| Králík na poslední řádek, blokace / konec hry | <span style="color:#2e7d32">✓</span> | Hra správně detekuje stav GAME_OVER na základě všech možných situací, včetně znemožnění tahu. |
| **Hrací hodiny** (čas přemýšlení) | <span style="color:#2e7d32">✓</span> | Implementováno (`PlayChessClockModel`), reálný čas se ukazuje v UI v postranním panelu. |
| Uložení, načtení, krokování tahů, **oficiální notace** | <span style="color:#2e7d32">✓</span> | Hra má interaktivní panel „Historie tahů“, který dovoluje listovat historií, vracet tahy a generovat notaci v oficiálním textovém formátu. |
| Dva hráči na **jednom PC** | <span style="color:#2e7d32">✓</span> | Základní režim v aplikaci. |
| Hra proti **počítači** | <span style="color:#2e7d32">✓</span> | Úrovně od 0 (náhodné pohyby chránící před pastí) do 2 (Alpha-Beta vyhledávání). |

## Modul Maven a spuštění

Zdrojové kódy a nastavení naleznete ve složce **[`Arimaa/`](Arimaa/)**

Pro zkompilování a spuštění aplikace použijte:
```bash
mvn -f Arimaa/pom.xml compile
mvn -f Arimaa/pom.xml javafx:run
```

**Hlavní třída:** `cz.cvut.fel.pjv.arimaa.ArimaaApp`

Další pokyny ohledně spuštění (nastavení logování) naleznete v [Uživatelském manuálu](Dokumentace/Manual-hrace.md).

## Aktuální vývojová verze (větev `CP3-ext`)

**Tag / verze:** **0.9.34** (viz `Arimaa/Changelog.md`, popis v `Arimaa/pom.xml`; číslo v dialogu **O programu** bere `AppVersion` z git tagu při buildu).

### Síťová hra — stabilizace 0.9.33–0.9.34

- **Host = Gold**, **klient = Silver**; kanonický stav na hostiteli, klient dostává `state_snapshot` (stejný textový formát jako uložená partie).
- **CPU v síti:** Gold CPU běží na hostu; Silver CPU na klientovi (animace kroků → intent `PLAY_SUBMIT_NOTATION`). Host nepřepočítává Silver PLAY lokálně.
- **Krátké tahy** s příponou `... pass` host přijímá (dříve odmítal → desynchronizace u Silver CPU).
- **Klient** po odeslání intentu čeká na snapshot; při chybě loadu se stav vrací (rollback) a CPU se nespouští z polorozbité desky.
- **Připojení klienta:** pokud host ještě nenaslouchá, TCP connect se opakuje až **~10 s** (stavový řádek s číslem pokusu).
- Během živé síťové partie host neprochází „Historie tahů“ tak, aby na klienta poslal historický pohled.

Podrobnosti pro hráče: [Manuál §8](Dokumentace/Manual-hrace.md). Protokol a třídy: [Technická dokumentace §3.7](Dokumentace/Technicka-dokumentace.md).
