package aqua.recommend;

import aqua.mal.data.FilteredListIterator;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.lang.Iterable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CFUser {
    private static final double FP_DOUBLE = 32.0d;
    private static final float FP_FLOAT = 32.0f;

    private static final CFRated[] EMPTY_CFRATED_ARRAY = new CFRated[0];
    private static final int COMPLETED_AND_DROPPED =
        statusMask(CFRated.COMPLETED, CFRated.DROPPED);
    private static final int COMPLETED =
        statusMask(CFRated.COMPLETED);
    private static final int DROPPED =
        statusMask(CFRated.DROPPED);
    private static final int WATCHING =
        statusMask(CFRated.WATCHING);
    private static final int PLANTOWATCH =
        statusMask(CFRated.PLANTOWATCH);
    private static final int ALL_BUT_PLANTOWATCH = ~PLANTOWATCH;

    public String username;
    public long userId;
    public CFRated[] animeList;
    public int[] completedAndDroppedIds;
    public byte[] completedAndDroppedRating;
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
            List<Byte> ratings = new ArrayList<>();
            for (CFRated rated : withStatusMask(COMPLETED_AND_DROPPED)) {
                if (rated.status == CFRated.COMPLETED)
                    ++completedCount;
                else if (rated.status == CFRated.DROPPED)
                    ++droppedCount;
                idList.add(rated.animedbId);
                ratings.add(toCappedFixedPoint(normalizedRating(rated)));
            }
            completedAndDroppedIds = Ints.toArray(idList);
            completedAndDroppedRating = Bytes.toArray(ratings);
        }
    }

    public void setAnimeList(CFParameters cfParameters, List<CFRated> animeList) {
        this.animeList = animeList.toArray(EMPTY_CFRATED_ARRAY);
        processAfterDeserialize(cfParameters);
    }

    public void setFilteredAnimeList(CFParameters cfParameters, List<CFRated> animeList) {
        List<CFRated> filtered = new ArrayList<>();
        for (CFRated item : new FilteredListIterator<>(animeList.toArray(EMPTY_CFRATED_ARRAY), COMPLETED_AND_DROPPED|WATCHING))
            filtered.add(item);
        this.animeList = filtered.toArray(EMPTY_CFRATED_ARRAY);
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

    public Iterable<CFRated> planToWatch() {
        return withStatusMask(PLANTOWATCH);
    }

    public Iterable<CFRated> allButPlanToWatch() {
        return withStatusMask(ALL_BUT_PLANTOWATCH);
    }

    public CFUser removeAnime(int animedbId) {
        CFUser filtered = new CFUser();

        filtered.username = username;
        filtered.userId = userId;
        filtered.animeList = Arrays.stream(animeList)
            .filter(rated -> rated.animedbId != animedbId)
            .collect(Collectors.toList())
            .toArray(EMPTY_CFRATED_ARRAY);
        filtered.processAfterDeserialize(cfParameters);

        return filtered;
    }

    public CFUser removeAnime(Set<Integer> animedbIds) {
        CFUser filtered = new CFUser();

        filtered.username = username;
        filtered.userId = userId;
        filtered.animeList = Arrays.stream(animeList)
            .filter(rated -> !animedbIds.contains(rated.animedbId))
            .collect(Collectors.toList())
            .toArray(EMPTY_CFRATED_ARRAY);
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

    public float completedAndDroppedRatingFloat(int index) {
        return completedAndDroppedRating[index] / FP_FLOAT;
    }


    public double completedAndDroppedRatingDouble(int index) {
        return completedAndDroppedRating[index] / FP_DOUBLE;
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

    private static byte toCappedFixedPoint(float rating) {
        int fpRating = (int) (rating * FP_FLOAT);
        return
            fpRating < - 128 ? -128 :
            fpRating > 127   ? 127 :
                               (byte) fpRating;
    }

    public static double ratingToDouble(int rating) {
        return rating / FP_DOUBLE;
    }

    public static double squaredRatingToDouble(int rating) {
        return rating / (FP_DOUBLE * FP_DOUBLE);
    }
}
