package aqua.recommend;

import aqua.mal.data.Anime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComputeCoOccurrencyItemItem {
    private interface AnimeIterator {
        void reset(CFUser user);
        int maxSize();
        boolean next();
        int animedbId();
        float normalizedRating();
    }

    private class CompletedAndDroppedAnime implements AnimeIterator {
        private final float goodScoreThreshold;
        private CFUser user;
        private int index;

        public CompletedAndDroppedAnime(float goodScoreThreshold) {
            this.goodScoreThreshold = goodScoreThreshold;
        }

        @Override
        public void reset(CFUser user) {
            this.user = user;
            this.index = -1;
        }

        @Override
        public int maxSize() {
            return user.completedAndDroppedIds.length;
        }

        @Override
        public boolean next() {
            if (index >= user.completedAndDroppedIds.length) {
                return false;
            }
            do {
                ++index;
            } while (index < user.completedAndDroppedIds.length && normalizedRating() < goodScoreThreshold);
            if (index >= user.completedAndDroppedIds.length) {
                return false;
            }
            return true;
        }

        @Override
        public int animedbId() {
            return user.completedAndDroppedIds[index];
        }

        @Override
        public float normalizedRating() {
            return user.completedAndDroppedRatingFloat(index);
        }
    }

    private class AiringAnime implements AnimeIterator {
        private final Map<Integer, Anime> animeMap;
        private CFUser user;
        private int index;
        private CFRated current;
        private List<CFRated> watching;

        public AiringAnime(Map<Integer, Anime> animeMap) {
            this.animeMap = animeMap;
        }

        @Override
        public void reset(CFUser user) {
            this.user = user;
            this.index = -1;
            this.watching = new ArrayList<>();

            for (CFRated rated : user.watching()) {
                this.watching.add(rated);
            }
        }

        @Override
        public int maxSize() {
            return watching.size();
        }

        @Override
        public boolean next() {
            if (index >= watching.size()) {
                return false;
            }
            for (;;) {
                ++index;
                if (index >= watching.size()) {
                    return false;
                }
                current = watching.get(index);
                Anime anime = animeMap.get(current.animedbId);
                if (anime != null && anime.status == Anime.AIRING) {
                    return true;
                }
            }
            // can't get here
        }

        @Override
        public int animedbId() {
            return current.animedbId;
        }

        @Override
        public float normalizedRating() {
            return user.normalizedRating(current);
        }
    }

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
        countCoOccurencies(users, new CompletedAndDroppedAnime(goodScoreThreshold), new CompletedAndDroppedAnime(goodScoreThreshold), alpha);
        fillSimilarAnime();
    }

    public void findSimilarAiringAnime(List<CFUser> users, float goodScoreThreshold, float alpha) {
        countCoOccurencies(users, new CompletedAndDroppedAnime(goodScoreThreshold), new AiringAnime(animeMap), alpha);
        fillSimilarAnime();
    }

    public ItemItemModel simpleItemItem() {
        return new ItemItemModel(animeIndexMap, similarAnimeCount, similarAnimeId, similarAnimeScore);
    }

    private void countCoOccurencies(List<CFUser> users, AnimeIterator watched, AnimeIterator coOccurring, float alpha) {
        int rowStride = animeIndexMap.size();
        int[] animeTotalOccurrences = new int[rowStride];

        for (CFUser user : users) {
            watched.reset(user);
            int[] highlyratedAnimeIndices = new int[watched.maxSize()];
            int highlyRatedCount = 0, userAnimeCount = 0;

            // find highly rated completed items or any airing item
            while (watched.next()) {
                Integer index = animeIndexMap.get(watched.animedbId());
                if (index != null) {
                    userAnimeCount++;
                    animeTotalOccurrences[index]++;
                    highlyratedAnimeIndices[highlyRatedCount++] = index;
                }
            }

            // find co-occurring anime
            coOccurring.reset(user);
            int[] coOccurringAnimeIndices = new int[coOccurring.maxSize()];
            int coOccurringCount = 0;

            // find highly rated completed items or any airing item
            while (coOccurring.next()) {
                Integer index = animeIndexMap.get(coOccurring.animedbId());
                if (index != null) {
                    coOccurringAnimeIndices[coOccurringCount++] = index;
                }
            }

            // add co-occurring highly rated anime to the count matrix
            if (userAnimeCount != 0 && coOccurringCount != 0) {
                // normalize the vector to unit length
                float normalizedScore = 1.0f / (float) Math.sqrt(userAnimeCount);

                for (int i = 0; i < highlyRatedCount; ++i) {
                    int startIndex = highlyratedAnimeIndices[i] * rowStride;
                    for (int j = 0; j < coOccurringCount; ++j) {
                        if (highlyratedAnimeIndices[i] != coOccurringAnimeIndices[j]) {
                            animeCounts[startIndex + coOccurringAnimeIndices[j]] += normalizedScore;
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
            int index = i * similarAnimeCount;
            for (int j = 0; j < similarAnimeCount && j < similarAnme.size(); ++j) {
                ScoredAnimeId scoredAnimeId = similarAnme.get(j);
                if (addFranchise(scoredAnimeId.animedbId, seenFranchises))
                    continue;
                similarAnimeId[index] = scoredAnimeId.animedbId;
                similarAnimeScore[index] = scoredAnimeId.score;
                ++index;
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
