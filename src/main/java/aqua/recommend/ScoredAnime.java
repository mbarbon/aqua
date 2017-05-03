package aqua.recommend;

import java.util.Comparator;

public class ScoredAnime {
    public static final Comparator<ScoredAnime> SORT_SCORE = new Comparator<ScoredAnime>() {
        @Override
        public int compare(ScoredAnime a, ScoredAnime b) {
            return Float.compare(a.score, b.score);
        }
    };

    public CFRated rated;
    public float score;
    public clojure.lang.Keyword tags;

    public ScoredAnime(CFRated rated, float score) {
        this.rated = rated;
        this.score = score;
    }

    public int animedbId() {
        return rated.animedbId;
    }

    @Override
    public int hashCode() {
        return rated.animedbId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ScoredAnime))
            return false;
        return equals((ScoredAnime) o);
    }

    public boolean equals(ScoredAnime other) {
        return rated.animedbId == other.rated.animedbId;
    }
}
