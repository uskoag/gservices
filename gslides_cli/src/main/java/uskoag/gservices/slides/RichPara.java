package uskoag.gservices.slides;

public record RichPara(int start, int end, String align, String bullet) {

    RichPara shift(int by) { return new RichPara(start + by, end + by, align, bullet); }
}
