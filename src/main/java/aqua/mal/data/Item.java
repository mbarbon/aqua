package aqua.mal.data;

public abstract class Item {
    public Franchise franchise;
    public LocalCover localCover;
    public boolean isHentai;
    public String image;

    public abstract int itemId();

    public String localImage(String root) {
        if (localCover == null || localCover.coverPath == null) {
            return image;
        } else {
            return root + localCover.coverPath;
        }
    }

    public String smallLocalImage(String root) {
        if (localCover == null || localCover.smallCoverPath == null) {
            return image;
        } else {
            return root + localCover.smallCoverPath;
        }
    }

    public String mediumLocalImage(String root) {
        if (localCover == null || localCover.mediumCoverPath == null) {
            return image;
        } else {
            return root + localCover.mediumCoverPath;
        }
    }
}
