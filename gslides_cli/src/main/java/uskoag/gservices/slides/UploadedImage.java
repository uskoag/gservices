package uskoag.gservices.slides;

/** The lh3 form is the one verified to render inside a Slides createImage request. */
public record UploadedImage(String id, String lh3Url, String ucUrl, String thumbUrl) {

    static UploadedImage of(String id) {
        return new UploadedImage(id,
                "https://lh3.googleusercontent.com/d/" + id + "=w2048",
                "https://drive.google.com/uc?export=view&id=" + id,
                "https://drive.google.com/thumbnail?id=" + id + "&sz=w2048");
    }
}
