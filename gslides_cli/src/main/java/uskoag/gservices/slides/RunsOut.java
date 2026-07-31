package uskoag.gservices.slides;

import java.util.List;

public record RunsOut(String elem, int chars, List<RunInfo> runs, List<ParaInfo> paragraphs) {}
