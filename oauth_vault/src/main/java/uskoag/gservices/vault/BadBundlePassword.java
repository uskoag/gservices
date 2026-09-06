package uskoag.gservices.vault;

/** The password does not open the bundle. Distinct from a corrupt file: the remedies differ. */
public class BadBundlePassword extends Exception {
    public BadBundlePassword() { super("that password does not open this bundle"); }
}
