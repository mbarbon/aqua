package aqua.recommend;

import aqua.mal.data.User;
import aqua.mal.data.Rated;

import java.util.Iterator;

public class Pearson {
    private final User usera;
    private final int minCommonItems;

    public Pearson(User usera, int minCommonItems) {
        this.usera = usera;
        this.minCommonItems = minCommonItems;
    }

    public double userSimilarity(User userb) {
        if (usera.equals(userb))
            return 1;
        Rated[] ratedA = usera.completedAndDroppedArray;
        Rated[] ratedB = userb.completedAndDroppedArray;
        int indexA = 0, indexB = 0, maxA = ratedA.length, maxB = ratedB.length;
        if (maxA == 0 || maxB == 0)
            return 0;
        Rated currentA = ratedA[indexA++];
        Rated currentB = ratedB[indexB++];
        double productSum = 0.0, sumSquaresA = 0.0, sumSquaresB = 0.0;
        int count = 0;

        for (;;) {
            int order = currentA.animedbId - currentB.animedbId;
            if (order == 0) {
                productSum += currentA.normalizedRating * currentB.normalizedRating;
                sumSquaresA += currentA.normalizedRating * currentA.normalizedRating;
                sumSquaresB += currentB.normalizedRating * currentB.normalizedRating;
                ++count;

                if (indexA == maxA)
                    break;
                currentA = ratedA[indexA++];
                if (indexB == maxB)
                    break;
                currentB = ratedB[indexB++];
            } else if (order > 0) {
                if (indexB == maxB)
                    break;
                currentB = ratedB[indexB++];
            } else {
                if (indexA == maxA)
                    break;
                currentA = ratedA[indexA++];
            }
        }

        if (100 * count / maxA < minCommonItems ||
                100 * count / maxB < minCommonItems)
            return 1;
        if (sumSquaresA == 0.0 || sumSquaresB == 0.0)
            return 1;
        return -productSum / (Math.sqrt(sumSquaresA) * Math.sqrt(sumSquaresB));
    }
}
