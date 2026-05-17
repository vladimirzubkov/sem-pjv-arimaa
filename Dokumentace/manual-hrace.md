# Uživatelský manuál — Arimaa (semestrální aplikace)

Tento dokument popisuje aplikaci studentské varianty hry **Arimaa** — pravidla v obecné rovině, ovládání v naší implementaci, spuštění na různých systémech a hra po síti.

**Oficiální zadání tématu** (FEL ČVUT, B0B36PJV): [Arimaa — semestrální práce](https://cw.fel.cvut.cz/wiki/courses/b0b36pjv/semestral/arima).

---

## 1. Co budete potřebovat

| Požadavek | Poznámka |
|-----------|----------|
| **Java** | minimálně **21** (dle zadání kurzu) |
| **Maven** | 3.x — ke kompilaci a spuštění přes profil OpenJFX |
| **Síť (volitelně)** | pro hru dvou lidí přes TCP — otevřený port na počítači hostitele (firewall/router) |

Herní figurky v aplikaci používají obrázky z modulu, např. sada `default` (PNG):

- příklad souboru v repozitáři: [`Arimaa/src/main/resources/images/figure_sets/default/gJumbo.png`](../Arimaa/src/main/resources/images/figure_sets/default/gJumbo.png) (zvířecí téma; „Jumbo“ = slon)

**Snímky obrazovky:** pro odevzdání nebo wiki je vhodné doplnit 2–4 screenshoty: hlavní okno v rozestavení, okno ve fázi PLAY, dialog síťového hostitele, případně dialog načtení partie. V tomto manuálu zatím nejsou vložené obrázky.

---

## 2. Spuštění aplikace

Pracovní adresář předpokládáme **kořen klonovaného repozitáře** (kde leží `README.md` a složka `Arimaa/`).

### 2.1 Windows, Linux, macOS — přes Maven (doporučeno)

```bash
cd /cesta/k/sem
mvn -f Arimaa/pom.xml compile
mvn -f Arimaa/pom.xml javafx:run
```

**Hlavní třída:** `cz.cvut.fel.pjv.arimaa.ArimaaApp`

**Logování (volitelné):**

- argumenty: `--log-level=INFO` nebo `--log-file=cesta/arimaa.log`
- nebo JVM: `-Dcz.cvut.fel.pjv.arimaa.log.level=DEBUG`
- úroveň logů lze měnit i **za běhu** v menu **Log → Logback Level**, zápis do souboru v menu **Log → Zapisovat do souboru**.

### 2.2 Linux / macOS — poznámky

- Ujistěte se, že `java` a `mvn` v `PATH` odpovídají JDK 21.
- OpenJFX stáhne Maven jako závislosti; při problémech s nativními knihovnami sestavujte a spouštějte na stejném OS, na kterém distribuci chcete používat (zejména „fat“ JAR ze shade pluginu).

### 2.3 Spuštění z IDE

**Main class:** `cz.cvut.fel.pjv.arimaa.ArimaaApp`  
**Program arguments:** např. `--log-level=INFO`  
**VM options:** např. `-Dcz.cvut.fel.pjv.arimaa.log.level=DEBUG`

---

## 3. Pravidla hry (stručně)

Cílem je dostat **králíka** (v aplikaci jde o typ figurky „Hare“ / králík) na **poslední řádek soupeře**.

- **Fáze SETUP:** střídavé rozestavení figurek na svou domovskou řadu dle kapacity a typů; potvrzení rozestavení.
- **Fáze PLAY:** každý tah obsahuje **1–4 ortogonální kroky**; silnější figury mohou slabší **tlačit** nebo **tahat**.
- **Pasti:** označená pole — figura na pasti bez vlastního souseda ortogonálně **zmizí**.
- **Konec hry:** mimo cíle s králíkem též blokace soupeře dle pravidel Arimaa.

**Úplné oficiální znění** pravidel nepopisuje tato aplikace — použijte odkazy ze stránky kurzu nebo zdroje komunity ([zadání Arimaa](https://cw.fel.cvut.cz/wiki/courses/b0b36pjv/semestral/arima)).

---

## 4. Jak začít — rozestavení (SETUP)

1. Po spuštění jste ve fázi **rozestavení**. **Gold** začíná (spodní / domovská strana dle orientace desky).
2. V postranním panelu vyberte **figuru z rezervy**, poté klikněte na **povolené pole** na domovské řadě. Klik na figurku na desce ji může vrátit do rezervy.
3. **Předvolby (presety):** v aplikaci jsou k dispozici uložené rozestavení (např. typické rozložení); vybírejte dle popisků v UI.
4. **Náhodné rozestavení:** tlačítko / **Mezerník** — doplní náhodně zbytek nebo přehází již umístěné (viz text tlačítka).
5. **Šachové mapování:** **Ctrl+Mezerník** — rychlé rozestavení podle mapování z klasické výchozí šachové pozice (specifikace v kódu `SetupPresets`).
6. Po dokončení klikněte na **Hotovo** na řadě, která je na tahu — nebo **Ctrl+Enter**.

**Nastavení (menu):** pro každou stranu (**Gold hráč**, **Silver hráč**) lze zvolit **člověka**, **počítač (úroveň 0–2)** nebo **protihráče po síti**. Pro lokální hru dvou lidí na jednom PC nechte obě strany jako člověk.

---

## 5. Průběh partie (PLAY)

- Klikání na figury a zvýrazněná cílová pole odpovídá **legálním** krokům, tahům a tlačením.
- **Enter** — odeslání celého tahu (**1–4 kroky** musí být vyčerpány v souladu s pravidly).
- **Esc** — zrušení rozpracovaného tahu (draft).

**Časovač:** v PLAY se zobrazuje souhrnný čas přemýšlení (Gold/Silver) — viz panel v UI (sloupce Celkem, průměr na tah).

**Historie tahů:** seznam s oficiální notací; jděte zpět/vpřed přes menu **Tah** nebo navigaci v seznamu; **Page Up / Down** — posun výběru po stránce (u klienta v síti může být omezeno).

**Uložení / načtení:** menu **Hra → Uložit / Načíst** — textový formát stavu partie včetně historie (viz technická dokumentace).

---

## 6. Klávesové zkratky

### Hra (PLAY)

| Klávesa | Účinek |
|---------|--------|
| **Enter** | Ukončit a potvrdit tah |
| **Esc** | Zrušit rozpracovaný tah |
| **Tab** / **Shift+Tab** | Cyklus cílů: nejdříve tahnutí (pull), pak varianty tlačení (push), jinak vlastní figury |
| **Ctrl+Tab** / **Ctrl+Shift+Tab** | **Jen vlastní figury** (např. při tlačení) |
| **Page Up** / **Page Down** | Historie tahů — posun výběru po stránce |
| **Mezerník** | Tahnutí nebo tlačení podle žlutého **fokusu** z Tabu; bez fokusu nejdřív pull, jinak push |
| **Šipky** / **WASD** | Pohyb kurzoru po šachovnici po výběru figury |

### Rozestavení (SETUP)

| Klávesa | Účinek |
|---------|--------|
| **Mezerník** | Náhodné rozestavení / doplnění (jako tlačítko) |
| **Ctrl+Mezerník** | Šachové rozestavení |
| **Ctrl+Enter** | Dokončit rozestavení (Hotovo) |
| **Mezerník** (je-li na tahu počítač) | Pauza automatického přehrávání tahu počítače |

Menu **Hra** obsahuje zkratky pro novou hru, uložení, načtení, ukončení; menu **Tah** — zpět / vpřed.

---

## 7. Počítačový soupeř — úrovně

V menu **Nastavení** nastavte u **Gold** nebo **Silver** položku **počítač — úroveň 0 / 1 / 2**.

| Úroveň | Chování |
|--------|--------|
| **0** | Náhodný **legální celý tah** (1–4 kroky); preferuje tahy bez okamžité ztráty figurky pastí; po mnoha pokusech vezme libovolný legální tah |
| **1** | „Greedy“: v časovém limitu **vzorkuje** náhodné legální tahy, ohodnocuje je **statickou heuristikou** koncové pozice, bere nejlepší |
| **2** | **Alfa-beta** minimax přes **celé tahy** s omezenou hloubkou a pevným časovým rozpočtem (aby UI nezamrzlo) |

**Posuvník „Pauza tahu počítače na krok“** — pouze **vizuální zpomalení** animace kroků; nesouvisí s hloubkou prohledávání.

---

## 8. Hra po síti (TCP)

Protokol: **NDJSON** přes **UTF-8**, synchronizace stavu autoritativně u **hostitele**. Port a formát detailů viz technická dokumentace / kód `ArimaaNetworkCoordinator`.

### 8.1 Hostitel (server)

1. V menu **Síť** zvolte **Hostovat…**.
2. Ve dialogu nastavte **port** (výchozí **7788**, musí být **> 1024** a ≤ 65535).
3. Aplikace zobrazí vaše **lokální IPv4 adresy** — sdělte je soupeři (spolu s portem).
4. Host je v naší konvenci **Gold**, klient **Silver**.
5. Nastavení **člověk vs. počítač** pro Gold dělejte v **Nastavení → Gold hráč** před/po připojení dle potřeby (viz text dialogu).

**Firewall:** na hostiteli povolte příchozí TCP na zvoleném portu.

### 8.2 Klient

1. **Síť → Připojit se…**
2. **Host:** IP adresa nebo hostname hostitele (např. `192.168.0.10` nebo veřejná IP; lokálně `localhost`).
3. **Port:** stejný jako u hostitele (výchozí **7788**).
4. Klient hraje za **Silver** — nastavení **Nastavení → Silver hráč** (člověk / počítač / síť se u klienta netýká „síťového peer“ pro vlastní sedadlo ve stejném smyslu jako druhá strana).

### 8.3 Odpojení

**Síť → Odpojit** ukončí lokální relaci; druhá strana může obdržet chybu spojení. Položka je aktivní po úspěšném hostování nebo připojení.

---

## 9. Zvuky a výherní video

- **Nastavení → Zvuky tahů (syntéza)** — krátké zvuky kroků a pastí (procedurální).
- Po **výhře** — pokud je tato položka zapnutá — aplikace hledá soubory **`assets/gold-victory.mp4`** a **`assets/silver-victory.mp4`** (viz také `target/assets/`, cesty v dokumentaci k `PlayVictoryMediaSfx`).
- **Doporučený formát:** **H.264 + AAC** v MP4 (zvuk **MP3** uvnitř MP4 často na Windows s JavaFX **nepřehraje**).

---

## 10. Kde hledat další nápovědu v aplikaci

Menu **Nápověda** — **Pravidla**, **Ovládání**, **O počítačovém soupeři**, dialog **O programu**.

Verze uvedená v aplikaci odpovídá `HelpCzechTexts.FALLBACK_APP_VERSION` / manifestu (aktuálně směrováno na vývojovou verzi 0.9.24).

---

*Tento manuál doplňuje technickou dokumentaci a diagramy v [`Dokumentace/puml/`](puml/) (PlantUML, obrázky v [`puml/out/`](puml/out/)).*
