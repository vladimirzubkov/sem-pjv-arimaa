# Changelog

## 0.7.13

- **Skiny:** složka **`default`** (PNG figurky), **`classic`** (SVG); **`FigureSkinDirectoryDiscovery`** — výpis podsložek `images/figure_sets/` jen z **jednoho** classpath URL (`file:` / `jar:` u stejného kořene jako načítání figurek), bez závislosti na ClassGraph (žádné „fantomní“ skiny z jiného modulu / classpath).
- **Gameplay → Skin:** výchozí **`default`**; výběr položky v menu **bez rozlišení velikosti písmen** v názvu složky.
- **PLAY — klávesnice:** **Ctrl+Tab** / **Ctrl+Shift+Tab** — přepnutí na jinou **vlastní** figuru i během **tahnutí** nebo **tlačení** (bez cyklování fialových / oranžových cílů).
- Textový save (`GameMementoTextCodec`): u desky jen **neprázdné řádky `R#`**, bez řádku **`GRID`**; starý formát s `GRID` se už nenačítá; test **`GameMementoTextCodecTest`**.
- **SETUP / model:** **`SetupPresets`** (šachová a rotující rozestavení, zrcadlení domovských řad), napojení v **`Game`**; test **`SetupPresetsTest`**.
- **PLAY — historie polů (`PlayTurnHistory`) a načtená partie:** při úpravě draftu se maže zastaralý `notationLineOrNull` ze souboru; řádek **Historie tahů** pro rozpracovaný tah se staví z **kroků** (Ctrl+Z na živém konci zkrátí náhled, ne celý starý text).
- **Undo / Redo draftu:** `return` po větvi mutace jen po **úspěšném** `pop`; `reconcilePlayPartialWithHistoryView()` drží `playPartialMove` v souladu s historií.
- **Legální pokračování tahu:** deska už má aplikovaný prefix — náhled obsazení z `simulatePlayPrefix(g, new Move())`, validace přídavku přes `isValidPlaySuffixFromViewHalfStart` (probe od `startSnap` polu).
- **UI:** status po výběru vlastní figury zmiňuje tahnutí jen při neprázdných fialových cílech.
- **GameController:** ve fázi PLAY je zdrojem pravdy pro notaci a krokové Zpět/Vpřed **`PlayTurnHistory`** (náhled rozpracovaného tahu, `recordCommittedPlayTurn`, `applyPlayHistoryViewToGame`); **`GameTimeline`** zůstává jen pro SETUP (undo/redo rozestavení).
- **GameSerializer:** základ TXT zápisu z **`PlayTurnHistory.anchorStartSnap`** (místo heuristiky nad časovou osou); načítání přes **`rebuildFromLoadedGame`** a **`applyPlayHistoryViewToGame`** — stejná cesta jako při interaktivní hře.
- **GameTimeline:** doplněný Javadoc u `canUndo`, `canRedo`, `undo`, `redo`.
- Úpravy **`MainController`** (napojení na nový PLAY historický model a UI) v rámci této verze.
- **Refaktor UI (bez změny chování):** část logiky vyčleněna z **`MainController`** do **`PlayDraftNotationSupport`** (kopie kroků, probe z mementa, náhled notace, validace sufixu tahu), **`PlayTurnDraftState`** (rozpracovaný PLAY tah), **`BoardGridView`** + **`BoardHostPane`** (mřížka, rámeček souřadnic, kreslení figurek a hover), **`ArimaaSaveLoadSupport`** (uložení / načtení souboru). **`GameController`** zůstává tenkou doménovou vrstvou.
- **Model:** přesun výčtů **`GameState`**, **`PieceType`**, **`PlayerSide`**, **`StepKind`** do balíčku **`cz.cvut.fel.pjv.arimaa.model.enums`** (importy v modulu a testech).
- **UI (kosmetika):** hlášky v **`MainController`** a chybové texty v **`ArimaaSaveLoadSupport`** přes **`String.formatted()`** místo konkatenace řetězců.
- **Další refaktor `MainController` (bez změny chování):** `MainWindowLayoutBuilder` + `MainWindowLayoutResult` (menu, postranní panel, SETUP/PLAY tlačítka), `PlayBoardHighlighter` (PLAY podsvícení), `SetupPhaseUiHandler` / `PlayPhaseUiHandler` (fáze SETUP vs PLAY); dřívější výčet `buildSetupReserveAndPlayButtons` / `buildMenuBar` v controlleru nahrazen builderem.

## 0.7.12

- Textový formát uložení a načtení partie (replay notace); rozpracovaný tah po načtení; opravy načítání notace a tahů.
- Dialogy pamatují poslední složku; **Ctrl+O** / **Ctrl+S**; po načtení přehrání historie na desce.

## 0.6.12

- **PLAY — klávesnice:** **Tab** / **Shift+Tab** — cyklus vlastních figurek na desce (v pořadí zleva doprava, shora dolů podle aktuálního otočení desky), stejně jako klik na vlastní figuru; při **tahnutí** (po tahu na volné pole) Tab cykluje **soupeřovy** figury označené pro dokončení tahnutí; při dostupném **tlačení** Tab cykluje **oranžové** cíle prvního kroku pushu (odlišné podsvícení od zelených běžných tahů); **mezerník** dokončí tahnutí nebo tlačení na zvýrazněný cíl (nebo první v pořadí). **Enter** a **Ctrl+Enter** končí tah (jako dříve Enter). Šipky / **WASD** beze změny. **Esc** zruší rozpracovaný tah (stejné jako tlačítko); tlačítko **Zrušit rozpracovaný tah** nemá tab-fokus — po startu je fokus na scéně, **mezerník** na desce už neaktivuje omylem zrušení.
- **SETUP — klávesnice:** **mezerník** = stejné jako tlačítko náhodného rozestavení / doplnění; **Ctrl+mezerník** = šachová rozestavení; **Ctrl+Enter** = Hotovo (ukončit rozestavení), pokud je tlačítko aktivní.

## 0.6.11

- **PLAY — Undo / Redo (Vpřed):** push a pull jsou v modelu **dvě atomické nohy** za sebou; `isValidPlayPrefix` často **jednu nohu samu** odmítne jako neúplný začátek tahu. Po úplném Zpět a opětovném Vpřed se z redo zásobníku obnovovaly kroky **po jednom**, takže první noha push/pull zůstávala odmítnutá a Vpřed „nefungoval“. **Oprava:** pokud jeden krok z redo není platný jako prefix, zkusí se **dvojice** dvou po sobě sebraných kroků (stejný pár jako při tahu z desky), a teprve pak se kroky vrátí do zásobníku.

## 0.6.10

- **PLAY — podsvícení rozpracovaného tahu:** počátek segmentu **aktuálně vybrané** figury (`playActiveSegmentOrigin`); po výběru jiné vlastní figury se přepočítá; zlatý nádech pro **Gold**, světlý kovový pro **Silver**; obrys `StrokeType.INSIDE` (stejně jako legální cíle / tahnutí). Lehký **salátový** odstín na poli **`from` posledního kroku** (odkud se právě šlo).
- **Klávesnice:** pohyb po šachovnici jako klik — **šipky**, **WASD**, numerická podsada; `KeyEvent` **filtr** na `Scene` (capture před `ScrollPane`, který jinak bere šipky na scroll); **Enter** končí tah (jako dříve).
- **Undo / Zrušit rozpracovaný tah:** snapshot po zrušení rozpracovaného tahu rozšířen o `playActiveSegmentOrigin`; obnova kroků (redo) obnoví počátek segmentu, pokud je potřeba.

## 0.6.9

- **`GameHistory` + `GameHistoryEvent`:** append-only журнал событий партии (шаги черновика, пошаговый undo/redo в PLAY, сброс черновика, зафиксированный полуход); экземпляр в `GameController`, сброс при `resetTimeline`.
- **PLAY — Undo/Redo:** Ctrl+Z / Ctrl+Y работают **только с черновиком текущего хода** (отмена последнего шага, redo шага или восстановление после **Zrušit rozpracovaný tah**); завершённый полуход отменить нельзя. **SETUP:** по-прежнему снимки в `GameTimeline`.
- **`GameTimeline`:** по-прежнему только зафиксированные состояния и нотация после завершения хода (логика записи без изменений).
- **UI:** zkratky **Ctrl+Z / Ctrl+Y** v PLAY — `KeyEvent` filtr na `Scene` (výchozí `TextArea` notace jinak zkratky „sežere“); panel notace bez tab-fokusu (`setFocusTraversable(false)`).
- Testy: `GameHistoryTest`.

## 0.6.8

- **UI (Gameplay):** volba **Otáčet desku — hráč na tahu dole**; při zapnutí v **PLAY** (a v **GAME_OVER** s vítězem) se deska zobrazí otočená o **180°** vůči výchozímu pohledu (nejen **řádky**, ale i **sloupce** / soubory a–h), aby hráč na tahu seděl „dole“ jako při klasické desce; v **SETUP** zůstává stabilní orientace (Gold dole).
- **MainController:** mapování vizuální mřížka ↔ model (`modelFileFromVisualCol` / `visualColFromModelFile` vedle ranků); dynamické popisky souborů nad/pod deskou; konzistentní kliky, podsvícení a šachovnice podle modelových souřadnic.

## 0.6.7

- **Notace tahů (Arimaa):** panel **Notace tahů** pod zajatými (viditelný až po rozestavení — PLAY / GAME_OVER); řádky ve stylu [arimaa.com notation](https://arimaa.com/arimaa/learn/notation.html) — `DefaultRuleEngine.buildArimaaNotationBody`, `Position.toAlgebraic`, `ArimaaNotation.formatFullTurn` s ` ... pass` při méně než 4 krocích; během rozpracovaného tahu náhled řádku (`formatPartialTurnLine`) bez `... pass`.
- **Časová osa:** `GameTimeline` ukládá paralelní texty k mementům; `GameController.recordAfterMutation(String)`; předpona `Ng` / `Ns` z `nextPlayNotationPrefix()`; Undo/Redo zúží i historii.
- **Úpravy:** výpočet plné řádky notace před `submitHumanMove` (deska před tahem); bez zdvojeného kopírování desky v UI (`buildArimaaNotationBody` kopíruje sám).
- **Tahnutí (pull):** nejdřív krok vlastní figury na volné pole (slide), volitelně dokončení kliknutím na **fialově označenou** soupeřovu slabší figuru; engine přijímá pár `SLIDE` + `PULL_DRAG_WEAKER` (stejně jako dříve `PULL_VACATE` + `PULL_DRAG`); push zůstává dvojkrok v jednom kliknutí; `enumerateStepBundles` doplňuje jednokrokové pull-drag pro DFS; test `applyMoveSlideThenPullDragSameAsPullVacatePair`.
- Testy: `ArimaaNotationTest`.

## 0.5.7

- **Rozestavení:** pokud jsou všechny figury na domovských řadách, tlačítko **Náhodně doplnit zbytek** se změní na **Náhodně rozestavit** a náhodně přeřadí 16 figurek na domově (`Game.shuffleSetupPiecesOnHomeRandomly`); Undo/Redo přes memento jako dosud.

## 0.5.6

- **UI (PLAY / GAME_OVER):** skrytí bloku rezervy a tlačítek rozestavení; řádek „Tah (Gold/Silver): …“; postranní panel **Zajaté (Gold) / (Silver)** s ikonami zajatých typů (lovčky; **36 px**), včetně náhledu během rozpracovaného tahu.
- **Model:** seznamy obětí pastí v `Game`, serializace v `GameMemento`; `Board.copy()`; `DefaultRuleEngine.applyMoveToBoard(Board, Move, Consumer)` a `trapCapturesIfPrefixApplied` pro náhled pastí bez mutace hry.
- **Gameplay (menu):** volitelné zakázání **Zrušit rozpracovaný tah** poté, co náhled prefixu odstraní vlastní figuru pastí.
- **Rozestavení:** tlačítko **Hotovo (ukončit rozestavení)** aktivní až když `Game.allSetupPiecesOnBoard` (prázdná rezerva, nic v ruce, 16 figurek na domově); plná legalita multisetu zůstává u `tryCompleteSetup`.

## 0.5.5

- Balíček `cz.cvut.fel.pjv.arimaa.exception`: **`IllegalMoveException`** (rozšíření `IllegalArgumentException`) — porušení pravidel tahu v `DefaultRuleEngine` (slide, push/pull, počet kroků, …).
- **`GamePhaseException`** (rozšíření `IllegalStateException`) — volání PLAY API (`applyMove`, `simulatePlayPrefix`) mimo `GameState.PLAY`; použito v `DefaultRuleEngine` a `Game.applyMove`.
- **`GameController.submitHumanMove`** zachytává oba typy; `MainController` beze změny (`catch` na `IllegalArgumentException` stále pokrývá `IllegalMoveException`).

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

- **Skin figurek (Gameplay → Skins):** `Default` / `Originál` / `None`; SVG v `images/figure_sets/classic/`, PNG v `images/figure_sets/default/`, rasterizace SVG přes **Apache Batik** do `javafx.scene.image.Image` ve třídě `FigureSvgRasterCache` (mezipaměť, super-sampling; u PNG případný fallback na SVG z `classic/`). Deska, rezerva a „V ruce“ zobrazují grafiku nebo písmena.
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
