// @flow
import { PubSub3 } from './PubSub'
import type AquaRecommendations from '../backend/AquaRecommendations'
import type { Anime, Recommendations } from '../backend/types'
import type { LocalAnime, StorageType } from '../state/types'

export default class FilteredRecommendations {
  aquaRecommendations: AquaRecommendations
  recommendations: ?Recommendations
  recommendationTime: ?number
  userMode: ?StorageType
  localAnimeSet: Set<number>
  filteredRecommendations: ?Recommendations
  pubSub: {
    recommendations: PubSub3<Recommendations, number, StorageType>
  }
  includedTags: Array<string>

  constructor (aquaRecommendations: AquaRecommendations) {
    this.aquaRecommendations = aquaRecommendations
    this.pubSub = {
      recommendations: new PubSub3()
    }
    this.includedTags = []
    this.localAnimeSet = new Set()
    aquaRecommendations.pubSub.recommendations.subscribe(
      this._newRecommendations.bind(this)
    )
  }

  setStatusFilter (includedTags: Array<string>) {
    this.includedTags = includedTags
    this._reapplyFilters()
  }

  setLocalAnimeList (animeList: Array<LocalAnime>) {
    if (!animeList) {
      this.localAnimeSet = new Set()
    } else {
      this.localAnimeSet = new Set(animeList.map(a => a.animedbId))
    }
    this._reapplyFilters()
  }

  _reapplyFilters () {
    const recommendations = this.recommendations
    const recommendationTime = this.recommendationTime
    const userMode = this.userMode
    if (
      recommendations == null ||
      recommendationTime == null ||
      userMode == null
    ) {
      return
    }
    let filteredRecommendations = {
      airing: this._filterAnimeList(recommendations.airing),
      completed: this._filterAnimeList(recommendations.completed)
    }
    this.filteredRecommendations = filteredRecommendations
    this.pubSub.recommendations.notify(
      filteredRecommendations,
      recommendationTime,
      userMode
    )
  }

  _newRecommendations (
    recommendations: Recommendations,
    recommendationTime: number,
    userMode: StorageType
  ) {
    this.recommendations = recommendations
    this.recommendationTime = recommendationTime
    this.userMode = userMode
    this._reapplyFilters()
  }

  _filterAnimeList (animeList: Array<Anime>): Array<Anime> {
    return animeList.filter(a => {
      if (this.localAnimeSet.has(a.animedbId)) {
        return false
      } else {
        return !a.tags || this.includedTags.indexOf(a.tags) !== -1
      }
    })
  }
}
