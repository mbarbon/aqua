<#include "header.ftlh">
<section class="aqua-body">
  <h1>${model.animeDetails.title}</h1>
  <a title="${model.animeDetails.title}" href="https://myanimelist.net/anime/${model.animeDetails.animedbId?c}" target="_blank">
    <img class="anime-details-image" src="${model.animeDetails.mediumImage}" alt="${model.animeDetails.title}">
  </a>
  <div class="anime-details">
    <div class="details" title="${model.animeDetails.genres}">${model.animeDetails.genres}</div>
    <div class="details">${model.animeDetails.episodes} eps
<#if model.animeDetails.franchiseEpisodes != 0>
      (${model.animeDetails.franchiseEpisodes} in franchise)
</#if>
    </div>
    <div class="details">${model.animeDetails.season}</div>
    <div class="details aqua-select-stars"
         data-animedbId="${model.animeDetails.animedbId?c}"
         data-title="${model.animeDetails.title}"
         data-image="${model.animeDetails.smallImage}"
         data-episodes="${model.animeDetails.episodes?c}"
         data-franchiseEpisodes="${model.animeDetails.franchiseEpisodes?c}"
         data-genres="${model.animeDetails.genres}"
         data-season="${model.animeDetails.season}"
         data-tags="${model.animeDetails.tags!}"
         data-status="${model.animeDetails.status?c}">
    </div>
  </div>
  <div style="clear: both;"></div>
</section>
<#list model.recommendations.airing>
<section class="aqua-body">
  <h3 class="recommendation-description">Airing anime people are watching</h3>
  <div class="recommendation-list">
  <#items as anime>
    <div class="recommendation-item">
      <a title="${anime.title}" href="https://myanimelist.net/anime/${model.animeDetails.animedbId?c}">
      <img class="recommendation-image anime-list-image" src="${anime.smallImage}" alt="${anime.title}"></a>
      <div class="recommendation-details">
        <div class="header"><a href="https://myanimelist.net/anime/${model.animeDetails.animedbId?c}" title="${anime.title}">${anime.title}</a></div>
        <div class="details" title="${anime.genres}">${anime.genres}</div>
        <div class="details">${anime.episodes} eps
  <#if anime.franchiseEpisodes != 0>
          (${anime.franchiseEpisodes} in franchise)
  </#if>
        </div>
        <div class="details">${anime.season}</div>
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
  <#items as anime>
    <div class="recommendation-item">
      <a title="${anime.title}" href="/anime/details/${anime.animedbId?c}">
      <img class="recommendation-image anime-list-image" src="${anime.smallImage}" alt="${anime.title}"></a>
      <div class="recommendation-details">
        <div class="header"><a href="/anime/details/${anime.animedbId?c}" title="${anime.title}">${anime.title}</a></div>
        <div class="details" title="${anime.genres}">${anime.genres}</div>
        <div class="details">${anime.episodes} eps
  <#if anime.franchiseEpisodes != 0>
          (${anime.franchiseEpisodes} in franchise)
  </#if>
        </div>
        <div class="details">${anime.season}</div>
        <div class="details aqua-select-stars"
             data-animedbId="${anime.animedbId?c}"
             data-animedbId="${anime.animedbId?c}"
             data-title="${anime.title}"
             data-image="${anime.smallImage}"
             data-episodes="${anime.episodes?c}"
             data-franchiseEpisodes="${anime.franchiseEpisodes?c}"
             data-genres="${anime.genres}"
             data-season="${anime.season}"
             data-tags="${anime.tags!}"
             data-status="${anime.status?c}">
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