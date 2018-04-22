// @flow
type Anime = {
  animedbId: number,
  title: string,
  displayTitle?: string,
  image: string,
  episodes: number,
  franchiseEpisodes: number,
  genres: string,
  season: string,
  tags: string,
  status: number
}

type Rating = [number, number, number]

type Recommendations = {
  airing: Array<Anime>,
  completed: Array<Anime>
}

export type { Anime, Rating, Recommendations }
