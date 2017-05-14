package aqua.recommend;

import java.util.concurrent.ThreadLocalRandom;

import no.uib.cipr.matrix.DenseMatrix;

import no.uib.cipr.matrix.sparse.SparseVector;
import no.uib.cipr.matrix.sparse.FlexCompRowMatrix;

public class MatrixUtils {
    // Uses Achlioptas' version of random projection to reduce the dimension
    // of the matrix, and then it normalizes all rows to be unit-length vectors
    //
    // It uses the fact that the source matrix is extremely sparse
    // (10-1000 non-zero items per row, while the row size is on the
    // order of 10000 items
    public static DenseMatrix reduceDimensionAndNormalize(FlexCompRowMatrix vectors, int targetSize) {
        byte[] projectionMatrix = new byte[vectors.numColumns() * targetSize];
        DenseMatrix target = new DenseMatrix(vectors.numRows(), targetSize);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int i = 0; i < vectors.numColumns(); ++i) {
            for (int j = 0; j < targetSize; ++j) {
                int which = rnd.nextInt(6);
                // the values here should be +-Math.sqrt(3), but we're
                // going to normalize the vectors anyway
                if (which == 0)
                    projectionMatrix[j * vectors.numColumns() + i] = 1;
                else if (which == 1)
                    projectionMatrix[j * vectors.numColumns() + i] = -1;
            }
        }

        randomProjectionMult(vectors, projectionMatrix, targetSize, target);

        // treat each row as a vector and make it unit length
        double[] norms = new double[target.numRows()];
        double[] data = target.getData();

        for (int j = 0, currentIndex = 0; j < target.numColumns(); ++j) {
            for (int i = 0; i < norms.length; ++i, ++currentIndex) {
                norms[i] += data[currentIndex] * data[currentIndex];
            }
        }

        for (int i = 0; i < norms.length; ++i) {
            norms[i] = Math.sqrt(norms[i]);
            // avoid NaN
            if (norms[i] == 0)
                norms[i] = 1;
        }

        for (int j = 0, currentIndex = 0; j < target.numColumns(); ++j) {
            for (int i = 0; i < norms.length; ++i, ++currentIndex) {
                data[currentIndex] /= norms[i];
            }
        }

        return target;
    }

    private static void randomProjectionMult(FlexCompRowMatrix A, byte[] B, int targetSize, DenseMatrix C) {
        int itemCount = A.numColumns();
        for (int i = 0; i < A.numRows(); ++i) {
            SparseVector Ar = A.getRow(i);
            for (int j = 0; j < targetSize; ++j) {
                int base = j * itemCount;

                C.set(i, j, randomProjectionDot(Ar, B, base));
            }
        }
    }

    private static double randomProjectionDot(SparseVector A, byte[] B, int bBase) {
        double[] aData = A.getData();
        int[] aIndices = A.getIndex();
        int aUsed = A.getUsed();
        double dot = 0;
        if (aUsed == 0)
            return dot;

        for (int ai = 0; ai < aUsed; ++ai) {
            int currentA = aIndices[ai];
            byte projection = B[bBase + currentA];
            if (projection == 1)
                dot += aData[ai];
            else if (projection == -1)
                dot -= aData[ai];
        }

        return dot;
    }
}
