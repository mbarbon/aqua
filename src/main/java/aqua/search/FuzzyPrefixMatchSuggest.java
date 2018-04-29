package aqua.search;

import aqua.recommend.HPPCUtils;
import com.carrotsearch.hppc.IntIntMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class FuzzyPrefixMatchSuggest {
    private static final Pattern SPLIT_NONWORD = Pattern.compile("[^a-z0-9]+");
    private static final AnimeTitle[] EMPTY_ANIME_TITLES = new AnimeTitle[0];
    private static final Suggestion[] EMPTY_SUGGESTIONS = new Suggestion[0];
    private static final WordEntry[] EMPTY_WORD_ENTRY = new WordEntry[0];

    private static class WordEntry {
        public final String word;
        public final AnimeTitle[] titles;

        public WordEntry(String word, AnimeTitle[] titles) {
            this.word = word;
            this.titles = titles;
        }

        public WordEntry(String word, Collection<AnimeTitle> titles) {
            this(word, titles.toArray(EMPTY_ANIME_TITLES));
        }

        @Override
        public int hashCode() {
            return word.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof WordEntry)
                return equals((WordEntry) o);
            return false;
        }

        public boolean equals(WordEntry o) {
            return word.equals(o.word);
        }
    }

    // only handles 0-9 and a-z
    private static int charIndex(char c) {
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 10;
        } else if (c >= '0' && c <= '9') {
            return c - '0';
        }
        throw new IllegalArgumentException(String.format("Invalid trie character '%c'", c));
    }

    private static class TrieNode {
        public static final TrieNode[] EMPTY_TRIE_CHILDREN = new TrieNode[0];

        public final TrieNode[] children;
        public final WordEntry[] directMatches;

        public TrieNode(TrieNode[] children, WordEntry[] directMatches) {
            this.children = children;
            this.directMatches = directMatches;
        }

        public TrieNode advance(char c) {
            return children != EMPTY_TRIE_CHILDREN ?
                children[charIndex(c)] :
                null;
        }
    }

    public static class MutableTrieNode {
        public MutableTrieNode[] children;
        public List<WordEntry> directMatches;

        public MutableTrieNode advance(char c) {
            if (children == null) {
                children = new MutableTrieNode[36];
            }
            int index = charIndex(c);
            if (children[index] == null) {
                children[index] = new MutableTrieNode();
            }
            return children[index];
        }

        public void addTitles(WordEntry wordEntry) {
            if (directMatches == null) {
                directMatches = new ArrayList<>();
            }
            directMatches.add(wordEntry);
        }

        public TrieNode toImmutableNode() {
            TrieNode[] immutableChildren = TrieNode.EMPTY_TRIE_CHILDREN;
            if (children != null) {
                immutableChildren = new TrieNode[children.length];
                for (int i = 0, max = children.length; i < max; ++i) {
                    immutableChildren[i] = children[i] != null ?
                        children[i].toImmutableNode() :
                        null;
                }
            }
            return new TrieNode(
                immutableChildren,
                directMatches != null ? directMatches.toArray(EMPTY_WORD_ENTRY) : EMPTY_WORD_ENTRY
            );
        }
    }

    private static class FuzzyPrefixMatch {
        public final AnimeTitle animeTitle;
        public final int animeRank;
        public int skipped, distance;
        public int level;
        public int matches;

        public FuzzyPrefixMatch(AnimeTitle animeTitle, int animeRank) {
            this.animeTitle = animeTitle;
            this.animeRank = animeRank;
        }

        public void update(XYZ entry, TrieNode trieNode, int level) {
            int skipped = Math.abs(entry.wordOffset - entry.prefixOffset);
            if (this.matches == 0 || this.skipped > skipped || (this.skipped == skipped && this.distance > entry.distance)) {
                this.skipped = skipped;
                this.distance = entry.distance;
                this.level = level;
                this.matches = 1;
            }
        }

        public void merge(FuzzyPrefixMatch o) {
            skipped += o.skipped;
            distance += o.distance;
            level = Math.max(level, o.level);
            matches++;
        }

        @Override
        public String toString() {
            return String.format(
                "[%s] skipped=%d distance=%d level=%d matches=%d",
                animeTitle.title,
                skipped, distance, level, matches
            );
        }
    }

    private final WordEntry[] words;
    private final TrieNode prefixes;
    private final IntIntMap animeRank;

    public FuzzyPrefixMatchSuggest(List<AnimeTitle> animeTitles, Map<Integer, Integer> animeRank) {
        this.animeRank = HPPCUtils.convertMap(animeRank);
        this.words = makeWords(animeTitles);
        this.prefixes = makePrefixes(this.words);
    }

    private static WordEntry[] makeWords(List<AnimeTitle> animeTitles) {
        Map<String, Set<AnimeTitle>> wordMap = new HashMap<>();

        for (AnimeTitle animeTitle : animeTitles) {
            for (String word : splitWords(animeTitle.title)) {
                Set<AnimeTitle> titleSet = wordMap.computeIfAbsent(word, w -> new HashSet<>());
                titleSet.add(animeTitle);
            }
        }

        return wordMap.entrySet().stream()
            .map(e -> new WordEntry(e.getKey(), e.getValue()))
            .toArray(WordEntry[]::new);
    }

    private static TrieNode makePrefixes(WordEntry[] words) {
        MutableTrieNode root = new MutableTrieNode();

        for (WordEntry wordEntry : words) {
            MutableTrieNode current = root;

            for (int i = 0, max = wordEntry.word.length(); i < max; ++i) {
                char c = wordEntry.word.charAt(i);
                current = current.advance(c);
            }

            current.addTitles(wordEntry);
        }

        return root.toImmutableNode();
    }

    public Suggestion[] suggest(String query, int limit) {
        Map<AnimeTitle, FuzzyPrefixMatch> suggestionMap = new HashMap<>();

        for (String word : splitWords(query)) {
            Map<AnimeTitle, FuzzyPrefixMatch> wordMatches = matchXXX(word);

            for (Map.Entry<AnimeTitle, FuzzyPrefixMatch> entry : wordMatches.entrySet()) {
                FuzzyPrefixMatch match = suggestionMap.putIfAbsent(entry.getKey(), entry.getValue());
                if (match != null) {
                    match.merge(entry.getValue());
                }
            }
        }

        Set<Integer> seenAnime = new HashSet<>();
        List<FuzzyPrefixMatch> suggestions = new ArrayList<>(suggestionMap.values());
        suggestions.sort(FuzzyPrefixMatchSuggest::sortSuggestions);
        List<Suggestion> result = new ArrayList<>(limit);
        for (FuzzyPrefixMatch suggestion : suggestions) {
            AnimeTitle animeTitle = suggestion.animeTitle;
            if (!seenAnime.add(animeTitle.animedbId))
                continue;
            result.add(new Suggestion(animeTitle.animedbId, animeTitle.title));
            if (result.size() >= limit)
                break;
        }
        return result.toArray(EMPTY_SUGGESTIONS);
    }

    private static List<String> splitWords(String title) {
        return Arrays.asList(SPLIT_NONWORD.split(title.toLowerCase()));
    }

    private static class XYZ {
        final TrieNode trieNode;
        final int wordOffset, prefixOffset;
        final int distance, maxDistance;

        public XYZ(TrieNode trieNode, int wordOffset, int prefixOffset, int distance, int maxDistance) {
            this.trieNode = trieNode;
            this.wordOffset = wordOffset;
            this.prefixOffset = prefixOffset;
            this.distance = distance;
            this.maxDistance = maxDistance;
        }

        @Override
        public int hashCode() {
            return trieNode.hashCode() ^ (prefixOffset * 5) ^ (wordOffset * 7) ^ (distance * 11);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof XYZ)
                return equals((XYZ) o);
            return false;
        }

        public boolean equals(XYZ o) {
            return wordOffset == o.wordOffset &&
                prefixOffset == o.prefixOffset &&
                distance == o.distance &&
                trieNode.equals(o.trieNode);
        }

        public boolean canConsume(String prefix) {
            return prefixOffset < prefix.length();
        }

        public boolean canError() {
            return distance < maxDistance;
        }

        public void addExact(Set<XYZ> partials, String prefix) {
            TrieNode nextTrieNode = trieNode.advance(prefix.charAt(prefixOffset));
            if (nextTrieNode != null) {
                partials.add(new XYZ(nextTrieNode, wordOffset + 1, prefixOffset + 1, distance, maxDistance));
            }
        }

        public void addDeletion(Set<XYZ> partials, String prefix) {
            for (TrieNode child : trieNode.children) {
                if (child != null) {
                    partials.add(new XYZ(child, wordOffset + 1, prefixOffset, distance + 1, maxDistance));
                }
            }
        }

        public void addInsertion(Set<XYZ> partials, String prefix) {
            if (prefixOffset + 1 < prefix.length()) {
                partials.add(new XYZ(trieNode, wordOffset, prefixOffset + 1, distance + 1, maxDistance));
            }
        }

        public void addReplacement(Set<XYZ> partials, String prefix) {
            if (prefixOffset + 1 < prefix.length()) {
                for (TrieNode child : trieNode.children) {
                    if (child != null) {
                        partials.add(new XYZ(child, wordOffset + 1, prefixOffset + 1, distance + 1,
                            maxDistance));
                    }
                }
            }
        }

        public void addTransposition(Set<XYZ> partials, String prefix) {
            if (prefixOffset + 2 < prefix.length()) {
                TrieNode firstChar = trieNode.advance(prefix.charAt(prefixOffset));
                TrieNode secondChar = firstChar != null ?
                    firstChar.advance(prefix.charAt(prefixOffset + 1)) :
                    null;

                if (secondChar != null) {
                    partials.add(new XYZ(secondChar, wordOffset + 2, prefixOffset + 2, distance + 1, maxDistance));
                }
            }
        }
    }

    private Map<AnimeTitle, FuzzyPrefixMatch> matchXXX(String prefix) {
        Set<XYZ> currentPartials = new HashSet<>();
        Set<XYZ> acceptedPartials = new HashSet<>();
        int maxDistance =
            prefix.length() <= 2 ? 0 :
                prefix.length() <= 5 ? 1 :
                    2;

        currentPartials.add(new XYZ(prefixes, 0, 0, 0, maxDistance));

        while (!currentPartials.isEmpty()) {
            Set<XYZ> newPartials = new HashSet<>();

            for (XYZ partial : currentPartials) {
                if (partial.canConsume(prefix)) {
                    partial.addExact(newPartials, prefix);
                    if (partial.canError()) {
                        partial.addDeletion(newPartials, prefix);
                        partial.addInsertion(newPartials, prefix);
                        partial.addReplacement(newPartials, prefix);
                        partial.addTransposition(newPartials, prefix);
                    }
                } else {
                    acceptedPartials.add(partial);
                }
            }

            currentPartials = newPartials;
        }

        Map<AnimeTitle, FuzzyPrefixMatch> result = new HashMap<>(acceptedPartials.size());
        for (XYZ entry : acceptedPartials) {
            collectTitles(result, entry, entry.trieNode,0);
        }
        return result;
    }

    private void collectTitles(Map<AnimeTitle, FuzzyPrefixMatch> result, XYZ entry, TrieNode trieNode, int level) {
        for (WordEntry wordEntry : trieNode.directMatches) {
            for (AnimeTitle title : wordEntry.titles) {
                FuzzyPrefixMatch match = result.computeIfAbsent(title, i -> new FuzzyPrefixMatch(i, animeRank.getOrDefault(i.animedbId, Integer.MAX_VALUE)));

                match.update(entry, trieNode, level);
            }
        }
        for (TrieNode child : trieNode.children) {
            if (child != null) {
                collectTitles(result, entry, child, level + 1);
            }
        }
    }

    private static int sortSuggestions(FuzzyPrefixMatch a, FuzzyPrefixMatch b) {
        if (a.matches != b.matches)
            return b.matches - a.matches;
        int aScore = a.distance * 10 / a.matches;
        int bScore = b.distance * 10 / b.matches;
        if (aScore != bScore)
            return aScore - bScore;
        if (a.skipped != b.skipped)
            return a.skipped - b.skipped;
        if (a.animeRank != b.animeRank)
            return a.animeRank - b.animeRank;
        return a.level - b.level;
    }
}
