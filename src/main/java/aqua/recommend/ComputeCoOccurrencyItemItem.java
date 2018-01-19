package aqua.recommend;

import aqua.mal.data.Anime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComputeCoOccurrencyItemItem {
    private final Map<Integer, Anime> animeMap;
    private final Map<Integer, Integer> animeIndexMap;
    private final int similarAnimeCount;
    private final float[] animeCounts;
    private final int[] similarAnimeId;
    private final float[] similarAnimeScore;

    public ComputeCoOccurrencyItemItem(Map<Integer, Anime> animeMap, Map<Integer, Integer> animeIndexMap, int similarAnimeCount) {
        this.animeMap = animeMap;
        this.animeIndexMap = animeIndexMap;
        this.similarAnimeCount = similarAnimeCount;
        this.animeCounts = new float[animeIndexMap.size() * animeIndexMap.size()];
        this.similarAnimeId = new int[animeIndexMap.size() * similarAnimeCount];
        this.similarAnimeScore = new float[animeIndexMap.size() * similarAnimeCount];
    }

    public void findSimilarAnime(List<CFUser> users, float goodScoreThreshold, float alpha) {
        countoCoOccurencies(users, goodScoreThreshold, alpha);
        fillSimilarAnime();
    }

    public ItemItemModel simpleItemItem() {
        return new ItemItemModel(animeIndexMap, similarAnimeCount, similarAnimeId, similarAnimeScore);
    }

    private void countoCoOccurencies(List<CFUser> users, float goodScoreThreshold, float alpha) {
        int rowStride = animeIndexMap.size();
        int[] animeTotalOccurrences = new int[rowStride];

        for (CFUser user : users) {
            int[] highlyratedAnimeIndices = new int[user.completedAndDroppedIds.length];
            int highlyRatedCount = 0, userAnimeCount = 0;

            // find highly rated items
            for (int i = 0, max = user.completedAndDroppedIds.length; i < max; ++i) {
                if (user.completedAndDroppedRating[i] > goodScoreThreshold) {
                    Integer index = animeIndexMap.get(user.completedAndDroppedIds[i]);
                    if (index != null) {
                        userAnimeCount++;
                        animeTotalOccurrences[index]++;
                        highlyratedAnimeIndices[highlyRatedCount++] = index;
                    }
                }
            }

            // add co-occurring highly rated anime to the count matrix
            if (userAnimeCount != 0) {
                // normalize the vector to unit length
                float normalizedScore = 1.0f / (float) Math.sqrt(userAnimeCount);

                for (int i = 0; i < highlyRatedCount; ++i) {
                    int startIndex = highlyratedAnimeIndices[i] * rowStride;
                    for (int j = 0; j < highlyRatedCount; ++j) {
                        if (i != j) {
                            animeCounts[startIndex + highlyratedAnimeIndices[j]] += normalizedScore;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < rowStride; ++i) {
            if (animeTotalOccurrences[i] == 0) {
                continue;
            }
            float frequencyI = animeTotalOccurrences[i];
            for (int j = 0; j < rowStride; ++j) {
                if (animeTotalOccurrences[j] == 0) {
                    continue;
                }
                float frequencyJ = animeTotalOccurrences[j];
                animeCounts[i * rowStride + j] /= frequencyI * Math.pow(frequencyJ, alpha);
            }
        }
    }

    private void fillSimilarAnime() {
        int rowStride = animeIndexMap.size();
        int[] animeRatedMap = new int[animeIndexMap.size()];
        for (Map.Entry<Integer, Integer> entry : animeIndexMap.entrySet())
            animeRatedMap[entry.getValue()] = entry.getKey();

        for (int i = 0, rowStart = 0; rowStart < animeCounts.length; ++i, rowStart += rowStride) {
            int animedbId = animeRatedMap[i];
            List<ScoredAnimeId> similarAnme = new ArrayList<>();
            for (int j = 0; j < rowStride; ++j) {
                float score = animeCounts[rowStart + j];
                if (score == 0) {
                    continue;
                }
                similarAnme.add(new ScoredAnimeId(animeRatedMap[j], -score));
            }
            similarAnme.sort(ScoredAnimeId.SORT_SCORE);

            Set<Integer> seenFranchises = new HashSet<>();
            addFranchise(animedbId, seenFranchises);
            for (int j = 0; j < similarAnimeCount && j < similarAnme.size(); ++j) {
                ScoredAnimeId scoredAnimeId = similarAnme.get(j);
                if (addFranchise(scoredAnimeId.animedbId, seenFranchises))
                    continue;
                int index = i * similarAnimeCount + j;
                similarAnimeId[index] = scoredAnimeId.animedbId;
                similarAnimeScore[index] = scoredAnimeId.score;
            }
        }
    }

    private boolean addFranchise(int animedbId, Set<Integer> seenFranchises) {
        Anime anime = animeMap.get(animedbId);
        if (anime != null && anime.franchise != null) {
            if (seenFranchises.contains(anime.franchise.franchiseId))
                return true;
            seenFranchises.add(anime.franchise.franchiseId);
        }
        return false;
    }
}
