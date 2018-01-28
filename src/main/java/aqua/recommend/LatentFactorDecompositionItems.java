package aqua.recommend;

public class LatentFactorDecompositionItems {
  public final ItemItemModel complete;
  public final ItemItemModel airing;

  public LatentFactorDecompositionItems(ItemItemModel complete, ItemItemModel airing) {
    this.complete = complete;
    this.airing = airing;
  }
}
