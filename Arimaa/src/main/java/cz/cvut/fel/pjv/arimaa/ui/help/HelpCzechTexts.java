package cz.cvut.fel.pjv.arimaa.ui.help;

/**
 * Czech copy for the Nápověda menu (rules, controls, CPU description). No JavaFX types.
 */
public final class HelpCzechTexts {

    private HelpCzechTexts() {}

    /** Fallback when JAR manifest has no {@code Implementation-Version} (e.g. IDE run). */
    public static final String FALLBACK_APP_VERSION = "0.9.22";

    public static final String TITLE_PRAVIDLA = "Pravidla hry";
    public static final String TITLE_OVLADANI = "Ovládání";
    public static final String TITLE_CPU = "O počítačovém soupeři";

    public static final String BODY_PRAVIDLA =
            """
                    Arimaa je abstraktní strategická hra pro dva hráče (zlatý a stříbrný).

                    Cíl: dostat králíka (typ figury) na poslední řádek soupeře (domovská řada soupeře).

                    Fáze rozestavení (SETUP): hráči střídavě umisťují figury na svou domovskou řadu podle pravidel \
                    kapacity a typů. Potvrdíte dokončení rozestavení (tlačítko v panelu).

                    Fáze hry (PLAY): v každém tahu máte až čtyři kroky. Figury se pohybují ortogonálně \
                    (ne diagonálně). Silnější figury mohou slabší tlačit nebo tahat podle pravidel Arimaa.

                    Pasti: pole označená jako past — pokud na něm stojí vaše figura a nemáte vedle \
                    spojeneckou figuru, figura je odstraněna z desky.

                    Tato aplikace je studentská implementace; pro úplné oficiální znění pravidel \
                    použijte zdroje komunity Arimaa (arimaa.com apod.).
                    """
                    .stripTrailing();

    public static final String BODY_OVLADANI =
            """
                    Obecně: klikněte na buňku šachovnice. V rozestavení vybíráte figuru z rezervy a umísťujete \
                    ji na domovské pole, nebo berete figuru z desky zpět do rezervy.

                    Hra (PLAY) — myš:
                    • Klik na vlastní figuru začne tah (výběr výchozího pole).
                    • Další klikačné cíle odpovídají legálním krokům / tahání / tlačení (zvýraznění na desce).

                    Hra (PLAY) — klávesnice (když není fokus v editovatelném textovém poli):
                    • Enter — ukončit tah (potvrdit celý tah).
                    • Esc — zrušit rozpracovaný tah (draft).
                    • Tab / Shift+Tab — jeden cyklus cílů: nejdřív všechna tahnutí (pull), pak každá legální varianta tlačení (i na stejnou buňku z jiné soupeřovy figury), jinak vlastní figury.
                    • Ctrl+Tab / Ctrl+Shift+Tab — vždy jen vlastní figury (např. při tlačení).
                    • Mezerník — tahnutí nebo tlačení podle žlutého fokusu z Tabu (u tlačení tlustý rámeček na cílovém poli, tenčí na tlačené figuře); bez fokusu nejdřív pull, jinak push.
                    • Šipky nebo WASD — pohyb po šachovnici po výběru figury.

                    Rozestavení (SETUP) — klávesnice:
                    • Mezerník — náhodné rozestavení (jako tlačítko pro náhodu).
                    • Ctrl+Mezerník — šachové rozestavení (mapování z šachové výchozí pozice).
                    • Ctrl+Enter — dokončit rozestavení (jako tlačítko Hotovo).
                    • Je-li na tahu počítač: Mezerník přepíná pauzu automatického přehrávání tahu.

                    Menu Hra: zkratky Nová hra, Uložit, Načíst, Ukončit. Menu Tah: Zpět / Vpřed.
                    """
                    .stripTrailing();

    public static final String BODY_CPU =
            """
                    V menu Gameplay můžete pro zlatého a stříbrného hráče zvolit člověka nebo počítač — úroveň 0, 1 nebo 2.

                    Úroveň 0: náhodný výběr legálního celého tahu. Program se snaží upřednostnit tahy, které \
                    hned neztratí vlastní figuru pastí; po několika desítkách pokusů vezme libovolný legální tah.

                    Úroveň 1 („greedy“): v časovém limitu náhodně vzorkuje legální celé tahy, každý ohodnotí \
                    statickou heuristikou pozice a vybere nejlepší (při shodě náhodná volba). Nevyčísluje všechny \
                    tahy — v hustých pozicích to drží odezvu rozumnou.

                    Úroveň 2: minimax s ořezáváním alfa-beta přes celé tahy (ne jen jednotlivé kroky). \
                    Maximální hloubka je omezená (typicky dva plné tahy od kořene; při velmi mnoha legálních \
                    tazích se hloubka sníží). Je nastavený pevný časový rozpočet na jeden tah, aby UI nezamrzlo.

                    Posuvník „Pauza tahu počítače na krok“ v menu Gameplay pouze zpomaluje animaci kroků tahu \
                    počítače; nesouvisí s hloubkou hledání úrovní 1 a 2.
                    """
                    .stripTrailing();
}
