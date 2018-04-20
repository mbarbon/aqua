package aqua.search;

import java.util.List;

public class AnimeTitle {
    public final int animedbId;
    public final String title;

    public AnimeTitle(List<Object> clojureList) {
        this(((Number) clojureList.get(0)).intValue(), (String) clojureList.get(1));
    }

    public AnimeTitle(int animedbId, String title) {
        this.animedbId = animedbId;
        this.title = title;
    }
}
