package aqua.mal.data;

import java.lang.Iterable;
import java.util.List;

public class User {
    private static final Rated[] EMPTY_RATED_ARRAY = new Rated[0];
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
    public Rated[] animeList;

    public void setAnimeList(List<Rated> animeList) {
        this.animeList = animeList.toArray(EMPTY_RATED_ARRAY);
    }

    private Iterable<Rated> withStatusMask(int mask) {
        return new FilteredListIterator<>(animeList, mask);
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
