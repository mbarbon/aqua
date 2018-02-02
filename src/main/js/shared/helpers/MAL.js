function malLink (anime) {
  return `https://myanimelist.net/anime/${anime.animedbId}`
}

function malLinkClick (anime) {
  if (window.ga) {
    window.ga('send', 'event', {
      eventCategory: 'mal_link',
      eventAction: 'click',
      eventLabel: '' + anime.animedbId
    })
  }
}

export { malLink, malLinkClick }
