package cz.cvut.fel.pjv.arimaa.ui.help;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

/** Builds the menu „Nápověda“ and wires actions to help dialogs. */
public final class HelpMenuFactory {

    private HelpMenuFactory() {}

    /**
     * @param ownerWindow parent for modal dialogs (e.g. primary {@code Stage}); may be {@code null}
     */
    public static Menu buildMenu(Window ownerWindow) {
        Menu menu = new Menu("N_ápověda");
        menu.setMnemonicParsing(true);

        MenuItem pravidla = new MenuItem("Pravidla hry");
        pravidla.setOnAction(e -> HelpScrollableDialog.show(ownerWindow, HelpCzechTexts.TITLE_PRAVIDLA, HelpCzechTexts.BODY_PRAVIDLA));

        MenuItem ovladani = new MenuItem("Ovládání");
        ovladani.setOnAction(e -> HelpScrollableDialog.show(ownerWindow, HelpCzechTexts.TITLE_OVLADANI, HelpCzechTexts.BODY_OVLADANI));

        MenuItem cpu = new MenuItem("O počítačovém soupeři");
        cpu.setOnAction(e -> HelpScrollableDialog.show(ownerWindow, HelpCzechTexts.TITLE_CPU, HelpCzechTexts.BODY_CPU));

        MenuItem about = new MenuItem("O programu…");
        about.setOnAction(e -> AboutProgramDialog.show(ownerWindow));

        menu.getItems().addAll(pravidla, ovladani, cpu, about);
        return menu;
    }
}
