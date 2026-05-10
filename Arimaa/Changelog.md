# Changelog

## 0.9.22

- **Klávesnice (PLAY):** jeden cyklus **Tab** / Shift+Tab pro **tahnutí** a pak všechny dostupné varianty **tlačení** — včetně tlačení na **stejné prázdné pole** od různých soupeřových figur nebo **dvou směrů** u jedné figury; **mezerník** respektuje právě zvolenou variantu (ne vždy automaticky tahnutí, když jde o tlačení).
- **Deska (PLAY):** u tlačení z klávesnice **dva žluté rámečky** — tenčí ukazuje **tlačenou** figuru, tlustší **cílové pole** prvního kroku; sjednocení výraznosti obrysů tlačení s tahnutím tam, kde to hráče nejvíc zajímá.
- **Nápověda:** aktualizované texty **Ovládání** a stručná **Pravidla** v souladu s chováním aplikace; menu **Nápověda** a dialog **O programu** (včetně vloženého náhledu videa).
- **Sestavení:** závislost **javafx-web** pro zobrazení nápovědy a náhledu v dialogu O programu.

## 0.9.21

- **Gameplay:** procedurální **zvuky tahů** (`javax.sound.sampled`, `PlayProceduralSfx`) — krátký „dřevěný“ úder po kroku a samostatný efekt při novém pádu do pasti; přehrávání na virtuálním vlákně; položka **Zvuky tahů (syntéza)** (výchozí zapnuto); **bez druhého** přehrání efektu pasti při odeslání celého tahu (už zněl po krocích náhledu).
- **Deska (PLAY):** pole **pastí** — diagonální výplň „propasti“ (`BoardGridView.trapChasmFill`): světlý tón pole vlevo nahoře, tmavší směrem vpravo dolů (částečná průhlednost rohu).
- **Gameplay / hráči:** ve výchozím stavu **Silver — počítač úroveň 1** (`MainController`, menu **Silver hráč**); po odpojení ze sítě stejný výchozí (Gold člověk, Silver CPU 1) a synchronizace výběru v menu.

## 0.9.20

- **Časovač (PLAY):** dvě strany (**Gold** / **Silver**), tabulka **Časovač** — sloupce **Celkem** a **Průměr na tah** (průměr z dokončených polotahů); neukládá se do souboru partie.
- **Vlákna (JavaFX):** periodický přepočet přes **`ScheduledService`** + **`Task`** na pozadí, aktualizace buněk tabulky jen přes **`Platform.runLater`** (`PlayChessClockTicker`, `PlayChessClockModel`); při ukončení aplikace **`JavafxApp.stop()`** zruší ticker.
- **Gameplay:** položka **Zobrazit jednotlivé kroky počítače při tahu** je **ve výchozím stavu zapnutá**.
- **Boční panel:** menší vertikální mezera mezi řádkem **Tah** a blokem **Zajaté** (bez dodatečného spaceru, `USE_PREF_SIZE` výšky u popisku tahu, spodní **Region** s `VBox.setVgrow` pro volné místo ve scrollu).
- **Historie tahů:** po obnovení listu se **vždy** posune výběr do viditelné oblasti (`scrollTo`), nejen během animace tahu PC.

## 0.9.19

- **Síť (TCP):** dvouhráčová hra; zprávy jako **jeden JSON na řádek** (řádkový JSON, **NDJSON** / Newline Delimited JSON / JSON Lines). **Hostitel = Gold**, **klient = Silver**; kanonický stav na hostiteli, klient dostává **`state_snapshot`** s **`saveText`** (stejný formát jako uložená partie).
- **Aplikační protokol v2** (pole **`protocolVersion`** v `hello`/`welcome`): rozšířený handshake o přiřazení hráčů (**`silverSeatControl`**, **`goldSeatControl`**), zpráva **`seat_control`**, rozšířené **`intent`** včetně **`PLAY_SUBMIT_NOTATION`** a **`SETUP_SILVER_CPU_AUTOFILL`**, **`ping`/`pong`**, **`error`**, **`bye`**.
- **UI:** menu **Síť** (hostovat / připojit se, port; u hostitele přehled **lokálních IPv4**); **Hráči** — **protihráč (síť)** a volba člověk/počítač.
- **Implementace:** `ArimaaNetworkCoordinator`, `NetworkJson`, `WireMessages`, `NetworkAssignmentCodec`, `NetworkLocalAddresses`; v `logback.xml` logger `cz.cvut.fel.pjv.arimaa.network` vždy **INFO** na konzoli.
- **Testy:** `NetworkJsonWireTest` (protokol v2, seat control, intent s notací).
- **Distribuce:** `maven-shade-plugin` — spustitelný JAR se závislostmi.
- **Poznámka:** klient ve **VirtualBox NAT** se k hostiteli na **Windows** typicky připojuje na **`10.0.2.2`** (ne na LAN IP uvedené v dialogu hostitele).

## 0.8.19

- **Gameplay / hráči:** tři úrovně počítače (**0** — náhodný tah s filtrem pastí, **1** — greedy podle heuristiky z **náhodně vzorkovaných** legálních tahů (bez výpisu všech tahů) + časový limit ~2,5 s, **2** — minimax + alpha-beta: max. **2** celé tahy při ≤28 kořenových tazích, jinak hloubka **1**; **horní limit ~3,5 s** na jeden výběr tahu; řazení potomků podle délky tahu kvůli cutům); posuvník **jen pauza animace** kroků tahu PC (**0,1–2,0 s**, výchozí 1 s); heuristika upřednostňuje **vývoj silnějších figur** (ne jen králíci), SETUP CPU zkouší **šachová rozestavení** (náhodné pořadí presetů) a teprve pak náhodné doplnění.
- **Panel Stav:** blok **Hráči** — `Gold` / `Silver` jako člověk nebo počítač s úrovní (`PlayerControllerKind.assignmentDescriptionCs`).
- **Model:** `PlayerControllerKind.COMPUTER_LEVEL_1/2`, `isComputer()`, rozšířené `MainController.isComputerControlled`; výběr tahu přes `ComputerPlayMove.selectPlayMove` (`HeuristicEvaluation`, `GreedyComputerMove`, `AlphaBetaComputerMove`).
- **Testy:** `CpuAiEvaluationAndMoveTest` (heuristika, greedy/alpha-beta na šachovém rozestavení, odmítnutí HUMAN u `ComputerPlayMove`).
- **Gameplay / hráči / úroveň 0:** Alt mnemoniky v menu; svislý rozsah slideru prodlevy kroků CPU a zobrazená hodnota s dolním limitem (`MainWindowLayoutBuilder`).
- **Historie tahů:** Zpět/Vpřed i po skončení partie (**GAME_OVER**, `MainUiLayoutPhase.isPlayOrGameOver`); po `refreshAll` obnova statusu a štítku výhry u „ruky“ (`MainController`). **Oprava (list + tah PC):** po výběru dokončeného řádku náhled na začátek následujícího polotahu / aktuální draft (v listu je vždy i prázdný rozpracovaný řádek); tah počítače se plánuje jen při náhledu na koncovém draftu; při změně výběru řádku přerušení animace nebo async výběru tahu PC (`cancelComputerPlayForHistoryScrub`).

## 0.8.18

- **Gameplay:** volba **Zobrazit jednotlivé kroky počítače při tahu** — při animaci tahu PC se poslední řádek v „Historie tahů“ prodlužuje podle již provedených kroků (`appliedPrefixSteps`); vypnuto = celý rozpracovaný tah v notaci najednou.

## 0.8.17

- Minimální pauza mezi kroky animace tahu počítače 100 ms (slider + clamp).
- Historie tahů: výběr řádku funguje i ve stavu GAME_OVER
  (showCapturesAndNotationHistory místo jen PLAY).
- refreshNotationHistory: auto-scroll ListView jen při computerActionPending,
  aby šlo listovat historii po partii.
- clearPlayTurnUi jen ve fázi SETUP.

## 0.8.16

- **Počítač na tahu:** **mezerník** zapne/vypne **pauzu** automatické činnosti (SETUP i PLAY); po pauze uprostřed krokované animace tahu PC pokračuje **stejný** tah; zrušení pauzy bez rozpracované animace znovu spustí plánování tahu. **Invalidace** běžícího výběru tahu na vlákně přes `AtomicLong` při pauze / přepnutí hráče z PC na člověka. **Overlay** „Pauza — mezerník pokračuje“ nad deskou (`StackPane` v `MainController.attachToStage`).

## 0.8.15

- **Počítač úroveň 0:** výběr tahu **náhodným DFS** (`DefaultRuleEngine.sampleRandomLegalCompleteMove`) místo výpisu všech tahů; opakované vzorkování kvůli **pastem** (`RandomTrapAvoidingMoveChooser`). **Oprava DFS:** nejdřív se zkouší **prodloužení** prefixu, teprve pak přijme jednokrokový **celý** tah (dříve se často brala jen jedna noha).
- **PLAY:** výpočet tahu CPU na **vlákně** (`ExecutorService`), animace **krok za krokem** přes `PlayTurnHistory.setViewPrefix` + `applyPlayHistoryViewToGame` a `PauseTransition`, potom jeden `submitHumanMove`.
- **SETUP (Počítač):** s **20%** pravděpodobností jedno ze **šachových** rozestavení (`applyChessMappedSetup` — klasické nebo jedna z **4** rotujících variant), jinak `placeRemainingPiecesRandomly`; při neúspěchu presetu náhradně náhodné doplnění.
- **Gameplay:** posuvník **Pauza tahu počítače na krok** (0–2000 ms, **výchozí 1000 ms**); zobrazení v **sekundách** se **dvěma** desetinnými místy, **max. šířka posuvníku 100 px**, **fixní šířka** pole pro hodnotu (šířka menu se nemění); zarovnání řádku s ostatními položkami; **oprava menu:** odložené `refreshAll` po přepnutí hráčů / některých položek; u posuvníku **`setFocusTraversable(false)`** a `CustomMenuItem.setMnemonicParsing(false)` — menší riziko „zaseknutého“ zvýraznění.
- **JavaFX (menu):** `arimaa-menus.css` — jednotné odsazení **10 px** vlevo/vpravo u horní lišty (`.menu-bar .menu-button`) a u okraje popupu (`.context-menu`); řádky uvnitř popupu — výchozí Modena (bez dodatečných stylů na jednotlivé položky).
- **Testy:** `sampleRandomLegalCompleteMove_returnsApplicableFullTurn`, `sampleRandomLegalCompleteMove_oftenMultiStepFromStandardOpening` v `RandomTrapAvoidingMoveChooserTest`.

## 0.8.14

- **Gameplay → hráči:** podmenu **Gold — hráč** / **Silver — hráč** — **Člověk** nebo **Počítač — úroveň 0** (`PlayerControllerKind`); výchozí oba lidští.
- **Počítač úroveň 0:** náhodný **plný legální tah** s upřednostněním variant **bez pádu vlastní figury do pasti** (`DefaultRuleEngine.trapCapturesIfPrefixApplied`); pokud žádná taková není, náhodně z celého seznamu (`RandomTrapAvoidingMoveChooser`).
- **Model:** `DefaultRuleEngine.enumerateLegalCompleteMoves(Game)` — výpis všech legálních celých tahů (DFS jako `existsLegalTurn`).
- **JavaFX:** po `refreshAll()` odložený tah počítače (`Platform.runLater`), SETUP — `placeRemainingPiecesRandomly` + `tryCompleteSetup`, PLAY — stejná cesta jako člověk (`submitHumanMove`, `recordCommittedPlayTurn`, historie); blokace vstupu a tlačítek na tahu CPU (setup/play panely, scéna, draft cancel).
- **Testy:** `RandomTrapAvoidingMoveChooserTest` (lovná past c3, filtr vs. fallback).

## 0.7.14

- **Model (`Game`):** `setupReserveCountsByType`, `canFillRemainingReserveRandomly` (stejná logika jako před `placeRemainingPiecesRandomly` včetně figury v ruce), `restoredFromMemento` pro probe ze snapshotu.
- **`Step.copyOf` / `PlayHalfTurn.copyStep`:** jedna cesta kopírování kroku.
- **`PlayTurnHistory`:** `viewPrefixRemovesPieceViaTrap(Game)` — detekce pádu do pasti v náhledu prefixu (místo logiky v `MainController`).
- **UI:** `BoardViewHost` + `BoardGridView(BoardViewHost)` — deska nezávislá na konkrétní třídě `MainController`.
- **UI:** `SetupSidePanelController`, `PlaySidePanelController` — rezerva / setup tlačítka vs. zajatí, notace, PLAY tlačítka.
- **UI:** `MainUiViewModel` + JavaFX bind (`visible`/`managed`) pro blok rezervy, setup ovládání, zajaté a historii tahů; `MainUiLayoutPhase`, `GameUiPhaseSnapshot` pro opakované větve podle fáze.
- **`MainController`:** `refreshAll()` zůstává centrálním přepočtem (deska, souřadnice, podsvícení, stav), delegace na panely a VM místo starších `refreshReserve*` / ruční viditelnosti tam, kde stačí binding.
- **Testy:** `GameTest` pro `canFillRemainingReserveRandomly` a `setupReserveCountsByType`; **`PieceTypeTest`** pro `PieceType.notationChar()`.
- **Model (`PieceType`):** jednopísmenné značky v souladu s UI (rezerva, deska, zajatí).
- Refaktor bez zamýšlené změny chování hry a UI.

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
- **Model / UI:** **`PieceType.notationChar()`** — jedna latinská písmena na typ (deska, rezerva, zajatí); **`BoardGridView`** už nevolá `MainController` jen kvůli zkratce; popisek „V ruce“ přes **`updateHandLabel`**; test **`PieceTypeTest`**.
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
