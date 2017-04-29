package aqua.recommend;

import aqua.mal.data.FilteredListIterator;

import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;

import java.lang.Iterable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CFUser {
    private static final int COMPLETED_AND_DROPPED =
        statusMask(CFRated.COMPLETED, CFRated.DROPPED);
    private static final int COMPLETED =
        statusMask(CFRated.COMPLETED);
    private static final int DROPPED =
        statusMask(CFRated.DROPPED);
    private static final int WATCHING =
        statusMask(CFRated.WATCHING);

    public String username;
    public long userId;
    public List<CFRated> animeList;
    public int[] completedAndDroppedIds;
    public float[] completedAndDroppedRating;
    public int completedCount, droppedCount;
    public float ratingMean, ratingStddev;
    public byte minRating;
    public CFParameters cfParameters;

    public void processAfterDeserialize(CFParameters cfParameters) {
        this.cfParameters = cfParameters;

        completedCount = droppedCount = 0;
        ratingMean = ratingStddev = 0;
        minRating = 0;

        {
            int nonZeroCount = 0, sum = 0, sumSq = 0;
            byte min = 11;
            for (CFRated rated : withStatusMask(COMPLETED_AND_DROPPED)) {
                if (rated.rating == 0)
                    continue;
                ++nonZeroCount;
                sum += rated.rating;
                sumSq += rated.rating * rated.rating;
                min = min < rated.rating ? min : rated.rating;
            }
            if (nonZeroCount == 0) {
                ratingMean = 0;
                ratingStddev = 1;
                minRating = 0;
            } else {
                ratingMean = (float) sum / nonZeroCount;
                float variance = (sumSq / (float) nonZeroCount) - (ratingMean * ratingMean);
                ratingStddev = variance == 0 ? 1.0f : (float) Math.sqrt(variance);
                minRating = min == 11 ? 0 : min;
            }
        }

        {
            List<Integer> idList = new ArrayList<>();
            List<Float> ratings = new ArrayList<>();
            for (CFRated rated : withStatusMask(COMPLETED_AND_DROPPED)) {
                if (rated.status == CFRated.COMPLETED)
                    ++completedCount;
                else if (rated.status == CFRated.DROPPED)
                    ++droppedCount;
                idList.add(rated.animedbId);
                ratings.add(normalizedRating(rated));
            }
            completedAndDroppedIds = Ints.toArray(idList);
            completedAndDroppedRating = Floats.toArray(ratings);
        }
    }

    public void setAnimeList(CFParameters cfParameters, List<CFRated> animeList) {
        this.animeList = animeList;
        processAfterDeserialize(cfParameters);
    }

    public void setFilteredAnimeList(CFParameters cfParameters, List<CFRated> animeList) {
        List<CFRated> filtered = new ArrayList<>();
        for (CFRated item : new FilteredListIterator<>(animeList, COMPLETED_AND_DROPPED|WATCHING))
            filtered.add(item);
        this.animeList = filtered;
        processAfterDeserialize(cfParameters);
    }

    private Iterable<CFRated> withStatusMask(int mask) {
        return new FilteredListIterator<>(animeList, mask);
    }

    public Iterable<CFRated> completedAndDropped() {
        return withStatusMask(COMPLETED_AND_DROPPED);
    }

    public Iterable<CFRated> completed() {
        return withStatusMask(COMPLETED);
    }

    public Iterable<CFRated> dropped() {
        return withStatusMask(DROPPED);
    }

    public Iterable<CFRated> watching() {
        return withStatusMask(WATCHING);
    }

    public CFUser removeAnime(int animedbId) {
        CFUser filtered = new CFUser();

        filtered.username = username;
        filtered.userId = userId;
        filtered.animeList = animeList.stream()
            .filter(rated -> rated.animedbId != animedbId)
            .collect(Collectors.toList());
        filtered.processAfterDeserialize(cfParameters);

        return filtered;
    }

    public float normalizedRating(CFRated rated) {
        if (rated.rating > 0)
            return (rated.rating - ratingMean + cfParameters.nonRatedCompleted) / ratingStddev;
        else if (rated.status == CFRated.COMPLETED)
            return cfParameters.nonRatedCompleted / ratingStddev;
        else if (rated.status == CFRated.DROPPED)
            return (minRating + cfParameters.nonRatedDropped - ratingMean) / ratingStddev;
        else
            return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CFUser))
            return false;
        return equals((CFUser) o);
    }

    public boolean equals(CFUser other) {
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
