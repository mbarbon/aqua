// @flow
import React, { Component } from 'react'
import ReactDOM from 'react-dom'
import {
  localState,
  localAnimeList,
  aquaRecommendations
} from '../shared/state/Globals'
import AquaButton from './components/AquaButton'
import AnimeListStars from './components/AnimeListStars'

import type { Anime } from '../shared/backend/types'
import type { LocalAnime } from '../shared/state/types'

const recommendationsValidity = 24 * 3600

type Props = {}

type State = {
  userMode: ?('mal' | 'local' | 'loading'),
  localAnimeList: Array<LocalAnime>
}

class AnimeDetails extends Component<Props, State> {
  userModeChangedCallback: () => void
  localAnimeListChanged: (Array<LocalAnime>, boolean, boolean) => void
  elements: { [number]: HTMLDivElement }
  pageAnime: { [number]: Anime }

  constructor (props: Props) {
    super(props)
    this.state = {
      userMode: 'loading',
      localAnimeList: []
    }
    this.userModeChangedCallback = this.userModeChanged.bind(this)
    this.localAnimeListChanged = this.animeListChanged.bind(this)

    // cache elements to look up stars
    this.elements = {}
    this.pageAnime = {}
    for (let element of window.document.getElementsByClassName(
      'aqua-select-stars'
    )) {
      let ds = element.dataset
      this.elements[ds.animedbid] = element
      this.pageAnime[ds.animedbid] = {
        animedbId: toNumber(ds.animedbid),
        title: ds.title,
        image: ds.image,
        episodes: toNumber(ds.episodes),
        franchiseEpisodes: toNumber(ds.franchiseepisodes),
        genres: ds.genres,
        season: ds.season,
        tags: ds.tags,
        status: toNumber(ds.status)
      }
    }
  }

  componentWillMount () {
    aquaRecommendations.pubSub.userMode.subscribe(this.userModeChangedCallback)
    localAnimeList.pubSub.animeList.subscribe(this.localAnimeListChanged)
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
    aquaRecommendations.pubSub.userMode.unsubscribe(
      this.userModeChangedCallback
    )
    localAnimeList.pubSub.animeList.unsubscribe(this.localAnimeListChanged)
  }

  userModeChanged () {
    let userMode = aquaRecommendations.getUserMode()
    this.setState({ userMode })
  }

  animeListChanged (
    localAnimeList: Array<LocalAnime>,
    reloadRecommendations: boolean,
    hasChanges: boolean
  ) {
    this.setState({ localAnimeList })
  }

  changeLocalRating (anime: Anime, rating: number) {
    if (this.state.userMode == 'mal') {
      return
    } else if (this.state.userMode != 'mal') {
      localState.setLocalUser()
    }
    localAnimeList.addRating(anime, rating)
  }

  render () {
    let localList = this.state.localAnimeList
    if (this.state.userMode == 'local' && localList.length > 0) {
      return (
        <div className='main-page-head-actions'>
          <AquaButton
            label='Recommendations'
            onClick={() => {
              window.location.href = '/'
            }}
          />
          {this.renderStars(localList)}
        </div>
      )
    } else if (this.state.userMode != 'mal') {
      return (
        <div className='main-page-head-actions'>
          {this.renderStars(localList)}
        </div>
      )
    } else if (this.state.userMode == 'mal') {
      return (
        <div className='main-page-head-actions'>
          <AquaButton
            label='Recommendations'
            onClick={() => {
              window.location.href = '/'
            }}
          />
        </div>
      )
    } else {
      return null
    }
  }

  renderStars (localList: Array<LocalAnime>) {
    let animeMap = {}
    for (let anime of localList) {
      animeMap[anime.animedbId] = anime
    }

    let stars = []
    for (let key in this.elements) {
      let animedbId = toNumber(key)
      let element = this.elements[animedbId]
      let anime = this.pageAnime[animedbId]
      let rated = animeMap[animedbId]
      stars.push(
        ReactDOM.createPortal(
          <AnimeListStars
            key={'anime.stars.' + anime.animedbId}
            anime={rated ? rated : anime}
            onChange={this.changeLocalRating.bind(this)}
          />,
          element
        )
      )
    }
    return stars
  }
}

function toNumber (v: string) {
  return parseInt(v || 0, 10)
}

export default AnimeDetails
