<#include "header.ftlh">
<div class="aqua-body">
  <h1>${model.items.size()?c} manga starting with '${model.headLetter}'</h1>
  <div class="recommendation-list">
<#list model.items as item>
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
        </div>
        <div class="details">${item.published}</div>
      </div>
    </div>
</#list>
  </div>
</div>
<#include "footer.ftlh">
