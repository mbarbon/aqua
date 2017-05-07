package aqua.recommend;

import java.util.Comparator;

public class ScoredRated implements ScoredAnime {
    public static final Comparator<ScoredRated> SORT_SCORE = new Comparator<ScoredRated>() {
        @Override
        public int compare(ScoredRated a, ScoredRated b) {
            return Float.compare(a.score, b.score);
        }
    };

    public CFRated rated;
    public float score;
    public clojure.lang.Keyword tags;

    public ScoredRated(CFRated rated, float score) {
        this.rated = rated;
        this.score = score;
    }

    @Override
    public int animedbId() {
        return rated.animedbId;
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
        return rated.animedbId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ScoredRated))
            return false;
        return equals((ScoredRated) o);
    }

    public boolean equals(ScoredRated other) {
        return rated.animedbId == other.rated.animedbId;
    }
}
