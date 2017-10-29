// @flow

type LocalAnime = {
  animedbId: number,
  title: string,
  image: string,
  episodes: number,
  franchiseEpisodes: number,
  genres: string,
  season: string,
  tags: string,
  status: number,
  userRating: number,
  userStatus: number
}

type FilterState = {
  [string]: boolean
}

type StorageType = 'mal' | 'local'

export type { FilterState, LocalAnime, StorageType }
