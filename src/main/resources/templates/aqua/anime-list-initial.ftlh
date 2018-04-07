<#include "header.ftlh">
<div class="aqua-body">
  <h1>${model.anime.size()?c} anime starting with '${model.headLetter}'</h1>
  <div class="recommendation-list">
<#list model.anime as anime>
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
</#list>
  </div>
</div>
<#include "footer.ftlh">
