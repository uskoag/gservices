package uskoag.gservices.slides;

/** Element geometry in inches, as reported and as accepted. */
public record Box(double x, double y, double w, double h, double rot) {

    boolean overlaps(Box o) {
        return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
    }
}
