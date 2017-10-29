// @flow
import React, { Component } from 'react'
import UserMode from './UserMode'
import UserRecommendations from './UserRecommendations'
import {
  localAnimeList,
  localState,
  aquaAutocomplete,
  aquaRecommendations,
  filteredRecommendations
} from '../shared/state/Globals'
import './App.css'

import type { Anime, Recommendations } from '../shared/backend/types'
import type { LocalAnime, StorageType } from '../shared/state/types'

const recommendationsValidity = 24 * 3600

type State = {
  userMode: ?('mal' | 'local' | 'loading'),
  recommendations: ?Recommendations,
  recommendationTime: number,
  autocompleteAnime: ?Array<Anime>,
  queuePosition: ?number,
  localListChanged: boolean
}

class App extends Component<{}, State> {
  userModeChangedCallback: () => void
  recommendationsChangedCallback: (
    p1: Recommendations,
    p2: number,
    p3: StorageType
  ) => void
  filteredRecommendationsChangedCallback: (
    p1: Recommendations,
    p2: number,
    p3: StorageType
  ) => void
  autocompleteResultCallback: (Array<Anime>) => void
  queuePositionCallback: (?number) => void

  constructor (props: {}) {
    super(props)
    this.state = {
      userMode: 'loading',
      recommendations: null,
      recommendationTime: 0,
      autocompleteAnime: null,
      queuePosition: null,
      localListChanged: false
    }
    this.userModeChangedCallback = this.userModeChanged.bind(this)
    this.recommendationsChangedCallback = this.recommendationsChanged.bind(this)
    this.filteredRecommendationsChangedCallback = this.filteredRecommendationsChanged.bind(
      this
    )
    this.autocompleteResultCallback = this.autocompleteResult.bind(this)
    this.queuePositionCallback = this.queuePosition.bind(this)

    localAnimeList.pubSub.animeList.subscribe(
      this.recommendFromLocalList.bind(this)
    )
  }

  componentWillMount () {
    aquaAutocomplete.pubSub.subscribe(this.autocompleteResultCallback)
    aquaRecommendations.pubSub.queuePosition.subscribe(
      this.queuePositionCallback
    )
    aquaRecommendations.pubSub.userMode.subscribe(this.userModeChangedCallback)
    filteredRecommendations.pubSub.recommendations.subscribe(
      this.filteredRecommendationsChangedCallback
    )
    aquaRecommendations.pubSub.recommendations.subscribe(
      this.recommendationsChangedCallback
    )
    localState
      .loadUserMode(recommendationsValidity)
      .then(() => {
        this.setState({ userMode: aquaRecommendations.getUserMode() })
      })
      .catch(error => {
        console.error(error)
        this.setState({ userMode: null })
      })
  }

  componentWillUnmount () {
    aquaAutocomplete.pubSub.unsubscribe(this.autocompleteResultCallback)
    aquaRecommendations.pubSub.queuePosition.unsubscribe(
      this.queuePositionCallback
    )
    aquaRecommendations.pubSub.userMode.unsubscribe(
      this.userModeChangedCallback
    )
    filteredRecommendations.pubSub.recommendations.unsubscribe(
      this.filteredRecommendationsChangedCallback
    )
    aquaRecommendations.pubSub.recommendations.unsubscribe(
      this.recommendationsChangedCallback
    )
  }

  queuePosition (position: ?number) {
    this.setState({ queuePosition: position })
  }

  autocompleteResult (anime: Array<Anime>) {
    this.setState({ autocompleteAnime: anime.length ? anime : null })
  }

  userModeChanged () {
    let userMode = aquaRecommendations.getUserMode()
    this.setState({ userMode, recommendations: null, localListChanged: false })
  }

  filteredRecommendationsChanged (
    recommendations: Recommendations,
    recommendationTime: number,
    userMode: string
  ) {
    if (userMode === this.state.userMode) {
      this.setState({ recommendations, recommendationTime })
    }
  }

  recommendationsChanged (
    recommendations: Recommendations,
    recommendationTime: number,
    userMode: StorageType
  ) {
    if (userMode === this.state.userMode) {
      localState
        .setCachedRecommendations(recommendations, recommendationTime, userMode)
        .catch(error => {
          console.error(error)
        })
    }
  }

  recommendFromLocalList (
    animeList: Array<LocalAnime>,
    reloadRecommendations: boolean,
    hasChanges: boolean
  ) {
    filteredRecommendations.setLocalAnimeList(animeList)
    if (reloadRecommendations && animeList.length) {
      let ratingList = animeList.map(item => [
        item.animedbId,
        item.userStatus,
        item.userRating
      ])
      aquaRecommendations.loadRecommendations('local', ratingList)
      this.setState({ localListChanged: false })
    } else {
      this.setState({ localListChanged: hasChanges })
    }
  }

  canRefresh (): boolean {
    if (this.state.localListChanged) {
      return true
    } else {
      return (
        !!this.state.recommendationTime &&
        Math.abs(Date.now() / 1000 - this.state.recommendationTime) >
          recommendationsValidity
      )
    }
  }

  render () {
    const { userMode, recommendations } = this.state

    if (userMode === 'loading') {
      return null
    } else if (recommendations != null && userMode != null) {
      return (
        <UserRecommendations
          malUsername={aquaRecommendations.getMalUsername()}
          queuePosition={this.state.queuePosition}
          canRefresh={this.canRefresh()}
          userMode={userMode}
          recommendations={recommendations}
          autocompleteAnime={this.state.autocompleteAnime}
          localAnimeList={localAnimeList.getAnimeList()}
        />
      )
    } else if (userMode === 'local') {
      return (
        <UserRecommendations
          malUsername={null}
          queuePosition={null}
          canRefresh={this.canRefresh()}
          userMode={userMode}
          recommendations={recommendations}
          autocompleteAnime={this.state.autocompleteAnime}
          localAnimeList={localAnimeList.getAnimeList()}
        />
      )
    } else if (!userMode || !recommendations) {
      return <UserMode queuePosition={this.state.queuePosition} />
    }
  }
}

export default App
