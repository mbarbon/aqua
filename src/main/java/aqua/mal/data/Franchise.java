package aqua.mal.data;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;

public class Franchise {
    public int franchiseId;
    public Set<Anime> anime;

    public Franchise(int franchiseId, Collection<Anime> anime) {
        this.franchiseId = franchiseId;
        this.anime = ImmutableSet.copyOf(anime);
    }

    public int episodes() {
        return anime.stream()
            .mapToInt(a -> a.episodes)
            .sum();
    }

    public Set<Anime> items() {
        return anime;
    }

    @Override
    public String toString() {
        return String.format("Franchise %d", franchiseId);
    }
}
