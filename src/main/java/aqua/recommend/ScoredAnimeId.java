package aqua.recommend;

import java.util.Comparator;

public class ScoredAnimeId {
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

    public int animedbId() {
        return animedbId;
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
