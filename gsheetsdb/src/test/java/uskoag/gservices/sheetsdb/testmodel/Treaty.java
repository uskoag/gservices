package uskoag.gservices.sheetsdb.testmodel;

import java.time.LocalDate;
import uskoag.gservices.sheetsdb.SheetDates;
import uskoag.gservices.sheetsdb.TypeDef;

import static uskoag.gservices.sheetsdb.TypeDefBuilder.typeDef;

/**
 * The shape a consumer writes, and the worked example the README points at — so keep it honest:
 * package-private fields, {@code final} class, {@code extends Treaty_A}, one {@code DEF} constant,
 * and a sheet named {@code Treaty} because that is what the class is called.
 *
 * <p>{@code signedOn} is a {@code Double} because the DataHelper processor's field whitelist has no
 * {@link LocalDate}, so the field is the serial number the spreadsheet actually stores and
 * {@link SheetDates} converts at the edge. Same reason {@code status} is a {@code String} rather than
 * an enum.
 */
@datapotter.datahelper.Data
public final class Treaty extends Treaty_A {

    String treatyId;
    String title;
    Integer year;
    Boolean ratified;
    Double signedOn;
    String status;

    public static final TypeDef<Treaty> DEF =
            typeDef(Treaty.class, Treaty::new)
                    .key($treatyId)
                    .__();

    /** Named to avoid colliding with the generated {@code signedOn()} accessor. */
    public LocalDate signedOnDate() {
        return SheetDates.toLocalDate(signedOn);
    }

    /** Named for the same reason. */
    public Treaty signedOnDate(LocalDate date) {
        return signedOn(SheetDates.toSerial(date));
    }
}
