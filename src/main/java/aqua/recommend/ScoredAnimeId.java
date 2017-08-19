package aqua.recommend;

import java.util.Comparator;

public class ScoredAnimeId implements RecommendationItem, ScoredAnime {
    public static final Comparator<ScoredAnimeId> SORT_SCORE = new Comparator<ScoredAnimeId>() {
        @Override
        public int compare(ScoredAnimeId a, ScoredAnimeId b) {
            return Float.compare(a.score, b.score);
        }
    };

    public int animedbId;
    public float score;
    public clojure.lang.Keyword tags;

    public ScoredAnimeId(int animedbId, float score) {
        this.animedbId = animedbId;
        this.score = score;
    }

    @Override
    public int animedbId() {
        return Math.abs(animedbId);
    }

    @Override
    public boolean isHentai() {
        return animedbId < 0;
    }

    @Override
    public clojure.lang.Keyword tags() {
        return tags;
    }

    @Override
    public void setTags(clojure.lang.Keyword tags) {
        this.tags = tags;
    }

    @Override
    public int hashCode() {
        return animedbId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ScoredAnimeId))
            return false;
        return equals((ScoredAnimeId) o);
    }

    public boolean equals(ScoredAnimeId other) {
        return animedbId == other.animedbId;
    }
}
