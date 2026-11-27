package com.geomaster.core.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps only the brands the answer text really contains (extraction-analysis REQ-7) and ranks them
 * by first occurrence (REQ-8). A brand is kept when its name or domain appears in the text as a
 * whole word and its evidence quote appears verbatim, both after {@link MatchText} folding; any
 * other brand is dropped and counted as a grounding rejection. The kept brands' positions are
 * 1..n in order of the earliest index of name, domain or quote; ties keep the model's order.
 */
public final class GroundingCheck {

    /** A kept brand with its 1-based position in the answer. */
    public record Grounded(ExtractedBrand brand, int position) {}

    /** Kept brands in position order and the number dropped. */
    public record Result(List<Grounded> kept, int rejections) {}

    private record Located(ExtractedBrand brand, int index) {}

    private GroundingCheck() {}

    public static Result apply(String answerText, List<ExtractedBrand> brands) {
        String text = MatchText.fold(answerText);
        List<Located> located = new ArrayList<>();
        int rejections = 0;
        for (ExtractedBrand b : brands) {
            int mention = PositionLocator.firstMention(text, b);
            int quote = PositionLocator.firstQuote(text, MatchText.foldQuote(b.evidence()));
            if (mention == PositionLocator.ABSENT || quote == PositionLocator.ABSENT) {
                rejections++;
            } else {
                located.add(new Located(b, Math.min(mention, quote)));
            }
        }
        located.sort(Comparator.comparingInt(Located::index).thenComparingInt(l -> l.brand().ordinal()));
        List<Grounded> kept = new ArrayList<>(located.size());
        for (int i = 0; i < located.size(); i++) {
            kept.add(new Grounded(located.get(i).brand(), i + 1));
        }
        return new Result(List.copyOf(kept), rejections);
    }
}
