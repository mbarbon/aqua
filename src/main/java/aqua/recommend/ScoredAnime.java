package aqua.recommend;

interface ScoredAnime {
    int animedbId();

    clojure.lang.Keyword tags();

    void setTags(clojure.lang.Keyword tags);
}
