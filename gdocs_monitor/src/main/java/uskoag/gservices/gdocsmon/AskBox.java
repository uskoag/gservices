package uskoag.gservices.gdocsmon;

import static luvjfx.Fx.*;

import java.util.*;
import javafx.application.Platform;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/**
 * The approval pop-up: one small always-on-top box per thing that needs the owner (an attachment request or
 * an ASK-mode comment), because Windows will not let a background app bring its main window forward, so a row
 * in a list goes unseen. Enter/F9 approves, Esc/F10 denies. The box closes itself when the item is settled elsewhere.
 */
final class AskBox {

    private static final Map<String, Stage> open = new HashMap<>();

    private AskBox() {}

    static void attach(Att a) {
        Platform.runLater(() -> {
            if (open.containsKey(a.id)) return;
            var mode = choiceBox(String.class);
            mode.node.getItems().setAll(Att.ASK, Att.AUTO, Att.INFORM);
            mode.node.setValue(Att.ASK);
            var hours = choiceBox(Integer.class);
            hours.node.getItems().setAll(1, 2, 4, 8, 24);
            hours.node.setValue(4);
            var body = "A Claude Code session asks to LISTEN to a Google Doc and receive its comments.\n\n"
                    + "session: " + a.session + "\nfolder: " + a.cwd + "\ndocument: " + a.doc + "\n\n"
                    + "ASK = each comment asks you first · AUTO = Claude acts on your comments (max 1 hour) · INFORM = Claude is told, must not act";
            show(a.id, "Claude session wants to listen to a document", body,
                    hbox().spacing(8).nodes(label("for"), hours, label("hours, mode"), mode),
                    () -> State.decideAttach(a, true, mode.node.getValue(), hours.node.getValue() * 60),
                    () -> State.decideAttach(a, false, null, 0));
        });
    }

    static void event(Ev e) {
        Platform.runLater(() -> {
            if (open.containsKey(e.id)) return;
            var body = (e.owner ? "Your comment" : "A comment by " + e.author + " (NOT the owner)") + " in document " + e.doc + ":\n\n\"" + Ev.clip(e.text, 400) + "\"\n\n"
                    + "Approve = the attached Claude session acts on it. No answer by " + Home.stamp(e.expires) + " = nothing happens.";
            show(e.id, "Let Claude act on this comment?", body, null, () -> State.decide(e, true), () -> State.decide(e, false));
        });
    }

    /** Called on every state change: closes boxes whose item was settled elsewhere (tray window, timeout). */
    static void sweep() {
        Platform.runLater(() -> {
            for (var id : new ArrayList<>(open.keySet())) {
                var still = State.attList().stream().anyMatch(a -> a.id.equals(id) && a.status.equals(Att.PENDING))
                        || State.byStatus(Ev.PENDING).stream().anyMatch(e -> e.id.equals(id));
                if (!still) open.remove(id).close();
            }
        });
    }

    private static void show(String id, String title, String body, luvjfx.FxHBox extra, Runnable yes, Runnable no) {
        var s = new Stage();
        var root = vbox().spacing(12).padding(18).nodes(
                label(title).style("-fx-font-weight: bold; -fx-font-size: 15;"),
                label(body).wrapText(true).maxWidth(560),
                hbox().spacing(10).nodes(
                        button("Approve (Enter)").defaultButton(true).attr(b -> b.setOnAction(x -> { yes.run(); s.close(); })),
                        button("Deny (Esc)").attr(b -> b.setOnAction(x -> { no.run(); s.close(); }))));
        if (extra != null) root.node.getChildren().add(2, extra.node);
        var scene = scene(root, 600, extra == null ? 260 : 320);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, k -> {
            switch (k.getCode()) {
                case F9 -> { yes.run(); s.close(); }
                case ESCAPE, F10 -> { no.run(); s.close(); }
                default -> { return; }
            }
            k.consume();
        });
        s.setScene(scene);
        s.setTitle("gdocs monitor — approval needed");
        s.setAlwaysOnTop(true);
        s.setOnHidden(x -> open.remove(id));
        open.put(id, s);
        s.show();
        s.toFront();
        Tray.message("gdocs monitor — approval needed", title);
    }
}
