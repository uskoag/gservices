package uskoag.gservices.vault;

/** Outcome of trying a passphrase against a vault. */
public enum UnlockStatus { UNLOCKED, CREATED, WRONG_PASSPHRASE, DECLINED, FAILED }
