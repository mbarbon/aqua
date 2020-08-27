<#include "header.ftlh">
<div class="aqua-body"><h1>Browse all manga</h1></div>
<#list model.parts as part>
<section class="aqua-body">
  <h2><a href="/manga/list/${part.headLetter}">${part.headLetter}</a></h2>
  <div class="recommendation-list">
  <#list part.exampleItems as item>
    <div class="recommendation-item">
      <a title="${item.title}" href="/manga/details/${item.itemdbId?c}">
      <img class="recommendation-image item-list-image" src="${item.smallImage}" alt="${item.title}"></a>
      <div class="recommendation-details">
        <div class="header"><a href="/manga/details/${item.itemdbId?c}" title="${item.title}">${item.title}</a></div>
        <div class="details" title="${item.genres}">${item.genres}</div>
        <div class="details">${item.chapters} chp
    <#if item.franchiseChapters != 0>
          (${item.franchiseChapters} in franchise)
    </#if>
        <div class="details">${item.published}</div>
        </div>
      </div>
    </div>
  </#list>
    <a href="/item/list/${part.headLetter}">More...</a>
  </div>
</section>
</#list>
<#include "footer.ftlh">