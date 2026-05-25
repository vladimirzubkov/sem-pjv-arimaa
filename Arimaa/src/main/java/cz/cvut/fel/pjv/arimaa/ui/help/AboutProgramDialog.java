package cz.cvut.fel.pjv.arimaa.ui.help;

import cz.cvut.fel.pjv.arimaa.AppVersion;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Worker;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;

/** Modal „O programu“: version / technical text on the left, embedded YouTube on the right. */
public final class AboutProgramDialog {

    private static final String YOUTUBE_VIDEO_ID = "O60xU-PAWys";

    /** Share link with {@code si=} (tracking); same video as embed. */
    private static final String YOUTUBE_SHORTS_SHARE_URL =
            "https://youtube.com/shorts/" + YOUTUBE_VIDEO_ID + "?si=nPcyeHmX-AO9QkPX";

    /**
     * Top-level navigation to the embed page (not {@code loadContent} / {@code srcdoc}) so WebKit sends a normal
     * referrer chain; {@code loadContent} wrappers with nested iframes often still produce YouTube error 153 (see e.g.
     * Simon Willison, \"fixing-153-embed\").
     */
    private static final String YOUTUBE_EMBED_TOP_URL =
            "https://www.youtube-nocookie.com/embed/"
                    + YOUTUBE_VIDEO_ID
                    + "?rel=0&modestbranding=1&playsinline=1&enablejsapi=1"
                    + "&origin=https://www.youtube-nocookie.com";

    /**
     * YouTube expects a modern browser UA; default WebKit reports {@code JavaFX/… Safari/…} and the iframe player may
     * show error 153 even when {@link Worker.State#SUCCEEDED}.
     */
    private static final String WEBVIEW_CHROME_LIKE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36";

    private static final String READABLE_TEXT_STYLE = "-fx-text-fill: #141414;";

    private AboutProgramDialog() {}

    /* Same frameless ScrollPane chrome removal as other help dialogs (transparent track). */
    private static void styleFramelessScroll(ScrollPane scroll) {
        scroll.setStyle(
                "-fx-background-color: transparent; -fx-background: transparent; "
                        + "-fx-border-color: transparent; -fx-padding: 0;");
    }

    /* Loads YouTube embed URL in WebView with a Chrome-like UA (mitigates WebKit embed error 153). */
    private static void applyYoutubeEmbedInWebView(WebEngine engine) {
        engine.setUserAgent(WEBVIEW_CHROME_LIKE_USER_AGENT);
        engine.load(YOUTUBE_EMBED_TOP_URL);
    }

    /* Fallback: full Shorts page load when the compact embed still fails in JavaFX WebKit. */
    private static void applyYoutubeShortsPageInWebView(WebEngine engine) {
        engine.setUserAgent(WEBVIEW_CHROME_LIKE_USER_AGENT);
        engine.load(YOUTUBE_SHORTS_SHARE_URL);
    }

    /* Cancels load worker and navigates to about:blank so video/audio stops when the About dialog closes. */
    private static void unloadWebEngine(WebEngine engine) {
        if (engine == null) {
            return;
        }
        try {
            engine.getLoadWorker().cancel();
        } catch (Exception ignored) {
            // ignore
        }
        try {
            engine.load("about:blank");
        } catch (Exception ignored) {
            // ignore
        }
    }

    /* Opens the Shorts share URL in the desktop browser (hyperlink fallback). */
    private static void openShortsInSystemBrowser() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                if (d.isSupported(Desktop.Action.BROWSE)) {
                    d.browse(URI.create(YOUTUBE_SHORTS_SHARE_URL));
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    /**
     * @param owner used for {@link Stage#initOwner(Window)}; may be {@code null}
     */
    public static void show(Window owner) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("O programu");

        String versionLine = resolveImplementationVersionLine();

        Label headline = new Label("Arimaa");
        headline.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;" + READABLE_TEXT_STYLE);
        headline.setWrapText(false);
        headline.setTextOverrun(OverrunStyle.CLIP);
        headline.setMaxWidth(Double.MAX_VALUE);

        Label blurb =
                new Label(
                        "Semestrální práce — předmět B0B36PJV (Programování v Javě), FEL ČVUT.\n"
                                + "Grafické rozhraní: JavaFX.");
        blurb.setWrapText(true);
        blurb.setTextOverrun(OverrunStyle.CLIP);
        blurb.setStyle(READABLE_TEXT_STYLE);

        Label tech = new Label(buildTechnicalBlock(versionLine));
        tech.setWrapText(true);
        tech.setTextOverrun(OverrunStyle.CLIP);
        tech.setFont(Font.font("Consolas", 12));
        tech.setStyle(READABLE_TEXT_STYLE);

        ScrollPane techScroll = new ScrollPane(tech);
        techScroll.setFitToWidth(true);
        styleFramelessScroll(techScroll);
        tech.maxWidthProperty()
                .bind(
                        Bindings.createDoubleBinding(
                                () -> {
                                    Bounds vb = techScroll.getViewportBounds();
                                    double vw =
                                            vb != null && vb.getWidth() > 0
                                                    ? vb.getWidth()
                                                    : techScroll.getWidth();
                                    return Math.max(0, vw - 6);
                                },
                                techScroll.viewportBoundsProperty(),
                                techScroll.widthProperty()));

        VBox left = new VBox(12, headline, blurb, techScroll);
        left.setPadding(new Insets(0, 8, 0, 0));
        left.setMinWidth(260);
        left.setPrefWidth(340);
        VBox.setVgrow(techScroll, Priority.ALWAYS);

        Label videoCaption = new Label("Zdroj vítězného videa");
        videoCaption.setStyle("-fx-font-weight: bold;" + READABLE_TEXT_STYLE);

        WebView webView = new WebView();
        webView.setPrefSize(420, 315);
        WebEngine videoEngine = webView.getEngine();

        Label loadingOverlay = new Label("Načítám YouTube…");
        loadingOverlay.setStyle(
                "-fx-text-fill: #222; -fx-background-color: rgba(255,255,255,0.94); -fx-padding: 12 16;");
        StackPane webStack = new StackPane(webView, loadingOverlay);
        StackPane.setAlignment(loadingOverlay, Pos.CENTER);
        VBox.setVgrow(webStack, Priority.ALWAYS);

        videoEngine
                .getLoadWorker()
                .stateProperty()
                .addListener(
                        (obs, prev, state) -> {
                            if (state == Worker.State.SUCCEEDED || state == Worker.State.FAILED) {
                                loadingOverlay.setVisible(false);
                                loadingOverlay.setManaged(false);
                            }
                        });

        Button reloadShortsPage = new Button("Načíst Shorts znovu");
        reloadShortsPage.setTooltip(
                new Tooltip(
                        "Výchozí režim: celá stránka YouTube Shorts v WebView — v JavaFX WebKit obvykle spolehlivější než kompaktní embed."));
        reloadShortsPage.setOnAction(e -> applyYoutubeShortsPageInWebView(videoEngine));

        Button reloadEmbed = new Button("Kompaktní embed");
        reloadEmbed.setTooltip(
                new Tooltip(
                        "Stránka youtube-nocookie.com/embed — může zobrazit chybu 153 v některých verzích WebKit."));
        reloadEmbed.setOnAction(e -> applyYoutubeEmbedInWebView(videoEngine));

        Button stopVideo = new Button("Zastavit přehrávání");
        stopVideo.setOnAction(
                e -> {
                    unloadWebEngine(videoEngine);
                    loadingOverlay.setVisible(true);
                    loadingOverlay.setManaged(true);
                });

        Hyperlink openInBrowser = new Hyperlink("Otevřít video v prohlížeči");
        openInBrowser.setOnAction(e -> openShortsInSystemBrowser());

        HBox videoButtons = new HBox(8, reloadShortsPage, reloadEmbed, stopVideo);
        videoButtons.setAlignment(Pos.CENTER_LEFT);

        VBox right = new VBox(8, videoCaption, webStack, videoButtons, openInBrowser);
        right.setPadding(new Insets(0, 0, 0, 8));

        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.45);
        blurb.maxWidthProperty()
                .bind(
                        Bindings.createDoubleBinding(
                                () -> {
                                    double w = split.getWidth();
                                    if (w <= 0) {
                                        return 320.0;
                                    }
                                    double pos =
                                            split.getDividerPositions().length > 0
                                                    ? split.getDividerPositions()[0]
                                                    : 0.45;
                                    return Math.max(
                                            120.0,
                                            w * pos - left.getPadding().getLeft() - left.getPadding().getRight() - 4.0);
                                },
                                split.widthProperty(),
                                split.getDividers().get(0).positionProperty()));

        Button close = new Button("Zavřít");
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.close());

        VBox root = new VBox(12, split, close);
        root.setPadding(new Insets(14));
        VBox.setVgrow(split, Priority.ALWAYS);
        close.setMaxWidth(Double.MAX_VALUE);
        root.setAlignment(Pos.TOP_LEFT);

        /* Single unload path avoids duplicate about:blank loads (see prior debug session H7 + close). */
        stage.setOnHidden(ev -> unloadWebEngine(videoEngine));

        Scene scene = new Scene(root, 880, 460);
        stage.setScene(scene);
        /* Default: full Shorts page — reliable in JavaFX WebKit; embed is optional (may show error 153). */
        Platform.runLater(() -> applyYoutubeShortsPageInWebView(videoEngine));
        stage.showAndWait();
    }

    /* First line of the left column: nearest Git tag from build or {@code git describe} (Czech). */
    private static String resolveImplementationVersionLine() {
        return AppVersion.aboutVersionLine();
    }

    /* Long Czech “runtime / stack” blurb for the About dialog (Java, JavaFX, SLF4J, Jackson, Batik, Maven, video URLs). */
    private static String buildTechnicalBlock(String versionBlock) {
        String javafx = System.getProperty("javafx.version");
        String javafxLine =
                (javafx != null && !javafx.isBlank())
                        ? ("JavaFX (runtime): " + javafx)
                        : "JavaFX (runtime): vlastnost javafx.version není nastavena";

        String slf4j = safeImplVersion(org.slf4j.Logger.class);
        String jackson = safeImplVersion(com.fasterxml.jackson.databind.ObjectMapper.class);

        return versionBlock
                + "\n\n"
                + "— Runtime / prostředí —\n"
                + "Jazyk: Java\n"
                + "Verze Javy: "
                + System.getProperty("java.version", "?")
                + "\n"
                + "Poskytovatel JRE: "
                + System.getProperty("java.vendor", "?")
                + "\n"
                + "VM: "
                + System.getProperty("java.vm.name", "?")
                + "\n"
                + javafxLine
                + "\n"
                + "Operační systém: "
                + System.getProperty("os.name", "?")
                + "\n"
                + "Architektura: "
                + System.getProperty("os.arch", "?")
                + "\n"
                + "Kódování souborů: "
                + System.getProperty("file.encoding", "?")
                + "\n"
                + "Pracovní adresář: "
                + System.getProperty("user.dir", "?")
                + "\n"
                + "Hlavní třída: cz.cvut.fel.pjv.arimaa.ArimaaApp"
                + "\n\n"
                + "— Technologie / knihovny (projekt) —\n"
                + "• Java SE + JavaFX (GUI, včetně WebView / vestavěný WebKit)\n"
                + "• SLF4J + Logback — logování"
                + (slf4j.isEmpty() ? "\n" : " (verze SLF4J API: " + slf4j + ")\n")
                + "• Jackson (databind) — JSON"
                + (jackson.isEmpty() ? "\n" : " (verze z manifestu: " + jackson + ")\n")
                + "• Apache Batik — rastr SVG figurek na bitmapu\n"
                + "• Maven — sestavení a závislosti\n\n"
                + "— Vložené video (About) —\n"
                + "Výchozí: celá stránka Shorts v WebView — "
                + YOUTUBE_SHORTS_SHARE_URL
                + "\n"
                + "Volitelně: kompaktní embed — "
                + YOUTUBE_EMBED_TOP_URL
                + "\n";
    }

    /* Reads Implementation-Version from a library class Package, or empty when missing (About text). */
    private static String safeImplVersion(Class<?> clazz) {
        try {
            Package p = clazz.getPackage();
            if (p != null && p.getImplementationVersion() != null) {
                return p.getImplementationVersion();
            }
        } catch (Exception ignored) {
            // empty
        }
        return "";
    }
}
