package uskoag.wallet.ui;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import uskoag.wallet.daemon.AccountVerbs;
import uskoag.wallet.daemon.WalletCore;
import uskoag.wallet.wire.Asks;

import java.nio.file.Files;
import java.util.List;

import static luvjfx.Fx.button;
import static luvjfx.Fx.hbox;
import static luvjfx.Fx.label;
import static luvjfx.Fx.listView;
import static luvjfx.Fx.textField;
import static luvjfx.Fx.vbox;

/**
 * The inventory, at the level it actually has: an OAuth client per organisation, and a token per
 * account underneath it.
 *
 * <p>Tools do not appear here at all, which is the point — one consent covers every tool for an
 * account, and which tools are ready is a consequence of the scopes rather than a thing to manage.
 */
public final class AccountsPane {

    private AccountsPane() {
    }

    public static Node build(WalletCore core) {
        var verbs = new AccountVerbs(core);
        var list = listView(String.class);
        var org = textField().promptText("org id, e.g. uskf");
        var email = textField().promptText("someone@uskfoundation.or.ke");
        var status = label("");

        Runnable refresh = () -> {
            var lines = new java.util.ArrayList<String>();
            for (var o : core.orgs()) {
                lines.add("ORG  " + o.id() + "   " + String.join(", ", o.domains())
                        + "   " + o.accounts() + " account(s)");
                core.accounts().stream().filter(a -> o.id().equalsIgnoreCase(a.org())).forEach(a ->
                        lines.add("      " + (a.hasRefreshToken() ? "[ready] " : "[no token] ") + a.email()
                                + "   " + a.scopes().size() + " scope(s)   ready for: "
                                + (a.readyProfiles().isEmpty() ? "-" : String.join(", ", a.readyProfiles()))));
            }
            var orphans = core.accounts().stream()
                    .filter(a -> core.orgs().stream().noneMatch(o -> o.id().equalsIgnoreCase(a.org()))).toList();
            orphans.forEach(a -> lines.add("      (no org) " + a.email()));
            if (lines.isEmpty()) lines.add("Nothing yet. Add an org's credentials.json, then log in an account.");
            ((ListView<String>) list.node).setItems(FXCollections.observableArrayList(lines));
        };
        refresh.run();

        var upload = button("Add org credentials.json");
        var login = button("Log in account");
        var forget = button("Forget account");
        var reload = button("Refresh");

        upload.attr(b -> b.setOnAction(e -> {
            var id = ((TextField) org.node).getText().trim();
            if (id.isEmpty()) {
                status.text("Give the org a short id first, e.g. uskf.");
                return;
            }
            var chooser = new FileChooser();
            chooser.setTitle("credentials.json for org " + id);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("credentials.json", "*.json"));
            var file = chooser.showOpenDialog(list.node.getScene().getWindow());
            if (file == null) return;
            try {
                var domain = domainOf(((TextField) email.node).getText().trim());
                verbs.addOrg(new Asks.AddOrg(id, id, Files.readString(file.toPath()),
                        domain == null ? List.of() : List.of(domain)));
                status.text("Stored. Now type an email and use 'Log in account'.");
                refresh.run();
            } catch (Exception ex) {
                status.text(String.valueOf(ex.getMessage()));
            }
        }));

        login.attr(b -> b.setOnAction(e -> {
            var who = ((TextField) email.node).getText().trim();
            var id = ((TextField) org.node).getText().trim();
            if (who.isEmpty()) {
                status.text("Type the account email first.");
                return;
            }
            status.text("A browser window will open. Consent there, then come back.");
            new Thread(() -> {
                try {
                    verbs.login(new Asks.Login(who, id.isEmpty() ? null : id, null, 8888));
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
                verbs.forget(new Asks.Forget(((TextField) email.node).getText().trim()));
                refresh.run();
            } catch (Exception ex) {
                status.text(String.valueOf(ex.getMessage()));
            }
        }));
        reload.attr(b -> b.setOnAction(e -> refresh.run()));

        return vbox().spacing(8).padding(12).nodes(
                label("Organisations and accounts").style("-fx-font-weight: bold;"),
                label("One credentials.json per org - that is what an OAuth client is. Every account and"
                        + " every tool in the org uses it.").style("-fx-font-size: 11px; -fx-text-fill: #666;"),
                list,
                hbox().spacing(6).nodes(label("org"), org, label("email"), email),
                hbox().spacing(6).nodes(upload, login, forget, reload),
                status.wrapText(true).style("-fx-text-fill: #1b5e20;")).node;
    }

    private static String domainOf(String email) {
        var at = email == null ? -1 : email.indexOf('@');
        return at < 0 ? null : email.substring(at + 1).toLowerCase();
    }
}
