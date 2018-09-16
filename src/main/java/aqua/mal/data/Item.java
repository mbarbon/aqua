package aqua.mal.data;

public abstract class Item {
    public Franchise franchise;
    public LocalCover localCover;
    public boolean isHentai;

    public abstract int itemId();
}
