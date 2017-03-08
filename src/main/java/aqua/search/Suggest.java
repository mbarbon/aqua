package aqua.search;

import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Suggest {
    private class Entry {
        public String string;
        public int start;
        public int[] animedbIds;

        public Entry(String string, int start, int[] animedbIds) {
            this.string = string;
            this.start = start;
            this.animedbIds = animedbIds;
        }

        public boolean matchesStringPrefix(String s) {
            int thisLen = string.length() - start;
            int sLen = s.length();

            for (int i = 0, max = Math.min(thisLen, sLen); i < max; ++i) {
                char thisChar = string.charAt(start + i);
                char sChar = s.charAt(i);

                if (thisChar != sChar)
                    return false;
            }

            return true;
        }

        public int compareString(String s) {
            int thisLen = string.length() - start;
            int sLen = s.length();

            for (int i = 0, max = Math.min(thisLen, sLen); i < max; ++i) {
                char thisChar = string.charAt(start + i);
                char sChar = s.charAt(i);

                if (thisChar != sChar)
                    return thisChar - sChar;
            }

            if (thisLen != sLen)
                return thisLen - sLen;
            return 0;
        }
    }

    private class Suggestion {
        public int matches = 1;
        public int matchRank = Integer.MAX_VALUE;
        public int animeRank;
        public int animedbId;

        public Suggestion(int animedbId, int animeRank) {
            this.animedbId = animedbId;
            this.animeRank = animeRank;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<Integer, Integer> animeRank;

    public Suggest(Map<String, Collection<Integer>> searchMap, Map<Integer, Integer> animeRank) {
        this.animeRank = animeRank;

        for (Map.Entry<String, Collection<Integer>> entry : searchMap.entrySet())
            insertEntry(entry.getKey(), Ints.toArray(entry.getValue()));
        entries.sort(Suggest::compareEntries);
    }

    public int[] suggest(List<String> parts, int limit) {
        Map<Integer, Suggestion> suggestionMap = new HashMap<>();

        for (String part : parts) {
            Map<Integer, Suggestion> thisPart = new HashMap<>();
            int firstIndex = findFirst(part, 0, entries.size());

            for (;; ++firstIndex) {
                Entry entry = entries.get(firstIndex);

                if (!entry.matchesStringPrefix(part))
                    break;
                int rank;

                if (entry.start != 0)
                    rank = entry.start * 2 - part.length() / 3 + (entry.string.length() - part.length()) * 3;
                else
                    rank = entry.string.length() - part.length();

                for (int animeId : entry.animedbIds) {
                    Suggestion suggestion = thisPart.computeIfAbsent(animeId, i -> new Suggestion(i, animeRank.getOrDefault(animeId, Integer.MAX_VALUE)));

                    suggestion.matchRank = Math.min(rank, suggestion.matchRank);
                }
            }

            for (Map.Entry<Integer, Suggestion> entry : thisPart.entrySet()) {
                Suggestion suggestion = suggestionMap.get(entry.getKey());
                if (suggestion != null) {
                    suggestion.matches += 1;
                    suggestion.matchRank += entry.getValue().matchRank;
                } else
                    suggestionMap.put(entry.getKey(), entry.getValue());
            }
        }

        List<Suggestion> suggestions = new ArrayList<>(suggestionMap.values());

        suggestions.sort(Suggest::sortSuggestions);

        return suggestions.stream()
            .limit(limit)
            .mapToInt(e -> e.animedbId)
            .toArray();
    }

    private void insertEntry(String string, int[] animedbIds) {
        for (int i = 0, max = Math.max(1, string.length() - 1); i < max; ++i)
            entries.add(new Entry(string, i, animedbIds));
    }

    private int findFirst(String part, int start, int end) {
        while (start < end) {
            int mid = (start + end) / 2;
            Entry entry = entries.get(mid);
            int cmp = entry.compareString(part);

            if (cmp >= 0) {
                end = mid;
            } else {
                start = mid + 1;
            }
        }

        return start;
    }

    private static int compareEntries(Entry a, Entry b) {
        String as = a.string, bs = b.string;
        int ai = a.start, bi = b.start;
        int am = as.length(), bm = bs.length();

        while (ai < am && bi < bm) {
            char ac = as.charAt(ai), bc = bs.charAt(bi);
            if (ac != bc)
                return ac - bc;
            ++ai;
            ++bi;
        }

        if (ai != am || bi != bm)
            return ai != am ? 1 : -1;
        if (a.start != b.start)
            return a.start - b.start;
        return 0;
    }

    private static int sortSuggestions(Suggestion a, Suggestion b) {
        if (a.matches != b.matches)
            return b.matches - a.matches;
        if (a.matchRank != b.matchRank)
            return a.matchRank - b.matchRank;
        if (a.animeRank != b.animeRank)
            return a.animeRank - b.animeRank;
        return 0;
    }
}
