package uskoag.gservices.gdocsmon;

import static luvjfx.Fx.*;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * The one window. Top: Claude sessions asking to attach / attached, with their mode. Middle: comments waiting
 * for approval. Bottom: what is watched, and the log. F-keys only, since nothing here is typed into.
 */
final class MonWindow {

    private static final String HEAD = "-fx-font-weight: bold; -fx-font-size: 13;";

    static Stage stage;
    private static ListView<Att> sessions;
    private static ListView<Ev> pending;
    private static ListView<String> watches, log;
    private static TextArea detail;
    private static ChoiceBox<String> mode;
    private static ChoiceBox<Integer> hours;

    private MonWindow() {}

    static void build(Stage s) {
        stage = s;
        var sv = listView(Att.class).prefHeight(110);
        sessions = sv.node;
        sessions.setCellFactory(v -> cell(Att::line));
        sessions.setPlaceholder(new Label("No Claude session has asked to listen to a document."));
        var p = listView(Ev.class).prefHeight(130);
        pending = p.node;
        pending.setCellFactory(v -> cell(e -> e.line() + (e.expires > 0 ? "  (times out " + Home.stamp(e.expires) + ")" : "")));
        pending.setPlaceholder(new Label("Nothing is waiting for approval."));
        pending.getSelectionModel().selectedItemProperty().addListener((o, a, e) -> detail.setText(e == null ? "" : e.render()));
        var d = textArea().editable(false).wrapText(true).prefHeight(120);
        detail = d.node;
        var w = listView(String.class).prefHeight(60);
        watches = w.node;
        var l = listView(String.class);
        log = l.node;
        VBox.setVgrow(log, Priority.ALWAYS);
        var m = choiceBox(String.class);
        mode = m.node;
        mode.getItems().setAll(Att.ASK, Att.AUTO, Att.INFORM);
        mode.setValue(Att.ASK);
        var h = choiceBox(Integer.class);
        hours = h.node;
        hours.getItems().setAll(1, 2, 4, 8, 24);
        hours.setValue(4);
        var root = vbox().spacing(6).padding(12).nodes(
                label("Claude sessions").style(HEAD), sv,
                hbox().spacing(8).nodes(label("attach for"), h, label("hours, mode"), m,
                        button("F9 Approve attach").attr(b -> b.setOnAction(e -> attach(true))),
                        button("F10 Deny / detach").attr(b -> b.setOnAction(e -> attach(false))),
                        button("F6 AUTO 1h").attr(b -> b.setOnAction(e -> mode(Att.AUTO))),
                        button("F7 ASK").attr(b -> b.setOnAction(e -> mode(Att.ASK))),
                        button("F11 INFO only").attr(b -> b.setOnAction(e -> mode(Att.INFORM)))),
                label("Comments waiting for your approval").style(HEAD), p, d,
                hbox().spacing(8).nodes(button("F5 Approve").attr(b -> b.setOnAction(e -> decide(true))), button("F8 Deny").attr(b -> b.setOnAction(e -> decide(false)))),
                label("Watching").style(HEAD), w,
                label("Log").style(HEAD), l,
                label("F9 approve attach · F10 deny/detach · F6 AUTO · F7 ASK · F11 INFO only · F5 approve comment · F8 deny comment · Esc hide").style("-fx-text-fill: #555;"));
        var scene = scene(root, 1000, 760);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, k -> {
            switch (k.getCode()) {
                case F5 -> decide(true);
                case F8 -> decide(false);
                case F9 -> attach(true);
                case F10 -> attach(false);
                case F6 -> mode(Att.AUTO);
                case F7 -> mode(Att.ASK);
                case F11 -> mode(Att.INFORM);
                case ESCAPE -> stage.hide();
                default -> { return; }
            }
            k.consume();
        });
        stage.setScene(scene);
        stage.setTitle("gdocs monitor");
        stage.getIcons().add(fxImage(Tray.image(false)));
        stage.setOnCloseRequest(e -> { e.consume(); stage.hide(); });
    }

    private static <T> ListCell<T> cell(java.util.function.Function<T, String> text) {
        return new ListCell<>() {
            @Override protected void updateItem(T t, boolean empty) { super.updateItem(t, empty); setText(empty || t == null ? null : text.apply(t)); }
        };
    }

    private static Att selectedAtt() {
        var a = sessions.getSelectionModel().getSelectedItem();
        if (a == null) for (var x : sessions.getItems()) if (x.status.equals(Att.PENDING)) return x;
        return a;
    }

    private static void attach(boolean yes) {
        var a = selectedAtt();
        if (a == null) return;
        if (a.status.equals(Att.PENDING)) State.decideAttach(a, yes, mode.getValue(), hours.getValue() * 60);
        else if (!yes) State.detach(a, "detached by the owner");
    }

    private static void mode(String m) {
        var a = selectedAtt();
        if (a != null) State.setMode(a, m);
    }

    private static void decide(boolean yes) {
        var e = pending.getSelectionModel().getSelectedItem();
        if (e == null && !pending.getItems().isEmpty()) e = pending.getItems().get(0);
        if (e != null) State.decide(e, yes);
    }

    private static javafx.scene.image.Image fxImage(java.awt.image.BufferedImage b) {
        var img = new javafx.scene.image.WritableImage(b.getWidth(), b.getHeight());
        var pw = img.getPixelWriter();
        for (var y = 0; y < b.getHeight(); y++) for (var x = 0; x < b.getWidth(); x++) pw.setArgb(x, y, b.getRGB(x, y));
        return img;
    }

    static void show() { stage.show(); stage.setIconified(false); stage.toFront(); stage.requestFocus(); }

    static void refresh() {
        Platform.runLater(() -> {
            var selA = sessions.getSelectionModel().getSelectedItem();
            var atts = State.attList().stream().filter(a -> !a.status.equals(Att.ENDED) && !a.status.equals(Att.DENIED)).toList();
            sessions.getItems().setAll(atts);
            if (selA != null && atts.contains(selA)) sessions.getSelectionModel().select(selA);
            else if (!atts.isEmpty()) sessions.getSelectionModel().select(0);
            var sel = pending.getSelectionModel().getSelectedItem();
            var list = State.byStatus(Ev.PENDING);
            pending.getItems().setAll(list);
            if (sel != null && list.contains(sel)) pending.getSelectionModel().select(sel);
            else if (!list.isEmpty()) pending.getSelectionModel().select(0);
            else detail.setText("");
            watches.getItems().setAll(State.watchList().stream().map(Watch::line).toList());
            log.getItems().setAll(State.logList());
            var asking = list.size() + atts.stream().filter(a -> a.status.equals(Att.PENDING)).count();
            Tray.pending(asking > 0, "gdocs monitor — " + asking + " waiting, " + atts.size() + " session(s), " + State.watches.size() + " watched");
        });
    }
}
