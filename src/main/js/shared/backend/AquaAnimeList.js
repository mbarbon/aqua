// @flow
import type { Anime, Recommendations } from './types'

type AnimeListExtractItem = {
  headLetter: string,
  exampleAnime: Array<Anime>
}

type AnimeListExtract = {
  parts: Array<AnimeListExtractItem>
}

type AnimeList = {
  anime: Array<Anime>
}

export default class AquaSingleAnimeRecommendations {
  fetchAnimeListExtract (): Promise<AnimeListExtract> {
    let headers = new Headers()

    headers.append('Cache-Control', 'max-age=' + 3600 * 4)
    headers.append('Cache-Control', 'max-stale')

    return fetch('/list/anime-by-initial', {
      headers: headers
    }).then(response => response.json())
  }

  fetchAnimeList (headLetter: string): Promise<AnimeList> {
    let headers = new Headers()

    headers.append('Cache-Control', 'max-age=' + 3600 * 4)
    headers.append('Cache-Control', 'max-stale')

    return fetch('/list/anime-by-initial/' + headLetter, {
      headers: headers
    }).then(response => response.json())
  }
}

export type { AnimeListExtractItem }
