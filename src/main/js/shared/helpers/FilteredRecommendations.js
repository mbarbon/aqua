// @flow
import PubSub from './PubSub'
import type AquaRecommendations from '../backend/AquaRecommendations'
import type { PubSub3 } from './PubSub'
import type { Anime, Recommendations } from '../backend/types'

export default class FilteredRecommendations {
  aquaRecommendations: AquaRecommendations
  recommendations: ?Recommendations
  recommendationTime: ?number
  userMode: ?string
  localAnimeSet: Set<number>
  filteredRecommendations: ?Recommendations
  pubSub: {
    recommendations: PubSub3<Array<Anime>, number, 'mal' | 'local'>
  }
  includedTags: Array<string>

  constructor (aquaRecommendations: AquaRecommendations) {
    this.aquaRecommendations = aquaRecommendations
    this.pubSub = {
      recommendations: new PubSub()
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

  setLocalAnimeList (animeList: Array<Anime>) {
    if (!animeList) {
      this.localAnimeSet = new Set()
    } else {
      this.localAnimeSet = new Set(animeList.map(a => a.animedbId))
    }
    this._reapplyFilters()
  }

  _reapplyFilters () {
    const recommendations = this.recommendations
    if (recommendations === null || recommendations === undefined) {
      return
    }
    this.filteredRecommendations = {
      airing: this._filterAnimeList(recommendations.airing),
      completed: this._filterAnimeList(recommendations.completed)
    }
    this.pubSub.recommendations.notify(
      this.filteredRecommendations,
      this.recommendationTime,
      this.userMode
    )
  }

  _newRecommendations (
    recommendations: Recommendations,
    recommendationTime: number,
    userMode: string
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
