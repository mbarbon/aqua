// @flow
import { localState } from './Globals'
import PubSub from '../helpers/PubSub'

import type { PubSub3 } from '../helpers/PubSub'
import type { Anime } from '../backend/types'

export default class LocalAnimeList {
  animeList: Array<Anime>
  pubSub: {
    animeList: PubSub3<Array<Anime>, boolean, boolean>
  }

  constructor () {
    this.animeList = []
    this.pubSub = {
      animeList: new PubSub()
    }
  }

  addRating (item: Anime, rating: number) {
    item.userRating = rating
    item.userStatus = 2

    let newRatings = this.animeList.slice()
    for (let i = 0; i < newRatings.length; ++i) {
      if (newRatings[i].animedbId === item.animedbId) {
        newRatings[i] = item
        return localState.setLocalAnimeList(newRatings)
      }
    }

    newRatings.unshift(item)
    return localState.setLocalAnimeList(newRatings)
  }

  removeRating (item: Anime) {
    let newRatings = this.animeList.slice()
    for (let i = 0; i < newRatings.length; ++i) {
      if (newRatings[i].animedbId === item.animedbId) {
        delete item.userRating
        delete item.userStatus
        newRatings.splice(i, 1)
        return localState.setLocalAnimeList(newRatings)
      }
    }
    return Promise.reject('Item not found')
  }

  setAnimeList (
    animeList: Array<Anime>,
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
