package aqua.search;

import java.util.List;

public class AnimeTitle {
    public final int animedbId;
    public final String title;
    public final short titleId;

    // convenience constructor for tests
    public AnimeTitle(List<Object> clojureList) {
        this(((Number) clojureList.get(0)).intValue(),
                (String) clojureList.get(1),
                clojureList.size() == 3 ? ((Number) clojureList.get(2)).shortValue() : 0);
    }

    public AnimeTitle(int animedbId, String title, short titleId) {
        this.animedbId = animedbId;
        this.title = title;
        this.titleId = titleId;
    }
}
