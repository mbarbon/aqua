package aqua.recommend;

import java.util.Iterator;

public class Pearson {
    private final CFUser usera;
    private final int minCommonItems;

    public Pearson(CFUser usera, int minCommonItems) {
        this.usera = usera;
        this.minCommonItems = minCommonItems;
    }

    public double userSimilarity(CFUser userb) {
        if (usera.equals(userb))
            return 1;
        float[] ratedRatingA = usera.completedAndDroppedRating;
        float[] ratedRatingB = userb.completedAndDroppedRating;
        int[] ratedA = usera.completedAndDroppedIds;
        int[] ratedB = userb.completedAndDroppedIds;
        int indexA = 0, indexB = 0, maxA = ratedA.length, maxB = ratedB.length;
        if (maxA == 0 || maxB == 0)
            return 0;
        int currentA = ratedA[indexA++];
        int currentB = ratedB[indexB++];
        double productSum = 0.0, sumSquaresA = 0.0, sumSquaresB = 0.0;
        int count = 0;

        for (;;) {
            int order = currentA - currentB;
            if (order == 0) {
                float normalizedA = ratedRatingA[indexA - 1];
                float normalizedB = ratedRatingB[indexB - 1];
                productSum += normalizedA * normalizedB;
                sumSquaresA += normalizedA * normalizedA;
                sumSquaresB += normalizedB * normalizedB;
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
