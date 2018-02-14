<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
<#list model.anime as anime>
  <url>
    <loc>https://aqua-recommend.net/anime/details/${anime.animedbId?c}</loc>
  </url>
</#list>
</urlset>