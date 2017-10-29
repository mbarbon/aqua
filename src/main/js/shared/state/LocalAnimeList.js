// @flow
import { localState } from './Globals'
import { PubSub3 } from '../helpers/PubSub'

import type { Anime } from '../backend/types'
import type { LocalAnime } from './types'

export default class LocalAnimeList {
  animeList: Array<LocalAnime>
  pubSub: {
    animeList: PubSub3<Array<LocalAnime>, boolean, boolean>
  }

  constructor () {
    this.animeList = []
    this.pubSub = {
      animeList: new PubSub3()
    }
  }

  addRating (item: Anime, rating: number) {
    let itemCopy = {
      ...item,
      userRating: rating,
      userStatus: 2
    }

    let newRatings = this.animeList.slice()
    for (let i = 0; i < newRatings.length; ++i) {
      if (newRatings[i].animedbId === item.animedbId) {
        newRatings[i] = itemCopy
        return localState.setLocalAnimeList(newRatings)
      }
    }

    newRatings.unshift(itemCopy)
    return localState.setLocalAnimeList(newRatings)
  }

  removeRating (item: Anime): Promise<void> {
    let newRatings = this.animeList.slice()
    for (let i = 0; i < newRatings.length; ++i) {
      if (newRatings[i].animedbId === item.animedbId) {
        newRatings.splice(i, 1)
        return localState.setLocalAnimeList(newRatings)
      }
    }
    return Promise.reject('Item not found')
  }

  setAnimeList (
    animeList: Array<LocalAnime>,
    reloadRecommendations: boolean,
    hasChanges: boolean
  ) {
    this.animeList = animeList
    this.pubSub.animeList.notify(animeList, reloadRecommendations, hasChanges)
  }

  getAnimeList () {
    return this.animeList
  }
}
