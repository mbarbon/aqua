package aqua.mal.data;

import java.lang.Iterable;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class User {
    private static class FilteredListIterator implements Iterable<Rated>, Iterator<Rated> {
        private final int mask;
        private final Iterator<Rated> parent;
        private Rated next;

        private FilteredListIterator(List<Rated> animeList, int mask) {
            this.mask = mask;
            this.parent = animeList.iterator();
            this.next = findNext();
        }

        @Override
        public Iterator<Rated> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Rated next() {
            if (next == null)
                throw new NoSuchElementException();
            Rated result = next;
            next = findNext();
            return result;
        }

        private Rated findNext() {
            while (parent.hasNext()) {
                Rated maybeNext = parent.next();
                if ((mask & (1 << maybeNext.status)) != 0)
                    return maybeNext;
            }
            return null;
        }
    }

    private static final int COMPLETED_AND_DROPPED =
        statusMask(Rated.COMPLETED, Rated.DROPPED);
    private static final int COMPLETED =
        statusMask(Rated.COMPLETED);
    private static final int DROPPED =
        statusMask(Rated.DROPPED);
    private static final int WATCHING =
        statusMask(Rated.WATCHING);

    public String username;
    public long userId;
    public List<Rated> animeList;

    public Iterable<Rated> withStatusMask(int mask) {
        return new FilteredListIterator(animeList, mask);
    }

    public Iterable<Rated> completedAndDropped() {
        return withStatusMask(COMPLETED_AND_DROPPED);
    }

    public Iterable<Rated> completed() {
        return withStatusMask(COMPLETED);
    }

    public Iterable<Rated> dropped() {
        return withStatusMask(DROPPED);
    }

    public Iterable<Rated> watching() {
        return withStatusMask(WATCHING);
    }

    public User removeAnime(int animedbId) {
        User filtered = new User();

        filtered.username = username;
        filtered.userId = userId;
        filtered.animeList = animeList.stream()
            .filter(rated -> rated.animedbId != animedbId)
            .collect(Collectors.toList());

        return filtered;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof User))
            return false;
        return equals((User) o);
    }

    public boolean equals(User other) {
        return userId == other.userId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(userId);
    }

    private static int statusMask(int... statuses) {
        int mask = 0;
        for (int status : statuses)
            mask |= (1 <<  status);
        return mask;
    }
}
