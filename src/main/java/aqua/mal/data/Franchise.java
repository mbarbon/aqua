package aqua.mal.data;

import java.util.ArrayList;
import java.util.List;

public class Franchise {
    public int franchiseId;
    public List<Anime> anime;

    public Franchise(int franchiseId) {
        this.franchiseId = franchiseId;
        this.anime = new ArrayList<>();
    }

    public int episodes() {
        return anime.stream()
            .mapToInt(a -> a.episodes)
            .sum();
    }
}
