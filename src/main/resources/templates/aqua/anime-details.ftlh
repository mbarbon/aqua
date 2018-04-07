<#include "header.ftlh">
<div class="aqua-body">
  <h1>${model.animeDetails.title}</h1>
  <a title="${model.animeDetails.title}" href="https://myanimelist.net/anime/${model.animeDetails.animedbId?c}" target="_blank">
    <img class="anime-image" src="${model.animeDetails.image}" alt="Aikatsu!" style="max-width: 168px; max-height: 228px; height: auto;">
  </a>
  <div class="anime-details">
    <div class="details" title="${model.animeDetails.genres}">${model.animeDetails.genres}</div>
    <div class="details">${model.animeDetails.episodes} eps
<#if model.animeDetails.franchiseEpisodes != 0>
      (${model.animeDetails.franchiseEpisodes} in franchise)
</#if>
    </div>
    <div class="details">${model.animeDetails.season}</div>
  </div>
  <div style="clear: both;"></div>
</div>
<#list model.recommendations.airing>
<div class="aqua-body">
  <h3 class="recommendation-description">Airing anime people are watching</h3>
  <div class="recommendation-list">
  <#items as anime>
    <div class="recommendation-item">
      <a title="${anime.title}" href="https://myanimelist.net/anime/${model.animeDetails.animedbId?c}">
      <img class="recommendation-image" src="${anime.image}" alt="${anime.title}" style="max-width: 84px; max-height: 114px; height: auto;"></a>
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
</div>
</#list>
<#list model.recommendations.completed>
<div class="aqua-body">
  <h3 class="recommendation-description">People also liked</h3>
  <div class="recommendation-list">
  <#items as anime>
    <div class="recommendation-item">
      <a title="${anime.title}" href="/anime/details/${anime.animedbId?c}">
      <img class="recommendation-image" src="${anime.image}" alt="${anime.title}" style="max-width: 84px; max-height: 114px; height: auto;"></a>
      <div class="recommendation-details">
        <div class="header"><a href="/anime/details/${anime.animedbId?c}" title="${anime.title}">${anime.title}</a></div>
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
</div>
</#list>
<#if model.recommendations.completed.isEmpty() && model.recommendations.airing.isEmpty()>
<div class="aqua-body">
  There are no recommendations for this anime
</div>
</#if>