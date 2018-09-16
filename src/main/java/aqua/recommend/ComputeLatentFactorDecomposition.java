package aqua.recommend;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.sparse.FlexCompColMatrix;
import no.uib.cipr.matrix.sparse.FlexCompRowMatrix;
import no.uib.cipr.matrix.sparse.SparseVector;

import java.util.Map;
import java.util.Random;

import static aqua.recommend.MatrixUtils.partialTransAmult;

public class ComputeLatentFactorDecomposition {
    private final int animeCount, userCount, rank;
    private final double lambda;
    private final Map<Integer, Integer> animeIndexMap;
    private final CFUser[] userMap;
    // from
    // https://datasciencemadesimpler.wordpress.com/tag/alternating-least-squares/
    private final FlexCompRowMatrix ratingMatrixByUser; // R matrix
    private final FlexCompColMatrix ratingMatrixByAnime; // R matrix
    private final DenseMatrix userFactors; // U matrix
    private final DenseMatrix animeFactors; // P matrix
    // temporaries, pre-allocated to reduce garbage
    private final DenseVector userRatingSlice; // R(i) (transposed row slice of the R matrix))
    private final DenseVector animeRatingSlice; // R(j) (column slice of the R matrix)

    // shared between alternating steps, since the result of both is a
    // linear system with "rank" equations
    private final DenseMatrix linearSystemCoefficients;
    private final DenseVector linearSystemValues;
    private final DenseVector linearSystemSolution;

    // timing information
    private long preprocessTime, coefficientTime, valueTime, solveTime;

    public ComputeLatentFactorDecomposition(Map<Integer, Integer> animeIndexMap, int userCount, int rank,
            double lambda) {
        this.animeIndexMap = animeIndexMap;
        this.animeCount = animeIndexMap.size();
        this.userCount = userCount;
        this.userMap = new CFUser[userCount];
        this.rank = rank;
        this.lambda = lambda;
        this.ratingMatrixByUser = new FlexCompRowMatrix(userCount, animeCount);
        this.ratingMatrixByAnime = new FlexCompColMatrix(userCount, animeCount);
        this.userFactors = new DenseMatrix(userCount, rank);
        this.animeFactors = new DenseMatrix(animeCount, rank);

        this.userRatingSlice = new DenseVector(userCount);
        this.animeRatingSlice = new DenseVector(animeCount);
        this.linearSystemCoefficients = new DenseMatrix(rank, rank);
        this.linearSystemValues = new DenseVector(rank);
        this.linearSystemSolution = new DenseVector(rank);
    }

    private ComputeLatentFactorDecomposition(Map<Integer, Integer> animeIndexMap, int userCount, int rank,
            double lambda, DenseMatrix userFactors) {
        this.animeIndexMap = animeIndexMap;
        this.animeCount = animeIndexMap.size();
        this.userCount = userCount;
        this.userMap = null;
        this.rank = rank;
        this.lambda = lambda;
        this.ratingMatrixByUser = null;
        this.ratingMatrixByAnime = new FlexCompColMatrix(userCount, animeCount);
        this.userFactors = userFactors;
        this.animeFactors = new DenseMatrix(animeCount, rank);

        this.userRatingSlice = new DenseVector(userCount);
        this.animeRatingSlice = null;
        this.linearSystemCoefficients = new DenseMatrix(rank, rank);
        this.linearSystemValues = new DenseVector(rank);
        this.linearSystemSolution = new DenseVector(rank);
    }

    public int userCount() {
        return userCount;
    }

    public int animeCount() {
        return animeCount;
    }

    public long preprocessTime() {
        return preprocessTime;
    }

    public long coefficientTime() {
        return coefficientTime;
    }

    public long valueTime() {
        return valueTime;
    }

    public long solveTime() {
        return solveTime;
    }

    public ComputeLatentFactorDecomposition forAiring(Map<Integer, Integer> animeIndexMap) {
        return new ComputeLatentFactorDecomposition(animeIndexMap, userCount, rank, lambda, userFactors);
    }

    public LatentFactorDecomposition decomposition() {
        return new LatentFactorDecomposition(lambda, animeIndexMap, animeFactors);
    }

    public LatentFactorDecompositionUsers decompositionUsers() {
        return new LatentFactorDecompositionUsers(decomposition(), userMap, userFactors);
    }

    public void addCompletedRatings(int userIndex, CFUser user) {
        for (int i = 0; i < user.completedAndDroppedIds.length; ++i) {
            int animedbId = user.completedAndDroppedIds[i];
            float rating = user.completedAndDroppedRatingFloat(i);
            Integer animeIndex = animeIndexMap.get(animedbId);

            // some anime are filtered out, so they will have no index
            if (animeIndex != null) {
                ratingMatrixByUser.set(userIndex, animeIndex, rating);
                ratingMatrixByAnime.set(userIndex, animeIndex, rating);
            }
        }

        userMap[userIndex] = user;
    }

    public void addAiringRatings(int userIndex, CFUser user) {
        for (CFRated rated : user.watching()) {
            Integer animeIndex = animeIndexMap.get(rated.animedbId);

            // some anime are filtered out, so they will have no index
            if (animeIndex != null)
                ratingMatrixByAnime.set(userIndex, animeIndex, 1);
        }

        for (CFRated rated : user.dropped()) {
            Integer animeIndex = animeIndexMap.get(rated.animedbId);

            // some anime are filtered out, so they will have no index
            if (animeIndex != null)
                ratingMatrixByAnime.set(userIndex, animeIndex, -1);
        }
    }

    public void addInProgressAndDropped(int userIndex, CFUser user) {
        for (CFRated rated : user.inProgressAndDropped()) {
            float rating = user.normalizedRating(rated);
            Integer animeIndex = animeIndexMap.get(rated.animedbId);

            // some anime are filtered out, so they will have no index
            if (animeIndex != null) {
                ratingMatrixByUser.set(userIndex, animeIndex, rating);
                ratingMatrixByAnime.set(userIndex, animeIndex, rating);
            }
        }

        userMap[userIndex] = user;
    }

    public void initializeIteration() {
        Random rnd = new Random();

        for (int i = 0; i < userCount; ++i)
            for (int j = 0; j < rank; ++j)
                userFactors.set(i, j, rnd.nextGaussian() / rank);

        for (int i = 0; i < animeCount; ++i)
            for (int j = 0; j < rank; ++j)
                animeFactors.set(i, j, rnd.nextGaussian() / rank);
    }

    public double rootMeanSquaredError() {
        Vector userFactorsSlice = new DenseVector(rank);
        Vector animeEstimates = new DenseVector(animeCount);
        double absoluteSquaredError = 0.0;
        int itemCount = 0;

        for (int i = 0; i < userCount; i += 100) {
            userFactorsSlice.zero();
            for (int j = 0; j < rank; ++j)
                userFactorsSlice.set(j, userFactors.get(i, j));
            animeFactors.mult(userFactorsSlice, animeEstimates);
            for (int j = 0; j < animeCount; ++j) {
                double rating = ratingMatrixByUser.get(i, j);
                if (rating != 0) {
                    double delta = rating - animeEstimates.get(j);
                    absoluteSquaredError += delta * delta;
                    itemCount += 1;
                }
            }
        }

        return Math.sqrt(absoluteSquaredError / itemCount);
    }

    public void userStep(int userIndex) {
        long preprocessStart = System.currentTimeMillis();
        SparseVector currentItem = ratingMatrixByUser.getRow(userIndex);
        int[] indices = currentItem.getIndex();
        double[] data = currentItem.getData();
        int indexCount = currentItem.getUsed();
        double weightedLambda = lambda * indexCount;

        animeRatingSlice.zero();
        for (int i = indexCount - 1; i > 0; --i)
            animeRatingSlice.set(indices[i], data[i]);
        if (indexCount > 0)
            animeRatingSlice.set(indices[0], data[0]);

        // duplicated in LatentFactorDecomposition.computeUserVector
        long coefficientsStart = System.currentTimeMillis();
        partialTransAmult(animeFactors, indices, indexCount, animeFactors, linearSystemCoefficients);
        for (int i = 0; i < rank; ++i)
            linearSystemCoefficients.add(i, i, weightedLambda);

        long valuesStart = System.currentTimeMillis();
        partialTransAmult(animeFactors, indices, indexCount, animeRatingSlice, linearSystemValues);

        long solveStart = System.currentTimeMillis();
        linearSystemCoefficients.solve(linearSystemValues, linearSystemSolution);

        long endTime = System.currentTimeMillis();
        for (int i = 0; i < rank; ++i)
            userFactors.set(userIndex, i, linearSystemSolution.get(i));

        preprocessTime += coefficientsStart - preprocessStart;
        coefficientTime += valuesStart - coefficientsStart;
        valueTime += solveStart - valuesStart;
        solveTime += endTime - solveStart;
    }

    public void itemStep(int animeIndex) {
        long preprocessStart = System.currentTimeMillis();
        SparseVector currentItem = ratingMatrixByAnime.getColumn(animeIndex);
        int[] indices = currentItem.getIndex();
        double[] data = currentItem.getData();
        int indexCount = currentItem.getUsed();
        double weightedLambda = lambda * (indexCount == 0 ? 1 : indexCount);

        userRatingSlice.zero();
        for (int i = indexCount - 1; i > 0; --i)
            userRatingSlice.set(indices[i], data[i]);
        if (indexCount > 0)
            userRatingSlice.set(indices[0], data[0]);

        long coefficientsStart = System.currentTimeMillis();
        partialTransAmult(userFactors, indices, indexCount, userFactors, linearSystemCoefficients);
        for (int i = 0; i < rank; ++i)
            linearSystemCoefficients.add(i, i, weightedLambda);

        long valuesStart = System.currentTimeMillis();
        partialTransAmult(userFactors, indices, indexCount, userRatingSlice, linearSystemValues);

        long solveStart = System.currentTimeMillis();
        linearSystemCoefficients.solve(linearSystemValues, linearSystemSolution);

        long endTime = System.currentTimeMillis();

        for (int i = 0; i < rank; ++i)
            animeFactors.set(animeIndex, i, linearSystemSolution.get(i));

        preprocessTime += coefficientsStart - preprocessStart;
        coefficientTime += valuesStart - coefficientsStart;
        valueTime += solveStart - valuesStart;
        solveTime += endTime - solveStart;
    }
}
