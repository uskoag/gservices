package uskoag.wallet.ui;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import uskoag.wallet.daemon.WalletCore;
import uskoag.wallet.wire.Asks;
import uskoag.wallet.wire.Profiles;

import java.nio.file.Files;

import static luvjfx.Fx.button;
import static luvjfx.Fx.choiceBox;
import static luvjfx.Fx.hbox;
import static luvjfx.Fx.label;
import static luvjfx.Fx.listView;
import static luvjfx.Fx.textField;
import static luvjfx.Fx.vbox;

/**
 * The account inventory, made explicit — which is a usability win quite apart from any security
 * property. Today this is implicit: scattered across directories, discoverable only by listing folders,
 * with login state unknowable because the tokens are encrypted per key.
 */
public final class AccountsPane {

    private AccountsPane() {
    }

    public static Node build(WalletCore core) {
        var list = listView(String.class);
        var email = textField().promptText("someone@example.org");
        var profile = choiceBox(String.class);
        ((ChoiceBox<String>) profile.node).setItems(FXCollections.observableArrayList(Profiles.known()));
        ((ChoiceBox<String>) profile.node).setValue(Profiles.GSHEETS);

        var status = label("");
        Runnable refresh = () -> ((ListView<String>) list.node).setItems(FXCollections.observableArrayList(
                core.accounts().stream()
                        .map(a -> (a.hasRefreshToken() ? "[ready]   " : "[no token] ") + a.email()
                                + "   /   " + a.profile() + "   " + a.scopes().size() + " scope(s)")
                        .toList()));
        refresh.run();

        var upload = button("Upload credentials.json");
        var login = button("Log in (browser consent)");
        var forget = button("Forget");
        var reload = button("Refresh");

        upload.attr(b -> b.setOnAction(e -> {
            var who = ((TextField) email.node).getText().trim();
            var what = ((ChoiceBox<String>) profile.node).getValue();
            if (who.isEmpty()) {
                status.text("Type the account email first.");
                return;
            }
            var chooser = new FileChooser();
            chooser.setTitle("credentials.json for " + who);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("credentials.json", "*.json"));
            var file = chooser.showOpenDialog(list.node.getScene().getWindow());
            if (file == null) return;
            try {
                var json = Files.readString(file.toPath());
                new uskoag.wallet.daemon.AccountVerbs(core).add(new Asks.AddCredentials(who, what, json));
                status.text("Stored. Now use 'Log in' to consent.");
                refresh.run();
            } catch (Exception ex) {
                status.text(String.valueOf(ex.getMessage()));
            }
        }));

        login.attr(b -> b.setOnAction(e -> {
            var who = ((TextField) email.node).getText().trim();
            var what = ((ChoiceBox<String>) profile.node).getValue();
            status.text("A browser window will open. Consent there, then come back.");
            new Thread(() -> {
                try {
                    new uskoag.wallet.daemon.AccountVerbs(core)
                            .login(new Asks.Login(who, what, Profiles.scopes(what), 8888));
                    javafx.application.Platform.runLater(() -> {
                        status.text("Consented.");
                        refresh.run();
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> status.text(String.valueOf(ex.getMessage())));
                }
            }, "wallet-consent").start();
        }));

        forget.attr(b -> b.setOnAction(e -> {
            try {
                new uskoag.wallet.daemon.AccountVerbs(core).forget(new Asks.Forget(
                        ((TextField) email.node).getText().trim(), ((ChoiceBox<String>) profile.node).getValue()));
                refresh.run();
            } catch (Exception ex) {
                status.text(String.valueOf(ex.getMessage()));
            }
        }));

        reload.attr(b -> b.setOnAction(e -> refresh.run()));

        return vbox().spacing(8).padding(12).nodes(
                label("Accounts").style("-fx-font-weight: bold;"),
                list,
                hbox().spacing(6).nodes(label("email"), email, label("profile"), profile),
                hbox().spacing(6).nodes(upload, login, forget, reload),
                status.style("-fx-text-fill: #1b5e20;")).node;
    }
}
