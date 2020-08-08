package aqua.mal.data;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;

public class Franchise {
    public final int franchiseId;
    public final Set<Anime> anime;
    public final Set<Manga> manga;

    public Franchise(int franchiseId, Collection<? extends Item> items) {
        Item first = items.isEmpty() ? null : items.iterator().next();
        Set<? extends Item> itemSet = ImmutableSet.copyOf(items);

        if (first == null) {
            this.anime = ImmutableSet.of();
            this.manga = null;
        } else if (first instanceof Anime) {
            this.anime = (Set<Anime>) itemSet;
            this.manga = null;
        } else {
            this.anime = null;
            this.manga = (Set<Manga>) itemSet;
        }

        this.franchiseId = franchiseId;
    }

    public int episodes() {
        return anime.stream().mapToInt(a -> a.episodes).sum();
    }

    public int chapters() {
        return manga.stream().mapToInt(a -> a.chapters).sum();
    }

    public int volumes() {
        return manga.stream().mapToInt(a -> a.volumes).sum();
    }

    public Set<? extends Item> items() {
        if (anime != null)
            return anime;
        else
            return manga;
    }

    @Override
    public String toString() {
        return String.format("Franchise %d", franchiseId);
    }
}
