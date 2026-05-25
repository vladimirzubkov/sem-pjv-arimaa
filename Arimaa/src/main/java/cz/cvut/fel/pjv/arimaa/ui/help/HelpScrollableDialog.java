package cz.cvut.fel.pjv.arimaa.ui.help;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Modal window with read-only scrollable text and a close button. */
public final class HelpScrollableDialog {

    private static final String READABLE_TEXT_STYLE = "-fx-text-fill: #141414;";

    private HelpScrollableDialog() {}

    /* Removes ScrollPane chrome so wrapped Label reads like plain text on the dialog background. */
    private static void styleFramelessScroll(ScrollPane scroll) {
        scroll.setStyle(
                "-fx-background-color: transparent; -fx-background: transparent; "
                        + "-fx-border-color: transparent; -fx-padding: 0;");
        scroll.setPannable(false);
    }

    /**
     * Shows a modal dialog with {@code body} in a read-only scrollable {@link Label} (no TextArea chrome).
     *
     * @param owner used for {@link Stage#initOwner(Window)}; may be {@code null}
     */
    public static void show(Window owner, String title, String body) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);

        Label content = new Label(body);
        content.setWrapText(true);
        content.setStyle(READABLE_TEXT_STYLE);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        styleFramelessScroll(scroll);
        content.maxWidthProperty().bind(scroll.widthProperty().subtract(16));

        Button close = new Button("Zavřít");
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.close());

        VBox root = new VBox(10, scroll, close);
        root.setPadding(new Insets(12));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        close.setMaxWidth(Double.MAX_VALUE);
        root.setAlignment(Pos.CENTER);
        VBox.setMargin(close, new Insets(4, 0, 0, 0));

        Scene scene = new Scene(root, 560, 440);
        scene.setFill(Color.rgb(236, 236, 238));
        stage.setScene(scene);
        stage.showAndWait();
    }
}
