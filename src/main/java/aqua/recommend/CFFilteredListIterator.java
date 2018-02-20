package aqua.recommend;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class CFFilteredListIterator implements Iterable<CFRated>, Iterator<CFRated> {
    private final int[] packedAnimeList;
    private final int mask;
    private int nextIndex = -1;

    public CFFilteredListIterator(int[] packedAnimeList, int mask) {
        this.packedAnimeList = packedAnimeList;
        this.mask = mask;
        this.nextIndex = findNext();
    }

    @Override
    public Iterator<CFRated> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return nextIndex != -1;
    }

    @Override
    public CFRated next() {
        if (nextIndex == -1)
            throw new NoSuchElementException();
        CFRated result = new CFRated(packedAnimeList[nextIndex]);
        nextIndex = findNext();
        return result;
    }

    private int findNext() {
        for (int i = nextIndex + 1, max = packedAnimeList.length; i < max; ++i) {
            if ((mask & (1 << CFRated.unpackStatus(packedAnimeList[i]))) != 0)
                return i;
        }
        return -1;
    }
}
