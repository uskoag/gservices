package uskoag.wallet.ui;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.FileChooser;
import uskoag.wallet.daemon.AccountVerbs;
import uskoag.wallet.daemon.TokenVerbs;
import uskoag.wallet.daemon.WalletCore;
import uskoag.wallet.wire.Asks;
import uskoag.wallet.wire.TokenInfo;

import java.nio.file.Files;
import java.util.List;

import static luvjfx.Fx.button;
import static luvjfx.Fx.hbox;
import static luvjfx.Fx.label;
import static luvjfx.Fx.textArea;
import static luvjfx.Fx.textField;
import static luvjfx.Fx.vbox;

/**
 * Org, then account, then token — the three levels the model actually has.
 *
 * <p>Labels stay human; the detail panel underneath carries the exact Google scope strings, because the
 * only way to answer "what can this actually do" is to see them, and the only way to audit it elsewhere
 * is to copy them out.
 */
public final class AccountsPane {

    private AccountsPane() {
    }

    public static Node build(WalletCore core) {
        var accounts = new AccountVerbs(core);
        var tokens = new TokenVerbs(core);
        var tree = new TreeView<Row>();
        tree.setShowRoot(false);
        tree.setPrefHeight(300);

        var detail = textArea().promptText("Select a token to see exactly which Google permissions it carries.");
        detail.attr(t -> {
            t.setEditable(false);
            t.setPrefRowCount(7);
            t.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");
        });

        var org = textField().promptText("org id");
        var email = textField().promptText("someone@example.org");
        var status = label("");

        Runnable refresh = () -> {
            var root = new TreeItem<>(new Row("root", null, null, null));
            for (var o : core.orgs()) {
                var orgNode = new TreeItem<>(new Row("org", o.id(),
                        o.id() + "   " + o.label() + "   " + String.join(", ", o.domains()), null));
                orgNode.setExpanded(true);
                for (var a : core.accounts()) {
                    if (!o.id().equalsIgnoreCase(a.org())) continue;
                    var accNode = new TreeItem<>(new Row("account", a.email(), a.email(), null));
                    accNode.setExpanded(true);
                    if (!a.hasAnyToken()) {
                        accNode.getChildren().add(new TreeItem<>(
                                new Row("empty", a.email(), "(no tokens - select and press Grant)", null)));
                    }
                    for (var t : a.tokens()) accNode.getChildren().add(new TreeItem<>(
                            new Row("token", a.email(), describe(t), t)));
                    orgNode.getChildren().add(accNode);
                }
                root.getChildren().add(orgNode);
            }
            tree.setRoot(root);
        };
        refresh.run();

        tree.getSelectionModel().selectedItemProperty().addListener((o, was, is) -> {
            var t = is == null ? null : is.getValue().token();
            if (t == null) {
                ((javafx.scene.control.TextArea) detail.node).clear();
                return;
            }
            ((javafx.scene.control.TextArea) detail.node).setText(
                    t.label() + "\n" + t.detail() + "\n\nGoogle classes this: " + t.tier().label
                            + "\nUsed " + TokenInfo.count(t.useCount()) + " time(s)\n\nScopes:\n  "
                            + String.join("\n  ", t.scopes()));
            ((TextField) email.node).setText(t.account());
        });

        var grant = button("Grant...");
        var remove = button("Remove token");
        var removeUnused = button("Remove unused...");
        var upload = button("Add org credentials.json");
        var copy = button("Copy as TSV");
        var reload = button("Refresh");

        grant.attr(b -> b.setOnAction(e -> {
            var who = ((TextField) email.node).getText().trim();
            if (who.isEmpty()) {
                status.text("Select an account, or type an email.");
                return;
            }
            var chosen = GrantDialog.ask(who);
            if (chosen == null || chosen.isEmpty()) return;
            var orgId = ((TextField) org.node).getText().trim();
            status.text("Consent windows will open, one per group.");
            new Thread(() -> {
                try {
                    accounts.login(new Asks.Login(who, orgId.isEmpty() ? null : orgId, chosen.groups(),
                            "custom", chosen.customScopes(), 8888));
                    javafx.application.Platform.runLater(() -> {
                        status.text("Granted.");
                        refresh.run();
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> status.text(String.valueOf(ex.getMessage())));
                }
            }, "wallet-consent").start();
        }));

        remove.attr(b -> b.setOnAction(e -> {
            var sel = tree.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue().token() == null) {
                status.text("Select a token first.");
                return;
            }
            var t = sel.getValue().token();
            if (!Confirm.ask("Remove token", "Remove " + t.label() + " for " + t.account() + "?\n\n"
                    + "This removes it from the wallet only. Google's grant survives - revoke that at\n"
                    + "myaccount.google.com/permissions if that is what you mean.", "Remove", "Cancel")) return;
            try {
                tokens.remove(new Asks.TokenRef(t.account(), t.group()));
                refresh.run();
            } catch (Exception ex) {
                status.text(String.valueOf(ex.getMessage()));
            }
        }));

        removeUnused.attr(b -> b.setOnAction(e -> {
            var stale = core.accounts().stream().flatMap(a -> a.unused().stream()).toList();
            if (stale.isEmpty()) {
                status.text("Every token has been used at least once.");
                return;
            }
            var listing = stale.stream().map(t -> "  " + t.account() + "  " + t.label()).toList();
            if (!Confirm.ask("Remove unused tokens", "These have never been used:\n\n"
                    + String.join("\n", listing)
                    + "\n\nRemove from the wallet? Google's grants survive.", "Remove", "Cancel")) return;
            try {
                tokens.removeUnused(new Asks.Unused(0));
                refresh.run();
            } catch (Exception ex) {
                status.text(String.valueOf(ex.getMessage()));
            }
        }));

        upload.attr(b -> b.setOnAction(e -> {
            var id = ((TextField) org.node).getText().trim();
            if (id.isEmpty()) {
                status.text("Give the org a short id first, e.g. uskf.");
                return;
            }
            var chooser = new FileChooser();
            chooser.setTitle("credentials.json for org " + id);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("credentials.json", "*.json"));
            var file = chooser.showOpenDialog(tree.getScene().getWindow());
            if (file == null) return;
            try {
                var domain = domainOf(((TextField) email.node).getText().trim());
                accounts.addOrg(new Asks.AddOrg(id, id, Files.readString(file.toPath()),
                        domain == null ? List.of() : List.of(domain)));
                status.text("Stored. Type an email and press Grant.");
                refresh.run();
            } catch (Exception ex) {
                status.text(String.valueOf(ex.getMessage()));
            }
        }));

        copy.attr(b -> b.setOnAction(e -> {
            var sb = new StringBuilder(TokenInfo.tsvHeader()).append('\n');
            core.accounts().forEach(a -> a.tokens().forEach(t -> sb.append(t.tsv()).append('\n')));
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(sb.toString());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            status.text("Whole inventory copied as TSV.");
        }));

        reload.attr(b -> b.setOnAction(e -> refresh.run()));

        return vbox().spacing(8).padding(12).nodes(
                label("Organisations, accounts and tokens").style("-fx-font-weight: bold;"),
                label("One credentials.json per org. One token per scope group, so each expires on its own"
                        + " and an unused mail grant cannot take Sheets down with it.")
                        .wrapText(true).style("-fx-font-size: 11px; -fx-text-fill: #666;"),
                luvjfx.Fx.fx(tree),
                detail,
                hbox().spacing(6).nodes(label("org"), org, label("email"), email),
                hbox().spacing(6).nodes(grant, remove, removeUnused, upload, copy, reload),
                status.wrapText(true).style("-fx-text-fill: #1b5e20;")).node;
    }

    private static String describe(TokenInfo t) {
        return t.order() + ". " + pad(t.label(), 34) + pad(t.tier().label, 13)
                + "used " + pad(TokenInfo.count(t.useCount()), 7)
                + (t.lastUsed() == 0 ? "never" : "last " + java.time.Instant.ofEpochMilli(t.lastUsed()));
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s.substring(0, width - 1) + " " : s + " ".repeat(width - s.length());
    }

    private static String domainOf(String email) {
        var at = email == null ? -1 : email.indexOf('@');
        return at < 0 ? null : email.substring(at + 1).toLowerCase();
    }
}
