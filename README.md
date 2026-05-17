# Semestrální práce B0B36PJV — Arimaa

Desktopová hra **Arimaa** v Javě 21 s GUI v **JavaFX**. Partie podporuje plná pravidla hry (tahy 1–4 kroky, tlačení, tažení, pasti, zmrazení, konec hry), **časovač** stran, **ukládání a načítání** v textové notaci, hru **dva lidé na jednom PC**, hru proti **počítači** (více úrovní než pouhý náhodný generátor) a **síťový režim** host–klient přes **TCP**.

**Zadání kurzu (obecné):** [Semestrální práce — B0B36PJV](https://cw.fel.cvut.cz/wiki/courses/b0b36pjv/semestral/start)  
**Zadání tématu Arimaa:** [Arimaa — semestrální práce](https://cw.fel.cvut.cz/wiki/courses/b0b36pjv/semestral/arima)

## Uživatelská dokumentace

- **[Uživatelský manuál](Dokumentace/manual-hrace.md)** — pravidla (shrnutí), ovládání, klávesové zkratky, úrovně AI, rozestavení, spuštění na Windows/Linux/macOS, síť (host **7788**, klient).

V GitLab Wiki je potřeba mít **aktuální** uživatelský manuál dle zadání CP3; obsah tohoto souboru lze do wiki zkopírovat nebo na něj odkázat.

## Technická dokumentace (v repozitáři)

- **PlantUML** zdroje: [`Dokumentace/puml/`](Dokumentace/puml/)  
- Vygenerované **PNG**: [`Dokumentace/puml/out/`](Dokumentace/puml/out/) (diagram balíčků, model, UI, stavy; přegenerování: `java -jar plantuml.jar -tpng -o out *.puml` ve složce `Dokumentace/puml/`)
- **Síť:** protokol NDJSON přes TCP, typy zpráv v balíčku `cz.cvut.fel.pjv.arimaa.network` — pro detailní specifikaci viz zdrojové soubory a Javadoc u veřejných typů

## Splnění povinných požadavků kurzu (kontrolní seznam)

Přehled vůči stránce [Semestrální práce — nutné požadavky](https://cw.fel.cvut.cz/wiki/courses/b0b36pjv/semestral/start).

| # | Požadavek | Status |
|---|-----------|--------|
| 1 | Java ≥ 21, projekt pod **Maven** | Ano (`Arimaa/pom.xml`, `maven.compiler.release` 21) |
| 2 | Průběžné commity na **GitLab**, rozumná historie | Zodpovědnost týmu / cvičícího hodnocení |
| 3 | **JavaFX** GUI; alespoň jedna netriviální část **bez Scene Builderu** | Ano — např. `MainController`, `BoardGridView`, herní layout a logika v kódu |
| 4 | **Vlákna** mimo triviální `Timer`; u JavaFX typicky **Task** / **Service** | Ano — např. `PlayChessClockTicker` + `Task`, síťové IO na thread poolu, animace tahu PC |
| 5 | **Unit testy** nebo funkční testy (JUnit) | Ano — více testových tříd v `Arimaa/src/test/java` (model, AI, perzistence, síťová serializace, …) |
| 6 | **Loggery** (SLF4J/Logback), zap/vyp **bez editace kódu** | Ano — CLI `--log-level` / JVM vlastnost, menu **Log → Logback Level**, volitelný soubor |
| 7 | **Uložení stavu** aplikace / partie | Ano — ukládání/načítání přes serializer (`GameSerializer`, soubor) |
| 8 | **Javadoc** u netriviálních public API | Ano — povinné prvky mají Javadoc (kontinuálně doplňováno) |
| 9 | Komentáře u netriviálních částí | Ano |
| 10 | **Uživatelský manuál** a **technická dokumentace** (není to Javadoc) | Uživatelský: `Dokumentace/manual-hrace.md` + diagramy; technická: diagramy + balíčky/network v kódu — **wiki GitLab** dle CP3 |
| 11 | Kód a komentáře **anglicky**; uživ./tech. dokumentace **CS/SK** povoleno | Ano — tento README a manuál česky; zdrojáky anglicky |

### Téma Arimaa — očekávané funkce dle wiki

Z [zadání Arimaa](https://cw.fel.cvut.cz/wiki/courses/b0b36pjv/semestral/arima):

| Funkce dle zadání | Status |
|-------------------|--------|
| Tahy 1–4, tlačení, tažení | Implementováno (`DefaultRuleEngine`, …) |
| Pasti, zmrazení | Implementováno |
| Králík na poslední řádek, blokace / konec hry | Implementováno |
| **Hrací hodiny** (čas přemýšlení) | Implementováno (`PlayChessClockModel`, UI tabulka) |
| Uložení, načtení, krokování tazy, **oficiální notace** | Implementováno (textový formát, historie) |
| Dva hráči na **jednom PC** | Ano |
| Hra proti **počítači** (minimum náhodné tahy) | Ano — úrovně 0–2 (`PlayerControllerKind`, AI balíček) |

## Modul Maven a spuštění

Zdroje: **[`Arimaa/`](Arimaa/)**

```bash
mvn -f Arimaa/pom.xml compile
mvn -f Arimaa/pom.xml javafx:run
```

**Hlavní třída:** `cz.cvut.fel.pjv.arimaa.ArimaaApp`

### Logování

- **Výchozí:** tichý režim (`OFF`) na konzoli.
- **Argumenty / JVM:** `--log-level=DEBUG` nebo `-Dcz.cvut.fel.pjv.arimaa.log.level=INFO`
- **Za běhu:** menu **Log → Logback Level**, **Log → Zapisovat do souboru**

Podrobnosti viz [Uživatelský manuál — sekce Spuštění](Dokumentace/manual-hrace.md).

## Použité technologie (shrnutí)

| Oblast | Technologie |
|--------|-------------|
| Jazyk | Java 21 |
| Build | Maven |
| GUI | JavaFX (controls, media, web pro nápovědu) |
| Logování | SLF4J + Logback |
| Testy | JUnit 5 |
| Serializace / JSON (síť) | Jackson (dle modulu) |
| Ostatní | Batik pro rasterizaci SVG figurek |

## Struktura balíčků (MVC orientace)

- **`model`** — `Game`, `Board`, pravidla (`DefaultRuleEngine`), historie, notace
- **`controller`** — `GameController`
- **`ui`** — JavaFX obrazovka, menu, klávesnice, síťové dialogy
- **`persistence`** — ukládání/načítání
- **`ai`** — tahy počítače
- **`network`** — TCP host/klient, wire zprávy
- **`logging`** — konfigurace Logbacku z CLI a UI

---

*Text README je určen pro cvičícího a kontrolu souladu se zadáním; neobsahuje nástroje editoru ani interní postupy vývoje.*
