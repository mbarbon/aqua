package aqua.recommend;

public class Cosine {
    private final CFUser usera;
    private final double sumSquaresA;

    public Cosine(CFUser usera) {
        this.usera = usera;
        this.sumSquaresA = sumSquares(usera);
    }

    public double userSimilarity(CFUser userb) {
        if (usera.equals(userb) || sumSquaresA == 0)
            return 1;
        double sumSquaresB = sumSquares(userb);
        if (sumSquaresB == 0)
            return 1;
        double productSum = productSum(usera, userb);
        return -productSum / (sumSquaresA * sumSquaresB);
    }

    private static double sumSquares(CFUser user) {
        int sum = 0;
        for (byte rating : user.completedAndDroppedRating)
            sum += rating * rating;
        return Math.sqrt(CFUser.squaredRatingToDouble(sum));
    }

    private static double productSum(CFUser usera, CFUser userb) {
        byte[] ratedRatingA = usera.completedAndDroppedRating;
        byte[] ratedRatingB = userb.completedAndDroppedRating;
        int[] ratedA = usera.completedAndDroppedIds;
        int[] ratedB = userb.completedAndDroppedIds;
        int indexA = 0, indexB = 0, maxA = ratedA.length, maxB = ratedB.length;
        if (maxA == 0 || maxB == 0)
            return 0;
        int currentA = ratedA[indexA++];
        int currentB = ratedB[indexB++];
        int sum = 0;

        for (;;) {
            int order = currentA - currentB;
            if (order == 0) {
                sum += ratedRatingA[indexA - 1] * ratedRatingB[indexB - 1];

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

        return CFUser.squaredRatingToDouble(sum);
    }
}
