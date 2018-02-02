// @flow
import type { Anime, Recommendations } from './types'

type SingleAnimeRecommendation = {
  animeDetails: Anime,
  recommendations: Recommendations
}

export default class AquaSingleAnimeRecommendations {
  fetchRecommendations (animedbId: number): Promise<SingleAnimeRecommendation> {
    let headers = new Headers()

    headers.append('Cache-Control', 'max-age=' + 3600 * 4)
    headers.append('Cache-Control', 'max-stale')

    return fetch('/recommend/anime/' + animedbId, {
      headers: headers
    }).then(response => response.json())
  }
}
