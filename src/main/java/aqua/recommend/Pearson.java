package aqua.recommend;

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
        byte[] ratedRatingA = usera.completedAndDroppedRating;
        byte[] ratedRatingB = userb.completedAndDroppedRating;
        int[] ratedA = usera.completedAndDroppedIds;
        int[] ratedB = userb.completedAndDroppedIds;
        int indexA = 0, indexB = 0, maxA = ratedA.length, maxB = ratedB.length;
        if (maxA == 0 || maxB == 0)
            return 0;
        int currentA = ratedA[indexA++];
        int currentB = ratedB[indexB++];
        int productSum = 0, sumSquaresA = 0, sumSquaresB = 0;
        int count = 0;

        for (;;) {
            int order = currentA - currentB;
            if (order == 0) {
                byte normalizedA = ratedRatingA[indexA - 1];
                byte normalizedB = ratedRatingB[indexB - 1];
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

        if (100 * count / maxA < minCommonItems || 100 * count / maxB < minCommonItems)
            return 1;
        if (sumSquaresA == 0 || sumSquaresB == 0)
            return 1;
        return -CFUser.ratingToDouble(productSum) / (Math.sqrt(CFUser.squaredRatingToDouble(sumSquaresA))
                * Math.sqrt(CFUser.squaredRatingToDouble(sumSquaresB)));
    }
}
