package aqua.recommend;

import no.uib.cipr.matrix.DenseMatrix;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class LatentFactorDecompositionUsers {
    private final LatentFactorDecomposition lfd;
    public final CFUser[] userMap;
    public final float[] userFactors;
    public final int rank;

    public LatentFactorDecompositionUsers(LatentFactorDecomposition lfd, CFUser[] userMap, DenseMatrix userFactors) {
        // userFactors is users x rank, column major, but making
        // the factors for a single user contiguous gives us better cache
        // locality
        DenseMatrix transposedFactors = new DenseMatrix(userFactors.numColumns(), userFactors.numRows());
        userFactors.transpose(transposedFactors);

        this.lfd = lfd;
        this.userMap = userMap;
        this.userFactors = toFloatArray(transposedFactors.getData());
        this.rank = this.userFactors.length / userMap.length;
    }

    public LatentFactorDecompositionUsers(LatentFactorDecomposition lfd, int rank, CFUser[] userMap,
            float[] userFactors, int[] validIndices) {
        if (lfd.rank() != rank)
            throw new IllegalArgumentException("Inconsiten model ranks " + lfd.rank() + " != " + rank);
        this.lfd = lfd;
        if (userFactors.length == validIndices.length * rank) {
            // when the full set of users was loaded
            this.userMap = userMap;
            this.userFactors = userFactors;
        } else {
            // when only a subset of the users was loaded
            this.userMap = new CFUser[validIndices.length];
            this.userFactors = new float[validIndices.length * rank];
            for (int i = 0; i < validIndices.length; ++i) {
                int source = validIndices[i];
                this.userMap[i] = userMap[source];
                if (userMap[source] == null)
                    throw new RuntimeException("Incosistent valid anime map: " + i + " " + source);
                System.arraycopy(userFactors, source * rank, this.userFactors, i * rank, rank);
            }
        }
        this.rank = rank;
    }

    private LatentFactorDecompositionUsers(LatentFactorDecomposition lfd, int rank, CFUser[] userMap,
            float[] userFactors) {
        this.lfd = lfd;
        this.rank = rank;
        this.userMap = userMap;
        this.userFactors = userFactors;
    }

    public LatentFactorDecompositionUsers reduceUserCount(int rowCount) {
        CFUser[] restrictedUsers = Arrays.copyOfRange(userMap, 0, rowCount);
        float[] restrictedFactors = Arrays.copyOfRange(userFactors, 0, rowCount * rank);

        return new LatentFactorDecompositionUsers(lfd, rank, restrictedUsers, restrictedFactors);
    }

    public List<ScoredUser> computeUserUserScores(CFUser usera) {
        float[] userVector = toFloatArray(lfd.computeUserVector(usera));
        double sumSquaresA = sumSquares(userVector, 0, userVector.length);
        List<ScoredUser> scored = new ArrayList<>(userMap.length);

        for (int i = 0, userIdx = 0; userIdx < userMap.length; i += rank, ++userIdx) {
            CFUser userb = userMap[userIdx];
            if (usera.equals(userb))
                continue;
            double sumSquaresB = sumSquares(userFactors, 0, rank);
            double productSum = productSum(userVector, userFactors, i, rank);
            double score = -productSum / (sumSquaresA * sumSquaresB);

            scored.add(new ScoredUser(userb, (float) score));
        }

        return scored;
    }

    public UserData prepareUserData(CFUser usera) {
        return new UserData(usera);
    }

    private static double sumSquares(float[] factors, int start, int length) {
        double sum = 0.0;
        for (int i = start, end = start + length; i < end; ++i)
            sum += factors[i] * factors[i];
        return Math.sqrt(sum);
    }

    private static double productSum(float[] useraFactors, float[] userbFactors, int start, int length) {
        double sum = 0.0;
        for (int i = 0, j = start; i < length; ++i, ++j)
            sum += useraFactors[i] * userbFactors[j];
        return sum;
    }

    private static float[] toFloatArray(double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; ++i)
            floats[i] = (float) doubles[i];
        return floats;
    }

    public class UserData {
        private final CFUser usera;
        private final float[] userVector;
        private final double sumSquaresA;

        public UserData(CFUser usera) {
            this.usera = usera;
            this.userVector = toFloatArray(lfd.computeUserVector(usera));
            this.sumSquaresA = sumSquares(userVector, 0, userVector.length);
        }

        public float userSimilarity(int userIdx) {
            CFUser userb = userMap[userIdx];
            if (usera.equals(userb))
                return 1;
            int base = userIdx * rank;
            double sumSquaresB = sumSquares(userFactors, 0, rank);
            if (sumSquaresB == 0)
                return 1;
            double productSum = productSum(userVector, userFactors, base, rank);
            return (float) (-productSum / (sumSquaresA * sumSquaresB));
        }
    }
}
