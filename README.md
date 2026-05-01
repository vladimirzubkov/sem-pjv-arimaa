# Semestrální práce B0B36PJV – Arimaa

Desktopová hra **Arimaa** v Javě. Tento repozitář obsahuje **kostru projektu (CP2)** podle schválené vize; herní logika, UI scény a síťová komunikace budou doplněny v dalších iteracích.

## Modul Maven

Zdrojový kód je v adresáři [`Arimaa/`](Arimaa/).

- **Kompilace:** `mvn -f Arimaa/pom.xml compile`
- **Spuštění JavaFX:** `mvn -f Arimaa/pom.xml javafx:run`  
  Hlavní třída: `cz.cvut.fel.pjv.arimaa.ArimaaApp` (nastavení logování, poté `Application.launch(JavafxApp.class, args)`).

### Logování

- **Výchozí:** žádný výstup na konzoli (úroveň **OFF**).
- **Při spuštění z příkazové řádky:** přidejte argument ve tvaru `--log-level=DEBUG` (hodnoty: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`, `ALL`; lze použít i `NONE` místo `OFF`).
- **Alternativa (JVM):** `-Dcz.cvut.fel.pjv.arimaa.log.level=INFO`
- **Volitelný zápis do souboru** (vedle konzole, stejný formát jako STDOUT): argument `--log-file=CESTA` nebo JVM vlastnost `-Dcz.cvut.fel.pjv.arimaa.log.file=CESTA` zapne při startu druhý appender na root loggeru (`LoggingSupport`). Bez vlastní cesty lze cestu nastavit programově; výchozí soubor je **`arimaa.log` v kořeni modulu Maven** (vedle `src/`), pokud jde spustit z `target/classes` nebo z JARu v `target/`; jinak **`arimaa.log` v aktuálním pracovním adresáři** (`user.dir`). Používá se jednoduchý **FileAppender** (append), bez rotace. Úroveň logů je nezávislá; při **OFF** se do souboru ani na konzoli nic nevyšle. Ovládání úrovně a souboru z menu aplikace je ve verzi **0.5.4**.

Při spuštění přes Maven lze použít JVM vlastnost (funguje i bez programových argumentů):

```text
mvn -f Arimaa/pom.xml javafx:run -Dcz.cvut.fel.pjv.arimaa.log.level=DEBUG
```

Spuštění z IDE: **Main class** `cz.cvut.fel.pjv.arimaa.ArimaaApp`, do **Program arguments** např. `--log-level=INFO` nebo `--log-file=C:/temp/arimaa.log`, nebo VM options `-Dcz.cvut.fel.pjv.arimaa.log.level=INFO` / `-Dcz.cvut.fel.pjv.arimaa.log.file=...`.

## Použité technologie

| Oblast | Technologie |
|--------|-------------|
| Jazyk | Java 21 |
| Build | Maven |
| GUI | JavaFX (controls, FXML připraveno na později) |
| Logování | SLF4J + Logback (`src/main/resources/logback.xml`) |
| Testy (příprava na CP3) | JUnit 5 (závislost v POM, testy zatím nejsou povinné pro CP2) |

## Objektový návrh (MVC)

Aplikace je členěna do vrstev:

- **`model`** – doménový model: `Game`, `Board`, `Piece`, `Position`, `Move` (celý tah až se čtyřmi kroky), `Step` (jeden atomický krok), výčty `GameState`, `PlayerSide`, `PieceType`, rozhraní `MoveValidator` a `RuleEngine` pro validaci a aplikaci tahů.
- **`controller`** – tenká vrstva `GameController` mezi UI a modelem (např. předání lidského tahu).
- **`ui`** – JavaFX: `JavafxApp`, `MainController` (propojení s FXML přijde později).
- **`persistence`** – ukládání a načítání (`GameRepository`, `GameSerializer` / notace).
- **`ai`** – generování tahů a náhodná AI (`MoveGenerator`, `RandomAiPlayer`).
- **`network`** – rozhraní `GameMessage`, `NetworkClient`, `NetworkServer` **bez implementace**; TCP klient–server podle IP a portu bude doplněn později spolu s popisem protokolu pro finální dokumentaci.
- **`util`** – např. konstanty desky (`BoardConstants`).
- **`logging`** – nastavení úrovně Logbacku z CLI / JVM (`LoggingSupport`); nabídka Log v UI od verze **0.5.4**.

V CP2 jsou těla metod záměrně prázdná nebo vracejí neutrální hodnoty; nejde o hratelnou hru.

### Diagram balíčků

![Vrstvy balíčků](Dokumentace/out/package-structure.png)

### Diagram tříd – model

![Doménový model](Dokumentace/out/class-model.png)

**Doménový model – stručně k metodám (plán):**

- **Game** – drží stav partie a hráče na tahu; `applyMove` zpracuje celý souhrnný tah (1–4 kroky), případně změní fázi hry.
- **Board** – reprezentace desky 8×8; metody pro čtení/zápis obsazení pole, inicializaci a reset (rozestavění / nová hra).
- **Piece**, **Position** – vlastnosti figury (typ, strana) a souřadnice pole; přístup pro vykreslení a pravidla.
- **Move**, **Step** – `Move` je jeden tah hráče jako posloupnost až čtyř `Step`; každý `Step` popíše jeden atomický posun (včetně směrů push/pull až v implementaci).
- **MoveValidator** – ověří, zda je krok nebo celý tah v aktuálním stavu legální (včetně počtu kroků v tahu).
- **RuleEngine** – po platném tahu aktualizuje stav (odstranění v pasti, zmrazení, výměna strany, konec hry).

Třídy **GameState**, **PlayerSide**, **PieceType** budou především výčty bez vlastní logiky.

### Diagram tříd – JavaFX a MVC

![UI a GameController](Dokumentace/out/class-ui.png)

### Stavový diagram

Stavy zahrnují režim hry na jednom počítači. Na stavovém diagramu značí popisek **(opce)** u přechodů se stavem **`CONNECTING`** volitelné síťové připojení (IP:port).

![Stavy aplikace](Dokumentace/out/state-game.png)

## Dokumentace ve formátu PlantUML

Zdrojové soubory diagramů jsou v [`Dokumentace/*.puml`](Dokumentace/). Obrázky v [`Dokumentace/out/`](Dokumentace/out/) lze znovu vygenerovat například:

- nástrojem [PlantUML](https://plantuml.com/) (CLI nebo plugin do IDE), nebo
- službou [Kroki](https://kroki.io/) (HTTP POST těla `.puml` na endpoint `plantuml/png`).

Příklad (PowerShell z kořene repozitáře):

```powershell
Invoke-WebRequest -Uri "https://kroki.io/plantuml/png" -Method Post `
  -Body ([IO.File]::ReadAllText("Dokumentace/class-model.puml")) `
  -ContentType "text/plain; charset=utf-8" `
  -OutFile "Dokumentace/out/class-model.png"
```

## Síťová hra (plán)

Po dokončení základní lokální hry je plánována **jednoduchá komunikace po TCP** (adresa hostitele a číslo portu). Klienti budou posílat serializované zprávy (`GameMessage` a odvozené typy). Detaily protokolu budou popsány v technické dokumentaci u finálního odevzdání (CP3).

## Starší odevzdání

Soubory vize projektu (CP1) a zadání kurzu jsou v adresáři [`zadani/`](zadani/).
