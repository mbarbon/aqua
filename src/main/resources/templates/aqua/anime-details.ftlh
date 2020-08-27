<#include "header.ftlh">
<section class="aqua-body">
  <h1>${model.itemDetails.title}</h1>
  <a title="${model.itemDetails.title}" href="https://myanimelist.net/anime/${model.itemDetails.itemdbId?c}" target="_blank">
    <img class="anime-details-image" src="${model.itemDetails.mediumImage}" alt="${model.itemDetails.title}">
  </a>
  <div class="anime-details">
    <div class="details" title="${model.itemDetails.genres}">${model.itemDetails.genres}</div>
    <div class="details">${model.itemDetails.episodes} eps
<#if model.itemDetails.franchiseEpisodes != 0>
      (${model.itemDetails.franchiseEpisodes} in franchise)
</#if>
    </div>
    <div class="details">${model.itemDetails.season}</div>
    <div class="details aqua-select-stars"
         data-animedbId="${model.itemDetails.itemdbId?c}"
         data-title="${model.itemDetails.title}"
         data-image="${model.itemDetails.smallImage}"
         data-episodes="${model.itemDetails.episodes?c}"
         data-franchiseEpisodes="${model.itemDetails.franchiseEpisodes?c}"
         data-genres="${model.itemDetails.genres}"
         data-season="${model.itemDetails.season}"
         data-tags="${model.itemDetails.tags!}"
         data-status="${model.itemDetails.status?c}">
    </div>
  </div>
  <div style="clear: both;"></div>
</section>
<#list model.recommendations.airing>
<section class="aqua-body">
  <h3 class="recommendation-description">Airing anime people are watching</h3>
  <div class="recommendation-list">
  <#items as item>
    <div class="recommendation-item">
      <a title="${item.title}" href="https://myanimelist.net/anime/${item.itemdbId?c}">
      <img class="recommendation-image item-list-image" src="${item.smallImage}" alt="${item.title}"></a>
      <div class="recommendation-details">
        <div class="header"><a href="https://myanimelist.net/anime/${item.itemdbId?c}" title="${item.title}">${item.title}</a></div>
        <div class="details" title="${item.genres}">${item.genres}</div>
        <div class="details">${item.episodes} eps
  <#if item.franchiseEpisodes != 0>
          (${item.franchiseEpisodes} in franchise)
  </#if>
        </div>
        <div class="details">${item.season}</div>
      </div>
    </div>
  </#items>
  </div>
</section>
</#list>
<#list model.recommendations.completed>
<section class="aqua-body">
  <h3 class="recommendation-description">People also liked</h3>
  <div class="recommendation-list">
  <#items as item>
    <div class="recommendation-item">
      <a title="${item.title}" href="/anime/details/${aniitemitemme.itemdbId?c}">
      <img class="recommendation-image item-list-image" src="${item.smallImage}" alt="${item.title}"></a>
      <div class="recommendation-details">
        <div class="header"><a href="/anime/details/${item.itemdbId?c}" title="${item.title}">${item.title}</a></div>
        <div class="details" title="${item.genres}">${item.genres}</div>
        <div class="details">${item.episodes} eps
  <#if item.franchiseEpisodes != 0>
          (${item.franchiseEpisodes} in franchise)
  </#if>
        </div>
        <div class="details">${item.season}</div>
        <div class="details aqua-select-stars"
             data-animedbId="${item.itemdbId?c}"
             data-animedbId="${item.itemdbId?c}"
             data-title="${item.title}"
             data-image="${item.smallImage}"
             data-episodes="${item.episodes?c}"
             data-franchiseEpisodes="${item.franchiseEpisodes?c}"
             data-genres="${item.genres}"
             data-season="${item.season}"
             data-tags="${item.tags!}"
             data-status="${item.status?c}">
        </div>
      </div>
    </div>
  </#items>
  </div>
</section>
</#list>
<#if model.recommendations.completed.isEmpty() && model.recommendations.airing.isEmpty()>
<div class="aqua-body">
  There are no recommendations for this anime
</div>
</#if>
<#include "footer.ftlh">
<script type="text/javascript" src="${model.jsBundle}"></script>