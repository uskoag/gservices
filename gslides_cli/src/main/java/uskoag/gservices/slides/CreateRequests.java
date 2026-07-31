package uskoag.gservices.slides;

import com.google.api.services.slides.v1.model.CreateImageRequest;
import com.google.api.services.slides.v1.model.CreateLineRequest;
import com.google.api.services.slides.v1.model.CreateShapeRequest;
import com.google.api.services.slides.v1.model.CreateVideoRequest;
import com.google.api.services.slides.v1.model.Outline;
import com.google.api.services.slides.v1.model.OutlineFill;
import com.google.api.services.slides.v1.model.Request;
import com.google.api.services.slides.v1.model.ShapeBackgroundFill;
import com.google.api.services.slides.v1.model.ShapeProperties;
import com.google.api.services.slides.v1.model.UpdateShapePropertiesRequest;
import java.util.ArrayList;
import java.util.List;

public final class CreateRequests {

    private CreateRequests() {}

    static List<Request> textBox(String id, String pageId, Box b, RichText rt) {
        var reqs = new ArrayList<Request>();
        reqs.add(shapeOf(id, pageId, b, "TEXT_BOX"));
        if (rt != null && !rt.text().isEmpty()) {
            reqs.add(RichRequests.insert(id, 0, rt.text()));
            reqs.addAll(RichRequests.styling(id, rt));
        }
        return reqs;
    }

    static List<Request> shape(String id, String pageId, Box b, String shapeType,
                               String fill, Double alpha, String outline, Double weight, RichText rt) {
        var reqs = new ArrayList<Request>();
        reqs.add(shapeOf(id, pageId, b, shapeType));
        var style = appearance(id, fill, alpha, outline, weight);
        if (style != null) reqs.add(style);
        if (rt != null && !rt.text().isEmpty()) {
            reqs.add(RichRequests.insert(id, 0, rt.text()));
            reqs.addAll(RichRequests.styling(id, rt));
        }
        return reqs;
    }

    static Request appearance(String id, String fill, Double alpha, String outline, Double weight) {
        var props = new ShapeProperties();
        var fields = new ArrayList<String>();
        if (fill != null) {
            props.setShapeBackgroundFill(new ShapeBackgroundFill()
                    .setSolidFill(Colors.fill(fill, alpha == null ? 1.0 : alpha)));
            fields.add("shapeBackgroundFill");
        }
        if (outline != null) {
            var o = new Outline().setOutlineFill(new OutlineFill().setSolidFill(Colors.fill(outline, 1.0)));
            if (weight != null) o.setWeight(new com.google.api.services.slides.v1.model.Dimension()
                    .setMagnitude(weight).setUnit("PT"));
            props.setOutline(o);
            fields.add("outline");
        } else if (weight != null) {
            Out.error("--weight ignored without --outline");
        }
        if (fields.isEmpty()) return null;
        return new Request().setUpdateShapeProperties(new UpdateShapePropertiesRequest()
                .setObjectId(id).setShapeProperties(props).setFields(String.join(",", fields)));
    }

    static Request noOutline(String id) {
        return new Request().setUpdateShapeProperties(new UpdateShapePropertiesRequest()
                .setObjectId(id)
                .setShapeProperties(new ShapeProperties().setOutline(Colors.noOutline()))
                .setFields("outline"));
    }

    static Request image(String id, String pageId, Box b, String url) {
        var r = new CreateImageRequest().setUrl(url)
                .setElementProperties(Geom.props(pageId, b.x(), b.y(), b.w(), b.h()));
        if (id != null) r.setObjectId(id);
        return new Request().setCreateImage(r);
    }

    static Request video(String id, String pageId, Box b, String youtubeId) {
        var r = new CreateVideoRequest().setSource("YOUTUBE").setId(youtubeId)
                .setElementProperties(Geom.props(pageId, b.x(), b.y(), b.w(), b.h()));
        if (id != null) r.setObjectId(id);
        return new Request().setCreateVideo(r);
    }

    static Request line(String id, String pageId, Box b, String category) {
        var r = new CreateLineRequest().setCategory(category)
                .setElementProperties(Geom.props(pageId, b.x(), b.y(), b.w(), b.h()));
        if (id != null) r.setObjectId(id);
        return new Request().setCreateLine(r);
    }

    private static Request shapeOf(String id, String pageId, Box b, String shapeType) {
        var r = new CreateShapeRequest().setShapeType(shapeType)
                .setElementProperties(Geom.props(pageId, b.x(), b.y(), b.w(), b.h()));
        if (id != null) r.setObjectId(id);
        return new Request().setCreateShape(r);
    }
}
