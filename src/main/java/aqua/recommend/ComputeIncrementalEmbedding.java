package aqua.recommend;

import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.IntIntMap;
import com.carrotsearch.hppc.cursors.IntIntCursor;
import java.util.Arrays;
import java.util.Random;

public class ComputeIncrementalEmbedding {
    public static class Parameters {
        public int rank;
        public float learningRate;
        public float frequencySmoothing;
    }

    public static class State {
        public int rank;
        public IntIntMap itemToIndex;
        public float[] vectors, outputs;
        public int[] itemHistogram;
    }

    private static final int NEGATIVE_SAMPLE_SIZE = 1_000_000;
    private static final int INITIAL_SIZE = 4;
    private static final float MAX_EXP = 6;

    private final Parameters parameters;
    private final IntIntMap itemToIndex = new IntIntHashMap();
    private final int rank;
    private final float learningRate;
    private float[] vectors, outputs;

    private int iItemId, iItemIndex;
    private final float[] updateVector;

    private int itemHistogramCount;
    private int[] itemHistogram;
    private int[] negativeSample;
    private final Random negativeSampleRnd = new Random();

    public ComputeIncrementalEmbedding(Parameters parameters) {
        this.parameters = parameters;
        this.rank = parameters.rank;
        this.learningRate = parameters.learningRate;
        this.updateVector = new float[rank];
        this.vectors = new float[0];
        this.outputs = new float[0];
        this.itemHistogram = new int[0];
        this.negativeSample = new int[NEGATIVE_SAMPLE_SIZE];

        growModel(INITIAL_SIZE);
    }

    public State saveState() {
        State state = new State();

        state.rank = rank;
        state.itemToIndex = itemToIndex;
        state.itemHistogram = itemHistogram;
        state.outputs = outputs;
        state.vectors = vectors;

        return state;
    }

    public void loadState(State state) {
        if (rank != state.rank) {
            throw new IllegalArgumentException("Attempting to change model rank");
        }

        this.itemHistogram = state.itemHistogram;
        this.itemHistogramCount = 0;
        for (int itemCount : this.itemHistogram) {
            this.itemHistogramCount += itemCount;
        }

        this.itemToIndex.clear();
        for (IntIntCursor item : state.itemToIndex) {
            this.itemToIndex.put(item.key, item.value);
        }

        this.outputs = state.outputs;
        this.vectors = state.vectors;

        updateNegativeSample();
    }

    public void beginLearning(int iItemId) {
        this.iItemId = iItemId;
        this.iItemIndex = lookupIndex(iItemId);

        Arrays.fill(updateVector, 0);
    }

    public void endLearning() {
        int currentBase = iItemIndex * rank;

        for (int s = 0; s < rank; s++) {
            vectors[currentBase + s] += updateVector[s] * learningRate;
        }
        iItemIndex = iItemId = -1;
    }

    public void updateItemHistogram(int itemId) {
        int itemIndex = lookupIndex(itemId);

        itemHistogramCount++;
        itemHistogram[itemIndex]++;
    }

    public void updateNegativeSample() {
        int histogramSize = itemToIndex.size();
        float scale = 0.0f;
        for (int i = 0; i < histogramSize; ++i) {
            scale += (float) Math.pow(itemHistogram[i] / (float) itemHistogramCount, parameters.frequencySmoothing);
        }

        int j = 0, next = 0;
        float sum = 0.0f;
        for (int i = 0; i < histogramSize; ++i) {
            sum += (float) Math.pow(itemHistogram[i] / (float) itemHistogramCount, parameters.frequencySmoothing);
            next = (int) (negativeSample.length * sum / scale);
            while (j < next) {
                negativeSample[j] = i;
                j++;
            }
        }
    }

    public int negativeSample() {
        return negativeSample[negativeSampleRnd.nextInt(negativeSample.length)];
    }

    public double addPositiveSample(int jItemId) {
        return addSample(lookupIndex(jItemId), 1);
    }

    public double addNegativeSample(int jItemId) {
        return addSample(lookupIndex(jItemId), 0);
    }

    public double addSample(int jItemIndex, float prediction) {
        int currentBase = iItemIndex * rank, itemBase = jItemIndex * rank;
        float z = 0;

        for (int i = 0; i < rank; ++i)
            z += vectors[currentBase + i] * outputs[itemBase + i];

        float a = sigmoid(z), error = prediction - a;

        for (int i = 0; i < rank; ++i) {
            float deltaVectorI = outputs[itemBase + i] * error;
            float deltaOutputI = vectors[currentBase + i] * error;

            // activation == weight for previous layer
            outputs[itemBase + i] += deltaOutputI * learningRate;
            // input is one-hot, so activation is 1
            updateVector[i] += deltaVectorI;
        }

        return Math.abs(error);
    }

    private int lookupIndex(int itemId) {
        int index = itemToIndex.getOrDefault(itemId, -1);

        if (index == -1) {
            index = itemToIndex.size();
            int maxIndex = vectors.length / rank;

            if (index == maxIndex) {
                growModel(maxIndex * 3 / 2);
            }
            itemToIndex.put(itemId, index);
        }

        return index;
    }

    private void growModel(int toSize) {
        Random rnd = new Random();
        float scale = 1 / (float) Math.sqrt(rank);
        int currentSize = vectors.length;

        vectors = Arrays.copyOf(vectors, toSize * rank);
        outputs = Arrays.copyOf(outputs, toSize * rank);

        for (int i = currentSize; i < vectors.length; ++i) {
            vectors[i] = (rnd.nextFloat() - 0.5f) * scale;
            // outputs[i] = (rnd.nextFloat() - 0.5f) * scale;
        }

        itemHistogram = Arrays.copyOf(itemHistogram, toSize);
    }

    private float sigmoid(float x) {
        if (x < -MAX_EXP) {
            return 0;
        }
        if (x > MAX_EXP) {
            return 1;
        }
        return (float) (1 / (1 + Math.exp(-x)));
    }
}
