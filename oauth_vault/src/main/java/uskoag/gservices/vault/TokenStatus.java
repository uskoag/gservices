package uskoag.gservices.vault;

/** Whether a stored identity could be loaded. UNREADABLE means present but broken, not absent. */
public enum TokenStatus { AUTHORIZED, MISSING, UNREADABLE }
