package aqua.recommend;

import com.google.common.collect.Lists;

import aqua.mal.data.RatedBase;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ComputeEmbedding {
    public static class Parameters {
        public int rank;
        public int window;
        public int negativeProportion;
        public float frequencySmoothing;
        public float samplingAlpha;
    }

    private final float maxError = 1.2f;
    private final float leak = 0.05f;

    private final Parameters parameters;
    private final ModelType modelType;
    private final Map<Integer, Integer> animeIndexMap;
    private final float[] vectors, outputs;
    private final float[] updateVector;
    private final float[] frequencies;
    private final int[] negative;

    private float learningRate;
    private int currentItem;

    public final int animeCount, rank;

    public ComputeEmbedding(ModelType modelType, Map<Integer, Integer> animeIndexMap, Parameters parameters) {
        this.modelType = modelType;
        this.animeCount = animeIndexMap.size();
        this.animeIndexMap = animeIndexMap;
        this.parameters = parameters;
        this.rank = parameters.rank;
        this.vectors = new float[rank * animeCount];
        this.outputs = new float[rank * animeCount];
        this.updateVector = new float[rank];
        this.frequencies = new float[animeCount];
        this.negative = new int[1_000_000];

        initializeIteration();
    }

    public int[] itemMap() {
        int[] map = new int[animeCount];

        for (Map.Entry<Integer, Integer> entry : animeIndexMap.entrySet()) {
            map[entry.getValue()] = entry.getKey();
        }

        return map;
    }

    public float[] embedding() {
        return vectors;
    }

    private void initializeIteration() {
        Random rnd = new Random();
        float scale = 1 / (float) Math.sqrt(rank);

        int p = 0;
        for (int i = 0; i < animeCount; ++i) {
            for (int j = 0; j < rank; ++j) {
                vectors[p] = (rnd.nextFloat() - 0.5f) * scale;
                // outputs[p] = (rnd.nextFloat() - 0.5f) * scale;
                p++;
            }
        }
    }

    public void computeFrequencies(List<CFUser> users) {
        int itemCount = 0;

        for (CFUser user : users) {
            Iterable<CFRated> items = modelType.isManga() ? user.inProgressAndDropped() : user.completedAndDropped();
            for (CFRated item : items) {
                int itemIndex = animeIndexMap.get(item.animedbId);

                frequencies[itemIndex]++;
                itemCount++;
            }
        }

        float scale = 0.0f;
        for (int i = 0; i < frequencies.length; ++i) {
            frequencies[i] /= itemCount;
            scale += (float) Math.pow(frequencies[i], parameters.frequencySmoothing);
        }

        int j = 0, next = 0;
        float sum = 0.0f;
        for (int i = 0; i < frequencies.length; ++i) {
            sum += (float) Math.pow(frequencies[i], parameters.frequencySmoothing);
            next = (int) (negative.length * sum / scale);
            while (j < next) {
                negative[j] = i;
                j++;
            }
        }

    }

    public double trainEpoch(List<CFUser> users, float learningRate) {
        long itemCount = 0;
        double loss = 0;
        Random rnd = new Random();
        Set<Integer> used = new HashSet<>();

        this.learningRate = learningRate;
        for (CFUser user : users) {
            List<CFRated> contextItems = Lists
                    .newArrayList(modelType.isManga() ? user.inProgressAndDropped() : user.completedAndDropped());
            byte truncatedMean = (byte) user.ratingMean;

            contextItems.sort(new RatedBase.StableDescendingRating(rnd.nextInt()));
            int to = 0;
            for (int i = 0; i < contextItems.size(); ++i) {
                CFRated iItem = contextItems.get(i);
                int iItemIndex = animeIndexMap.get(iItem.animedbId);
                float a = frequencies[iItemIndex] / parameters.samplingAlpha;
                double threshold = (Math.sqrt(a) + 1) * (1 / a);
                if (threshold > rnd.nextDouble()) {
                    contextItems.set(to, iItem);
                    to++;
                }
            }
            contextItems = contextItems.subList(0, to);

            for (int i = 0; i < contextItems.size(); ++i) {
                CFRated iItem = contextItems.get(i);
                if (iItem.rating < truncatedMean)
                    break;
                int iItemIndex = animeIndexMap.get(iItem.animedbId);
                int currentBase = iItemIndex * rank;
                int positiveCount = 0;

                setCurrentItem(iItemIndex);
                used.clear();
                used.add(iItemIndex);
                Arrays.fill(updateVector, 0);

                for (int j = Math.max(i - parameters.window, 0), jMax = Math.min(i + parameters.window,
                        contextItems.size()); j < jMax; ++j) {
                    CFRated jItem = contextItems.get(j);
                    int jItemIndex = animeIndexMap.get(jItem.animedbId);
                    if (used.contains(jItemIndex))
                        continue;

                    loss += addContextItem(jItemIndex, outputs, 1);
                    used.add(jItemIndex);
                    itemCount++;
                    positiveCount++;
                }

                for (int s = 0, sMax = positiveCount * parameters.negativeProportion; s < sMax; ++s) {
                    int jItemIndex = negative[rnd.nextInt(negative.length)];
                    if (jItemIndex == i || used.contains(jItemIndex))
                        continue;

                    loss += addContextItem(jItemIndex, outputs, 0);
                    used.add(jItemIndex);
                    itemCount++;
                }

                for (int s = 0; s < rank; s++) {
                    vectors[currentBase + s] += updateVector[s] * learningRate;
                }
            }
        }

        return loss / itemCount;
    }

    private void setCurrentItem(int currentItem) {
        this.currentItem = currentItem;
    }

    private double addContextItem(int item, float[] itemVectors, float prediction) {
        int currentBase = currentItem * rank, itemBase = item * rank;
        float z = 0;

        for (int i = 0; i < rank; ++i)
            z += vectors[currentBase + i] * itemVectors[itemBase + i];

        float a = leakyReLu(z), e = prediction - a;
        if (Math.abs(e) > maxError)
            return Math.abs(e);
        float deltaOutput = e * leakyReLuDer(z);

        // System.out.println("z " + z + " dO " + deltaOutput);
        for (int i = 0; i < rank; ++i) {
            float deltaVectorI = itemVectors[itemBase + i] * deltaOutput;
            float deltaOutputI = vectors[currentBase + i] * deltaOutput;

            // activation == weight for previous layer
            itemVectors[itemBase + i] += deltaOutputI * learningRate;
            // input is one-hot, so activation is 1
            updateVector[i] += deltaVectorI;
        }

        return Math.abs(e);
    }

    private float leakyReLu(float x) {
        return Math.max(x, leak * x);
    }

    private float leakyReLuDer(float x) {
        return x <= 0 ? leak : 1;
    }
}
