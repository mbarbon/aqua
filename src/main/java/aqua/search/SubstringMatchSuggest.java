package aqua.search;

import aqua.recommend.HPPCUtils;
import com.carrotsearch.hppc.IntIntMap;
import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class SubstringMatchSuggest {
    private static final Pattern SPLIT_NONWORD = Pattern.compile("\\W+");

    private static class Entry {
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

    private static class PrefixMatch {
        public int matches = 1;
        public int matchRank = Integer.MAX_VALUE;
        public int animeRank;
        public int animedbId;

        public PrefixMatch(int animedbId, int animeRank) {
            this.animedbId = animedbId;
            this.animeRank = animeRank;
        }
    }

    private final List<Entry> entries;
    private final IntIntMap animeRank;

    public SubstringMatchSuggest(List<AnimeTitle> animeTitles, Map<Integer, Integer> animeRank) {
        this.animeRank = HPPCUtils.convertMap(animeRank);
        this.entries = makeEntryList(animeTitles);
    }

    public Suggestion[] suggest(String query, int limit) {
        List<String> parts = splitWords(query);
        Map<Integer, PrefixMatch> suggestionMap = new HashMap<>();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            Map<Integer, PrefixMatch> thisPart = new HashMap<>();
            int firstIndex = findFirst(part, 0, entries.size());

            for (; firstIndex < entries.size(); ++firstIndex) {
                Entry entry = entries.get(firstIndex);

                if (!entry.matchesStringPrefix(part))
                    break;
                int rank;

                if (entry.start != 0)
                    rank = entry.start * 2 - part.length() / 3 + (entry.string.length() - part.length()) * 3;
                else
                    rank = entry.string.length() - part.length();

                for (int animeId : entry.animedbIds) {
                    PrefixMatch suggestion = thisPart.computeIfAbsent(animeId, i -> new PrefixMatch(i, animeRank.getOrDefault(animeId, Integer.MAX_VALUE)));

                    suggestion.matchRank = Math.min(rank, suggestion.matchRank);
                }
            }

            for (Map.Entry<Integer, PrefixMatch> entry : thisPart.entrySet()) {
                PrefixMatch suggestion = suggestionMap.get(entry.getKey());
                if (suggestion != null) {
                    suggestion.matches += 1;
                    suggestion.matchRank += entry.getValue().matchRank;
                } else
                    suggestionMap.put(entry.getKey(), entry.getValue());
            }
        }

        List<PrefixMatch> suggestions = new ArrayList<>(suggestionMap.values());
        suggestions.sort(SubstringMatchSuggest::sortSuggestions);
        Suggestion[] result = new Suggestion[Math.min(limit, suggestions.size())];
        int index = 0;
        for (PrefixMatch suggestion : suggestions) {
            result[index++] = new Suggestion(suggestion.animedbId);
            if (index >= limit)
                break;
        }
        return result;
    }

    private static List<Entry> makeEntryList(List<AnimeTitle> titles) {
        Map<String, Set<Integer>> searchMap = new HashMap<>();
        for (AnimeTitle animeTitle : titles) {
            for (String word : splitWords(animeTitle.title)) {
                if (word.isEmpty()) {
                    continue;
                }
                Set<Integer> animedbIds = searchMap.computeIfAbsent(word, w -> new HashSet<>());
                animedbIds.add(animeTitle.animedbId);
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Set<Integer>> entry : searchMap.entrySet())
            insertEntry(entries, entry.getKey(), Ints.toArray(entry.getValue()));
        entries.sort(SubstringMatchSuggest::compareEntries);
        return entries;
    }

    private static List<String> splitWords(String title) {
        return Arrays.asList(SPLIT_NONWORD.split(title.toLowerCase()));
    }

    private static void insertEntry(List<Entry> entries, String string, int[] animedbIds) {
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

    private static int sortSuggestions(PrefixMatch a, PrefixMatch b) {
        if (a.matches != b.matches)
            return b.matches - a.matches;
        if (a.matchRank != b.matchRank)
            return a.matchRank - b.matchRank;
        if (a.animeRank != b.animeRank)
            return a.animeRank - b.animeRank;
        return 0;
    }
}
