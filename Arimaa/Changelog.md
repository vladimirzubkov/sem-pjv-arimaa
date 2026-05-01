# Changelog

## 0.5.4

- **Plná pravidla PLAY:** `DefaultRuleEngine` — tahy 1–4 kroků, `StepKind` (`SLIDE`, dvojice push/pull), **mražení** (`isFrozen` / `isFrozenOccupancy`), **pasti** po každém kroku, konec hry (králík v cíli, žádný králík, **imobilizace** po přepnutí strany); pravidlo „cizí králík v cílovém řádku“ z kódu odstraněno (způsobovalo předčasný konec kvůli legálnímu rozestavení Silver na horním řádku). `Game.matchWinner` + `GameMemento`.
- **UI:** náhled rozpracovaného tahu přes `simulatePlayPrefix`, legální cíle včetně **push/pull**; menu **Log → Logback Level** a **Log → Zapisovat do souboru** (`LoggingSupport`); titulek okna po **GAME_OVER** bez zavádějícího „na tahu“ po imobilizaci.
- **Model:** `StepKind`, `PieceStrength`, úpravy `Game`, `Step`, `GameMemento`, `GameTimeline`; logy v `DefaultRuleEngine`, `Game`, `GameController`, `GameTimeline` a vybrané akce v `MainController`.
- Testy v `GameTest`: past uprostřed tahu (simulace), push/pull, mražení, výhra králíkem / bez králíků / imobilizace, tah Gold při Silver králíkovi na 8. řádku (hra pokračuje), zamítnutí tahu po skončení hry.

## 0.4.4

- **Logování (SLF4J + Logback):** výchozí úroveň **OFF** (`logback.xml`); balíček `logging` — třída `LoggingSupport` (`bootstrapFromArgs`, úprava root úrovně, volitelný souborový appender `ARO_FILE`, CLI `--log-level` / `--log-file`, JVM `cz.cvut.fel.pjv.arimaa.log.level` / `cz.cvut.fel.pjv.arimaa.log.file`, výchozí soubor `arimaa.log` v kořeni modulu Maven vedle `src/` nebo v `user.dir`).
- Vstupní třída `ArimaaApp`: bootstrap logování před `Application.launch`; v `pom.xml` cíl `javafx:run` → `cz.cvut.fel.pjv.arimaa.ArimaaApp`.

## 0.4.3

- **Fáze PLAY — jednoduché tahy:** ortogonální krok na prázdné pole (bez push/pull a bez mražení), 1–4 kroky na tah, zákaz couvání králíků (Gold nahoru po ranku, Silver dolů), po tahu vyhodnocení **pastí** (`BoardConstants.trapSquares()` — figura bez ortogonálně sousední vlastní figury zmizí) a přepnutí `sideToMove`. `DefaultRuleEngine` + `Game.applyMove`.
- **UI:** výběr figury, náhled rozpracovaného tahu, **Konec tahu** / **Zrušit rozpracovaný tah**, Enter; `GameController.submitHumanMove`; po platném tahu zápis na časovou osu (Undo/Redo); ve fázi PLAY **podsvícení** vybraného pole a legálních cílů dalšího kroku (`CellData` ukládá základní barvy pole).
- Testy v `GameTest`: platný krok, neplatné tahy, past na c3, `DefaultRuleEngine.isValidPlayPrefix`. `ArimaaApp`: popis odpovídá PLAY v UI.

## 0.3.3

- **Skin figurek (Gameplay → Skins):** `Default` / `None`; sada SVG v `images/figure_sets/default/` (Gold/Silver), rasterizace přes **Apache Batik** (`batik-transcoder` + `batik-codec`) do `javafx.scene.image.Image` ve třídě `FigureSvgRasterCache` (mezipaměť, super-sampling pro ostřejší zobrazení, PNG s alfou bez vynuceného bílého pozadí). Deska, rezerva a „V ruce“ zobrazují grafiku nebo písmena.
- **Rozestavení — náhled:** při figuře v ruce poloprůhledný náhled (cca 50 %) na domovském poli pod kurzorem, pokud je umístění legální (`Game.isLegalSetupHandPlacementTarget`).
- **Rozestavení — automatická ruka po umístění:** po `confirmSetupHandPlacement` se z rezervy bere další figura **stejného typu**, jinak první dostupný typ v pořadí síly **E, M, H, D, K, R** (`PieceType`). Ruční výběr z panelu rezervy zůstává.
- Testy: `FigureSvgRasterCacheTest`, úpravy a doplnění `GameTest`.

## 0.3.2

- **Memento + časová osa (Undo/Redo):** `GameMemento`, `GameTimeline` (caretaker), `Game.createMemento` / `restoreMemento`; `GameController` drží časovou osu, `resetTimeline`, `recordAfterMutation`, `undo` / `redo`. V UI po úspěšných změnách během rozestavení (rezerva, ruka, umístění, vrácení do rezervy, náhodné/šachové rozestavení, dokončení fáze, zrušení ruky) se stav zaznamenává; **Tah → Zpět / Vpřed** (Ctrl+Z / Ctrl+Y) a obnovení desky z mementa.
- **Oprava undo u šachové rozestavení:** po úspěšném `applyChessMappedSetup` se v `MainController` volá `recordTimeline()` — *Zpět* vrátí stav před šachovým rozložením (dříve se nový stav na osu nezapsal).
- **Pasti na desce:** obrys polí `StrokeType.INSIDE` a konzistentní šířka čáry; pasti podle `BoardConstants.trapSquares()` — rovnoměrné červené ohraničení při škálování.
- **Hra → Ukončit** ukončí aplikaci (`Platform.exit()`), zkratka Ctrl+Q; oddělovač od položky Nová hra.

## 0.2.2

- Deska v `MainController`: notace **a–h** a **1–8** po celém obvodu; jedna společná `GridPane` pro souřadnice i pole (bez vnořené mřížky), správný výpočet `perimeterSpanPixels()` odpovídající devíti mezerám `BOARD_GAP` na řádek/sloupec — symetrické rozložení a odsazení.
- Odebrána hnědá výplň rámečku; lehký obrys; kořen scény `HBox` (`Hgrow` pro oblast desky) místo `BorderPane` kvůli rovnoměrné šířce; `BoardHostPane` se škálováním přes `Scale` s pivotem (0,0) a středěním podle `min(šířka, výška)` okna.

## 0.2.1

- JavaFX rozhraní pro **rozestavení**: deska 8×8, rezerva s výběrem typu figury, umístění klikem na domovské pole, vrácení figury z desky do rezervy bez figury v ruce.
- Tlačítka: náhodné doplnění zbytku (`placeRemainingPiecesRandomly`), šachová rozestavení (`applyChessMappedSetup`), dokončení rozestavení (`tryCompleteSetup`), zrušení výběru z ruky, nová hra.
- `JavafxApp` spouští hru a propojí `Game` → `GameController` → `MainController`; drobná úprava popisu v `ArimaaApp`.
- Model: `Game.getSetupHand()` pro zobrazení figury čekající na umístění.

## 0.1.1

- Dokončení rozestavení: `tryCompleteSetup(PlayerSide)` — kontrola prázdné rezervy, přesně 16 vlastních figurek na domácím území, oficiální multiset a obsazení; přechody `SETUP_GOLD` → `SETUP_SILVER` → `PLAY` včetně nastavení `sideToMove`.
- `placeRemainingPiecesRandomly(PlayerSide)` — náhodné doplnění zbývajících figurek z rezervy na volná pole domova (lze kdykoli během rozestavení).
- `applyChessMappedSetup(PlayerSide)` — pevné „šachové“ rozložení (zadní řada podle mapování typů, přední řada osmi králíků; Gold řady 0–1, Silver 6–7).
- Rozšířené testy v `GameTest` (náhodné doplnění, šachová rozestavení, přechody a negativní případy).

## 0.0.1

- Základní doménový model: `Board`, `Position`, `Piece` (včetně konstruktoru pro typ a stranu).
- `BoardConstants` (geometrie desky včetně pastí), `HomeTerritory` (domovské řady Gold / Silver, volitelné zrcadlení ranků).
- Přípravná fáze v `Game`: `startNewGame()`, rezervy 16 figurek dle oficiálního multisetu, ruční výběr z rezervy, umístění na domovské pole, zrušení výběru, vrácení figury z desky do rezervy; oprava `setPiece(..., null)` na desce (odvázání pozice u figury).
- Skripty `0_compile.bat`–`3_start.bat` pro Maven v adresáři modulu.
- JUnit 5: `BoardTest`, `GameTest` (`mvn test`).
