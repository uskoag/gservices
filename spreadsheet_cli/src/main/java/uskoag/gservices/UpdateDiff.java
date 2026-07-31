package uskoag.gservices;

import java.util.List;

/** Outcome of a {@link SheetUpdate}: rows matched by the UPDATE, plus the per-cell before→after changes. */
public record UpdateDiff(int affected, List<CellChange> changes) {}
